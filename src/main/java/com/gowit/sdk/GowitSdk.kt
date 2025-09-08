package com.gowit.sdk

import android.content.Context
import com.gowit.sdk.core.ApiResult
import com.gowit.sdk.core.GowitConfig
import com.gowit.sdk.core.GowitException
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.Ad
import com.gowit.sdk.model.AdRequest
import com.gowit.sdk.model.AdRequestBuilder
import com.gowit.sdk.model.AdResponse
import com.gowit.sdk.model.EventRequest
import com.gowit.sdk.model.EventType
import com.gowit.sdk.model.PlacementRequest
import com.gowit.sdk.model.Product
import com.gowit.sdk.model.Sale
import com.gowit.sdk.service.AdsService
import com.gowit.sdk.service.EventsService
import java.util.UUID

/**
 * Main SDK class for Gowit platform integration
 *
 * Usage:
 * ```
 * // Configure SDK
 * Gowit.shared.configure("platform-stage.gowit.com", "106")
 *
 * // Fetch ads using builder pattern
 * val builder = AdRequestBuilder(placementId = 47)
 *     .with(sessionId = "session-123")
 *
 * val response = Gowit.shared.getAds(builder)
 *
 * // Send events
 * Gowit.shared.sendImpressionEvent("ad-id", "session-123")
 *
 * ```
 */
