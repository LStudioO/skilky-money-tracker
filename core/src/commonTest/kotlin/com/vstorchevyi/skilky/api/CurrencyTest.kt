package com.vstorchevyi.skilky.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyTest {
    @Test
    fun `fromCode resolves canonical codes`() {
        assertEquals(Currency.UAH, Currency.fromCode("UAH"))
        assertEquals(Currency.USD, Currency.fromCode("USD"))
        assertEquals(Currency.EUR, Currency.fromCode("EUR"))
        assertEquals(Currency.GBP, Currency.fromCode("GBP"))
    }

    @Test
    fun `fromCode is case-insensitive`() {
        assertEquals(Currency.USD, Currency.fromCode("usd"))
        assertEquals(Currency.EUR, Currency.fromCode("EuR"))
    }

    @Test
    fun `fromCode returns null for unknown codes`() {
        assertNull(Currency.fromCode("XYZ"))
        assertNull(Currency.fromCode(""))
    }

    @Test
    fun `each entry exposes code and symbol`() {
        assertEquals("UAH", Currency.UAH.code)
        assertEquals("₴", Currency.UAH.symbol)
        assertEquals("USD", Currency.USD.code)
        assertEquals("$", Currency.USD.symbol)
        assertEquals("€", Currency.EUR.symbol)
        assertEquals("£", Currency.GBP.symbol)
    }
}
