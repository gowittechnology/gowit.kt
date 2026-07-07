package com.gowit.sdk.htmlAd

import com.gowit.sdk.model.Ad

/**
 * Listener interface for HTML ad click events.
 *
 * All methods have empty default implementations so you only need to override
 * the callbacks that are relevant to your integration.
 *
 * The [onAdClicked] and [onAdClickResolved] methods are only called when
 * [HtmlAdConfiguration.clickBehavior] is set to [HtmlClickBehavior.HANDLE_BY_LISTENER].
 *
 * Example:
 * ```kotlin
 * htmlAdView.load(
 *     ad        = ad,
 *     sessionId = sessionId,
 *     listener  = object : HtmlAdClickListener {
 *         override fun onAdTapped(ad: Ad, clickedUrl: String) {
 *             analytics.track("html_ad_tap")
 *         }
 *         override fun onAdClicked(ad: Ad, destinationUrl: String) {
 *             // Open in your custom in-app browser
 *             openInAppBrowser(destinationUrl)
 *         }
 *     },
 * )
 * ```
 */
interface HtmlAdClickListener {
    /**
     * Called whenever the user taps a link in the ad, before any redirect resolution.
     *
     * This is always invoked regardless of [HtmlAdConfiguration.clickBehavior].
     * Useful for tracking the raw tap event or for analytics.
     *
     * @param ad The ad that was tapped
     * @param clickedUrl The raw URL that was clicked inside the WebView
     */
    fun onAdTapped(
        ad: Ad,
        clickedUrl: String,
    ) {}

    /**
     * Called only when [HtmlAdConfiguration.clickBehavior] is [HtmlClickBehavior.HANDLE_BY_LISTENER].
     *
     * Provides the final destination URL after following all HTTP redirects.
     * Implement this to open the URL in a custom browser, handle deep links, etc.
     *
     * @param ad The ad that was clicked
     * @param destinationUrl Final URL after all redirects have been resolved
     */
    fun onAdClicked(
        ad: Ad,
        destinationUrl: String,
    ) {}

    /**
     * Called only when [HtmlAdConfiguration.clickBehavior] is [HtmlClickBehavior.HANDLE_BY_LISTENER].
     *
     * Provides the full result of redirect resolution — either a success with the
     * final URL and redirect count, or a failure with the underlying error.
     * Useful for analytics and debugging.
     *
     * @param ad The ad that was clicked
     * @param result [Result] wrapping the final URL string on success, or an [Exception] on failure
     */
    fun onAdClickResolved(
        ad: Ad,
        result: Result<String>,
    ) {}
}
