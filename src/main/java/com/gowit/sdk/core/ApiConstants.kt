package com.gowit.sdk.core

/**
 * API constants for Gowit platform
 */
internal object ApiConstants {
    const val ADS_ENDPOINT = "/sdk/ads"
    const val EVENTS_ENDPOINT = "/sdk/events"
    const val SALE_EVENTS_ENDPOINT = "/sdk/sale_events"

    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val CONTENT_TYPE_JSON = "application/json"

    const val HTTP_TIMEOUT_SECONDS = 30L
    const val MAX_RETRIES = 3
}
