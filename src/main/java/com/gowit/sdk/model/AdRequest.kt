package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Request model for fetching ads (SDK version)
 */
@Parcelize
data class AdRequest(
    // UUID string from config
    val marketplaceId: String,
    val placements: List<PlacementRequest>,
    val sessionId: String,
    val customer: Customer? = null,
    val products: List<Product>? = null,
    val search: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val maxAds: Int? = null,
    val filters: List<String>? = null,
    val pageNumber: Int? = null,
    val locationId: String? = null,
    val regionId: String? = null,
    val language: String? = null,
) : Parcelable {
    /**
     * Builder class for creating AdRequest instances
     */
    class Builder {
        private var placements: List<PlacementRequest> = emptyList()
        private var sessionId: String = ""
        private var customer: Customer? = null
        private var products: List<Product>? = null
        private var search: String? = null
        private var category: String? = null
        private var categoryId: String? = null
        private var maxAds: Int? = null
        private var filters: List<String>? = null
        private var pageNumber: Int? = null
        private var locationId: String? = null
        private var regionId: String? = null

        fun placements(placements: List<PlacementRequest>) = apply { this.placements = placements }

        fun sessionId(sessionId: String) = apply { this.sessionId = sessionId }

        fun customer(customer: Customer) = apply { this.customer = customer }

        fun products(products: List<Product>) = apply { this.products = products }

        fun search(search: String) = apply { this.search = search }

        fun category(category: String) = apply { this.category = category }

        fun categoryId(categoryId: String) = apply { this.categoryId = categoryId }

        fun maxAds(maxAds: Int) = apply { this.maxAds = maxAds }

        fun filters(filters: List<String>) = apply { this.filters = filters }

        fun pageNumber(pageNumber: Int) = apply { this.pageNumber = pageNumber }

        fun locationId(locationId: String) = apply { this.locationId = locationId }

        fun regionId(regionId: String) = apply { this.regionId = regionId }

        fun build(): AdRequest {
            require(sessionId.isNotBlank()) { "Session ID must be provided" }
            require(placements.isNotEmpty()) { "At least one placement must be provided" }

            return AdRequest(
                // Will be set automatically from config
                marketplaceId = "",
                placements = placements,
                sessionId = sessionId,
                customer = customer,
                products = products,
                search = search,
                category = category,
                categoryId = categoryId,
                maxAds = maxAds,
                filters = filters,
                pageNumber = pageNumber,
                locationId = locationId,
                regionId = regionId,
                language = null,
            )
        }
    }
}
