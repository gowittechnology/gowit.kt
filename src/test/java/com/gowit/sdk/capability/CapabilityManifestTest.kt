package com.gowit.sdk.capability

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.gowit.sdk.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first tests in this repository.
 *
 * [FROZEN_ANDROID_VECTOR] is the `android-1.0.5` capability vector from the platform's frozen contract
 * corpus, embedded verbatim rather than fetched, so a drift on either side becomes a failing test in a
 * diff someone reads. Bumping `versionName` therefore requires a new frozen vector on the contract
 * side — which is the intended coupling, not an accident of this test.
 */
class CapabilityManifestTest {
    @Test
    fun installedManifestMatchesTheFrozenContractVector() {
        val encoded = JsonParser.parseString(GowitCapabilities.encodedManifest())
        val frozen = JsonParser.parseString(FROZEN_ANDROID_VECTOR)
        assertEquals(frozen, encoded)
    }

    @Test
    fun rendererVersionIsTheCompiledBuildIdentity() {
        assertEquals(BuildConfig.SDK_VERSION_NAME, GowitCapabilities.rendererVersion)
        assertTrue(GowitCapabilities.rendererVersion.isNotBlank())
    }

    @Test
    fun protocolMajorIsTheServingGenerationNotTheBuild() {
        assertEquals(1, GowitCapabilities.PROTOCOL_MAJOR)
        assertEquals(1, GowitCapabilities.installed.protocolMajor)
    }

    @Test
    fun absentCollectionsStayAbsentOnTheWire() {
        val bare =
            RendererCapability(
                protocolMajor = 1,
                platform = RendererPlatform.ANDROID,
                rendererVersion = "test",
            )
        val encoded = JsonParser.parseString(Gson().toJson(bare)).asJsonObject
        assertFalse("an absent array must not become []", encoded.has("primitives"))
        assertFalse(encoded.has("actions"))
        assertFalse(encoded.has("observation"))
        assertFalse(encoded.has("media"))
        assertFalse(encoded.has("isolation"))
        assertFalse(encoded.has("geometry"))
    }

    @Test
    fun theInstalledManifestCarriesNoRequestTimeGeometry() {
        assertNull(GowitCapabilities.installed.geometry)
    }

    @Test
    fun geometryIsAttachedPerRequestWithoutMutatingTheManifest() {
        val geometry = GeometryCapability(availableWidth = 360, availableHeight = 200, density = 2.0)
        val request = GowitCapabilities.installedIn(geometry)
        assertEquals(geometry, request.geometry)
        assertNull(GowitCapabilities.installed.geometry)
        assertEquals(GowitCapabilities.installed.primitives, request.primitives)
    }

    @Test
    fun declaresAnswersOnlyForTokensTheVectorNames() {
        val vector = GowitCapabilities.installed
        assertTrue(vector.declares(CapabilityToken.Primitive.IMAGE))
        assertTrue(vector.declares(CapabilityToken.Action.OPEN_URL))
        assertTrue(vector.declares(CapabilityToken.Observation.CLICK))
        assertFalse(vector.declares(CapabilityToken.Primitive.VIDEO))
        assertFalse(vector.declares("primitive.something_invented_later"))
    }

    @Test
    fun anAbsentCollectionDeclaresNothingRatherThanEverything() {
        val bare =
            RendererCapability(
                protocolMajor = 1,
                platform = RendererPlatform.ANDROID,
                rendererVersion = "test",
            )
        assertFalse(bare.declares(CapabilityToken.Primitive.IMAGE))
        assertEquals(
            listOf(CapabilityToken.Primitive.IMAGE),
            bare.missingCapabilities(listOf(CapabilityToken.Primitive.IMAGE)),
        )
    }

