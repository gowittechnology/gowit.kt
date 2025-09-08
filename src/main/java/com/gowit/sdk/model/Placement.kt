package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Placement containing ads
 */
@Parcelize
data class Placement(
    val placementId: Int,
    val placementIdentifier: String? = null,
    val ads: List<Ad>? = null,
) : Parcelable {
    // MARK: - Convenience Methods

    /**
     * Check if this placement has ads
     */
    val hasAds: Boolean
        get() = ads?.isNotEmpty() == true

    /**
     * Get the number of ads in this placement
     */
    val adCount: Int
        get() = ads?.size ?: 0

    /**
     * Get the first ad in this placement
     */
    val firstAd: Ad?
        get() = ads?.firstOrNull()
}
