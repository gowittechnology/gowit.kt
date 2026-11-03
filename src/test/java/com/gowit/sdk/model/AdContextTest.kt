package com.gowit.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AdContextTest {
    @Test
    fun mergedOver_nullBase_returnsSelf() {
        val context = AdContext(language = "AR")
        assertSame(context, context.mergedOver(null))
    }

    @Test
    fun mergedOver_specificValuesWin_andMissingValuesFallBack() {
        val base =
            AdContext(
                language = "EN",
                locationId = "1",
                regionId = "10",
                category = "Burgers",
                filters = listOf("brand:a"),
                pageNumber = 2,
                sessionId = "base-session",
            )
        val specific = AdContext(language = "AR", search = "pizza", sessionId = "view-session")

        val merged = specific.mergedOver(base)

        assertEquals("AR", merged.language)
        assertEquals("pizza", merged.search)
        assertEquals("view-session", merged.sessionId)
        assertEquals("1", merged.locationId)
        assertEquals("10", merged.regionId)
        assertEquals("Burgers", merged.category)
        assertEquals(listOf("brand:a"), merged.filters)
        assertEquals(2, merged.pageNumber)
    }

    @Test
    fun mergedOver_mergesCustomerFieldByField() {
        val base =
            AdContext(
                customer = Customer(customerId = "42", city = "Jeddah", segments = listOf("vip")),
            )
        val specific = AdContext(customer = Customer(city = "Riyadh", deviceType = "MOBILE"))

        val customer = specific.mergedOver(base).customer!!

        assertEquals("42", customer.customerId)
        assertEquals("Riyadh", customer.city)
        assertEquals("MOBILE", customer.deviceType)
        assertEquals(listOf("vip"), customer.segments)
    }

    @Test
    fun customerMergedOver_handlesNullSides() {
        val customer = Customer(customerId = "42")
        assertSame(customer, customer.mergedOver(null))
        assertSame(customer, (null as Customer?).mergedOver(customer))
        assertNull((null as Customer?).mergedOver(null))
    }

    @Test
    fun mergedOver_replacesListsInsteadOfConcatenating() {
        val base = AdContext(products = listOf(Product(id = "1")), filters = listOf("a"))
        val specific = AdContext(products = listOf(Product(id = "2")))

        val merged = specific.mergedOver(base)

        assertEquals(listOf(Product(id = "2")), merged.products)
        assertEquals(listOf("a"), merged.filters)
    }

    @Test
    fun builderWithContext_overridesBuilderValues_andMergesCustomer() {
        val builder =
            AdRequestBuilder(placementId = 8)
                .with(customer = Customer(customerId = "42"))
                .withLanguage("EN")
                .with(
                    AdContext(
                        customer = Customer(city = "Riyadh"),
                        language = "AR",
                        regionId = "5",
                        products = listOf(Product(id = "p1", categories = listOf("Burgers", "Fast Food"))),
                    ),
                )

        assertEquals("AR", builder.language)
        assertEquals("5", builder.regionId)
        assertEquals("42", builder.customer?.customerId)
        assertEquals("Riyadh", builder.customer?.city)
        assertEquals(listOf("Burgers", "Fast Food"), builder.products?.first()?.categories)
    }

    @Test
    fun builderToAdContext_keepsAllFields() {
        val context =
            AdRequestBuilder(placementId = 8)
                .with(sessionId = "s1")
                .with(products = listOf(ProductRequest("p1", "Burgers", listOf("Burgers"), listOf("7"))))
                .withSearch("burger")
                .withCategory("Burgers")
                .withCategoryId("7")
                .withLocationId("3")
                .withRegionId("4")
                .withLanguage("AR")
                .toAdContext()

        assertEquals("s1", context.sessionId)
        assertEquals("burger", context.search)
        assertEquals("Burgers", context.category)
        assertEquals("7", context.categoryId)
        assertEquals("3", context.locationId)
        assertEquals("4", context.regionId)
        assertEquals("AR", context.language)
        assertEquals(0, context.pageNumber)
        assertEquals(
            Product(id = "p1", category = "Burgers", categories = listOf("Burgers"), categoryIdStructures = listOf("7")),
            context.products?.first(),
        )
    }
}
