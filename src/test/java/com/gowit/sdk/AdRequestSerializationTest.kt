package com.gowit.sdk

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gowit.sdk.core.JsonSerializer
import com.gowit.sdk.model.AdContext
import com.gowit.sdk.model.Customer
import com.gowit.sdk.model.PlacementRequest
import com.gowit.sdk.model.Product
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AdRequestSerializationTest {
    private lateinit var sdk: GowitSdk

    @Before
    fun setUp() {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        sdk = GowitSdk.initialize(context)
    }

    @After
    fun tearDown() {
        GowitSdk.clearInstance()
    }

    private fun requestJson(context: AdContext): JsonObject {
        val request = sdk.buildRequest(listOf(PlacementRequest(placementId = 8, maxAds = 1)), context)
        return JsonParser.parseString(JsonSerializer.toJson(request)).asJsonObject
    }

    @Test
    fun serializesEveryContextField_inApiFieldNames() {
        val json =
            requestJson(
                AdContext(
                    customer =
                        Customer(
                            id = "u1",
                            customerId = "42",
                            gender = "F",
                            age = 30,
                            city = "Riyadh",
                            deviceType = "MOBILE",
                            environmentType = "APP",
                            ip = "10.0.0.1",
                            segments = listOf("vip"),
                            userAgent = "HungerStation/1.0",
                        ),
                    language = "AR",
                    locationId = "3",
                    regionId = "4",
                    search = "burger",
                    category = "Burgers",
                    categoryId = "7",
                    products =
                        listOf(
                            Product(id = "p1", categories = listOf("Burgers", "Fast Food"), categoryIdStructures = listOf("7", "1")),
                        ),
                    filters = listOf("brand:a"),
                    pageNumber = 2,
                    sessionId = "s1",
                ),
            )

        assertEquals("s1", json["session_id"].asString)
        assertEquals("AR", json["language"].asString)
        assertEquals("3", json["location_id"].asString)
        assertEquals("4", json["region_id"].asString)
        assertEquals("burger", json["search"].asString)
        assertEquals("Burgers", json["category"].asString)
        assertEquals("7", json["category_id"].asString)
        assertEquals(2, json["page_number"].asInt)
        assertEquals("brand:a", json["filters"].asJsonArray[0].asString)

        val customer = json["customer"].asJsonObject
        assertEquals("u1", customer["id"].asString)
        assertEquals("42", customer["customer_id"].asString)
        assertEquals("F", customer["gender"].asString)
        assertEquals(30, customer["age"].asInt)
        assertEquals("Riyadh", customer["city"].asString)
        assertEquals("MOBILE", customer["device_type"].asString)
        assertEquals("APP", customer["environment_type"].asString)
        assertEquals("10.0.0.1", customer["ip"].asString)
        assertEquals("vip", customer["segments"].asJsonArray[0].asString)
        assertEquals("HungerStation/1.0", customer["user_agent"].asString)

        val product = json["products"].asJsonArray[0].asJsonObject
        assertEquals("p1", product["id"].asString)
        assertEquals("Fast Food", product["categories"].asJsonArray[1].asString)
        assertEquals("1", product["category_id_structures"].asJsonArray[1].asString)

        val placement = json["placements"].asJsonArray[0].asJsonObject
        assertEquals(8, placement["placement_id"].asInt)
        assertEquals(1, placement["max_ads"].asInt)
    }

    @Test
    fun omitsUnsetFields() {
        val json = requestJson(AdContext(sessionId = "s1"))

        assertFalse(json.has("customer"))
        assertFalse(json.has("language"))
        assertFalse(json.has("region_id"))
        assertFalse(json.has("page_number"))
    }

    @Test
    fun generatesSessionId_whenNoneIsSet() {
        val json = requestJson(AdContext())
        assertTrue(json["session_id"].asString.isNotBlank())
    }

    @Test
    fun defaultContext_isReadableAndClearable() {
        sdk.setDefaultContext(AdContext(language = "AR"))
        assertEquals("AR", sdk.defaultContext?.language)

        sdk.setDefaultContext(null)
        assertEquals(null, sdk.defaultContext)
    }
}
