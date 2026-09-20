package com.example.finora.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically adds the Finnhub API key
 * as a "token" query parameter to every outgoing request.
 */
class FinnhubAuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalHttpUrl = originalRequest.url

        val authenticatedUrl = originalHttpUrl.newBuilder()
            .addQueryParameter("token", apiKey)
            .build()

        val authenticatedRequest = originalRequest.newBuilder()
            .url(authenticatedUrl)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
