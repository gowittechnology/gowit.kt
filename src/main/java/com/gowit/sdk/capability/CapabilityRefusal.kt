package com.gowit.sdk.capability

import com.google.gson.annotations.SerializedName

/**
 * The class a refusal belongs to. A reader branches on `class` before it reads `code`, so that the
 * same invalid tuple gets the same class in every language.
 */
enum class RefusalClass {
    @SerializedName("unauthorized")
    UNAUTHORIZED,

    @SerializedName("incompatible")
    INCOMPATIBLE,

    @SerializedName("unsupported")
    UNSUPPORTED,

    @SerializedName("invalid_content")
    INVALID_CONTENT,

    @SerializedName("conflict")
    CONFLICT,

    /**
     * An upstream outage. NOT [INCOMPATIBLE]: a known absent capability is incompatibility, while an
     * outage is not a statement about the ad.
     */
    @SerializedName("degraded")
    DEGRADED,
}

/**
 * The subset of the platform's closed refusal vocabulary this client can raise locally. The server's
 * vocabulary is wider; a client that meets a code it does not know reports it verbatim rather than
 * reclassifying it.
 *
 * The constant name IS the wire value for every entry, which is pinned by a test rather than by an
 * annotation on each line.
 */
enum class RefusalCode {
    MISSING_CAPABILITY,
    UNSUPPORTED_PROTOCOL_MAJOR,
    GEOMETRY_INCOMPATIBLE,
    NO_COMPATIBLE_VARIANT,
}

/**
 * A structured refusal. A refusal names WHAT was wrong; it never carries advertiser copy, media bytes
 * or markup, which is why there is no field that could hold them.
 */
data class CapabilityRefusal(
    @SerializedName("class")
    val refusalClass: RefusalClass,
    @SerializedName("code")
    val code: RefusalCode,
    /**
     * On [RefusalCode.MISSING_CAPABILITY]: the tokens the variant required and this runtime did not
     * declare. Naming them is the difference between a refusal a caller can act on and one it can only
     * log.
     */
    @SerializedName("missing_capabilities")
    val missingCapabilities: List<String>? = null,
    /**
     * Optional operator-facing English. Never rendered to an advertiser, never parsed.
     */
    @SerializedName("message")
    val message: String? = null,
) {
    /**
     * A short operator-facing rendering of the code and, where there are any, the missing tokens.
     */
    fun describe(): String {
        val tokens = missingCapabilities
        if (tokens.isNullOrEmpty()) return code.name
        return "${code.name}: ${tokens.joinToString(", ")}"
    }

    companion object {
        fun missingCapability(
            tokens: List<String>,
            message: String? = null,
        ): CapabilityRefusal =
            CapabilityRefusal(
                refusalClass = RefusalClass.INCOMPATIBLE,
                code = RefusalCode.MISSING_CAPABILITY,
                missingCapabilities = tokens,
                message = message,
            )

        fun unsupportedProtocolMajor(major: Int): CapabilityRefusal =
            CapabilityRefusal(
                refusalClass = RefusalClass.INCOMPATIBLE,
                code = RefusalCode.UNSUPPORTED_PROTOCOL_MAJOR,
                message = "protocol_major $major",
            )

        fun noCompatibleVariant(message: String): CapabilityRefusal =
            CapabilityRefusal(
                refusalClass = RefusalClass.INCOMPATIBLE,
                code = RefusalCode.NO_COMPATIBLE_VARIANT,
                message = message,
            )
    }
}
