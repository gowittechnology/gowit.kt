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
    val productId: String? = null,
    val creativeId: Int? = null,
    val imgUrl: String? = null,
    val videoUrl: String? = null,
    val vastTag: String? = null,
    val duration: Int? = null,
    val size: String? = null,
    val redirect: Redirect? = null,
    val position: Int? = null,
    val advertiserId: String? = null,
    val campaignId: Long? = null,
    val products: List<PromotedProduct>? = null,
    val language: String? = null,
    val html: String? = null,
) : Parcelable {
    /**
     * Determines if this ad is a Sponsored Product ad.
     * A product ad contains a product_id (SKU) that customers can use to query product details.
     */
    val isProductAd: Boolean
        get() = !productId.isNullOrBlank()

    /**
     * Determines if this ad is a Sponsored Display ad.
     * A display ad contains img_url, html, or redirect information for direct display.
     */
    val isDisplayAd: Boolean
        get() = !imgUrl.isNullOrBlank() || !html.isNullOrBlank() || redirect != null

    /**
     * Returns the SKU (product ID) if this is a product ad.
     */
    val sku: String?
        get() = if (isProductAd) productId else null

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
