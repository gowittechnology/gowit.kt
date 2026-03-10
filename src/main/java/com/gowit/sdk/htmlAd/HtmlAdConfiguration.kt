package com.gowit.sdk.htmlAd

// MARK: - Click Behavior

/**
 * Defines how clicks inside an HTML ad are handled.
 */
enum class HtmlClickBehavior {
    /**
     * Open the clicked URL directly in the device's default browser via an Intent.
     * The browser will follow redirects naturally (default).
     */
    OPEN_IN_BROWSER,

    /**
     * Resolve the redirect chain with OkHttp and pass the final destination URL
     * to the [HtmlAdClickListener]. The listener decides what to do with the URL
     * (e.g., open in a custom in-app browser, deep link, etc.).
     */
    HANDLE_BY_LISTENER,
}

// MARK: - HTML Ad Configuration

/**
 * Configuration options for [HtmlAdView] display and behaviour.
 *
 * Use the pre-built [default] or [listenerHandled] presets, or create your own:
 *
 * ```kotlin
 * val config = HtmlAdConfiguration(
 *     clickBehavior            = HtmlClickBehavior.HANDLE_BY_LISTENER,
 *     maxRedirects             = 5,
 *     allowsInlineMediaPlayback = true,
 *     isScrollEnabled          = false,
 * )
 * ```
 */
data class HtmlAdConfiguration(
    /** How to handle taps on links inside the ad HTML (default: [HtmlClickBehavior.OPEN_IN_BROWSER]) */
    val clickBehavior: HtmlClickBehavior = HtmlClickBehavior.OPEN_IN_BROWSER,

    /**
     * Maximum number of HTTP redirects to follow when resolving URLs
     * (only relevant for [HtmlClickBehavior.HANDLE_BY_LISTENER]; default: 10).
     */
    val maxRedirects: Int = 10,

    /** Whether the WebView should allow inline media playback (default: true) */
    val allowsInlineMediaPlayback: Boolean = true,

    /** Whether the WebView content should be scrollable (default: false) */
    val isScrollEnabled: Boolean = false,

    /** Enable debug logging (default: false) */
    val debugLogging: Boolean = false,
) {
    companion object {
        /**
         * Default configuration: opens clicked URLs in the device's default browser.
         */
        val default = HtmlAdConfiguration()

        /**
         * Configuration for delegate-controlled click handling.
         * Redirects are resolved and passed to [HtmlAdClickListener].
         */
        val listenerHandled =
            HtmlAdConfiguration(
                clickBehavior = HtmlClickBehavior.HANDLE_BY_LISTENER,
            )
    }
}
