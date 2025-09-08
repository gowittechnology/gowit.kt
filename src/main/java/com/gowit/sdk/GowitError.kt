package com.gowit.sdk

/**
 * Error types similar to Swift SDK AdError
 */
sealed class GowitError : Exception() {
    data class HttpError(val httpError: Exception) : GowitError() {
        override val message: String?
            get() = "HTTP Error: ${httpError.message}"
    }

    object SerializationError : GowitError() {
        override val message: String
            get() = "Failed to serialize request data"
    }

    data class DeserializationError(val error: Exception, val data: String?) : GowitError() {
        override val message: String
            get() = "Failed to parse response: ${error.message}"
    }

    object InvalidPlacements : GowitError() {
        override val message: String
            get() = "Invalid or empty placement configuration"
    }

    object InvalidAdId : GowitError() {
        override val message: String
            get() = "Ad ID is missing or invalid"
    }

    data class ConfigurationError(override val message: String) : GowitError()

    data class NetworkError(override val message: String) : GowitError()
}

/**
 * Server error response model similar to Swift SDK GowitError
 */
data class GowitServerError(
    val message: String,
    val code: String? = null,
    val details: Map<String, String>? = null,
)
