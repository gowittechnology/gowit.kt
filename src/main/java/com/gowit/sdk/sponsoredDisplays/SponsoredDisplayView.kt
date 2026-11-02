package com.gowit.sdk.sponsoredDisplays

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gowit.sdk.GowitSdk
import com.gowit.sdk.core.GowitException
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.Ad
import com.gowit.sdk.model.AdContext
import com.gowit.sdk.model.Customer
import com.gowit.sdk.model.PlacementRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Custom view for displaying Gowit sponsored display ads
 *
 * Usage in XML:
 * ```xml
 * <com.gowit.sdk.sponsoredDisplays.SponsoredDisplayView
 *     android:id="@+id/sponsoredDisplayView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * Usage in Activity:
 * ```kotlin
 * val sponsoredDisplayView = findViewById<SponsoredDisplayView>(R.id.sponsoredDisplayView)
 * sponsoredDisplayView.setup(
 *     sponsoredDisplayConfig = SponsoredDisplayConfig.CategorySingle("slot", "category"),
 *     screenName = "main_activity",
 *     onClick = { adId, advertiserId -> handleClick(adId, advertiserId) }
 * )
 * ```
 */
class SponsoredDisplayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private var webView: WebView? = null
        private var imageView: ImageView? = null
        private var loadingIndicator: ProgressBar? = null
        private var errorTextView: TextView? = null

        private var currentAd: Ad? = null
        private var sessionId: String = UUID.randomUUID().toString()
        private var isSetup = false
        private var callbacks: SponsoredDisplayCallbacks? = null
        private var clickHandler: SponsoredDisplayClickHandler? = null
        private var clickInterceptor: SponsoredDisplayClickInterceptor? = null
        private var placement: PlacementRequest? = null
        private var adContext: AdContext? = null
        private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        init {
            setupView()
        }

        private fun setupView() {
            // Create loading indicator
            loadingIndicator =
                ProgressBar(context).apply {
                    layoutParams =
                        LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER,
                        )
                    visibility = View.GONE
                }
            addView(loadingIndicator)

            // Create error text view
            errorTextView =
                TextView(context).apply {
                    layoutParams =
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                            Gravity.CENTER,
                        )
                    gravity = Gravity.CENTER
                    text = "Failed to load sponsored display"
                    textSize = 14f
                    setTextColor(Color.GRAY)
                    visibility = View.GONE
                }
            addView(errorTextView)
        }

        /**
         * Setup the sponsored display view for a placement.
         *
         * @param placement Placement to fill, with optional placement filters and max ads.
         * @param adContext Request context (customer, language, location, region, page context...).
         *   Merged over `Gowit.shared.defaultContext`; the customer is merged field by field.
         * @param sessionId Session used for the request and the view's events. Falls back to
         *   [adContext], then the default context, then a generated ID.
         * @param onClick Notified on every tap (analytics).
         * @param onAdClick Return `true` to handle navigation in the app; `false` lets the SDK
         *   open the destination URL.
         */
        suspend fun setup(
            placement: PlacementRequest,
            adContext: AdContext? = null,
            sessionId: String? = null,
            onClick: SponsoredDisplayClickHandler? = null,
            onAdClick: SponsoredDisplayClickInterceptor? = null,
            callbacks: SponsoredDisplayCallbacks? = null,
        ) {
            if (isSetup) {
                Logger.w("SponsoredDisplayView already setup, ignoring duplicate setup call")
                return
            }

            this.callbacks = callbacks
            this.clickHandler = onClick
            this.clickInterceptor = onAdClick
            this.placement = placement
            this.adContext = adContext
            this.isSetup = true

            Logger.d("Setting up SponsoredDisplayView for placement ${placement.placementId}")

            try {
                this.sessionId = sessionId
                    ?: adContext?.sessionId
                    ?: GowitSdk.shared.defaultContext?.sessionId
                    ?: this.sessionId
            } catch (e: Exception) {
                Logger.e("Error setting up SponsoredDisplayView", e)
                handleError(GowitException.ConfigurationException("Failed to setup sponsored display: ${e.message}"))
                return
            }

            loadAd()
        }

        /**
         * Setup the sponsored display view for a placement (lifecycle-aware version).
         */
        fun setup(
            lifecycleOwner: LifecycleOwner,
            placement: PlacementRequest,
            adContext: AdContext? = null,
            sessionId: String? = null,
            onClick: SponsoredDisplayClickHandler? = null,
            onAdClick: SponsoredDisplayClickInterceptor? = null,
            callbacks: SponsoredDisplayCallbacks? = null,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                setup(placement, adContext, sessionId, onClick, onAdClick, callbacks)
            }
        }

        /**
         * Setup the sponsored display view with configuration
         */
        @Deprecated(
            "Use setup(placement, adContext, ...) to pass customer, language, location and page context.",
            ReplaceWith(
                "setup(PlacementRequest(placementId), AdContext(customer = Customer(customerId = customerId)), sessionId, onClick)",
            ),
        )
        suspend fun setup(
            sponsoredDisplayConfig: SponsoredDisplayConfig,
            customerId: String? = null,
            sessionId: String? = null,
            onClick: SponsoredDisplayClickHandler? = null,
            callbacks: SponsoredDisplayCallbacks? = null,
        ) {
            Logger.d("Setting up SponsoredDisplayView with config: $sponsoredDisplayConfig")
            val locationId = (sponsoredDisplayConfig as? SponsoredDisplayConfig.CustomPlacement)?.locationId
            val adContext =
                AdContext(
                    customer = customerId?.let { Customer(customerId = it) },
                    locationId = locationId,
                )
            setup(
                placement = buildPlacements(sponsoredDisplayConfig).first(),
                adContext = adContext,
                sessionId = sessionId,
                onClick = onClick,
                callbacks = callbacks,
            )
        }

        /**
         * Setup the sponsored display view (lifecycle-aware version)
         */
        @Deprecated(
            "Use setup(lifecycleOwner, placement, adContext, ...) to pass customer, language, location and page context.",
        )
        fun setup(
            lifecycleOwner: LifecycleOwner,
            sponsoredDisplayConfig: SponsoredDisplayConfig,
            customerId: String? = null,
            sessionId: String? = null,
            onClick: SponsoredDisplayClickHandler? = null,
            callbacks: SponsoredDisplayCallbacks? = null,
        ) {
            lifecycleOwner.lifecycleScope.launch {
                @Suppress("DEPRECATION")
                setup(sponsoredDisplayConfig, customerId, sessionId, onClick, callbacks)
            }
        }

        private suspend fun loadAd() {
            val placement = placement ?: return
            try {
                showLoading()
                val response = GowitSdk.shared.getAds(placement, adContext, sessionId)
                handleAdResponse(response.getAds(placement.placementId)?.firstOrNull())
            } catch (e: Exception) {
                Logger.e("Error loading sponsored display", e)
                handleError(GowitException.ConfigurationException("Failed to setup sponsored display: ${e.message}"))
            }
        }

        private fun buildPlacements(sponsoredDisplayConfig: SponsoredDisplayConfig): List<PlacementRequest> {
            return when (sponsoredDisplayConfig) {
                is SponsoredDisplayConfig.CustomPlacement -> {
                    listOf(
                        PlacementRequest(
                            placementId = sponsoredDisplayConfig.placementId,
                            filters = sponsoredDisplayConfig.filters,
                        ),
                    )
                }

                is SponsoredDisplayConfig.CategorySingle -> {
                    listOf(
                        PlacementRequest(
                            placementId = sponsoredDisplayConfig.placementId,
                            filters = sponsoredDisplayConfig.filters + listOf(listOf("category:${sponsoredDisplayConfig.category}")),
                        ),
                    )
                }

                is SponsoredDisplayConfig.CategoryMultiple -> {
                    val categoryFilters = sponsoredDisplayConfig.categories.map { listOf("category:$it") }
                    listOf(
                        PlacementRequest(
                            placementId = sponsoredDisplayConfig.placementId,
                            filters = sponsoredDisplayConfig.filters + categoryFilters,
                        ),
                    )
                }

                is SponsoredDisplayConfig.ProductBased -> {
                    val productFilters = sponsoredDisplayConfig.productIds.map { listOf("product:$it") }
                    listOf(
                        PlacementRequest(
                            placementId = sponsoredDisplayConfig.placementId,
                            filters = sponsoredDisplayConfig.filters + productFilters,
                        ),
                    )
                }
            }
        }

        private fun handleAdResponse(ad: Ad?) {
            hideLoading()

            if (ad == null) {
                Logger.d("No ads returned")
                callbacks?.onNoAds?.invoke()
                showError("No ads available")
                return
            }

            currentAd = ad
            Logger.d("Received ad: ${ad.adId}")

            try {
                displayAd(ad)
                callbacks?.onSponsoredDisplayShown?.invoke(ad)

                // Send impression automatically
                ad.adId?.let { adId ->
                    viewScope.launch {
                        sendImpression(adId)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error displaying ad", e)
                handleError(GowitException.NetworkException("Failed to display ad: ${e.message}", e))
            }
        }

        private fun displayAd(ad: Ad) {
            when {
                !ad.html.isNullOrBlank() -> displayHtmlAd(ad)
                !ad.imgUrl.isNullOrBlank() -> displayImageAd(ad)
                else -> {
                    Logger.w("Ad has no HTML or image content")
                    showError("Invalid ad content")
                }
            }
        }

        private fun displayHtmlAd(ad: Ad) {
            // Remove any existing views
            clearContentViews()

            webView =
                WebView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                callbacks?.onImageLoad?.invoke()
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?,
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                callbacks?.onImageLoadError?.invoke(Exception("WebView error: $description"))
                            }
                        }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    setOnClickListener {
                        handleAdClick(ad)
                    }

                    loadDataWithBaseURL(null, ad.html ?: "", "text/html", "UTF-8", null)
                }

            addView(webView)
        }

        private fun displayImageAd(ad: Ad) {
            // Remove any existing views
            clearContentViews()

            imageView =
                ImageView(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP

                    setOnClickListener {
                        handleAdClick(ad)
                    }
                }

            addView(imageView)

            // Load image using a simple approach
            // In a production app, you'd want to use a proper image loading library like Glide or Picasso
            ad.imgUrl?.let { url ->
                loadImageFromUrl(url, imageView!!)
            }
        }

        private fun loadImageFromUrl(
            url: String,
            imageView: ImageView,
        ) {
            // This is a basic implementation - consider using Glide, Picasso, or Coil in production
            Thread {
                try {
                    val inputStream = java.net.URL(url).openConnection().getInputStream()
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

                    post {
                        imageView.setImageBitmap(bitmap)
                        callbacks?.onImageLoad?.invoke()
                    }
                } catch (e: Exception) {
                    post {
                        Logger.e("Failed to load image: $url", e)
                        callbacks?.onImageLoadError?.invoke(e)
                        showError("Failed to load image")
                    }
                }
            }.start()
        }

        private fun handleAdClick(ad: Ad) {
            Logger.d("Sponsored display clicked: ${ad.adId}")

            // Send click event and call handler
            ad.adId?.let { adId ->
                viewScope.launch {
                    sendClick(adId)
                }
                clickHandler?.invoke(adId, ad.advertiserId ?: "")
            }

            val destinationUrl = ad.redirect?.url
            if (clickInterceptor?.invoke(ad, destinationUrl) == true) {
                Logger.d("Sponsored display click handled by the app")
                return
            }

            // Handle redirect if available
            destinationUrl?.let { url ->
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Logger.e("Failed to open redirect URL: $url", e)
                }
            }
        }

        private suspend fun sendImpression(adId: String) {
            try {
                val sdk = GowitSdk.shared
                sdk.sendImpressionEvent(adId, sessionId)
                Logger.d("Impression sent for ad: $adId")
                callbacks?.onImpressionSent?.invoke(adId)
            } catch (e: Exception) {
                Logger.e("Error sending impression", e)
            }
        }

        private suspend fun sendClick(adId: String) {
            try {
                val sdk = GowitSdk.shared
                sdk.sendClickEvent(adId, sessionId)
                Logger.d("Click sent for ad: $adId")
                callbacks?.onClickSent?.invoke(adId)
            } catch (e: Exception) {
                Logger.e("Error sending click", e)
            }
        }

        private fun handleError(exception: GowitException) {
            hideLoading()

            Logger.e("SponsoredDisplayView error: ${exception.message}", exception)

            callbacks?.onError?.invoke(exception)

            showError("Failed to load sponsored display")
        }

        private fun showLoading() {
            clearContentViews()
            loadingIndicator?.visibility = View.VISIBLE
            errorTextView?.visibility = View.GONE
        }

        private fun hideLoading() {
            loadingIndicator?.visibility = View.GONE
        }

        private fun showError(message: String) {
            clearContentViews()
            errorTextView?.apply {
                text = message
                visibility = View.VISIBLE
            }
        }

        private fun clearContentViews() {
            webView?.let {
                removeView(it)
                it.destroy()
                webView = null
            }

            imageView?.let {
                removeView(it)
                imageView = null
            }
        }

        /**
         * Clean up resources when view is detached
         */
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            clearContentViews()
        }

        /**
         * Get the current ad being displayed
         */
        fun getCurrentAd(): Ad? = currentAd

        /**
         * Get the session ID for this sponsored display view
         */
        fun getSessionId(): String = sessionId

        /**
         * Request a new ad for the same placement, context and session.
         */
        suspend fun refresh() {
            if (!isSetup) {
                Logger.w("SponsoredDisplayView not setup, cannot refresh")
                return
            }

            currentAd = null
            loadAd()
        }
    }
