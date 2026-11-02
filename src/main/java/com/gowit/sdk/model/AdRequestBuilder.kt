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
    val categories: List<String>? = null,
    val categoryIdStructures: List<String>? = null,
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
    val language: String? = null,
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
        language = null,
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

    fun withLanguage(language: String): AdRequestBuilder = copy(language = language)

    /**
     * Applies an [AdContext]; its non-null values replace the builder's values.
     * The customer is merged field by field.
     */
    fun with(context: AdContext): AdRequestBuilder =
        copy(
            pageNumber = context.pageNumber ?: pageNumber,
            sessionId = context.sessionId ?: sessionId,
            customer = context.customer.mergedOver(customer),
            products =
                context.products?.map {
                    ProductRequest(it.id.orEmpty(), it.category, it.categories, it.categoryIdStructures)
                } ?: products,
            search = context.search ?: search,
            category = context.category ?: category,
            categoryId = context.categoryId ?: categoryId,
            filters = context.filters ?: filters,
            locationId = context.locationId ?: locationId,
            regionId = context.regionId ?: regionId,
            language = context.language ?: language,
        )

    internal fun toAdContext(): AdContext =
        AdContext(
            customer = customer,
            language = language,
            locationId = locationId,
            regionId = regionId,
            search = search,
            category = category,
            categoryId = categoryId,
            products =
                products?.map {
                    Product(
                        id = it.productId,
                        category = it.category,
                        categories = it.categories,
                        categoryIdStructures = it.categoryIdStructures,
                    )
                },
            filters = filters,
            pageNumber = pageNumber,
            sessionId = sessionId,
        )
}
