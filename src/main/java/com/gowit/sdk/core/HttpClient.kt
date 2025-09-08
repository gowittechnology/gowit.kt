package com.gowit.sdk.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * HTTP client for making API requests
 */
internal class HttpClient(private val config: GowitConfig) {
    private val client: OkHttpClient by lazy {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)

        if (config.enableLogging) {
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                    Logger.d(message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            builder.addInterceptor(loggingInterceptor)
        }

        builder.build()
    }

    suspend fun post(
        endpoint: String,
        body: String,
    ): ApiResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = body.toRequestBody(ApiConstants.CONTENT_TYPE_JSON.toMediaType())
                val request =
                    Request.Builder()
                        .url("${config.baseUrl}$endpoint")
                        .post(requestBody)
                        .addHeader(ApiConstants.HEADER_CONTENT_TYPE, ApiConstants.CONTENT_TYPE_JSON)
                        .build()

                Logger.d("Making POST request to: ${config.baseUrl}$endpoint")
                Logger.d("Request body: $body")

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Logger.d("Response (${response.code}): $responseBody")

                    // Handle 204 No Content as success with empty response (no ads available)
                    if (response.code == 204) {
                        Logger.d("HTTP 204: No ads available")
                        ApiResult.Success("")
                    } else {
                        ApiResult.Success(responseBody)
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Logger.e("API Error ${response.code}: $errorBody")
                    ApiResult.Error(
                        GowitException.ApiException(
                            code = response.code,
                            message = "API request failed: $errorBody",
                        ),
                    )
                }
            } catch (e: IOException) {
                val detailedMessage = buildNetworkErrorMessage(e, "${config.baseUrl}$endpoint")
                Logger.e("Network error: $detailedMessage", e)
                ApiResult.Error(
                    GowitException.NetworkException(detailedMessage, e),
                )
            } catch (e: Exception) {
                val detailedMessage = "Unexpected error: ${e.javaClass.simpleName} - ${e.message}"
                Logger.e("Unexpected error: $detailedMessage", e)
                ApiResult.Error(
                    GowitException.NetworkException(detailedMessage, e),
                )
            }
        }
    }

    suspend fun get(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
    ): ApiResult<String> {
        return withContext(Dispatchers.IO) {
            val url =
                if (params.isNotEmpty()) {
                    val queryParams = params.entries.joinToString("&") { "${it.key}=${it.value}" }
                    "${config.baseUrl}$endpoint?$queryParams"
                } else {
                    "${config.baseUrl}$endpoint"
                }

            try {
                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                Logger.d("Making GET request to: $url")

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Logger.d("Response: $responseBody")
                    ApiResult.Success(responseBody)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Logger.e("API Error ${response.code}: $errorBody")
                    ApiResult.Error(
                        GowitException.ApiException(
                            code = response.code,
                            message = "API request failed: $errorBody",
                        ),
                    )
                }
            } catch (e: IOException) {
                val detailedMessage = buildNetworkErrorMessage(e, url)
                Logger.e("Network error: $detailedMessage", e)
                ApiResult.Error(
                    GowitException.NetworkException(detailedMessage, e),
                )
            } catch (e: Exception) {
                val detailedMessage = "Unexpected error: ${e.javaClass.simpleName} - ${e.message}"
                Logger.e("Unexpected error: $detailedMessage", e)
                ApiResult.Error(
                    GowitException.NetworkException(detailedMessage, e),
                )
            }
        }
    }

    /**
     * Build detailed network error message for better debugging
     */
    private fun buildNetworkErrorMessage(
        exception: IOException,
        url: String,
    ): String {
        return when (exception) {
            is UnknownHostException -> {
                "DNS resolution failed for '${exception.message}'. " +
                    "Check internet connection and hostname: ${config.hostname}. " +
                    "Verify the hostname is correct and accessible."
            }
            is ConnectException -> {
                "Connection refused to $url. " +
                    "The server may be down, unreachable, or the port may be blocked. " +
                    "Check if the server is running and accessible."
            }
            is SocketTimeoutException -> {
                "Request timeout after ${config.timeoutSeconds} seconds to $url. " +
                    "The server is taking too long to respond. " +
                    "Try increasing timeout or check server performance."
            }
            is SSLException -> {
                "SSL/TLS error connecting to $url: ${exception.message}. " +
                    "This could be due to certificate issues, unsupported protocols, or network security policies."
            }
            else -> {
                "Network error connecting to $url: ${exception.javaClass.simpleName} - ${exception.message}. " +
                    "Check internet connection, firewall settings, and server availability."
            }
        }
    }
}
