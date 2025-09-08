package com.gowit.sdk.sponsoredDisplays

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Configuration for sponsored display requests
 */
sealed class SponsoredDisplayConfig : Parcelable {
    /**
     * Configuration for single category sponsored display
     */
    @Parcelize
    data class CategorySingle(
        val placementId: Int,
        val category: String,
        val filters: List<List<String>> = emptyList(),
    ) : SponsoredDisplayConfig()

    /**
     * Configuration for multiple categories sponsored display
     */
    @Parcelize
    data class CategoryMultiple(
        val placementId: Int,
        val categories: List<String>,
        val filters: List<List<String>> = emptyList(),
    ) : SponsoredDisplayConfig()

    /**
     * Configuration for product-based sponsored display
     */
    @Parcelize
    data class ProductBased(
        val placementId: Int,
        val productIds: List<String>,
        val filters: List<List<String>> = emptyList(),
    ) : SponsoredDisplayConfig()

    /**
     * Configuration for custom placement sponsored display
     */
    @Parcelize
    data class CustomPlacement(
        val placementId: Int,
        val filters: List<List<String>> = emptyList(),
        val locationId: String? = null,
    ) : SponsoredDisplayConfig()
}

// Extension functions for convenience
fun SponsoredDisplayConfig.getConfigPlacementId(): Int =
    when (this) {
        is SponsoredDisplayConfig.CategorySingle -> this.placementId
        is SponsoredDisplayConfig.CategoryMultiple -> this.placementId
        is SponsoredDisplayConfig.ProductBased -> this.placementId
        is SponsoredDisplayConfig.CustomPlacement -> this.placementId
    }

fun SponsoredDisplayConfig.getConfigFilters(): List<List<String>> =
    when (this) {
        is SponsoredDisplayConfig.CategorySingle -> this.filters
        is SponsoredDisplayConfig.CategoryMultiple -> this.filters
        is SponsoredDisplayConfig.ProductBased -> this.filters
        is SponsoredDisplayConfig.CustomPlacement -> this.filters
    }