class GowitSdk private constructor(
    private val context: Context,
) : GowitProtocol {
    private var hostname: String = "https://platform-stage.gowit.com"
    private var marketplaceId: String = ""
    private var autoImpressionEnabled: Boolean = false

    private lateinit var adsService: AdsService
    private lateinit var eventsService: EventsService

    // MARK: - Configuration

    override fun configure(
        hostname: String,
        marketplaceId: String,
    ) {
        configure(hostname, marketplaceId, false)
    }

    override fun configure(
        hostname: String,
        marketplaceId: String,
        autoImpressionEnabled: Boolean,
    ) {
        this.hostname = hostname
        this.marketplaceId = marketplaceId
        this.autoImpressionEnabled = autoImpressionEnabled

        // Create configuration and initialize services
        val config =
            GowitConfig(
                hostname = hostname,
                marketplaceId = marketplaceId,
                enableAutoImpression = autoImpressionEnabled,
                enableLogging = false,
            )

        adsService = AdsService(config)
        eventsService = EventsService(config, context)

        Logger.i("Gowit SDK configured with base URL: $hostname")
    }

    // MARK: - Ad Request Methods

    /**
     * Request ads for multiple placements
     */
    override suspend fun getAds(
        placements: List<PlacementRequest>,
        pageNumber: Int?,
        sessionId: String?,
    ): AdResponse {
        ensureConfigured()

        val request =
            AdRequest(
                marketplaceId = marketplaceId,
                placements = placements,
                sessionId = sessionId ?: UUID.randomUUID().toString(),
                pageNumber = pageNumber,
            )

        return when (val result = adsService.getAds(request)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> throw result.exception
        }
    }

    /**
     * Request ads for a single placement (convenience method)
     */
    override suspend fun getAds(
        placementId: Int,
        pageNumber: Int?,
        sessionId: String?,
    ): AdResponse {
        val placement = PlacementRequest(placementId)
        return getAds(listOf(placement), pageNumber, sessionId)
    }

    /**
     * Request ads using the builder pattern for cleaner API
     */
    override suspend fun getAds(builder: AdRequestBuilder): AdResponse {
        ensureConfigured()

        // Convert ProductRequest to Product if needed
        val products =
            builder.products?.map { productReq ->
                Product(id = productReq.productId, category = productReq.category)
            }

        // Create AdRequest with all builder parameters
        val request =
            AdRequest(
                marketplaceId = marketplaceId,
                placements = builder.placements,
                sessionId = builder.sessionId ?: UUID.randomUUID().toString(),
                customer = builder.customer,
                products = products,
                search = builder.search,
                category = builder.category,
                categoryId = builder.categoryId,
                maxAds = builder.maxAds,
                filters = builder.filters,
                pageNumber = builder.pageNumber,
                locationId = builder.locationId,
                regionId = builder.regionId,
            )

        return when (val result = adsService.getAds(request)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> throw result.exception
        }
    }

    // MARK: - Event Reporting Methods

    /**
     * Send impression event
     */
    override suspend fun sendImpressionEvent(
        adId: String,
        sessionId: String,
    ) {
        ensureConfigured()

        val request =
            EventRequest(
                marketplaceId = marketplaceId,
                eventType = EventType.IMPRESSION,
                sessionId = sessionId,
                adId = adId,
            )

        when (val result = eventsService.sendEvent(request)) {
            is ApiResult.Success -> { /* Success */ }
            is ApiResult.Error -> throw result.exception
        }
    }

    /**
     * Send viewable impression event
     */
    override suspend fun sendViewableImpressionEvent(
        adId: String,
        sessionId: String,
    ) {
        ensureConfigured()

        val request =
            EventRequest(
                marketplaceId = marketplaceId,
                eventType = EventType.VIEWABLE_IMPRESSION,
                sessionId = sessionId,
                adId = adId,
            )

        when (val result = eventsService.sendEvent(request)) {
            is ApiResult.Success -> { /* Success */ }
            is ApiResult.Error -> throw result.exception
        }
    }

    /**
     * Send click event
     */
    override suspend fun sendClickEvent(
        adId: String,
        sessionId: String,
    ) {
        ensureConfigured()

        val request =
            EventRequest(
                marketplaceId = marketplaceId,
                eventType = EventType.CLICK,
                sessionId = sessionId,
                adId = adId,
            )

        when (val result = eventsService.sendEvent(request)) {
            is ApiResult.Success -> { /* Success */ }
            is ApiResult.Error -> throw result.exception
        }
    }

    /**
     * Send sale event
     */
    override suspend fun sendSaleEvent(
        sales: List<Sale>,
        sessionId: String,
    ) {
        ensureConfigured()

        val request =
            EventRequest(
                marketplaceId = marketplaceId,
                eventType = EventType.SALE,
                sessionId = sessionId,
                sales = sales,
            )

        when (val result = eventsService.sendEvent(request)) {
            is ApiResult.Success -> { /* Success */ }
            is ApiResult.Error -> throw result.exception
        }
    }

    /**
     * Convenience method to send impression event for an Ad object (bypasses auto impression)
     */
    suspend fun sendImpressionEvent(
        ad: Ad,
        sessionId: String,
    ) {
        val adId = ad.adId ?: throw GowitException.ValidationException("Ad ID is missing or invalid")
        sendImpressionEvent(adId, sessionId)
    }

    /**
     * Convenience method to send viewable impression event for an Ad object
     */
    suspend fun sendViewableImpressionEvent(
        ad: Ad,
        sessionId: String,
    ) {
        val adId = ad.adId ?: throw GowitException.ValidationException("Ad ID is missing or invalid")
        sendViewableImpressionEvent(adId, sessionId)
    }

    /**
     * Convenience method to send click event for an Ad object
     */
    suspend fun sendClickEvent(
        ad: Ad,
        sessionId: String,
    ) {
        val adId = ad.adId ?: throw GowitException.ValidationException("Ad ID is missing or invalid")
        sendClickEvent(adId, sessionId)
    }

    private fun ensureConfigured() {
        if (marketplaceId.isBlank()) {
            throw GowitException.ConfigurationException("SDK not configured. Call configure() first.")
        }
    }

    // ========== Companion Object ==========

    companion object {
        private var instance: GowitSdk? = null

        /**
         * Shared instance similar to Swift SDK
         */
        @JvmStatic
        val shared: GowitSdk
            get() =
                instance ?: throw GowitException.ConfigurationException(
                    "SDK not initialized. Call Gowit.initialize() first.",
                )

        /**
         * Initialize the SDK with context (similar to Swift but requires Android context)
         *
         * @param context Application context
         * @return SDK instance
         */
        @JvmStatic
        fun initialize(context: Context): GowitSdk {
            val applicationContext = context.applicationContext
            instance = GowitSdk(applicationContext)
            return instance!!
        }

        /**
         * Check if SDK is initialized
         */
        @JvmStatic
        fun isInitialized(): Boolean = instance != null

        /**
         * Clear SDK instance (for testing)
         */
        @JvmStatic
        internal fun clearInstance() {
            instance = null
        }
    }
}

// Alias for easier access similar to Swift
typealias Gowit = GowitSdk
