package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Sale information for event reporting
 */
@Parcelize
data class Sale(
    val advertiserId: String,
    val quantity: Int,
    val unitPrice: Double,
    val productId: String,
) : Parcelable
