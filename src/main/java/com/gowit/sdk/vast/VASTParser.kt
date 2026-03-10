package com.gowit.sdk.vast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Parser for VAST 4.2 XML responses.
 *
 * Supports both direct XML parsing and URL fetching with Wrapper redirect resolution.
 *
 * Example:
 * ```kotlin
 * val parser = VASTParser()
 *
 * // Fetch and parse from a URL (follows Wrapper redirects automatically)
 * val response = parser.fetchAndParse(vastTagUrl)
 * val ad = response.firstAd ?: return
 *
 * // Access the video URL
 * val mediaFile = ad.inLine?.creatives?.firstOrNull()?.linear?.bestMediaFile()
 * val videoUrl = mediaFile?.url
 * ```
 */
class VASTParser {

    companion object {
        /** Maximum depth for following Wrapper redirects */
        const val DEFAULT_MAX_WRAPPER_DEPTH = 5

        /** Timeout for network requests in seconds */
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // MARK: - Public API

    /**
     * Parse VAST XML from a byte array.
     *
     * @param data Raw XML bytes
     * @return Parsed [VASTResponse]
     * @throws VASTError if parsing fails or no ads are found
     */
    fun parse(data: ByteArray): VASTResponse = parse(data.inputStream())

    /**
     * Parse VAST XML from an input stream.
     *
     * @param stream Raw XML stream
     * @return Parsed [VASTResponse]
     * @throws VASTError if parsing fails or no ads are found
     */
    fun parse(stream: InputStream): VASTResponse {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val xpp = factory.newPullParser()
            xpp.setInput(stream, "UTF-8")
            parseXml(xpp)
        } catch (e: VASTError) {
            throw e
        } catch (e: Exception) {
            throw VASTError.ParsingError(e.message ?: "Unknown parsing error")
        }
    }

    /**
     * Fetch and parse VAST from a URL, automatically following Wrapper redirects.
     *
     * @param url VAST tag URL string
     * @param maxWrapperDepth Maximum number of Wrapper redirects to follow (default: 5)
     * @param timeoutSeconds Request timeout per hop in seconds (default: 30)
     * @return Resolved [VASTResponse] with InLine ad content
     * @throws VASTError on network failure, parse failure, or wrapper depth exceeded
     */
    suspend fun fetchAndParse(
        url: String,
        maxWrapperDepth: Int = DEFAULT_MAX_WRAPPER_DEPTH,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): VASTResponse = fetchAndParse(url, currentDepth = 0, maxWrapperDepth, timeoutSeconds)

    // MARK: - Private Fetch Logic

    private suspend fun fetchAndParse(
        url: String,
        currentDepth: Int,
        maxWrapperDepth: Int,
        timeoutSeconds: Long,
    ): VASTResponse {
        if (currentDepth > maxWrapperDepth) {
            throw VASTError.WrapperDepthExceeded(maxWrapperDepth)
        }

        val data = fetchData(url, timeoutSeconds)
        val response = parse(data)

        val firstAd = response.firstAd ?: return response
        val wrapper = firstAd.wrapper ?: return response

        val wrappedResponse =
            fetchAndParse(
                url = wrapper.vastAdTagUri,
                currentDepth = currentDepth + 1,
                maxWrapperDepth = maxWrapperDepth,
                timeoutSeconds = timeoutSeconds,
            )

        return mergeWrapperWithInLine(wrapper, wrappedResponse, firstAd)
    }

