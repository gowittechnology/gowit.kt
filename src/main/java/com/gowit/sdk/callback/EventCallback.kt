package com.gowit.sdk.callback

import com.gowit.sdk.core.GowitException

/**
 * Callback interface for event reporting
 */
interface EventCallback {
    /**
     * Called when event is successfully reported
     */
    fun onSuccess()

    /**
     * Called when an error occurs while reporting event
     */
    fun onError(exception: GowitException)
}
