package com.gowit.sdk.callback

import com.gowit.sdk.core.GowitException
import com.gowit.sdk.model.AdResponse

/**
 * Callback interface for ad requests
 */
interface AdCallback {
    /**
     * Called when ads are successfully fetched
     */
    fun onSuccess(response: AdResponse)

    /**
     * Called when an error occurs while fetching ads
     */
    fun onError(exception: GowitException)
}
