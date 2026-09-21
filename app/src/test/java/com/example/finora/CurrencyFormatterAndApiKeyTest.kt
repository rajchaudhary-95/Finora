package com.example.finora

import com.example.finora.network.FinnhubAuthInterceptor
import com.example.finora.util.ApiKeyStore
import com.example.finora.util.CurrencyFormatter
import org.junit.Assert.*
import org.junit.Test

class CurrencyFormatterAndApiKeyTest {

    @Test
    fun currencyFormatter_formatsAmountsWithRupeeSymbol() {
        assertEquals("₹0.00", CurrencyFormatter.format(0.0))
        assertEquals("₹1,234.56", CurrencyFormatter.format(1234.56))
        assertEquals("₹50.00", CurrencyFormatter.format(50.0))
        assertEquals("₹1,000,000.00", CurrencyFormatter.format(1000000.0))
    }

    @Test
    fun currencyFormatter_formatsWithSign() {
        assertEquals("+₹500.00", CurrencyFormatter.formatWithSign(500.0))
        assertEquals("-₹250.75", CurrencyFormatter.formatWithSign(-250.75))
        assertEquals("₹0.00", CurrencyFormatter.formatWithSign(0.0))
    }

    @Test
    fun currencyFormatter_formatsAxis() {
        assertEquals("₹0.0", CurrencyFormatter.formatAxis(0.0f))
        assertEquals("₹150.5", CurrencyFormatter.formatAxis(150.5f))
    }

    @Test
    fun apiKeySanitization_stripsAccidentalPastPrefixAndWhitespace() {
        val rawPasted = "PASTdao05j9r01qqjqh3roh0dao05j9r01qqjqh3rohg"
        val sanitized = ApiKeyStore.sanitizeApiKey(rawPasted)
        assertEquals("dao05j9r01qqjqh3roh0dao05j9r01qqjqh3rohg", sanitized)

        val withSpaces = "  token12345  "
        assertEquals("token12345", ApiKeyStore.sanitizeApiKey(withSpaces))

        val withQuotes = "\"my_secret_token\""
        assertEquals("my_secret_token", ApiKeyStore.sanitizeApiKey(withQuotes))

        val lowerCasePast = "pasttokenABC"
        assertEquals("tokenABC", ApiKeyStore.sanitizeApiKey(lowerCasePast))
    }

    @Test
    fun apiKeyStore_inMemoryOverrideTakesPrecedence() {
        ApiKeyStore.setInMemoryOverride("test_key_123")
        assertEquals("test_key_123", ApiKeyStore.getApiKey())

        ApiKeyStore.setInMemoryOverride(null)
    }
}
