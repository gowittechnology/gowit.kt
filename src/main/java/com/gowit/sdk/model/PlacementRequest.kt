package com.gowit.sdk.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a filter group where each inner array is treated as an OR-group,
 * and the outer array applies AND semantics across those groups.
 *
 * Example: `[["brand:samsung", "brand:apple"], ["color:black", "color:white"]]`
 * means "(brand is Samsung OR Apple) AND (color is Black OR White)"
 */
@Parcelize
data class PlacementRequest(
    val placementId: Int,
    val placementIdentifier: String? = null,
    /**
     * Filters is an array of arrays where:
     * - Each inner array represents an OR-group of filter strings
     * - The outer array applies AND semantics across those groups
     * - Example: `[["brand:samsung", "brand:apple"], ["color:black", "color:white"]]`
     *   means "(brand is Samsung OR Apple) AND (color is Black OR White)"
     */
    val filters: List<List<String>>? = null,
    val maxAds: Int? = null,
) : Parcelable
