package com.gowit.sdk.vast

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

// MARK: - VAST Response

/**
 * Root VAST response containing one or more ads.
 */
data class VASTResponse(
    /** VAST version string (e.g. "4.2") */
    val version: String,
    /** All ad units in the response */
    val ads: List<VASTAd>,
) {
    /** Returns the first available ad */
    val firstAd: VASTAd? get() = ads.firstOrNull()

    /** Returns true if no ads are available */
    val isEmpty: Boolean get() = ads.isEmpty()
}

// MARK: - VAST Ad

/**
 * Represents a single ad unit which can be InLine or Wrapper.
 */
data class VASTAd(
    /** Unique identifier for the ad */
    val id: String,
    /** Sequence number for ad ordering */
    val sequence: Int? = null,
    /** InLine ad content (mutually exclusive with wrapper) */
    val inLine: VASTInLine? = null,
    /** Wrapper ad content (mutually exclusive with inLine) */
    val wrapper: VASTWrapper? = null,
) {
    /** Returns true if this is an InLine ad */
    val isInLine: Boolean get() = inLine != null

    /** Returns true if this is a Wrapper ad */
    val isWrapper: Boolean get() = wrapper != null

    /**
     * Convenience access to the trigger for firing tracking events.
     * Use [trigger].click(), [trigger].impression(), [trigger].viewability() to fire tracking URLs.
     */
    val trigger: VASTTrigger? get() = inLine?.trigger
}

// MARK: - VAST InLine

/**
 * InLine ad containing the actual ad content.
 */
data class VASTInLine(
    /** Ad system information */
    val adSystem: VASTAdSystem? = null,
    /** Ad title */
    val adTitle: String? = null,
    /** Impression tracking URLs */
    val impressions: List<VASTImpression> = emptyList(),
    /** Error tracking URLs */
    val errors: List<String> = emptyList(),
    /** Viewable impression tracking */
    val viewableImpression: VASTViewableImpression? = null,
    /** Creative elements */
    val creatives: List<VASTCreative> = emptyList(),
    /** Product extensions from VAST response */
    val extensions: List<VASTProduct> = emptyList(),
    /**
     * Trigger for manually firing tracking events (click, impression, viewability).
     * Populated automatically by the parser.
     */
    val trigger: VASTTrigger = VASTTrigger(),
)

// MARK: - VAST Wrapper

/**
 * Wrapper ad that redirects to another VAST tag.
 */
data class VASTWrapper(
    /** Ad system information */
    val adSystem: VASTAdSystem? = null,
    /** URI to the wrapped VAST tag */
    val vastAdTagUri: String,
    /** Impression tracking URLs (fired in addition to wrapped ad) */
    val impressions: List<VASTImpression> = emptyList(),
    /** Error tracking URLs */
    val errors: List<String> = emptyList(),
    /** Viewable impression tracking */
    val viewableImpression: VASTViewableImpression? = null,
    /** Creative elements (tracking additions) */
    val creatives: List<VASTCreative> = emptyList(),
)

// MARK: - VAST Product Extension

/**
 * Product information from VAST Extensions.
 */
data class VASTProduct(
    val advertiserId: String? = null,
    val brand: String? = null,
    val imageUrl: String? = null,
    val name: String? = null,
    val pdpUrl: String? = null,
    val price: Double? = null,
    val rating: Double? = null,
    val sku: String? = null,
    val stockCount: Int? = null,
)

// MARK: - VAST Trigger

/**
 * Trigger for manually firing VAST tracking events.
 * Access via [VASTAd.trigger] to fire click, impression, or viewability tracking URLs.
 *
 * Example:
 * ```kotlin
 * ad.trigger?.click()
 * ad.trigger?.impression()
 * ad.trigger?.viewability()
 * ```
 */
data class VASTTrigger(
    private val impressionUrls: List<String> = emptyList(),
    private val clickTrackingUrls: List<String> = emptyList(),
    private val viewableUrls: List<String> = emptyList(),
) {
    /**
     * Fire click tracking URLs in the background.
     * Call this when the user taps a product and the app navigates to the PDP page.
     */
    fun click() = fire(clickTrackingUrls)

    /** Fire impression tracking URLs in the background. */
    fun impression() = fire(impressionUrls)

    /** Fire viewability tracking URLs in the background. */
    fun viewability() = fire(viewableUrls)

    private fun fire(urls: List<String>) {
        for (urlString in urls) {
            try {
                val request = Request.Builder().url(urlString).build()
                httpClient.newCall(request).enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) { /* silent */ }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            response.close()
                        }
                    },
                )
            } catch (_: Exception) { /* silent */ }
        }
    }

    companion object {
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}

// MARK: - Ad System

/**
 * Information about the ad serving system.
 */
data class VASTAdSystem(
    /** Name of the ad system */
    val name: String,
    /** Version of the ad system */
    val version: String? = null,
)

// MARK: - Impression

/**
 * Impression tracking URL.
 */
data class VASTImpression(
    /** Optional identifier */
    val id: String? = null,
    /** Tracking URL */
    val url: String,
)

// MARK: - Viewable Impression

/**
 * ViewableImpression tracking for viewability measurement.
 */
data class VASTViewableImpression(
    val id: String? = null,
    /** URLs to fire when ad becomes viewable */
    val viewable: List<String> = emptyList(),
    /** URLs to fire when ad is not viewable */
    val notViewable: List<String> = emptyList(),
    /** URLs to fire when viewability is undetermined */
    val viewUndetermined: List<String> = emptyList(),
)

