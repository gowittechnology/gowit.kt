package com.gowit.sdk.render

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gowit.sdk.capability.CapabilityRefusal
import com.gowit.sdk.capability.GowitCapabilities
import com.gowit.sdk.model.Ad
import com.gowit.sdk.model.Redirect
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The instrumented example: one standard ad, from a bundled fixture, drawn with native views.
 *
 * It is deliberately hermetic — the fixture's image host does not resolve and the image loader is
 * replaced with one that records instead of fetching, so nothing here depends on a network. The
 * exposure rule is proved on the JVM in `AdRenderLifecycleTest`; what this file proves is that the
 * grammar reaches real Android views, that the disclosure is part of the tree, and that a tap
 * dispatches only the action the vector declared.
 */
@RunWith(AndroidJUnit4::class)
class StandardAdViewInstrumentedTest {
    @Test
    fun theFixtureAdDrawsAsANativeTree() {
        val events = mutableListOf<AdRenderEvent>()
        val view = boundView(events)

        assertEquals(2, view.childCount)
        assertTrue(view.getChildAt(0) is ImageView)
        val label = view.getChildAt(1) as TextView
        assertEquals(StandardAdResolver.SPONSORSHIP_LABEL, label.text.toString())
    }

    @Test
    fun bindingAnnouncesTheMountAndNothingElse() {
        val events = mutableListOf<AdRenderEvent>()
        boundView(events)

        assertEquals(1, events.size)
        assertEquals(AdRenderEvent.Mounted("fixture-ad-0001"), events.first())
    }

    @Test
    fun theWholeAdIsOneAccessibilityElementLedByTheDisclosure() {
        val view = boundView(mutableListOf())

        val summary = view.contentDescription?.toString().orEmpty()
        assertTrue(summary.startsWith(StandardAdResolver.SPONSORSHIP_LABEL))
        for (index in 0 until view.childCount) {
            assertEquals(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                view.getChildAt(index).importantForAccessibility,
            )
        }
    }

    @Test
    fun theMediaRegionKeepsTheRendererOwnedHeightCeiling() {
        val view = boundView(mutableListOf())

        val media = view.getChildAt(0)
        val expected = (MEDIA_MAX_HEIGHT_DP * view.resources.displayMetrics.density).toInt()
        assertEquals(expected, media.layoutParams.height)
    }

    @Test
    fun theImageRegionAsksTheHostLoaderForTheFixtureAddress() {
        val requested = mutableListOf<String>()
        val view = StandardAdView(context())
        onMainThread {
            view.imageLoader = AdImageLoader { url, _ -> requested.add(url) }
            view.bind(planFor(fixtureAd()), AdRenderLifecycle())
        }

        assertEquals(listOf("https://cdn.example.invalid/creative/kettle.png"), requested)
    }

    @Test
    fun aTapDispatchesOnlyTheDeclaredAction() {
        val dispatched = mutableListOf<AdActionRequest>()
        val view = StandardAdView(context())
        onMainThread {
            view.imageLoader = AdImageLoader { _, _ -> }
            view.bind(planFor(fixtureAd()), AdRenderLifecycle()) { dispatched.add(it) }
            view.performClick()
        }

        assertEquals(1, dispatched.size)
        assertEquals("action.open_url", dispatched.first().token)
        assertEquals("https://shop.example.invalid/p/kettle", dispatched.first().url)
    }

    @Test
    fun aRefusalDrawsNothingAndIsNotTappable() {
        val events = mutableListOf<AdRenderEvent>()
        val view = StandardAdView(context())
        onMainThread {
            view.imageLoader = AdImageLoader { _, _ -> }
            view.bind(planFor(fixtureAd()), AdRenderLifecycle { events.add(it) })
            view.refuse(
                CapabilityRefusal.missingCapability(listOf(StandardAdResolver.SANDBOX_TOKEN)),
                AdRenderLifecycle { events.add(it) },
            )
        }

        assertEquals(0, view.childCount)
        assertFalse(view.isClickable)
        assertTrue(events.last() is AdRenderEvent.Refused)
        assertFalse(events.any { it is AdRenderEvent.Exposed })
    }

    @Test
    fun rebindingReplacesTheDrawnTreeRatherThanAppendingToIt() {
        val view = StandardAdView(context())
        onMainThread {
            view.imageLoader = AdImageLoader { _, _ -> }
            view.bind(planFor(fixtureAd()), AdRenderLifecycle())
            view.bind(planFor(fixtureAd()), AdRenderLifecycle())
        }

        assertEquals(2, view.childCount)
    }

    @Test
    fun anEntityCardRootDrawsItsChildrenDirectlyOntoTheAdSurface() {
        val plan =
            StandardAdPlan(
                adId = "fixture-ad-0002",
                root =
                    StandardAdNode.EntityCard(
                        AdSlotRole.ROOT,
                        listOf(
                            StandardAdNode.Text(AdSlotRole.HEADLINE, "Stainless Steel Kettle 1.7L"),
                            StandardAdNode.Text(AdSlotRole.PRICE, "749,90"),
                            StandardAdNode.Text(
                                AdSlotRole.SPONSORSHIP_LABEL,
                                StandardAdResolver.SPONSORSHIP_LABEL,
                            ),
                        ),
                    ),
                action = null,
            )
        val view = StandardAdView(context())
        onMainThread { view.bind(plan, AdRenderLifecycle()) }

        assertEquals(3, view.childCount)
        assertTrue(view.getChildAt(0) is TextView)
        assertTrue(view.contentDescription.toString().startsWith(StandardAdResolver.SPONSORSHIP_LABEL))
    }

    private fun boundView(events: MutableList<AdRenderEvent>): StandardAdView {
        val view = StandardAdView(context())
        onMainThread {
            view.imageLoader = AdImageLoader { _, _ -> }
            view.bind(planFor(fixtureAd()), AdRenderLifecycle { events.add(it) })
        }
        return view
    }

    private fun planFor(ad: Ad): StandardAdPlan {
        val outcome = StandardAdResolver.resolve(ad, GowitCapabilities.installed)
        assertTrue("the fixture must resolve against the installed vector", outcome is StandardAdResolution.Resolved)
        return (outcome as StandardAdResolution.Resolved).plan
    }

    /**
     * The bundled fixture, read from the instrumentation package's own assets and mapped onto the
     * SDK's wire model by hand, so this file states exactly which fields the renderer consumes.
     */
    private fun fixtureAd(): Ad {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val raw =
            instrumentation.context.assets
                .open(FIXTURE_ASSET)
                .bufferedReader()
                .use { it.readText() }
        val ad =
            JSONObject(raw)
                .getJSONArray("placements")
                .getJSONObject(0)
                .getJSONArray("ads")
                .getJSONObject(0)
        val redirect = ad.getJSONObject("redirect")
        return Ad(
            adId = ad.getString("ad_id"),
            creativeId = ad.getInt("creative_id"),
            imgUrl = ad.getString("img_url"),
            redirect = Redirect(url = redirect.getString("url"), type = redirect.getString("type")),
            position = ad.getInt("position"),
            advertiserId = ad.getString("advertiser_id"),
            campaignId = ad.getLong("campaign_id"),
            width = ad.getLong("width"),
            height = ad.getLong("height"),
        )
    }

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private companion object {
        const val FIXTURE_ASSET = "standard-ad-response.json"
        const val MEDIA_MAX_HEIGHT_DP = 180
    }
}
