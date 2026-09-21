package com.example.finora.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically adds the Finnhub API key
 * as a "token" query parameter and "X-Finnhub-Token" header to every outgoing request.
 * Supports dynamic key providers (e.g., SharedPreferences or runtime updates).
 */
class FinnhubAuthInterceptor(private val keyProvider: () -> String) : Interceptor {

    constructor(staticKey: String) : this({ staticKey })

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalHttpUrl = originalRequest.url

        val apiKey = sanitizeApiKey(keyProvider())

        val authenticatedUrl = originalHttpUrl.newBuilder()
            .addQueryParameter("token", apiKey)
            .build()

        val authenticatedRequest = originalRequest.newBuilder()
            .header("X-Finnhub-Token", apiKey)
            .url(authenticatedUrl)
            .build()

        return chain.proceed(authenticatedRequest)
    }

    companion object {
        /**
         * Cleans user input: strips whitespace, surrounding quotes, and accidental
         * "PASTE" or "PAST" prefixes.
         */
        fun sanitizeApiKey(raw: String): String {
            var trimmed = raw.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (trimmed.startsWith("PASTE", ignoreCase = true)) {
                trimmed = trimmed.substring(5).trim()
            } else if (trimmed.startsWith("PAST", ignoreCase = true)) {
                trimmed = trimmed.substring(4).trim()
            }
            return trimmed
        }
    }
}
