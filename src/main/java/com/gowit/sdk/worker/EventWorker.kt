package com.gowit.sdk.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gowit.sdk.core.ApiResult
import com.gowit.sdk.core.GowitConfig
import com.gowit.sdk.core.HttpClient
import com.gowit.sdk.core.JsonSerializer
import com.gowit.sdk.core.Logger
import com.gowit.sdk.model.EventRequest

/**
 * Background worker for retrying failed event requests
 */
internal class EventWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    companion object {
        const val KEY_EVENT_JSON = "event_json"
        const val KEY_HOSTNAME = "hostname"
        const val KEY_MARKETPLACE_ID = "marketplace_id"
        const val KEY_ENABLE_LOGGING = "enable_logging"
    }

    override suspend fun doWork(): Result {
        return try {
            val eventJson =
                inputData.getString(KEY_EVENT_JSON)
                    ?: return Result.failure()

            val hostname =
                inputData.getString(KEY_HOSTNAME)
                    ?: return Result.failure()

            val enableLogging = inputData.getBoolean(KEY_ENABLE_LOGGING, false)

            val marketplaceId =
                inputData.getString(KEY_MARKETPLACE_ID)
                    ?: return Result.failure()

            val config =
                GowitConfig(
                    hostname = hostname,
                    marketplaceId = marketplaceId,
                    enableLogging = enableLogging,
                )

            Logger.enable(enableLogging)

            val eventRequest = JsonSerializer.fromJson<EventRequest>(eventJson)
            val httpClient = HttpClient(config)

            Logger.d("Retrying event request in background: ${eventRequest.eventType}")

            when (val result = httpClient.post("/server/events", eventJson)) {
                is ApiResult.Success -> {
                    Logger.d("Background event retry successful")
                    Result.success()
                }
                is ApiResult.Error -> {
                    Logger.e("Background event retry failed: ${result.exception.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Logger.e("Background event worker failed", e)
            Result.failure()
        }
    }
}
