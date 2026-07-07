package com.gowit.sdk.videoAd

import com.gowit.sdk.vast.VASTAd
import com.gowit.sdk.vast.VASTError

// MARK: - Loading Behavior

/**
 * Defines what to display while the video ad is loading.
 */
sealed class VideoAdLoadingBehavior {
    /** Hide the view completely until the video is ready (default) */
    object Hidden : VideoAdLoadingBehavior()

    /** Show a custom text label while loading */
    data class Text(val message: String) : VideoAdLoadingBehavior()

    /** Show a solid color placeholder */
    data class ColorPlaceholder(val colorInt: Int) : VideoAdLoadingBehavior()
}

// MARK: - Post-Ad Behavior

/**
 * Defines what happens after the video ad finishes playing.
 */
enum class VideoAdPostAdBehavior {
    /** Loop and replay the same video (default) */
    REPLAY,

    /** Request a new ad from the same VAST URL */
    REFRESH_AD,

    /** Freeze on the last frame */
    SHOW_LAST_FRAME,

    /** Hide the view after completion */
    HIDE,
}

// MARK: - Mute Button Behavior

/**
 * Controls mute button visibility.
 */
enum class VideoAdMuteButtonBehavior {
    /** Always show the mute button */
    ALWAYS_SHOW,

    /** Never show the mute button */
    ALWAYS_HIDE,

    /**
     * Show briefly on scroll/tap then auto-hide after 3 seconds.
     * Only shown when the video has an audio track (default).
     */
    AUTOMATIC,
}

// MARK: - Mute Button Corner

/**
 * Defines which corner of the video view the mute button is anchored to.
 */
enum class VideoAdMuteButtonCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

// MARK: - Video Ad State

/**
 * Represents the current lifecycle state of a [VideoAdView].
 */
sealed class VideoAdState {
    /** Initial state — not yet loaded */
    object Idle : VideoAdState()

    /** Fetching and parsing the VAST tag */
    object Loading : VideoAdState()

    /** Video is ready to play but has not started */
    object Ready : VideoAdState()

    /** Video is actively playing */
    object Playing : VideoAdState()

    /** Video is paused (scrolled out of view or explicitly paused) */
    object Paused : VideoAdState()

    /** Video playback completed */
    object Completed : VideoAdState()

    /** An error occurred — [message] describes the failure */
    data class Error(val message: String) : VideoAdState()

    /** No ad was available in the VAST response */
    object NoAd : VideoAdState()

    /** View is hidden (used with [VideoAdPostAdBehavior.HIDE]) */
    object Hidden : VideoAdState()
}

// MARK: - Video Ad Callbacks

/**
 * Callbacks for [VideoAdView] lifecycle events.
 *
 * All callbacks are invoked on the main thread.
 *
 * Example:
 * ```kotlin
 * val callbacks = VideoAdCallbacks(
 *     onAdLoaded   = { ad  -> /* VAST ad loaded  */ },
 *     onAdStarted  = {       /* playback started */ },
 *     onAdCompleted = {      /* playback ended   */ },
 *     onAdClicked  = { url -> openBrowser(url)   },
 *     onError      = { err -> Log.e("VAST", err.message) },
 *     onStateChanged = { state -> updateUI(state) },
 * )
 * ```
 */
data class VideoAdCallbacks(
    /** Invoked once the VAST response is parsed and the player is ready */
    val onAdLoaded: ((VASTAd) -> Unit)? = null,
    /** Invoked when the video starts playing for the first time */
    val onAdStarted: (() -> Unit)? = null,
    /** Invoked when video playback reaches the end */
    val onAdCompleted: (() -> Unit)? = null,
    /** Invoked when the user taps the ad; [url] is the click-through destination */
    val onAdClicked: ((String) -> Unit)? = null,
    /** Invoked when a [VASTError] prevents the ad from loading or playing */
    val onError: ((VASTError) -> Unit)? = null,
    /** Invoked whenever the [VideoAdState] changes */
    val onStateChanged: ((VideoAdState) -> Unit)? = null,
)

// MARK: - Video Ad Configuration

/**
 * Configuration options for [VideoAdView] behavior and appearance.
 *
 * Use the pre-built [default], [continuous], [singlePlay], or [refreshing] presets,
 * or construct your own with the primary constructor.
 *
 * Example:
 * ```kotlin
 * val config = VideoAdConfiguration(
 *     isMutedByDefault = false,
 *     postAdBehavior   = VideoAdPostAdBehavior.REFRESH_AD,
 *     showAdLabel      = true,
 *     adLabelText      = "Sponsored",
 * )
 * ```
 */
data class VideoAdConfiguration(
    /** What to display while the VAST tag is loading (default: hidden) */
    val loadingBehavior: VideoAdLoadingBehavior = VideoAdLoadingBehavior.Hidden,

    /** What to do after the video finishes (default: replay) */
    val postAdBehavior: VideoAdPostAdBehavior = VideoAdPostAdBehavior.REPLAY,

    /** Whether video starts muted (default: true) */
    val isMutedByDefault: Boolean = true,

    /**
     * Minimum fraction of the view that must be visible to trigger auto-play.
     * Range 0.0–1.0 (default: 0.5 = 50%)
     */
    val visibilityThreshold: Float = 0.5f,

    /** Whether to start playback automatically when the view becomes visible (default: true) */
    val autoPlay: Boolean = true,

    /** Mute button visibility behaviour (default: AUTOMATIC) */
    val muteButtonBehavior: VideoAdMuteButtonBehavior = VideoAdMuteButtonBehavior.AUTOMATIC,

    /** Which corner the mute button is anchored to (default: TOP_START) */
    val muteButtonCorner: VideoAdMuteButtonCorner = VideoAdMuteButtonCorner.TOP_START,

    /** Mute button size in dp (default: 30) */
    val muteButtonSizeDp: Int = 30,

    /** Distance of the mute button from the nearest edges in dp (default: 14) */
    val muteButtonPaddingDp: Int = 14,

    /** Whether to show the "Ad" label badge (default: true) */
    val showAdLabel: Boolean = true,

    /** Text for the ad label badge (default: "Ad") */
    val adLabelText: String = "Ad",

    /** Corner radius for the video container in dp (default: 0) */
    val cornerRadiusDp: Int = 0,

    /** Maximum Wrapper redirect depth (default: 5) */
    val maxWrapperDepth: Int = 5,

    /** Request timeout per network hop in seconds (default: 30) */
    val requestTimeoutSeconds: Long = 30,

    /** Enable debug logging (default: false) */
    val debugLogging: Boolean = false,
) {
    companion object {
        /** Default configuration suitable for most use cases */
        val default = VideoAdConfiguration()

        /** Continuous looping playback, muted */
        val continuous =
            VideoAdConfiguration(
                postAdBehavior = VideoAdPostAdBehavior.REPLAY,
                isMutedByDefault = true,
            )

        /** Single-play ad that freezes on the last frame after completion */
        val singlePlay =
            VideoAdConfiguration(
                postAdBehavior = VideoAdPostAdBehavior.SHOW_LAST_FRAME,
                isMutedByDefault = true,
            )

        /** Refresh-on-complete — fetches a new ad after each playback */
        val refreshing =
            VideoAdConfiguration(
                postAdBehavior = VideoAdPostAdBehavior.REFRESH_AD,
                isMutedByDefault = true,
            )
    }
}
