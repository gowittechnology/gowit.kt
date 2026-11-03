package com.gowit.sdk.render

import com.gowit.sdk.capability.CapabilityRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exposure rule is the one part of the renderer that decides what gets counted, so it is proved on
 * the JVM rather than only on a device.
 */
class AdRenderLifecycleTest {
    @Test
    fun exposureFiresOnceForOneIdentity() {
        val seen = mutableListOf<AdRenderEvent>()
        val lifecycle = AdRenderLifecycle { seen.add(it) }

        lifecycle.mount("ad-1")
        assertTrue(lifecycle.expose("ad-1"))
        assertFalse("a remount of the same ad is not a second opportunity", lifecycle.expose("ad-1"))

        assertEquals(
            listOf<AdRenderEvent>(AdRenderEvent.Mounted("ad-1"), AdRenderEvent.Exposed("ad-1")),
            seen,
        )
    }

    @Test
    fun aRecycledViewExposesTheAdItArrivesAt() {
        val exposed = mutableListOf<String?>()
        val lifecycle =
            AdRenderLifecycle { event ->
                if (event is AdRenderEvent.Exposed) exposed.add(event.adId)
            }

        lifecycle.expose("ad-1")
        lifecycle.dispose()
        lifecycle.expose("ad-2")
        lifecycle.dispose()
        lifecycle.expose("ad-1")

        assertEquals(listOf("ad-1", "ad-2", "ad-1"), exposed)
    }

    @Test
    fun aRecycledViewDoesNotDoubleCountTheAdItLeaves() {
        val exposed = mutableListOf<String?>()
        val lifecycle =
            AdRenderLifecycle { event ->
                if (event is AdRenderEvent.Exposed) exposed.add(event.adId)
            }

        lifecycle.expose("ad-1")
        lifecycle.expose("ad-2")
        lifecycle.expose("ad-2")

        assertEquals(listOf("ad-1", "ad-2"), exposed)
    }

    @Test
    fun disposeReportsTheIdentityItReleases() {
        val seen = mutableListOf<AdRenderEvent>()
        val lifecycle = AdRenderLifecycle { seen.add(it) }

        lifecycle.expose("ad-1")
        lifecycle.dispose()

        assertEquals(AdRenderEvent.Disposed("ad-1"), seen.last())
    }

    @Test
    fun anAdWithoutAnIdentityStillExposesExactlyOnce() {
        val exposed = mutableListOf<String?>()
        val lifecycle =
            AdRenderLifecycle { event ->
                if (event is AdRenderEvent.Exposed) exposed.add(event.adId)
            }

        assertTrue(lifecycle.expose(null))
        assertFalse(lifecycle.expose(null))
        lifecycle.dispose()
        assertTrue("a genuine remount is a new opportunity", lifecycle.expose(null))

        assertEquals(listOf<String?>(null, null), exposed)
    }

    @Test
    fun aRefusalIsReportedAndNeverReachesTheExposurePath() {
        val seen = mutableListOf<AdRenderEvent>()
        val lifecycle = AdRenderLifecycle { seen.add(it) }

        lifecycle.refuse(CapabilityRefusal.unsupportedProtocolMajor(2))

        assertEquals(1, seen.size)
        assertTrue(seen.first() is AdRenderEvent.Refused)
    }

    @Test
    fun anAbsentListenerIsNotAnError() {
        val lifecycle = AdRenderLifecycle()
        lifecycle.mount("ad-1")
        assertTrue(lifecycle.expose("ad-1"))
        lifecycle.dispose()
    }
}