    @Test
    fun missingCapabilitiesKeepsTheOrderItWasAskedIn() {
        val asked =
            listOf(
                CapabilityToken.Primitive.VIDEO,
                CapabilityToken.Primitive.IMAGE,
                CapabilityToken.Primitive.HTML_FRAME,
            )
        assertEquals(
            listOf(CapabilityToken.Primitive.VIDEO, CapabilityToken.Primitive.HTML_FRAME),
            GowitCapabilities.installed.missingCapabilities(asked),
        )
    }

    @Test
    fun noVideoIsAdvertisedBecauseThereIsNoVideoCode() {
        val vector = GowitCapabilities.installed
        assertFalse(vector.primitives.orEmpty().contains(CapabilityToken.Primitive.VIDEO))
        assertEquals(false, vector.media?.videoLifecycle)
        assertEquals(false, vector.media?.vast)
        assertEquals(false, vector.media?.autoplay)
        assertEquals(emptyList<String>(), vector.media?.codecs)
    }

    @Test
    fun videoCompletionCoverageIsNotAdvertised() {
        assertFalse(
            "a renderer that cannot play video cannot measure a completion",
            GowitCapabilities.installed.observation.orEmpty()
                .contains(CapabilityToken.Observation.VIDEO_COMPLETION),
        )
    }

    @Test
    fun noHtmlFrameAndNoSandboxAreAdvertised() {
        val vector = GowitCapabilities.installed
        assertFalse(vector.primitives.orEmpty().contains(CapabilityToken.Primitive.HTML_FRAME))
        assertEquals(false, vector.isolation?.sandboxed)
    }

    @Test
    fun theOperatingSystemFloorIsPublishedAsProvisional() {
        assertEquals(SupportStatus.PROVISIONAL, GowitCapabilities.support.status)
        assertEquals("Android API 24", GowitCapabilities.support.minimumOs)
        assertTrue(GowitCapabilities.support.note.isNotBlank())
    }

    @Test
    fun aRefusalEncodesItsClassAndCodeUnderTheContractSpelling() {
        val refusal =
            CapabilityRefusal.missingCapability(
                listOf("isolation.sandboxed"),
                "opaque markup",
            )
        val encoded = JsonParser.parseString(Gson().toJson(refusal)).asJsonObject
        assertEquals("incompatible", encoded.get("class").asString)
        assertEquals("MISSING_CAPABILITY", encoded.get("code").asString)
        assertEquals(1, encoded.getAsJsonArray("missing_capabilities").size())
        assertNotNull(encoded.get("message"))
    }

    @Test
    fun aRefusalWithNoMissingTokensCarriesNoSuchField() {
        val refusal = CapabilityRefusal.unsupportedProtocolMajor(7)
        val encoded = JsonParser.parseString(Gson().toJson(refusal)).asJsonObject
        assertFalse(encoded.has("missing_capabilities"))
        assertEquals("UNSUPPORTED_PROTOCOL_MAJOR", encoded.get("code").asString)
        assertEquals("UNSUPPORTED_PROTOCOL_MAJOR", refusal.describe())
    }

    @Test
    fun everyRefusalCodeNameIsItsWireValue() {
        val expected =
            setOf(
                "MISSING_CAPABILITY",
                "UNSUPPORTED_PROTOCOL_MAJOR",
                "GEOMETRY_INCOMPATIBLE",
                "NO_COMPATIBLE_VARIANT",
            )
        assertEquals(expected, RefusalCode.values().map { it.name }.toSet())
    }

    private companion object {
        const val FROZEN_ANDROID_VECTOR = """
            {
              "protocol_major": 1,
              "platform": "android",
              "renderer_version": "1.0.5",
              "primitives": [
                "primitive.image",
                "primitive.text",
                "primitive.container",
                "primitive.entity_card"
              ],
              "actions": [
                "action.open_url"
              ],
              "media": {
                "video_lifecycle": false,
                "autoplay": false,
                "codecs": [],
                "vast": false
              },
              "observation": [
                "observation.root_impression",
                "observation.click",
                "observation.viewability"
              ],
              "isolation": {
                "sandboxed": false
              }
            }
            """
    }
}
