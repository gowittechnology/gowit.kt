package com.gowit.sdk.render

import com.gowit.sdk.capability.CapabilityToken
import com.gowit.sdk.capability.GowitCapabilities
import com.gowit.sdk.capability.IsolationCapability
import com.gowit.sdk.capability.RefusalClass
import com.gowit.sdk.capability.RefusalCode
import com.gowit.sdk.capability.RendererCapability
import com.gowit.sdk.capability.RendererPlatform
import com.gowit.sdk.model.Ad
import com.gowit.sdk.model.Redirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardAdResolverTest {
    @Test
    fun anImageAdResolvesToAContainerCarryingTheSponsorshipLabel() {
        val plan = resolved(imageAd())
        val root = plan.root
        assertTrue(root is StandardAdNode.Container)
        assertEquals(AdSlotRole.ROOT, root.role)
        val children = root.childNodes()
        assertEquals(2, children.size)
        assertEquals(AdSlotRole.MEDIA, children[0].role)
        assertEquals(AdSlotRole.SPONSORSHIP_LABEL, children[1].role)
        assertEquals(StandardAdResolver.SPONSORSHIP_LABEL, (children[1] as StandardAdNode.Text).value)
    }

    @Test
    fun theRequiredCapabilitiesAreEveryTokenTheTreeAndTheActionNeed() {
        val plan = resolved(imageAd())
        assertEquals(
            listOf(
                CapabilityToken.Primitive.CONTAINER,
                CapabilityToken.Primitive.IMAGE,
                CapabilityToken.Primitive.TEXT,
                CapabilityToken.Action.OPEN_URL,
            ),
            plan.requiredCapabilities,
        )
    }

    @Test
    fun theDisclosureLeadsTheSpokenSummary() {
        val plan = resolved(imageAd())
        assertTrue(plan.accessibilitySummary.startsWith(StandardAdResolver.SPONSORSHIP_LABEL))
    }

    @Test
    fun opaqueMarkupIsRefusedNamingTheSandboxTokenRatherThanDrawnInAWebView() {
        val refusal = refused(imageAd().copy(html = "<div>advertiser markup</div>"))
        assertEquals(RefusalClass.INCOMPATIBLE, refusal.refusalClass)
        assertEquals(RefusalCode.MISSING_CAPABILITY, refusal.code)
        assertEquals(listOf(StandardAdResolver.SANDBOX_TOKEN), refusal.missingCapabilities)
    }

    @Test
    fun opaqueMarkupIsStillRefusedByARuntimeThatDeclaresASandbox() {
        val sandboxed =
            GowitCapabilities.installed.copy(isolation = IsolationCapability(sandboxed = true))
        val outcome = StandardAdResolver.resolve(imageAd().copy(html = "<div>x</div>"), sandboxed)
        val refusal = (outcome as StandardAdResolution.Refused).refusal
        assertEquals(listOf(StandardAdResolver.SANDBOX_TOKEN), refusal.missingCapabilities)
    }

    @Test
    fun anAdWithNoDrawableMediaIsRefused() {
        val refusal = refused(Ad(adId = "ad-1"))
        assertEquals(RefusalCode.NO_COMPATIBLE_VARIANT, refusal.code)
    }

    @Test
    fun anImageAddressThisRuntimeWillNotFetchIsNotDrawable() {
        val refusal = refused(Ad(adId = "ad-1", imgUrl = "file:///data/local/tmp/creative.png"))
        assertEquals(RefusalCode.NO_COMPATIBLE_VARIANT, refusal.code)
    }

    @Test
    fun anUnknownProtocolMajorIsRefusedExplicitly() {
        val future = GowitCapabilities.installed.copy(protocolMajor = 2)
        val outcome = StandardAdResolver.resolve(imageAd(), future)
        val refusal = (outcome as StandardAdResolution.Refused).refusal
        assertEquals(RefusalCode.UNSUPPORTED_PROTOCOL_MAJOR, refusal.code)
    }

    @Test
    fun aClickAddressThisRuntimeWillNotOpenIsRefusedRatherThanDropped() {
        val ad = imageAd().copy(redirect = Redirect(url = "javascript:alert(1)", type = "url"))
        val refusal = refused(ad)
        assertEquals(RefusalCode.NO_COMPATIBLE_VARIANT, refusal.code)
    }

    @Test
    fun anAdWithNoRedirectIsPlannedWithoutAnAction() {
        val plan = resolved(imageAd().copy(redirect = null))
        assertNull(plan.action)
        assertEquals(3, plan.requiredCapabilities.size)
    }

    @Test
    fun theInstalledVectorProducesTheOpenUrlAction() {
        val plan = resolved(imageAd())
        assertEquals(CapabilityToken.Action.OPEN_URL, plan.action?.token)
        assertEquals(CLICK_URL, plan.action?.url)
    }

    @Test
    fun anInAppBrowserIsPreferredByARuntimeThatDeclaresOne() {
        val richer =
            GowitCapabilities.installed.copy(
                actions =
                    listOf(
                        CapabilityToken.Action.OPEN_URL,
                        CapabilityToken.Action.IN_APP_BROWSER,
                    ),
            )
        val outcome = StandardAdResolver.resolve(imageAd(), richer)
        val plan = (outcome as StandardAdResolution.Resolved).plan
        assertEquals(CapabilityToken.Action.IN_APP_BROWSER, plan.action?.token)
    }

    @Test
    fun everyMissingPrimitiveIsNamedNotJustTheFirst() {
        val blind =
            RendererCapability(
                protocolMajor = 1,
                platform = RendererPlatform.ANDROID,
                rendererVersion = "test",
            )
        val refusal = refused(imageAd(), blind)
        assertEquals(RefusalCode.MISSING_CAPABILITY, refusal.code)
        assertEquals(
            listOf(
                CapabilityToken.Primitive.CONTAINER,
                CapabilityToken.Primitive.IMAGE,
                CapabilityToken.Primitive.TEXT,
                CapabilityToken.Action.OPEN_URL,
            ),
            refusal.missingCapabilities,
        )
    }

    @Test
    fun theEntityCardNodeDeclaresItsOwnPrimitive() {
        val card =
            StandardAdNode.EntityCard(
                AdSlotRole.ROOT,
                listOf(StandardAdNode.Text(AdSlotRole.HEADLINE, "Stainless Steel Kettle")),
            )
        assertEquals(
            listOf(CapabilityToken.Primitive.ENTITY_CARD, CapabilityToken.Primitive.TEXT),
            card.requiredCapabilities,
        )
    }

    private fun resolved(
        ad: Ad,
        capability: RendererCapability = GowitCapabilities.installed,
    ): StandardAdPlan = (StandardAdResolver.resolve(ad, capability) as StandardAdResolution.Resolved).plan

    private fun refused(
        ad: Ad,
        capability: RendererCapability = GowitCapabilities.installed,
    ) = (StandardAdResolver.resolve(ad, capability) as StandardAdResolution.Refused).refusal

    private fun imageAd(): Ad =
        Ad(
            adId = "fixture-ad-0001",
            imgUrl = "https://cdn.example.invalid/creative/kettle.png",
            redirect = Redirect(url = CLICK_URL, type = "url"),
            advertiserId = "advertiser-1",
        )

    private companion object {
        const val CLICK_URL = "https://shop.example.invalid/p/kettle"
    }
}
