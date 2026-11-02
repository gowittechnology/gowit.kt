package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Request context shared by ad requests and sponsored display views.
 *
 * Holds everything an ad request carries except the placements themselves.
 * Set an app-wide default once with `Gowit.shared.setDefaultContext(...)` and
 * pass a per-request or per-view context to override parts of it:
 * - Non-null fields of the more specific context win.
 * - [customer] is merged field by field, so a per-view `Customer(city = "Riyadh")`
 *   keeps the default `customerId`.
 * - Lists ([products], [filters], [Customer.segments]) are replaced, not concatenated.
 *
 * Example:
 * ```
 * Gowit.shared.setDefaultContext(
 *     AdContext(customer = Customer(customerId = "42"), language = "AR", sessionId = session.id)
 * )
 * ```
 */
@Parcelize
data class AdContext(
    val customer: Customer? = null,
    val language: String? = null,
    val locationId: String? = null,
    val regionId: String? = null,
    val search: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val products: List<Product>? = null,
    /** Request-level filters. Placement-level filters belong on [PlacementRequest.filters]. */
    val filters: List<String>? = null,
    val pageNumber: Int? = null,
    /** Session ID used when a request or view does not pass one explicitly. */
    val sessionId: String? = null,
) : Parcelable {
    /**
     * Returns a context where this context's non-null values take precedence over [base].
     */
    fun mergedOver(base: AdContext?): AdContext {
        if (base == null) return this
        return AdContext(
            customer = customer.mergedOver(base.customer),
            language = language ?: base.language,
            locationId = locationId ?: base.locationId,
            regionId = regionId ?: base.regionId,
            search = search ?: base.search,
            category = category ?: base.category,
            categoryId = categoryId ?: base.categoryId,
            products = products ?: base.products,
            filters = filters ?: base.filters,
            pageNumber = pageNumber ?: base.pageNumber,
            sessionId = sessionId ?: base.sessionId,
        )
    }
}
