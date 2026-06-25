package com.fintrackai

import com.fintrackai.domain.sms.ForexService
import org.junit.Assert.*
import org.junit.Test

class ForexServiceTest {

    @Test
    fun `normalizeCurrencyCode handles Rs`() {
        assertEquals("INR", ForexService.normalizeCurrencyCode("Rs."))
        assertEquals("INR", ForexService.normalizeCurrencyCode("rs"))
        assertEquals("INR", ForexService.normalizeCurrencyCode("INR"))
        assertEquals("INR", ForexService.normalizeCurrencyCode("₹"))
    }

    @Test
    fun `normalizeCurrencyCode handles foreign currencies`() {
        assertEquals("USD", ForexService.normalizeCurrencyCode("USD"))
        assertEquals("EUR", ForexService.normalizeCurrencyCode("EUR"))
        assertEquals("GBP", ForexService.normalizeCurrencyCode("GBP"))
    }

    @Test
    fun `normalizeCurrencyCode handles null and empty`() {
        assertEquals("INR", ForexService.normalizeCurrencyCode(null))
        assertEquals("INR", ForexService.normalizeCurrencyCode(""))
    }
}
