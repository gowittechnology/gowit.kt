package com.gowit.sdk.render

import com.gowit.sdk.capability.CapabilityRefusal

/**
 * The stages a rendered ad passes through, reported separately because request success, render
 * readiness, exposure and refusal are four different facts and collapsing them is how an empty
 * response comes to fire a success impression.
 */
sealed class AdRenderEvent {
    data class Mounted(val adId: String?) : AdRenderEvent()

    data class Exposed(val adId: String?) : AdRenderEvent()

    data class Refused(val refusal: CapabilityRefusal) : AdRenderEvent()

    data class Disposed(val adId: String?) : AdRenderEvent()
}

/**
 * Owns one rendered ad's lifetime.
 *
 * The impression is fired **once per mounted identity**. A recycled list row hands the same view a
 * different ad, so the guard is keyed on the ad's own id rather than on the view's existence — a
 * view-scoped `hasFired` flag would suppress the second ad's impression entirely and count the first
 * one twice on the way back.
 *
 * The "already exposed" state is a separate flag rather than a non-null check on the last identity,
 * because an ad with no id is still one opportunity: keying on nullability alone would let an
 * unidentified ad expose on every remount, or never.
 *
 * Nothing here retains a view, and nothing here touches the Android framework, so the rule is provable
 * on the JVM rather than only on a device.
 */
class AdRenderLifecycle(private val onEvent: ((AdRenderEvent) -> Unit)? = null) {
    private var exposedAdId: String? = null
    private var hasExposed: Boolean = false

    fun mount(adId: String?) {
        onEvent?.invoke(AdRenderEvent.Mounted(adId))
    }

    /**
     * Report exposure for [adId] unless this identity has already been reported. Returns whether it
     * fired, so a caller can assert the once-only rule.
     */
    fun expose(adId: String?): Boolean {
        if (hasExposed && exposedAdId == adId) return false
        exposedAdId = adId
        hasExposed = true
        onEvent?.invoke(AdRenderEvent.Exposed(adId))
        return true
    }

    fun refuse(refusal: CapabilityRefusal) {
        onEvent?.invoke(AdRenderEvent.Refused(refusal))
    }

    /**
     * Release the identity so a reused view starts clean, and tell the host the surface is gone.
     */
    fun dispose() {
        val adId = exposedAdId
        exposedAdId = null
        hasExposed = false
        onEvent?.invoke(AdRenderEvent.Disposed(adId))
    }
}
