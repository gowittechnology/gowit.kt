package com.gowit.sdk.vast

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fire-and-forget event tracker for VAST events.
 *
 * All tracking requests are sent asynchronously and failures are silently logged.
 * Use the [shared] singleton to fire events from anywhere in your app.
 *
 * Example:
 * ```kotlin
 * // Fire impressions when the ad is shown
 * VASTEventTracker.shared.fireImpressions(ad.inLine?.impressions ?: emptyList())
 *
 * // Fire quartile events during playback
 * VASTEventTracker.shared.checkQuartileEvents(
 *     currentTimeSec, durationSec, trackingEvents, firedQuartiles
 * )
 * ```
 */
class VASTEventTracker private constructor() {

    /** Enable/disable debug logging for tracking requests */
    var debugLoggingEnabled: Boolean = false

    /** Timeout for tracking requests in seconds (default: 10) */
    var timeoutSeconds: Long = 10

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // MARK: - Impression Tracking

    /** Fire all impression URLs for an InLine ad */
    fun fireImpressions(impressions: List<VASTImpression>) {
        impressions.forEach { fireUrl(it.url, "impression") }
    }

    /** Fire a single impression URL */
    fun fireImpression(impression: VASTImpression) {
        fireUrl(impression.url, "impression")
    }

    // MARK: - Error Tracking

    /**
     * Fire error tracking URLs, replacing the [ERRORCODE] macro.
     *
     * @param errorUrls List of error tracking URL templates
     * @param errorCode VAST error code to substitute into [ERRORCODE] macro
     */
    fun fireErrors(
        errorUrls: List<String>,
        errorCode: Int,
    ) {
        errorUrls.forEach { url ->
            val resolved = url.replace("[ERRORCODE]", errorCode.toString())
            fireUrl(resolved, "error")
        }
    }

    /** Fire a single error tracking URL, replacing the [ERRORCODE] macro */
    fun fireError(
        errorUrl: String,
        errorCode: Int,
    ) {
        val resolved = errorUrl.replace("[ERRORCODE]", errorCode.toString())
        fireUrl(resolved, "error")
    }

    // MARK: - Viewable Impression Tracking

    /** Fire viewable impression URLs (ad was sufficiently visible) */
    fun fireViewable(viewableImpression: VASTViewableImpression?) {
        viewableImpression?.viewable?.forEach { fireUrl(it, "viewable") }
    }

    /** Fire not-viewable impression URLs */
    fun fireNotViewable(viewableImpression: VASTViewableImpression?) {
        viewableImpression?.notViewable?.forEach { fireUrl(it, "notViewable") }
    }

    /** Fire view-undetermined impression URLs */
    fun fireViewUndetermined(viewableImpression: VASTViewableImpression?) {
        viewableImpression?.viewUndetermined?.forEach { fireUrl(it, "viewUndetermined") }
    }

    // MARK: - Tracking Events

    /** Fire all matching tracking events for the given [eventType] */
    fun fireTrackingEvent(
        eventType: VASTTrackingEventType,
        from: List<VASTTrackingEvent>,
    ) {
        from.filter { it.event == eventType }.forEach { fireUrl(it.url, eventType.value) }
    }

    /** Fire the start tracking event (0%) */
    fun fireStart(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.START, from)

    /** Fire the complete tracking event (100%) */
    fun fireComplete(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.COMPLETE, from)

    /** Fire the first quartile tracking event (25%) */
    fun fireFirstQuartile(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.FIRST_QUARTILE, from)

    /** Fire the midpoint tracking event (50%) */
    fun fireMidpoint(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.MIDPOINT, from)

    /** Fire the third quartile tracking event (75%) */
    fun fireThirdQuartile(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.THIRD_QUARTILE, from)

    /** Fire the mute tracking event */
    fun fireMute(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.MUTE, from)

    /** Fire the unmute tracking event */
    fun fireUnmute(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.UNMUTE, from)

    /** Fire the pause tracking event */
    fun firePause(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.PAUSE, from)

    /** Fire the resume tracking event */
    fun fireResume(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.RESUME, from)

    /** Fire the skip tracking event */
    fun fireSkip(from: List<VASTTrackingEvent>) = fireTrackingEvent(VASTTrackingEventType.SKIP, from)

    // MARK: - Click Tracking

    /** Fire all click tracking URLs */
    fun fireClickTracking(videoClicks: VASTVideoClicks?) {
        videoClicks?.clickTracking?.forEach { fireUrl(it, "clickTracking") }
    }

    /** Fire custom click URLs */
    fun fireCustomClick(videoClicks: VASTVideoClicks?) {
        videoClicks?.customClick?.forEach { fireUrl(it, "customClick") }
    }

    // MARK: - Quartile Tracking Helper

    /**
     * Check and fire quartile tracking events based on playback progress.
     *
     * Call this periodically during video playback. Already-fired quartiles are
     * tracked in [firedQuartiles] and will not be re-fired.
     *
     * @param currentTimeSec Current playback position in seconds
     * @param durationSec Total video duration in seconds
     * @param trackingEvents All VAST tracking events for the linear creative
     * @param firedQuartiles Mutable set of already-fired quartile events (updated in place)
     */
    fun checkQuartileEvents(
        currentTimeSec: Double,
        durationSec: Double,
        trackingEvents: List<VASTTrackingEvent>,
        firedQuartiles: MutableSet<VASTTrackingEventType>,
    ) {
        if (durationSec <= 0) return
        val progress = currentTimeSec / durationSec

        if (progress >= 0.25 && firedQuartiles.add(VASTTrackingEventType.FIRST_QUARTILE)) {
            fireFirstQuartile(trackingEvents)
        }
        if (progress >= 0.50 && firedQuartiles.add(VASTTrackingEventType.MIDPOINT)) {
            fireMidpoint(trackingEvents)
        }
        if (progress >= 0.75 && firedQuartiles.add(VASTTrackingEventType.THIRD_QUARTILE)) {
            fireThirdQuartile(trackingEvents)
        }
    }

    /**
     * Check and fire progress tracking events based on current playback time.
     *
     * @param currentTimeSec Current playback position in seconds
     * @param durationSec Total video duration in seconds
     * @param trackingEvents All VAST tracking events for the linear creative
     * @param firedProgress Mutable set of already-fired progress offsets (updated in place)
     */
    fun checkProgressEvents(
        currentTimeSec: Double,
        durationSec: Double,
        trackingEvents: List<VASTTrackingEvent>,
        firedProgress: MutableSet<Double>,
    ) {
        trackingEvents
            .filter { it.event == VASTTrackingEventType.PROGRESS }
            .forEach { event ->
                val offset = event.offset ?: return@forEach
                if (currentTimeSec >= offset && firedProgress.add(offset)) {
                    fireUrl(event.url, "progress")
                }
            }
    }

    // MARK: - Private

    private fun fireUrl(
        urlString: String,
        eventType: String,
    ) {
        if (urlString.isBlank()) return

        try {
            val request = Request.Builder().url(urlString).build()
            logDebug("Firing $eventType: $urlString")
            httpClient.newCall(request).enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        logDebug("$eventType failed: ${e.message}")
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        logDebug("$eventType response: ${response.code}")
                        response.close()
                    }
                },
            )
        } catch (e: Exception) {
            logDebug("Invalid URL for $eventType: $urlString")
        }
    }

    private fun logDebug(message: String) {
        if (debugLoggingEnabled) {
            android.util.Log.d("VASTEventTracker", message)
        }
    }

    companion object {
        /** Shared singleton instance */
        val shared: VASTEventTracker by lazy { VASTEventTracker() }
    }
}
