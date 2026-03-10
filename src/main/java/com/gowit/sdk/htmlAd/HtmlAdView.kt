package com.gowit.sdk.htmlAd

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gowit.sdk.GowitSdk
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.Ad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A self-contained view for rendering HTML ads served by the Gowit platform.
 *
 * This view handles:
 * - HTML rendering via an embedded [WebView]
 * - Automatic impression and click event tracking via [GowitSdk]
 * - Link click interception with two behaviours:
 *   - [HtmlClickBehavior.OPEN_IN_BROWSER] — launches the device browser via an `ACTION_VIEW` Intent
 *   - [HtmlClickBehavior.HANDLE_BY_LISTENER] — resolves the redirect chain and delivers the final
 *     URL to your [HtmlAdClickListener]
 * - Ad size parsing from the `size` field (e.g. `"300x250"`) with proportional scale-to-fit
 *
 * ## XML Usage
 * ```xml
 * <com.gowit.sdk.htmlAd.HtmlAdView
 *     android:id="@+id/htmlAdView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * ## Kotlin Usage
 * ```kotlin
 * // Basic — open clicks in the device browser
 * htmlAdView.load(
 *     ad        = ad,
 *     sessionId = sessionId,
 * )
 *
 * // With custom listener for click handling
 * htmlAdView.load(
 *     ad            = ad,
 *     sessionId     = sessionId,
 *     configuration = HtmlAdConfiguration.listenerHandled,
 *     listener      = object : HtmlAdClickListener {
 *         override fun onAdClicked(ad: Ad, destinationUrl: String) {
 *             openInAppBrowser(destinationUrl)
 *         }
 *     },
 * )
 * ```
 */
class HtmlAdView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {

        private var webView: WebView? = null
        private var currentAd: Ad? = null
        private var sessionId: String = UUID.randomUUID().toString()
        private var configuration: HtmlAdConfiguration = HtmlAdConfiguration.default
        private var listener: HtmlAdClickListener? = null
        private var impressionReported = false

        private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private val redirectClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        // MARK: - Public API

        /**
         * Load and display an HTML ad.
         *
         * If [ad] has no [Ad.html] content this call is a no-op.
         *
         * @param ad The [Ad] to display — must have a non-null, non-empty [Ad.html] field
         * @param sessionId Session identifier used for impression and click event tracking
         * @param configuration Visual and click-handling configuration (default: [HtmlAdConfiguration.default])
         * @param listener Optional listener for click events; required when
         *   [HtmlAdConfiguration.clickBehavior] is [HtmlClickBehavior.HANDLE_BY_LISTENER]
         */
        fun load(
            ad: Ad,
            sessionId: String = UUID.randomUUID().toString(),
            configuration: HtmlAdConfiguration = HtmlAdConfiguration.default,
            listener: HtmlAdClickListener? = null,
        ) {
            val html = ad.html?.takeIf { it.isNotBlank() } ?: return

            this.currentAd = ad
            this.sessionId = sessionId
            this.configuration = configuration
            this.listener = listener
            this.impressionReported = false

            applyAdSize(ad)
            setupWebView(html)
        }

        /**
         * Lifecycle-aware variant of [load].
         *
         * Impression tracking is automatically launched in the [lifecycleOwner]'s scope.
         */
        fun load(
            lifecycleOwner: LifecycleOwner,
            ad: Ad,
            sessionId: String = UUID.randomUUID().toString(),
            configuration: HtmlAdConfiguration = HtmlAdConfiguration.default,
            listener: HtmlAdClickListener? = null,
        ) {
            load(ad, sessionId, configuration, listener)
        }

        // MARK: - WebView Setup

        @SuppressLint("SetJavaScriptEnabled")
        private fun setupWebView(html: String) {
            destroyWebView()

            webView =
                WebView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    isVerticalScrollBarEnabled = configuration.isScrollEnabled
                    isHorizontalScrollBarEnabled = false

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = !configuration.allowsInlineMediaPlayback
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                    }

                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                // Only intercept explicit link activations (not the initial HTML load)
                                handleLinkClick(url)
                                return true
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                sendImpressionIfNeeded()
                            }
                        }

                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }

            addView(webView)
        }

        // MARK: - Size Calculation

        private fun applyAdSize(ad: Ad) {
            val adSizePx = parseAdSize(ad.size)
            val screenWidthPx = resources.displayMetrics.widthPixels
            val finalSize =
                if (adSizePx.first > screenWidthPx) {
                    // Scale proportionally to fit screen width
                    val scale = screenWidthPx.toFloat() / adSizePx.first.toFloat()
                    Pair(screenWidthPx, (adSizePx.second * scale).toInt())
                } else {
                    adSizePx
                }
            layoutParams = layoutParams?.also {
                it.width = finalSize.first
                it.height = finalSize.second
            } ?: LayoutParams(finalSize.first, finalSize.second)
        }

        /**
         * Parse an ad size string (e.g. `"300x250"`) into pixel dimensions.
         * Falls back to 300×250 px if the string is absent or malformed.
         */
        private fun parseAdSize(sizeString: String?): Pair<Int, Int> {
            if (sizeString.isNullOrBlank()) return Pair(dp(300), dp(250))
            val parts = sizeString.split("x")
            if (parts.size != 2) return Pair(dp(300), dp(250))
            val w = parts[0].toIntOrNull() ?: return Pair(dp(300), dp(250))
            val h = parts[1].toIntOrNull() ?: return Pair(dp(300), dp(250))
            return Pair(dp(w), dp(h))
        }

        // MARK: - Click Handling

        private fun handleLinkClick(url: String) {
            val ad = currentAd ?: return
            logDebug("Link click detected: $url")

            // Always notify the listener of the raw tap
            listener?.onAdTapped(ad, url)

            when (configuration.clickBehavior) {
                HtmlClickBehavior.OPEN_IN_BROWSER -> openInBrowser(url)
                HtmlClickBehavior.HANDLE_BY_LISTENER -> resolveAndNotifyListener(url, ad)
            }

            // Fire SDK click event
            ad.adId?.let { adId ->
                viewScope.launch {
                    sendClickEvent(adId)
                }
            }
        }

        private fun openInBrowser(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Logger.e("HtmlAdView: failed to open URL in browser: $url", e)
            }
        }

        private fun resolveAndNotifyListener(
            url: String,
            ad: Ad,
        ) {
            logDebug("Resolving redirect chain for: $url")
            viewScope.launch {
                val result =
                    runCatching {
                        resolveRedirectChain(url)
                    }

                result
                    .onSuccess { finalUrl ->
                        logDebug("Redirect resolved: $finalUrl")
                        listener?.onAdClickResolved(ad, Result.success(finalUrl))
                        listener?.onAdClicked(ad, finalUrl)
                    }
                    .onFailure { e ->
                        logDebug("Redirect resolution failed: ${e.message}")
                        listener?.onAdClickResolved(ad, Result.failure(e))
                    }
            }
        }

        /**
         * Follows HTTP redirects and returns the final URL after all hops.
         * OkHttp follows redirects automatically; the final URL is read from the
         * last response's request URL.
         */
        private suspend fun resolveRedirectChain(url: String): String =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).head().build()
                redirectClient.newCall(request).execute().use { response ->
                    response.request.url.toString()
                }
            }

        // MARK: - Event Tracking

        private fun sendImpressionIfNeeded() {
            if (impressionReported) return
            val ad = currentAd ?: return
            val adId = ad.adId ?: return
            impressionReported = true

            viewScope.launch {
                try {
                    GowitSdk.shared.sendImpressionEvent(adId, sessionId)
                    logDebug("Impression sent for ad: $adId")
                } catch (e: Exception) {
                    Logger.e("HtmlAdView: failed to send impression", e)
                }
            }
        }

        private suspend fun sendClickEvent(adId: String) {
            try {
                GowitSdk.shared.sendClickEvent(adId, sessionId)
                logDebug("Click sent for ad: $adId")
            } catch (e: Exception) {
                Logger.e("HtmlAdView: failed to send click event", e)
            }
        }

        // MARK: - View Lifecycle

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            destroyWebView()
        }

        private fun destroyWebView() {
            webView?.let {
                removeView(it)
                it.stopLoading()
                it.destroy()
                webView = null
            }
        }

        /** Returns the [Ad] currently being displayed, or null if none has been loaded */
        fun getCurrentAd(): Ad? = currentAd

        // MARK: - Helpers

        private fun dp(value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

        private fun logDebug(message: String) {
            if (configuration.debugLogging) {
                Log.d("HtmlAdView", message)
            }
        }
    }
