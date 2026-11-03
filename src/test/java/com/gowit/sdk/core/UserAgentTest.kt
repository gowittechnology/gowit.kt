package com.gowit.sdk.core

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UserAgentTest {
    @Test
    fun `builds sdk, android and api level`() {
        assertEquals(
            "gowit-kt/1.1.0 (Android 14; API 34)",
            UserAgent.build("1.1.0", "14", 34),
        )
    }

    @Test
    fun `falls back when android release is unknown`() {
        assertEquals(
            "gowit-kt/1.1.0 (Android unknown; API 0)",
            UserAgent.build("1.1.0", null, 0),
        )
    }

    @Test
    fun `interceptor replaces the user agent header`() {
        val original =
            Request.Builder()
                .url("https://example.com/server/v2/sdk/ads")
                .header(UserAgent.HEADER, "okhttp/4.12.0")
                .build()
        val chain = mock(Interceptor.Chain::class.java)
        val captor = ArgumentCaptor.forClass(Request::class.java)
        `when`(chain.request()).thenReturn(original)
        `when`(chain.proceed(captor.capture() ?: original)).thenAnswer {
            Response.Builder()
                .request(it.getArgument(0))
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .build()
        }

        UserAgent.Interceptor("gowit-kt/1.1.0 (Android 14; API 34)").intercept(chain)

        assertEquals("gowit-kt/1.1.0 (Android 14; API 34)", captor.value.header(UserAgent.HEADER))
        assertEquals(1, captor.value.headers(UserAgent.HEADER).size)
    }
}
