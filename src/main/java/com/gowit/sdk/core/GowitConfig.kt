package com.gowit.sdk.core

/**
 * Configuration for Gowit SDK
 */
data class GowitConfig(
    val hostname: String,
    val marketplaceId: String,
    val enableAutoImpression: Boolean = false,
    val enableLogging: Boolean = false,
    val timeoutSeconds: Long = ApiConstants.HTTP_TIMEOUT_SECONDS,
    val maxRetries: Int = ApiConstants.MAX_RETRIES,
) {
    /**
     * Get the base URL from hostname
     */
    val baseUrl: String
        get() =
            if (hostname.startsWith("http://") || hostname.startsWith("https://")) {
                hostname
            } else {
                "https://$hostname"
            }

    /**
     * Builder class for creating GowitConfig instances
     */
    class Builder {
        private var hostname: String = ""
        private var marketplaceId: String = ""
        private var enableAutoImpression: Boolean = false
        private var enableLogging: Boolean = false
        private var timeoutSeconds: Long = ApiConstants.HTTP_TIMEOUT_SECONDS
        private var maxRetries: Int = ApiConstants.MAX_RETRIES

        fun hostname(hostname: String) = apply { this.hostname = hostname }

        fun marketplaceId(marketplaceId: String) = apply { this.marketplaceId = marketplaceId }

        fun enableAutoImpression(enable: Boolean = true) = apply { this.enableAutoImpression = enable }

        fun enableLogging(enable: Boolean = true) = apply { this.enableLogging = enable }

        fun timeoutSeconds(timeout: Long) = apply { this.timeoutSeconds = timeout }

        fun maxRetries(retries: Int) = apply { this.maxRetries = retries }

        fun build(): GowitConfig {
            require(hostname.isNotBlank()) { "Hostname must be provided" }
            require(marketplaceId.isNotBlank()) { "Marketplace ID must be provided" }

            return GowitConfig(
                hostname = hostname,
                marketplaceId = marketplaceId,
                enableAutoImpression = enableAutoImpression,
                enableLogging = enableLogging,
                timeoutSeconds = timeoutSeconds,
                maxRetries = maxRetries,
            )
        }
    }
}
