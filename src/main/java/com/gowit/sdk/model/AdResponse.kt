package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Response model for ad requests
 */
@Parcelize
data class AdResponse(
    val responseId: String? = null,
    val placements: List<Placement>? = null,
) : Parcelable {
    // MARK: - Convenience Methods

    /**
     * Get ads for a specific placement ID
     */
    fun getAds(placementId: Int): List<Ad>? {
        return placements?.firstOrNull { it.placementId == placementId }?.ads
    }

    /**
     * Get all ads from all placements as a flat array
     */
    val allAds: List<Ad>
        get() = placements?.flatMap { it.ads ?: emptyList() } ?: emptyList()

    /**
     * Get placement by ID
     */
    fun getPlacement(id: Int): Placement? {
        return placements?.firstOrNull { it.placementId == id }
    }

    /**
     * Check if a specific placement has ads
     */
    fun hasAds(placementId: Int): Boolean {
        return getAds(placementId)?.isNotEmpty() == true
    }

    /**
     * Get the number of ads for a specific placement
     */
    fun adCount(placementId: Int): Int {
        return getAds(placementId)?.size ?: 0
    }

    // MARK: - Display Ad Convenience Methods

    /**
     * Get all display ads from all placements
     */
    val allDisplayAds: List<Ad>
        get() = allAds.filter { it.isDisplayAd }

    /**
     * Get display ads for a specific placement ID
     */
    fun getDisplayAds(placementId: Int): List<Ad>? {
        return getAds(placementId)?.filter { it.isDisplayAd }
    }

    /**
     * Check if a specific placement has display ads
     */
    fun hasDisplayAds(placementId: Int): Boolean {
        return getDisplayAds(placementId)?.isNotEmpty() == true
    }

    /**
     * Get the number of display ads for a specific placement
     */
    fun displayAdCount(placementId: Int): Int {
        return getDisplayAds(placementId)?.size ?: 0
    }
}
