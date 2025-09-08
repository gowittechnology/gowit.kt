package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Product information for ad requests
 */
@Parcelize
data class Product(
    val id: String? = null,
    val category: String? = null,
    val keywordScore: String? = null,
) : Parcelable
