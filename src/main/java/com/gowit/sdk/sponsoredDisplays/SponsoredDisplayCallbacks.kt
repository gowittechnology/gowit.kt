package com.gowit.sdk.sponsoredDisplays

import com.gowit.sdk.core.GowitException
import com.gowit.sdk.model.Ad

/**
 * Callbacks for sponsored display events
 */
data class SponsoredDisplayCallbacks(
    /**
     * Called when a general error occurs
     */
    val onError: ((GowitException) -> Unit)? = null,
    /**
     * Called when no ads are available
     */
    val onNoAds: (() -> Unit)? = null,
    /**
     * Called when sponsored display image loads successfully
     */
    val onImageLoad: (() -> Unit)? = null,
    /**
     * Called when sponsored display image fails to load
     */
    val onImageLoadError: ((Throwable) -> Unit)? = null,
    /**
     * Called when sponsored display is successfully displayed
     */
    val onSponsoredDisplayShown: ((Ad) -> Unit)? = null,
    /**
     * Called when sponsored display impression is sent
     */
    val onImpressionSent: ((String) -> Unit)? = null,
    /**
     * Called when sponsored display click is sent
     */
    val onClickSent: ((String) -> Unit)? = null,
)

/**
 * Click handler for sponsored display interactions
 */
typealias SponsoredDisplayClickHandler = (adId: String, advertiserId: String) -> Unit

/**
 * Click interceptor for sponsored display interactions.
 *
 * Called after the click event is recorded, with the tapped ad and its destination URL (if any).
 * Return `true` when the app handled navigation itself (e.g. opened an in-app screen);
 * return `false` to let the SDK open [destinationUrl] with an `ACTION_VIEW` intent.
 */
typealias SponsoredDisplayClickInterceptor = (ad: Ad, destinationUrl: String?) -> Boolean
