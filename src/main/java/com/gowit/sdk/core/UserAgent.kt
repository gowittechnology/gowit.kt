package com.gowit.sdk.core

import android.os.Build
import com.gowit.sdk.BuildConfig
import okhttp3.Response

/**
 * User-Agent sent with every SDK request, e.g. `gowit-kt/1.1.0 (Android 14; API 34)`.
 */
internal object UserAgent {
    const val HEADER = "User-Agent"

    val value: String by lazy {
        build(BuildConfig.SDK_VERSION, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
    }

    fun build(
        sdkVersion: String,
        androidRelease: String?,
        apiLevel: Int,
    ): String = "gowit-kt/$sdkVersion (Android ${androidRelease ?: "unknown"}; API $apiLevel)"

    class Interceptor(private val userAgent: String = value) : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): Response =
            chain.proceed(
                chain.request().newBuilder()
                    .header(HEADER, userAgent)
                    .build(),
            )
    }
}
