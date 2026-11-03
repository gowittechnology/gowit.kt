package com.gowit.sdk.render

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gowit.sdk.capability.CapabilityRefusal
import com.gowit.sdk.core.Logger
import java.net.URL

/**
 * Fetches the bytes behind an image node into an [ImageView].
 *
 * It is a seam rather than a pipeline: this library ships no image cache, and a host that already uses
 * one should not be made to run a second. [DefaultAdImageLoader] is the minimum that draws something.
 */
fun interface AdImageLoader {
    fun load(
        url: String,
        target: ImageView,
    )
}

/**
 * The loader used when the host supplies none: one background thread per image, no cache, result
 * dropped if the decode fails. It mirrors what `SponsoredDisplayView` already does in this package,
 * so the declarative renderer does not introduce a second image policy.
 */
object DefaultAdImageLoader : AdImageLoader {
    override fun load(
        url: String,
        target: ImageView,
    ) {
        Thread {
            val bitmap =
                try {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                } catch (error: Exception) {
                    Logger.e("Failed to load ad image: $url", error)
                    null
                }
            if (bitmap != null) {
                target.post { target.setImageBitmap(bitmap) }
            }
        }.start()
    }
}

/**
 * Draws a resolved plan with native views.
 *
 * It renders a [StandardAdPlan], never an `Ad`: resolution has already refused anything this runtime
 * cannot execute, so there is no branch here that could quietly substitute a browser view for a native
 * one. A refusal draws nothing at all — an ad that was never rendered cannot have been seen, and a
 * visible error card in a retailer's app would turn a contract decision into a defect a shopper sees.
 *
 * Host semantics worth knowing:
 *
 * - **Mount is bind; exposure is attachment.** Binding a plan announces the surface; the impression
 *   fires when the view actually reaches a window, and is released on detach so a recycled row starts
 *   clean.
 * - Text sizes are set in SP and alignment follows the view's start edge, so font scaling and RTL are
 *   the platform's business rather than this renderer's.
 * - The media region has a renderer-owned height ceiling. A region that grew to whatever the creative
 *   reported would let one ad push the host's own content off the screen.
 */
class StandardAdView(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    /**
     * Replaced by the host to use its own image pipeline.
     */
    var imageLoader: AdImageLoader = DefaultAdImageLoader

    private var plan: StandardAdPlan? = null
    private var lifecycle: AdRenderLifecycle? = null
    private var onAction: ((AdActionRequest) -> Unit)? = null

    init {
        orientation = LinearLayout.VERTICAL
    }

    /**
     * Draw [plan] and take ownership of its lifetime.
     *
     * [onAction] receives every declared action; when the host supplies none, `action.open_url` falls
     * back to the system browser. There is no path by which an action token this runtime did not
     * declare reaches the host — resolution refused the plan before it got here.
     */
    fun bind(
        plan: StandardAdPlan,
        lifecycle: AdRenderLifecycle,
        onAction: ((AdActionRequest) -> Unit)? = null,
    ) {
        this.plan = plan
        this.lifecycle = lifecycle
        this.onAction = onAction

        removeAllViews()
        val root = plan.root
        setPadding(0, 0, 0, 0)
        if (root is StandardAdNode.EntityCard) {
            val pad = dp(CARD_PADDING_DP)
            setPadding(pad, pad, pad, pad)
        }
        if (root is StandardAdNode.Container || root is StandardAdNode.EntityCard) {
            root.childNodes().forEach { addNode(it, this) }
        } else {
            addNode(root, this)
        }

        // One element for a screen reader: the children are silent and the ad speaks once, from the
        // summary the plan already ordered with the disclosure first.
        contentDescription = plan.accessibilitySummary.takeIf { it.isNotBlank() }
        isFocusable = true
        applyAction(plan.action)
        lifecycle.mount(plan.adId)
    }

    /**
     * Announce a refusal and draw nothing. A refused ad never reaches the exposure path.
     */
    fun refuse(
        refusal: CapabilityRefusal,
        lifecycle: AdRenderLifecycle,
    ) {
        this.plan = null
        this.lifecycle = lifecycle
        removeAllViews()
        setPadding(0, 0, 0, 0)
        contentDescription = null
        isFocusable = false
        applyAction(null)
        lifecycle.refuse(refusal)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val current = plan ?: return
        lifecycle?.expose(current.adId)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycle?.dispose()
    }

    private fun addNode(
        node: StandardAdNode,
        parent: LinearLayout,
    ) {
        when (node) {
            is StandardAdNode.Container -> parent.addView(groupFor(node.children, false))
            is StandardAdNode.EntityCard -> parent.addView(groupFor(node.children, true))
            is StandardAdNode.Image -> parent.addView(imageFor(node))
            is StandardAdNode.Text -> parent.addView(textFor(node))
        }
    }

    private fun groupFor(
        children: List<StandardAdNode>,
        isCard: Boolean,
    ): LinearLayout {
        val group = LinearLayout(context)
        group.orientation = LinearLayout.VERTICAL
        group.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        group.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (isCard) {
            val pad = dp(CARD_PADDING_DP)
            group.setPadding(pad, pad, pad, pad)
        }
        children.forEach { addNode(it, group) }
        return group
    }

    private fun imageFor(node: StandardAdNode.Image): ImageView {
        val image = ImageView(context)
        image.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(MEDIA_MAX_HEIGHT_DP))
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        image.setBackgroundColor(Color.rgb(PLACEHOLDER_GREY, PLACEHOLDER_GREY, PLACEHOLDER_GREY))
        imageLoader.load(node.url, image)
        return image
    }

    private fun textFor(node: StandardAdNode.Text): TextView {
        val text = TextView(context)
        text.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        text.text = node.value
        text.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        text.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        when (node.role) {
            AdSlotRole.HEADLINE -> {
                text.textSize = HEADLINE_TEXT_SP
                text.setTypeface(text.typeface, Typeface.BOLD)
            }

            AdSlotRole.PRICE -> {
                text.textSize = PRICE_TEXT_SP
                text.setTypeface(text.typeface, Typeface.BOLD)
            }

            AdSlotRole.SPONSORSHIP_LABEL -> {
                text.textSize = LABEL_TEXT_SP
                text.setTextColor(Color.GRAY)
            }

            else -> text.textSize = BODY_TEXT_SP
        }
        return text
    }

    private fun applyAction(action: AdActionRequest?) {
        if (action == null) {
            setOnClickListener(null)
            isClickable = false
            return
        }
        isClickable = true
        setOnClickListener {
            val handler = onAction
            if (handler != null) {
                handler(action)
            } else {
                openInSystemBrowser(action.url)
            }
        }
    }

    private fun openInSystemBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Logger.e("No activity can open the ad destination", error)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MEDIA_MAX_HEIGHT_DP = 180
        const val CARD_PADDING_DP = 12
        const val HEADLINE_TEXT_SP = 16f
        const val PRICE_TEXT_SP = 16f
        const val BODY_TEXT_SP = 14f
        const val LABEL_TEXT_SP = 12f
        const val PLACEHOLDER_GREY = 238
    }
}
