package com.gowit.sdk.capability

import com.google.gson.annotations.SerializedName

/**
 * The platform a renderer runs on.
 */
enum class RendererPlatform {
    @SerializedName("web")
    WEB,

    @SerializedName("ios")
    IOS,

    @SerializedName("android")
    ANDROID,
}

/**
 * Playback capability, declared separately from the video **primitive** because parsing is not
 * playing: a client can carry a VAST parser and still have no working player, and that state must be
 * expressible. This client has neither, which is why every field below is `false` or empty rather than
 * absent — an explicit "no" is a measured answer, and absence is only silence.
 *
 * Every field carries an explicit `@SerializedName` because a consumer's R8 may rename fields this
 * library does not keep, and the wire name must survive that.
 */
data class MediaCapability(
    @SerializedName("video_lifecycle")
    val videoLifecycle: Boolean? = null,
    @SerializedName("autoplay")
    val autoplay: Boolean? = null,
    @SerializedName("codecs")
    val codecs: List<String>? = null,
    @SerializedName("vast")
    val vast: Boolean? = null,
)

/**
 * The ACTUAL space available at request time — distinct from a placement's logical outer opportunity
 * and from a template region's own size. It is a property of the request, not of the installed build,
 * which is why it is absent from the static manifest and supplied by [RendererCapability.withGeometry].
 */
data class GeometryCapability(
    @SerializedName("available_width")
    val availableWidth: Int? = null,
    @SerializedName("available_height")
    val availableHeight: Int? = null,
    @SerializedName("density")
    val density: Double? = null,
    @SerializedName("height_may_change")
    val heightMayChange: Boolean? = null,
)

/**
 * What the runtime can actually ENFORCE around opaque markup.
 *
 * Declared separately from `primitive.html_frame` because a frame is a LAYOUT boundary and a sandbox
 * is a SECURITY boundary. A format whose content profile is opaque markup requires [sandboxed] and
 * will refuse a client that only has the frame — which is the correct answer, not a defect.
 */
data class IsolationCapability(
    @SerializedName("sandboxed")
    val sandboxed: Boolean? = null,
    @SerializedName("restricted_navigation")
    val restrictedNavigation: Boolean? = null,
    @SerializedName("restricted_storage")
    val restrictedStorage: Boolean? = null,
    @SerializedName("bridge_version")
    val bridgeVersion: Int? = null,
)

/**
 * What this runtime DECLARES it can draw, do, play and observe.
 *
 * The negotiation rule, in one place, so three decoders cannot disagree:
 *
 * 1. The client sends its vector on the ad request. A vector is not a claim of support for anything it
 *    does not name.
 * 2. The server evaluates one tuple per candidate and returns either a resolved render plan or a
 *    structured reason. It never returns a plan the vector cannot execute.
 * 3. An ABSENT array or object means "declares nothing of this kind". It never means "declares
 *    everything" — which is why every collection here is nullable and `null` is preserved through a
 *    round trip rather than normalised to empty.
 * 4. An UNKNOWN TOKEN in a vector is ignored, so a newer client cannot be refused by an older server
 *    for saying more than it was asked.
 * 5. An unknown FIELD in this envelope is ignored by both sides, so v1 stays additive. Gson gives that
 *    for free; do not add a strict-key check.
 * 6. [protocolMajor] is the one field whose mismatch is fatal in both directions.
 * 7. NO VECTOR AT ALL is legal and means the legacy generation.
 */
data class RendererCapability(
    @SerializedName("protocol_major")
    val protocolMajor: Int,
    @SerializedName("platform")
    val platform: RendererPlatform,
    @SerializedName("renderer_version")
    val rendererVersion: String,
    @SerializedName("primitives")
    val primitives: List<String>? = null,
    @SerializedName("actions")
    val actions: List<String>? = null,
    @SerializedName("media")
    val media: MediaCapability? = null,
    @SerializedName("observation")
    val observation: List<String>? = null,
    @SerializedName("geometry")
    val geometry: GeometryCapability? = null,
    @SerializedName("isolation")
    val isolation: IsolationCapability? = null,
) {
    /**
     * Whether this vector names [token]. Absence is the answer for an absent collection: "declares
     * nothing of this kind" is not "declares everything".
     */
    fun declares(token: String): Boolean {
        if (primitives?.contains(token) == true) return true
        if (actions?.contains(token) == true) return true
        if (observation?.contains(token) == true) return true
        return false
    }

    /**
     * The subset of [required] this vector does not declare, in the order asked. An empty result means
     * the variant is executable here.
     */
    fun missingCapabilities(required: List<String>): List<String> = required.filter { !declares(it) }

    /**
     * The same vector with request-time geometry attached. Geometry belongs to the request, not to the
     * installed build, so this returns a copy and never mutates the manifest.
     */
    fun withGeometry(geometry: GeometryCapability): RendererCapability = copy(geometry = geometry)
}
