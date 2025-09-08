package com.gowit.sdk

import com.gowit.sdk.model.AdRequestBuilder
import com.gowit.sdk.model.AdResponse
import com.gowit.sdk.model.PlacementRequest
import com.gowit.sdk.model.Sale

/**
 * Protocol interface for Gowit SDK similar to Swift implementation
 */
interface GowitProtocol {
    /**
     * Configure the SDK with basic parameters
     */
    fun configure(
        hostname: String,
        marketplaceId: String,
    )

    /**
     * Configure the SDK with auto impression enabled flag
     */
    fun configure(
        hostname: String,
        marketplaceId: String,
        autoImpressionEnabled: Boolean,
    )

    /**
     * Get ads for multiple placements
     */
    suspend fun getAds(
        placements: List<PlacementRequest>,
        pageNumber: Int? = null,
        sessionId: String? = null,
    ): AdResponse

    /**
     * Get ads for a single placement (convenience method)
     */
    suspend fun getAds(
        placementId: Int,
        pageNumber: Int? = null,
        sessionId: String? = null,
    ): AdResponse

    /**
     * Get ads using the builder pattern for cleaner API
     */
    suspend fun getAds(builder: AdRequestBuilder): AdResponse

    /**
     * Send impression event
     */
    suspend fun sendImpressionEvent(
        adId: String,
        sessionId: String,
    )

    /**
     * Send viewable impression event
     */
    suspend fun sendViewableImpressionEvent(
        adId: String,
        sessionId: String,
    )

    /**
     * Send click event
     */
    suspend fun sendClickEvent(
        adId: String,
        sessionId: String,
    )

    /**
     * Send sale event
     */
    suspend fun sendSaleEvent(
        sales: List<Sale>,
        sessionId: String,
    )
}
