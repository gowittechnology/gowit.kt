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

/**
 * Merges two customers field by field; non-null values of the receiver win over [base].
 */
fun Customer?.mergedOver(base: Customer?): Customer? {
    if (this == null) return base
    if (base == null) return this
    return Customer(
        id = id ?: base.id,
        customerId = customerId ?: base.customerId,
        gender = gender ?: base.gender,
        age = age ?: base.age,
        city = city ?: base.city,
        deviceType = deviceType ?: base.deviceType,
        environmentType = environmentType ?: base.environmentType,
        ip = ip ?: base.ip,
        segments = segments ?: base.segments,
        userAgent = userAgent ?: base.userAgent,
    )
}
