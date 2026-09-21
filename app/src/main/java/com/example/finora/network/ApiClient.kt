package com.example.finora.network

import com.example.finora.BuildConfig
import com.example.finora.util.ApiKeyStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton factory for Retrofit and FinnhubApiService.
 */
object ApiClient {

    private const val BASE_URL = "https://finnhub.io/api/v1/"

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun createOkHttpClient(keyProvider: () -> String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(FinnhubAuthInterceptor(keyProvider))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun createFinnhubService(apiKeyProvider: () -> String = { ApiKeyStore.getApiKey() }): FinnhubApiService {
        val client = createOkHttpClient(apiKeyProvider)
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FinnhubApiService::class.java)
    }

    fun createFinnhubService(staticApiKey: String): FinnhubApiService {
        return createFinnhubService { staticApiKey }
    }
}
