package com.gowit.sdk.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gowit.sdk.callback.EventCallback
import com.gowit.sdk.core.ApiConstants
import com.gowit.sdk.core.ApiResult
import com.gowit.sdk.core.GowitConfig
import com.gowit.sdk.core.GowitException
import com.gowit.sdk.core.HttpClient
import com.gowit.sdk.core.JsonSerializer
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.EventRequest
import com.gowit.sdk.model.EventType
import com.gowit.sdk.model.Sale
import com.gowit.sdk.worker.EventWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Service for sending events
 */
internal class EventsService(
    private val config: GowitConfig,
    private val context: Context,
) {
    private val httpClient = HttpClient(config)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val workManager = WorkManager.getInstance(context)

    /**
     * Send impression event via SDK GET endpoint
     * Note: If auto impression is enabled in config, this method will do nothing
     * as impressions are automatically counted by the platform
     */
    fun sendImpressionEvent(
        sessionId: String,
        adId: String,
        callback: EventCallback? = null,
    ) {
        // Skip impression counting if auto impression is enabled
        if (config.enableAutoImpression) {
            Logger.d("Auto impression is enabled, skipping manual impression sending")
            callback?.onSuccess()
            return
        }

        sendSdkEvent(EventType.IMPRESSION, sessionId, adId, null, callback)
    }

    /**
     * Send click event via SDK GET endpoint
     */
    fun sendClickEvent(
        sessionId: String,
        adId: String,
        callback: EventCallback? = null,
    ) {
        sendSdkEvent(EventType.CLICK, sessionId, adId, null, callback)
    }

    /**
     * Send sale event via SDK POST endpoint
     */
    fun sendSaleEvent(
        sessionId: String,
        sales: List<Sale>,
        callback: EventCallback? = null,
    ) {
        val request =
            EventRequest(
                // Will be set from SDK configuration
                marketplaceId = "",
                eventType = EventType.SALE,
                sessionId = sessionId,
                sales = sales,
            )

        sendSaleEventInternal(request, callback)
    }

    /**
     * Send viewable impression event via SDK GET endpoint
     * Note: If auto impression is enabled in config, this method will do nothing
     * as viewable impressions are automatically counted by the platform
     */
    fun sendViewableImpressionEvent(
        sessionId: String,
        adId: String,
        callback: EventCallback? = null,
    ) {
        // Skip viewable impression sending if auto impression is enabled
        if (config.enableAutoImpression) {
            Logger.d("Auto impression is enabled, skipping manual viewable impression sending")
            callback?.onSuccess()
            return
        }

        // Note: SDK events endpoint only supports impression and click
        // Viewable impression might need to use the regular events endpoint
        sendSdkEvent(EventType.IMPRESSION, sessionId, adId, null, callback)
    }

    /**
     * Queue event for background retry using WorkManager
     */
    private fun queueEventForRetry(request: EventRequest) {
        try {
            val inputData =
                Data.Builder()
                    .putString(EventWorker.KEY_EVENT_JSON, JsonSerializer.toJson(request))
                    .putString(EventWorker.KEY_HOSTNAME, config.hostname)
                    .putString(EventWorker.KEY_MARKETPLACE_ID, config.marketplaceId)
                    .putBoolean(EventWorker.KEY_ENABLE_LOGGING, config.enableLogging)
                    .build()

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<EventWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .build()

            val workName = "event_${UUID.randomUUID()}"
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.APPEND, workRequest)

            Logger.d("Event queued for background retry")
        } catch (e: Exception) {
            Logger.e("Failed to queue event for retry", e)
        }
    }

    /**
     * Send event via SDK GET endpoint (for impression and click events)
     */
    private fun sendSdkEvent(
        eventType: EventType,
        sessionId: String,
        adId: String,
        redirectUrl: String?,
        callback: EventCallback? = null,
    ) {
        serviceScope.launch {
            try {
                val result = sendSdkEventSuspend(eventType, sessionId, adId, redirectUrl)
                result.onSuccess {
                    callback?.onSuccess()
                }.onError { exception ->
                    callback?.onError(exception)
                }
            } catch (e: Exception) {
                Logger.e("Error in sendSdkEvent callback", e)
                callback?.onError(
                    GowitException.NetworkException("Unexpected error occurred", e),
                )
            }
        }
    }

    /**
     * Send event via SDK GET endpoint (suspending function)
     */
    internal suspend fun sendSdkEventSuspend(
        eventType: EventType,
        sessionId: String,
        adId: String,
        redirectUrl: String?,
    ): ApiResult<Unit> {
        return try {
            val params = mutableMapOf<String, String>()
            params["type"] = eventType.value
            params["session_id"] = sessionId
            params["ad_id"] = adId
            redirectUrl?.let { params["redirect"] = it }

            val result = httpClient.get(ApiConstants.EVENTS_ENDPOINT, params)

            result.map {
                Logger.d("SDK Event sent successfully")
                Unit
            }
        } catch (e: GowitException) {
            ApiResult.Error(e)
        } catch (e: Exception) {
            Logger.e("Unexpected error in sendSdkEvent", e)
            ApiResult.Error(GowitException.NetworkException("Unexpected error occurred", e))
        }
    }

    /**
     * Send sale event via SDK POST endpoint (internal method)
     */
    private fun sendSaleEventInternal(
        request: EventRequest,
        callback: EventCallback? = null,
    ) {
        serviceScope.launch {
            try {
                val result = sendSaleEventSuspend(request)
                result.onSuccess {
                    callback?.onSuccess()
                }.onError { exception ->
                    // If immediate sending fails, queue for background retry
                    queueEventForRetry(request)
                    callback?.onError(exception)
                }
            } catch (e: Exception) {
                Logger.e("Error in sendSaleEvent callback", e)
                queueEventForRetry(request)
                callback?.onError(
                    GowitException.NetworkException("Unexpected error occurred", e),
                )
            }
        }
    }

    /**
     * Send sale event via SDK POST endpoint (suspending function)
     */
    internal suspend fun sendSaleEventSuspend(request: EventRequest): ApiResult<Unit> {
        return try {
            validateEventRequest(request)

            val requestJson = JsonSerializer.toJson(request)
            Logger.d("Sale event request: $requestJson")

            val result = httpClient.post(ApiConstants.SALE_EVENTS_ENDPOINT, requestJson)

            result.map {
                Logger.d("Sale event sent successfully")
                Unit
            }
        } catch (e: GowitException) {
            ApiResult.Error(e)
        } catch (e: Exception) {
            Logger.e("Unexpected error in sendSaleEvent", e)
            ApiResult.Error(GowitException.NetworkException("Unexpected error occurred", e))
        }
    }

    private fun validateEventRequest(request: EventRequest) {
        if (request.sessionId.isBlank()) {
            throw GowitException.ValidationException("Session ID cannot be empty")
        }

        when (request.eventType) {
            EventType.IMPRESSION, EventType.CLICK, EventType.VIEWABLE_IMPRESSION -> {
                if (request.adId.isNullOrBlank()) {
                    throw GowitException.ValidationException("Ad ID must be provided for ${request.eventType} events")
                }
            }
            EventType.SALE -> {
                if (request.sales.isNullOrEmpty()) {
                    throw GowitException.ValidationException("Sales data must be provided for sale events")
                }

                request.sales.forEach { sale ->
                    if (sale.advertiserId.isBlank()) {
                        throw GowitException.ValidationException("Advertiser ID cannot be empty")
                    }
                    if (sale.productId.isBlank()) {
                        throw GowitException.ValidationException("Product ID cannot be empty")
                    }
                    if (sale.quantity <= 0) {
                        throw GowitException.ValidationException("Quantity must be greater than 0")
                    }
                    if (sale.unitPrice < 0) {
                        throw GowitException.ValidationException("Unit price cannot be negative")
                    }
                }
            }
        }
    }
}
