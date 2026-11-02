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
    /** All categories of the product, for products that belong to several categories. */
    val categories: List<String>? = null,
    /** ID structures matching [categories] by index, e.g. "1>23>456". */
    val categoryIdStructures: List<String>? = null,
) : Parcelable
