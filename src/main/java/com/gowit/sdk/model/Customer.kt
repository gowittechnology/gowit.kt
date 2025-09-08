package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Customer information for ad requests
 */
@Parcelize
data class Customer(
    val id: String? = null,
    val customerId: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val city: String? = null,
    val deviceType: String? = null,
    val environmentType: String? = null,
    val ip: String? = null,
    val segments: List<String>? = null,
    val userAgent: String? = null,
) : Parcelable
