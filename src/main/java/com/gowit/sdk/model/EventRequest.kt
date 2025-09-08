package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Enum representing different types of events
 */
enum class EventType(val value: String) {
    IMPRESSION("impression"),
    CLICK("click"),
    SALE("sale"),
    VIEWABLE_IMPRESSION("viewable_impression"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromString(value: String): EventType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Request model for reporting events
 */
@Parcelize
data class EventRequest(
    val marketplaceId: String,
    val eventType: EventType,
    val sessionId: String,
    val adId: String? = null,
    val sales: List<Sale>? = null,
) : Parcelable {
    /**
     * Builder class for creating EventRequest instances
     */
    class Builder {
        private var marketplaceId: String = ""
        private var eventType: EventType = EventType.IMPRESSION
        private var sessionId: String = ""
        private var adId: String? = null
        private var sales: List<Sale>? = null

        fun marketplaceId(marketplaceId: String) = apply { this.marketplaceId = marketplaceId }

        fun eventType(eventType: EventType) = apply { this.eventType = eventType }

        fun sessionId(sessionId: String) = apply { this.sessionId = sessionId }

        fun adId(adId: String) = apply { this.adId = adId }

        fun sales(sales: List<Sale>) = apply { this.sales = sales }

        fun build(): EventRequest {
            require(sessionId.isNotBlank()) { "Session ID must be provided" }
            require(marketplaceId.isNotBlank()) { "Marketplace ID must be provided" }

            return EventRequest(
                marketplaceId = marketplaceId,
                eventType = eventType,
                sessionId = sessionId,
                adId = adId,
                sales = sales,
            )
        }
    }
}