    private suspend fun fetchData(
        url: String,
        timeoutSeconds: Long,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val client =
                if (timeoutSeconds == DEFAULT_TIMEOUT_SECONDS) {
                    httpClient
                } else {
                    httpClient
                        .newBuilder()
                        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .build()
                }
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw VASTError.NetworkError("HTTP ${response.code}")
                }
                response.body?.bytes() ?: throw VASTError.NetworkError("Empty response body")
            } catch (e: VASTError) {
                throw e
            } catch (e: Exception) {
                throw VASTError.NetworkError(e.message ?: "Unknown network error")
            }
        }

    // MARK: - XML Parsing

    private fun parseXml(xpp: XmlPullParser): VASTResponse {
        // Mutable parser state
        var vastVersion = "4.2"
        val ads = mutableListOf<VASTAd>()

        // Current ad state
        var currentAdId: String? = null
        var currentAdSequence: Int? = null
        var currentInLine: VASTInLine? = null
        var currentWrapper: VASTWrapper? = null
        var currentAdSystem: VASTAdSystem? = null

        // InLine / Wrapper shared state
        val impressions = mutableListOf<VASTImpression>()
        val errors = mutableListOf<String>()
        var vastAdTagUri: String? = null
        var adTitle: String? = null

        // Viewable impression state
        val viewable = mutableListOf<String>()
        val notViewable = mutableListOf<String>()
        val viewUndetermined = mutableListOf<String>()
        var currentViewableImpression: VASTViewableImpression? = null

        // Creative state
        val creatives = mutableListOf<VASTCreative>()
        var currentCreativeId: String? = null
        var currentCreativeSequence: Int? = null
        var currentCreativeAdId: String? = null

        // Linear state
        var currentLinear: VASTLinear? = null
        var currentSkipOffset: String? = null
        val mediaFiles = mutableListOf<VASTMediaFile>()
        val trackingEvents = mutableListOf<VASTTrackingEvent>()
        val clickTracking = mutableListOf<String>()
        val customClick = mutableListOf<String>()
        var clickThrough: String? = null

        // Product extension state
        val products = mutableListOf<VASTProduct>()
        var isParsingExtensionProduct = false
        var prodAdvertiserId: String? = null
        var prodBrand: String? = null
        var prodImageUrl: String? = null
        var prodName: String? = null
        var prodPdpUrl: String? = null
        var prodPrice: Double? = null
        var prodRating: Double? = null
        var prodSku: String? = null
        var prodStockCount: Int? = null

        // Content accumulator
        var currentContent = StringBuilder()
        var currentAttrs = mapOf<String, String>()

        var eventType = xpp.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentContent = StringBuilder()
                    // Capture all attributes for this element
                    currentAttrs =
                        buildMap {
                            for (i in 0 until xpp.attributeCount) {
                                put(xpp.getAttributeName(i), xpp.getAttributeValue(i))
                            }
                        }

                    when (xpp.name) {
                        "VAST" -> vastVersion = currentAttrs["version"] ?: "4.2"

                        "Ad" -> {
                            currentAdId = currentAttrs["id"] ?: UUID.randomUUID().toString()
                            currentAdSequence = currentAttrs["sequence"]?.toIntOrNull()
                            impressions.clear()
                            errors.clear()
                            creatives.clear()
                            clickThrough = null
                            vastAdTagUri = null
                            adTitle = null
                            currentInLine = null
                            currentWrapper = null
                        }

                        "InLine" -> {
                            impressions.clear()
                            errors.clear()
                            creatives.clear()
                            products.clear()
                        }

                        "Wrapper" -> {
                            impressions.clear()
                            errors.clear()
                            creatives.clear()
                        }

                        "Creative" -> {
                            currentCreativeId = currentAttrs["id"]
                            currentCreativeSequence = currentAttrs["sequence"]?.toIntOrNull()
                            currentCreativeAdId = currentAttrs["adId"]
                            trackingEvents.clear()
                            mediaFiles.clear()
                            clickTracking.clear()
                            customClick.clear()
                        }

                        "Linear" -> {
                            currentSkipOffset = currentAttrs["skipoffset"]
                            trackingEvents.clear()
                            mediaFiles.clear()
                            clickTracking.clear()
                            customClick.clear()
                            clickThrough = null
                        }

                        "VideoClicks" -> {
                            clickTracking.clear()
                            customClick.clear()
                            clickThrough = null
                        }

                        "ViewableImpression" -> {
                            viewable.clear()
                            notViewable.clear()
                            viewUndetermined.clear()
                        }

                        "Extension" -> {
                            if (currentAttrs["type"] == "product") {
                                isParsingExtensionProduct = true
                            }
                        }

                        "Product" -> {
                            if (isParsingExtensionProduct) {
                                prodAdvertiserId = null; prodBrand = null; prodImageUrl = null
                                prodName = null; prodPdpUrl = null; prodPrice = null
                                prodRating = null; prodSku = null; prodStockCount = null
                            }
                        }
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    currentContent.append(xpp.text)
                }

                XmlPullParser.END_TAG -> {
                    val content = currentContent.toString().trim()

                    when (xpp.name) {
                        // ---- Structural ----
                        "VAST" -> {
                            // Response assembled below, nothing to do here
                        }

                        "Ad" -> {
                            val id = currentAdId ?: UUID.randomUUID().toString()
                            currentInLine?.let { ads.add(VASTAd(id, currentAdSequence, inLine = it)) }
                                ?: currentWrapper?.let { ads.add(VASTAd(id, currentAdSequence, wrapper = it)) }
                            currentInLine = null
                            currentWrapper = null
                            currentAdId = null
                            currentAdSequence = null
                        }

                        "InLine" -> {
                            val clickTrackingUrls = creatives.flatMap { it.linear?.videoClicks?.clickTracking ?: emptyList() }
                            val impressionUrls = impressions.map { it.url }
                            val viewableUrls = currentViewableImpression?.viewable ?: viewable.toList()
                            val trigger = VASTTrigger(impressionUrls, clickTrackingUrls, viewableUrls)

                            currentInLine =
                                VASTInLine(
                                    adSystem = currentAdSystem,
                                    adTitle = adTitle,
                                    impressions = impressions.toList(),
                                    errors = errors.toList(),
                                    viewableImpression = currentViewableImpression,
                                    creatives = creatives.toList(),
                                    extensions = products.toList(),
                                    trigger = trigger,
                                )
                            currentAdSystem = null
                            currentViewableImpression = null
                        }

                        "Wrapper" -> {
                            currentWrapper =
                                VASTWrapper(
                                    adSystem = currentAdSystem,
                                    vastAdTagUri = vastAdTagUri ?: "",
                                    impressions = impressions.toList(),
                                    errors = errors.toList(),
                                    viewableImpression = currentViewableImpression,
                                    creatives = creatives.toList(),
                                )
                            currentAdSystem = null
                            currentViewableImpression = null
                        }

                        "AdSystem" -> {
                            currentAdSystem = VASTAdSystem(name = content, version = currentAttrs["version"])
                        }

                        "AdTitle" -> adTitle = content

                        "Impression" -> {
                            if (content.isNotEmpty()) impressions.add(VASTImpression(id = currentAttrs["id"], url = content))
                        }

                        "Error" -> {
                            if (content.isNotEmpty()) errors.add(content)
                        }

                        "VASTAdTagURI" -> vastAdTagUri = content

                        // ---- Viewable Impression ----
                        "ViewableImpression" -> {
                            currentViewableImpression =
                                VASTViewableImpression(
                                    id = currentAttrs["id"],
                                    viewable = viewable.toList(),
                                    notViewable = notViewable.toList(),
                                    viewUndetermined = viewUndetermined.toList(),
                                )
                        }

                        "Viewable" -> { if (content.isNotEmpty()) viewable.add(content) }
                        "NotViewable" -> { if (content.isNotEmpty()) notViewable.add(content) }
                        "ViewUndetermined" -> { if (content.isNotEmpty()) viewUndetermined.add(content) }

                        // ---- Linear / Creative ----
                        "Creative" -> {
                            creatives.add(
                                VASTCreative(
                                    id = currentCreativeId,
                                    sequence = currentCreativeSequence,
                                    adId = currentCreativeAdId,
                                    linear = currentLinear,
                                ),
                            )
                            currentLinear = null
                            currentCreativeId = null
                            currentCreativeSequence = null
                            currentCreativeAdId = null
                        }

                        "Linear" -> {
                            val skipOffset = currentSkipOffset?.let { parseDuration(it) }
                            val videoClicks =
                                VASTVideoClicks(
                                    clickThrough = clickThrough,
                                    clickTracking = clickTracking.toList(),
                                    customClick = customClick.toList(),
                                )
                            currentLinear =
                                VASTLinear(
                                    duration = currentLinear?.duration,
                                    mediaFiles = mediaFiles.toList(),
                                    videoClicks = videoClicks,
                                    trackingEvents = trackingEvents.toList(),
                                    skipOffset = skipOffset,
                                )
                        }

                        "Duration" -> {
                            parseDuration(content)?.let { dur ->
                                currentLinear =
                                    VASTLinear(
                                        duration = dur,
                                        mediaFiles = mediaFiles.toList(),
                                        videoClicks = null,
                                        trackingEvents = trackingEvents.toList(),
                                        skipOffset = null,
                                    )
                            }
                        }

                        "MediaFile" -> {
                            mediaFiles.add(
                                VASTMediaFile(
                                    url = content,
                                    delivery = currentAttrs["delivery"],
                                    type = currentAttrs["type"],
                                    width = currentAttrs["width"]?.toIntOrNull(),
                                    height = currentAttrs["height"]?.toIntOrNull(),
                                    codec = currentAttrs["codec"],
                                    bitrate = currentAttrs["bitrate"]?.toIntOrNull(),
                                    minBitrate = currentAttrs["minBitrate"]?.toIntOrNull(),
                                    maxBitrate = currentAttrs["maxBitrate"]?.toIntOrNull(),
                                    scalable = currentAttrs["scalable"]?.lowercase() == "true",
                                    maintainAspectRatio = currentAttrs["maintainAspectRatio"]?.lowercase() == "true",
                                ),
                            )
                        }

                        "ClickThrough" -> clickThrough = content
                        "ClickTracking" -> { if (content.isNotEmpty()) clickTracking.add(content) }
                        "CustomClick" -> { if (content.isNotEmpty()) customClick.add(content) }

                        "Tracking" -> {
                            val eventStr = currentAttrs["event"]
                            val eventType2 = eventStr?.let { VASTTrackingEventType.from(it) }
                            if (eventType2 != null && content.isNotEmpty()) {
                                val offset = currentAttrs["offset"]?.let { parseDuration(it) }
                                trackingEvents.add(VASTTrackingEvent(event = eventType2, url = content, offset = offset))
                            }
                        }

                        // ---- Product Extension ----
                        "AdvertiserID" -> { if (isParsingExtensionProduct) prodAdvertiserId = content }
                        "Brand" -> { if (isParsingExtensionProduct) prodBrand = content }
                        "ImageURL" -> { if (isParsingExtensionProduct) prodImageUrl = content }
                        "Name" -> { if (isParsingExtensionProduct) prodName = content }
                        "PdpURL" -> { if (isParsingExtensionProduct) prodPdpUrl = content }
                        "Price" -> { if (isParsingExtensionProduct) prodPrice = content.toDoubleOrNull() }
                        "Rating" -> { if (isParsingExtensionProduct) prodRating = content.toDoubleOrNull() }
                        "Sku" -> { if (isParsingExtensionProduct) prodSku = content }
                        "StockCount" -> { if (isParsingExtensionProduct) prodStockCount = content.toIntOrNull() }

                        "Product" -> {
                            if (isParsingExtensionProduct) {
                                products.add(
                                    VASTProduct(
                                        advertiserId = prodAdvertiserId,
                                        brand = prodBrand,
                                        imageUrl = prodImageUrl,
                                        name = prodName,
                                        pdpUrl = prodPdpUrl,
                                        price = prodPrice,
                                        rating = prodRating,
                                        sku = prodSku,
                                        stockCount = prodStockCount,
                                    ),
                                )
                            }
                        }

                        "Extension" -> isParsingExtensionProduct = false
                    }
                }
            }
            eventType = xpp.next()
        }

        val response = VASTResponse(version = vastVersion, ads = ads)
        if (response.isEmpty) throw VASTError.NoAdsFound
        return response
    }

    // MARK: - Wrapper Merge

    private fun mergeWrapperWithInLine(
        wrapper: VASTWrapper,
        wrappedResponse: VASTResponse,
        originalAd: VASTAd,
    ): VASTResponse {
        val wrappedAd = wrappedResponse.firstAd ?: return wrappedResponse
        val inLine = wrappedAd.inLine ?: return wrappedResponse

        val mergedImpressions = wrapper.impressions + inLine.impressions
        val mergedErrors = wrapper.errors + inLine.errors

        // Merge viewable impressions
        val mergedViewable =
            (wrapper.viewableImpression?.viewable ?: emptyList()) +
                (inLine.viewableImpression?.viewable ?: emptyList())
        val mergedNotViewable =
            (wrapper.viewableImpression?.notViewable ?: emptyList()) +
                (inLine.viewableImpression?.notViewable ?: emptyList())
        val mergedViewUndetermined =
            (wrapper.viewableImpression?.viewUndetermined ?: emptyList()) +
                (inLine.viewableImpression?.viewUndetermined ?: emptyList())
        val mergedViewableImpression =
            if (mergedViewable.isNotEmpty() || mergedNotViewable.isNotEmpty() || mergedViewUndetermined.isNotEmpty()) {
                VASTViewableImpression(viewable = mergedViewable, notViewable = mergedNotViewable, viewUndetermined = mergedViewUndetermined)
            } else {
                null
            }

        // Merge tracking events from wrapper creatives into inLine creatives
        val mergedCreatives =
            inLine.creatives.toMutableList().also { mutableCreatives ->
                for (wrapperCreative in wrapper.creatives) {
                    val wrapperLinear = wrapperCreative.linear ?: continue
                    mutableCreatives.forEachIndexed { index, creative ->
                        val linear = creative.linear ?: return@forEachIndexed
                        val mergedTracking = linear.trackingEvents + wrapperLinear.trackingEvents
                        val mergedClickTracking = (linear.videoClicks?.clickTracking ?: emptyList()) + (wrapperLinear.videoClicks?.clickTracking ?: emptyList())
                        val mergedVideoClicks =
                            VASTVideoClicks(
                                clickThrough = linear.videoClicks?.clickThrough,
                                clickTracking = mergedClickTracking,
                                customClick = linear.videoClicks?.customClick ?: emptyList(),
                            )
                        val mergedLinear = linear.copy(videoClicks = mergedVideoClicks, trackingEvents = mergedTracking)
                        mutableCreatives[index] = creative.copy(linear = mergedLinear)
                    }
                }
            }

        val mergedClickTracking = mergedCreatives.flatMap { it.linear?.videoClicks?.clickTracking ?: emptyList() }
        val mergedTrigger =
            VASTTrigger(
                impressionUrls = mergedImpressions.map { it.url },
                clickTrackingUrls = mergedClickTracking,
                viewableUrls = mergedViewableImpression?.viewable ?: emptyList(),
            )

        val mergedInLine =
            inLine.copy(
                adSystem = inLine.adSystem ?: wrapper.adSystem,
                impressions = mergedImpressions,
                errors = mergedErrors,
                viewableImpression = mergedViewableImpression,
                creatives = mergedCreatives,
                trigger = mergedTrigger,
            )

        val mergedAd = VASTAd(id = originalAd.id, sequence = originalAd.sequence, inLine = mergedInLine)
        return VASTResponse(version = wrappedResponse.version, ads = listOf(mergedAd))
    }

    // MARK: - Helpers

    /**
     * Parse duration string in format HH:MM:SS or HH:MM:SS.mmm into seconds.
     */
    private fun parseDuration(string: String): Double? {
        val parts = string.split(":")
        if (parts.size != 3) return null
        val hours = parts[0].toDoubleOrNull() ?: return null
        val minutes = parts[1].toDoubleOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }
}
