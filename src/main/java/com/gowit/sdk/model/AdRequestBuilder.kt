package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Product request for ad builder
 */
@Parcelize
data class ProductRequest(
    val productId: String,
    val category: String? = null,
) : Parcelable

/**
 * Builder pattern for creating ad requests similar to Swift SDK
 */
@Parcelize
data class AdRequestBuilder(
    val placements: List<PlacementRequest>,
    val pageNumber: Int = 0,
    val sessionId: String? = null,
    val customer: Customer? = null,
    val products: List<ProductRequest>? = null,
    val search: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val maxAds: Int? = null,
    val filters: List<String>? = null,
    val locationId: String? = null,
    val regionId: String? = null,
) : Parcelable {
    constructor(placements: List<PlacementRequest>) : this(
        placements = placements,
        pageNumber = 0,
        sessionId = null,
        customer = null,
        products = null,
        search = null,
        category = null,
        categoryId = null,
        maxAds = null,
        filters = null,
        locationId = null,
        regionId = null,
    )

    constructor(placementId: Int) : this(
        placements = listOf(PlacementRequest(placementId)),
    )

    fun with(pageNumber: Int): AdRequestBuilder = copy(pageNumber = pageNumber)

    fun with(sessionId: String): AdRequestBuilder = copy(sessionId = sessionId)

    fun with(customer: Customer): AdRequestBuilder = copy(customer = customer)

    fun with(products: List<ProductRequest>): AdRequestBuilder = copy(products = products)

    fun withSearch(search: String): AdRequestBuilder = copy(search = search)

    fun withCategory(category: String): AdRequestBuilder = copy(category = category)

    fun withCategoryId(categoryId: String): AdRequestBuilder = copy(categoryId = categoryId)

    fun withMaxAds(maxAds: Int): AdRequestBuilder = copy(maxAds = maxAds)

    fun withFilters(filters: List<String>): AdRequestBuilder = copy(filters = filters)

    fun withLocationId(locationId: String): AdRequestBuilder = copy(locationId = locationId)

    fun withRegionId(regionId: String): AdRequestBuilder = copy(regionId = regionId)
}