// MARK: - Creative

/** Creative container */
data class VASTCreative(
    val id: String? = null,
    val sequence: Int? = null,
    val adId: String? = null,
    /** Linear video content */
    val linear: VASTLinear? = null,
)

// MARK: - Linear

/** Linear video ad content */
data class VASTLinear(
    /** Video duration in seconds */
    val duration: Double? = null,
    /** Media files for playback */
    val mediaFiles: List<VASTMediaFile> = emptyList(),
    /** Click-through and tracking */
    val videoClicks: VASTVideoClicks? = null,
    /** Tracking events */
    val trackingEvents: List<VASTTrackingEvent> = emptyList(),
    /** Skip offset in seconds (null if not skippable) */
    val skipOffset: Double? = null,
) {
    /**
     * Returns the best media file for the given criteria.
     * Prefers MP4 files and selects the one closest to [preferredWidth].
     */
    fun bestMediaFile(
        preferredWidth: Int = 640,
        preferredType: String = "video/mp4",
    ): VASTMediaFile? {
        val mp4Files = mediaFiles.filter { it.type == preferredType }
        val candidates = if (mp4Files.isEmpty()) mediaFiles else mp4Files
        return candidates.minByOrNull { file ->
            kotlin.math.abs((file.width ?: 0) - preferredWidth)
        }
    }
}

// MARK: - Media File

/** Video media file information */
data class VASTMediaFile(
    /** Video URL */
    val url: String,
    /** Delivery method (progressive, streaming) */
    val delivery: String? = null,
    /** MIME type (e.g. "video/mp4") */
    val type: String? = null,
    /** Video width in pixels */
    val width: Int? = null,
    /** Video height in pixels */
    val height: Int? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val minBitrate: Int? = null,
    val maxBitrate: Int? = null,
    val scalable: Boolean? = null,
    val maintainAspectRatio: Boolean? = null,
)

// MARK: - Video Clicks

/** Video click-through and tracking */
data class VASTVideoClicks(
    /** Click-through URL — the destination when the user clicks */
    val clickThrough: String? = null,
    /** Click tracking URLs — fired when the user clicks */
    val clickTracking: List<String> = emptyList(),
    val customClick: List<String> = emptyList(),
)

// MARK: - Tracking Event

/** VAST tracking event type */
enum class VASTTrackingEventType(val value: String) {
    START("start"),
    FIRST_QUARTILE("firstQuartile"),
    MIDPOINT("midpoint"),
    THIRD_QUARTILE("thirdQuartile"),
    COMPLETE("complete"),
    MUTE("mute"),
    UNMUTE("unmute"),
    PAUSE("pause"),
    RESUME("resume"),
    REWIND("rewind"),
    SKIP("skip"),
    PLAYER_EXPAND("playerExpand"),
    PLAYER_COLLAPSE("playerCollapse"),
    PROGRESS("progress"),
    CREATIVE_VIEW("creativeView"),
    ACCEPT_INVITATION("acceptInvitation"),
    AD_EXPAND("adExpand"),
    AD_COLLAPSE("adCollapse"),
    MINIMIZE("minimize"),
    CLOSE("close"),
    OVERLAY_VIEW_DURATION("overlayViewDuration"),
    OTHER_AD_INTERACTION("otherAdInteraction"),
    ;

    companion object {
        fun from(value: String): VASTTrackingEventType? = entries.find { it.value == value }
    }
}

/** Tracking event with URL */
data class VASTTrackingEvent(
    /** Event type */
    val event: VASTTrackingEventType,
    /** Tracking URL */
    val url: String,
    /** Offset for progress events (in seconds) */
    val offset: Double? = null,
)

// MARK: - VAST Error

/**
 * Errors that can occur during VAST processing.
 */
sealed class VASTError : Exception() {
    class NetworkError(val details: String) : VASTError()
    class ParsingError(val details: String) : VASTError()
    object NoAdsFound : VASTError()
    class WrapperDepthExceeded(val depth: Int) : VASTError()
    object InvalidMediaFile : VASTError()
    object InvalidClickThrough : VASTError()
    class InvalidUrl(val url: String) : VASTError()
    object Timeout : VASTError()
    class Unknown(val details: String) : VASTError()

    override val message: String
        get() =
            when (this) {
                is NetworkError -> "Network error: $details"
                is ParsingError -> "Parsing error: $details"
                is NoAdsFound -> "No ads found in VAST response"
                is WrapperDepthExceeded -> "Wrapper depth exceeded maximum of $depth"
                is InvalidMediaFile -> "No valid media file found"
                is InvalidClickThrough -> "Invalid click-through URL"
                is InvalidUrl -> "Invalid URL: $url"
                is Timeout -> "Request timed out"
                is Unknown -> "Unknown error: $details"
            }

    /** VAST error code for tracking */
    val vastErrorCode: Int
        get() =
            when (this) {
                is NetworkError -> 900
                is ParsingError -> 100
                is NoAdsFound -> 303
                is WrapperDepthExceeded -> 302
                is InvalidMediaFile -> 401
                is InvalidClickThrough -> 900
                is InvalidUrl -> 900
                is Timeout -> 301
                is Unknown -> 900
            }
}
