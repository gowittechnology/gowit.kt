package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Ad redirect information
 */
@Parcelize
data class Redirect(
    val url: String,
    val type: String,
) : Parcelable

/**
 * Promoted product information
 */
@Parcelize
data class PromotedProduct(
    val name: String? = null,
    val sku: String? = null,
    val imageUrl: String? = null,
    val rating: Double? = null,
    val price: Double? = null,
    val stockCount: Int? = null,
    val advertiserId: String? = null,
) : Parcelable

/**
 * Ad model representing an advertisement (SDK version)
 */
@Parcelize
data class Ad(
    val adId: String? = null,
    val creativeId: Int? = null,
    val imgUrl: String? = null,
    val redirect: Redirect? = null,
    val position: Int? = null,
    val advertiserId: String? = null,
    val campaignId: Long? = null,
    val html: String? = null,
    val width: Long? = null,
    val height: Long? = null,
) : Parcelable {
    // MARK: - Ad Type Detection Methods

    /**
     * Determines if this ad is a Sponsored Display ad.
     * A display ad contains img_url, html, or redirect information for direct display.
     */
    val isDisplayAd: Boolean
        get() = !imgUrl.isNullOrBlank() || !html.isNullOrBlank() || redirect != null

    /**
     * Returns the display URL for this ad if it's a display ad.
     * This is the image URL that should be displayed to users.
     */
    val displayImageUrl: String?
        get() = if (isDisplayAd) imgUrl else null

    /**
     * Returns the click URL for this ad if it has redirect information.
     */
    val clickUrl: String?
        get() = redirect?.url
}
