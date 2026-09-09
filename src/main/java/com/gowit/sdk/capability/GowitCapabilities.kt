package com.gowit.sdk.capability

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.gowit.sdk.BuildConfig

/**
 * How firm a support statement is.
 *
 * The operating-system floor is a **release-time** decision, deferred with a provisional default until
 * the installed distribution is measured. Publishing it as [PROVISIONAL] is what keeps a build target
 * from being read as a support commitment nobody made.
 */
enum class SupportStatus {
    @SerializedName("provisional")
    PROVISIONAL,

    @SerializedName("committed")
    COMMITTED,
}

/**
 * What this build says about the operating systems it runs on.
 *
 * [minimumOs] is the library's compiled `minSdk` — a fact. [status] is what that fact may be used for,
 * and it is [SupportStatus.PROVISIONAL] until the floor is fixed at release.
 */
data class SupportStatement(
    @SerializedName("minimum_os")
    val minimumOs: String,
    @SerializedName("status")
    val status: SupportStatus,
    @SerializedName("note")
    val note: String,
)

/**
 * The declared, typed manifest of what this client can render and measure.
 *
 * It exists so that negotiation is a property of the **installed build** rather than of whatever the
 * caller remembers to pass: a caller asks the package what it can do, and the package answers from one
 * place. Adding a capability here without adding the code that executes it is the failure this type is
 * meant to make visible, so every token is justified below.
 *
 * Justification, token by token:
 *
 * - `primitive.image`, `primitive.text`, `primitive.container` — the declarative standard-ad
 *   components in `com.gowit.sdk.render`.
 * - `primitive.entity_card` — `SponsoredDisplayView`, and the entity-card node of the declarative
 *   grammar.
 * - `primitive.video` and `primitive.html_frame` are ABSENT. There is no video code and no VAST code
 *   anywhere in this package, and no HTML display surface of its own: `SponsoredDisplayView` hands
 *   markup to a bare `WebView` with JavaScript enabled, which is a frame without a boundary and is
 *   therefore not a declarable capability. This is the sharpest asymmetry against the iOS client,
 *   which carries a full video and VAST stack.
 * - `action.open_url` — the redirect handler. `action.in_app_browser` is ABSENT: this package has no
 *   in-app browser.
 * - `media` is all-false with no codecs, which is the measured answer rather than silence.
 * - `observation.root_impression`, `observation.click`, `observation.viewability` — the typed
 *   `EventType` vocabulary and the events endpoint. `observation.video_completion` is deliberately
 *   ABSENT: a renderer that cannot play video cannot measure a completion.
 * - `isolation.sandboxed = false` — NOT MEASURED, and therefore false. Declaring `false` is what makes
 *   an opaque-markup format refuse this client, which is the correct outcome.
 */
object GowitCapabilities {
    /**
     * The serving protocol generation this client speaks.
     */
    const val PROTOCOL_MAJOR = 1

    /**
     * The build identity this client reports. Compiled in from the library's `versionName`, not
     * configured, so the client can report its own version rather than leaving the server to guess it.
     */
    val rendererVersion: String = BuildConfig.SDK_VERSION_NAME

    /**
     * The operating-system floor, provisional (see [SupportStatus]).
     */
    val support: SupportStatement =
        SupportStatement(
            minimumOs = "Android API 24",
            status = SupportStatus.PROVISIONAL,
            note =
                "The library's compiled minSdk. The supported-OS floor is a release-time decision and is " +
                    "not fixed by this build; three clients of one product currently compile to three " +
                    "different floors.",
        )

    /**
     * The capability vector for this build.
     */
    val installed: RendererCapability =
        RendererCapability(
            protocolMajor = PROTOCOL_MAJOR,
            platform = RendererPlatform.ANDROID,
            rendererVersion = rendererVersion,
            primitives =
                listOf(
                    CapabilityToken.Primitive.IMAGE,
                    CapabilityToken.Primitive.TEXT,
                    CapabilityToken.Primitive.CONTAINER,
                    CapabilityToken.Primitive.ENTITY_CARD,
                ),
            actions = listOf(CapabilityToken.Action.OPEN_URL),
            media =
                MediaCapability(
                    videoLifecycle = false,
                    autoplay = false,
                    codecs = emptyList(),
                    vast = false,
                ),
            observation =
                listOf(
                    CapabilityToken.Observation.ROOT_IMPRESSION,
                    CapabilityToken.Observation.CLICK,
                    CapabilityToken.Observation.VIEWABILITY,
                ),
            isolation = IsolationCapability(sandboxed = false),
        )

    /**
     * The installed vector with the space actually available at request time.
     */
    fun installedIn(geometry: GeometryCapability): RendererCapability = installed.withGeometry(geometry)

    /**
     * Encode the installed vector for transport. Absent collections stay absent: `null` must not be
     * normalised to `[]`, because the two mean different things, and Gson omits nulls by default.
     */
    fun encodedManifest(): String = Gson().toJson(installed)
}
