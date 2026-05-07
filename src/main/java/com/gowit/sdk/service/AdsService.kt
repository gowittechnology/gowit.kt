package com.gowit.sdk.service

import com.gowit.sdk.callback.AdCallback
import com.gowit.sdk.core.ApiConstants
import com.gowit.sdk.core.ApiResult
import com.gowit.sdk.core.GowitConfig
import com.gowit.sdk.core.GowitException
import com.gowit.sdk.core.HttpClient
import com.gowit.sdk.core.JsonSerializer
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.AdRequest
import com.gowit.sdk.model.AdResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service for fetching advertisements
 */
internal class AdsService(private val config: GowitConfig) {
    private val httpClient = HttpClient(config)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Fetch ads asynchronously with callback
     */
    fun getAds(
        request: AdRequest,
        callback: AdCallback,
    ) {
        serviceScope.launch {
            try {
                val result = getAds(request)
                result.onSuccess { response ->
                    callback.onSuccess(response)
                }.onError { exception ->
                    callback.onError(exception)
                }
            } catch (e: Exception) {
                Logger.e("Error in getAds callback", e)
                callback.onError(
                    GowitException.NetworkException("Unexpected error occurred", e),
                )
            }
        }
    }

    /**
     * Fetch ads suspending function
     */
    suspend fun getAds(request: AdRequest): ApiResult<AdResponse> {
        return try {
            validateRequest(request)

            val requestJson = JsonSerializer.toJson(request)
            Logger.d("Ads request: $requestJson")

            val result = httpClient.post(ApiConstants.ADS_ENDPOINT, requestJson)

            result.map { responseJson ->
                try {
                    // Handle empty response (HTTP 204) as no ads available
                    if (responseJson.isBlank()) {
                        Logger.d("Empty response - no ads available")
                        AdResponse(placements = emptyList(), responseId = null)
                    } else {
                        JsonSerializer.fromJson<AdResponse>(responseJson)
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to parse ads response", e)
                    throw GowitException.ParseException("Failed to parse response", e)
                }
            }
        } catch (e: GowitException) {
            ApiResult.Error(e)
        } catch (e: Exception) {
            Logger.e("Unexpected error in getAds", e)
            ApiResult.Error(GowitException.NetworkException("Unexpected error occurred", e))
        }
    }

    private fun validateRequest(request: AdRequest) {
        if (request.sessionId.isBlank()) {
            throw GowitException.ValidationException("Session ID cannot be empty")
        }

        // Validate that placements are provided
        if (request.placements.isEmpty()) {
            throw GowitException.ValidationException("At least one placement must be provided")
        }

        // Validate placement requests
        request.placements.forEach { placement ->
            if (placement.placementId <= 0) {
                throw GowitException.ValidationException("Placement ID must be greater than 0")
            }
        }
    }
}
