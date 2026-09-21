package com.example.finora.util

import android.content.Context
import com.example.finora.BuildConfig
import com.example.finora.network.FinnhubAuthInterceptor

/**
 * Manages Finnhub API key persistence and resolution.
 * Priority order:
 *  1. In-memory override (runtime/testing)
 *  2. In-app SharedPreferences (user-entered in Settings)
 *  3. BuildConfig.FINNHUB_API_KEY (from local.properties)
 */
object ApiKeyStore {

    private const val PREFS_NAME = "finora_api_prefs"
    private const val KEY_FINNHUB_TOKEN = "finnhub_api_key"

    @Volatile
    private var inMemoryOverrideKey: String? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getApiKey(context: Context? = appContext): String {
        // 1. In-memory override
        inMemoryOverrideKey?.let {
            val sanitized = FinnhubAuthInterceptor.sanitizeApiKey(it)
            if (sanitized.isNotBlank()) return sanitized
        }

        // 2. SharedPreferences
        val targetContext = context ?: appContext
        if (targetContext != null) {
            val prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedKey = prefs.getString(KEY_FINNHUB_TOKEN, null)
            if (!savedKey.isNullOrBlank()) {
                val sanitized = FinnhubAuthInterceptor.sanitizeApiKey(savedKey)
                if (sanitized.isNotBlank()) return sanitized
            }
        }

        // 3. BuildConfig fallback
        return FinnhubAuthInterceptor.sanitizeApiKey(BuildConfig.FINNHUB_API_KEY)
    }

    fun saveApiKey(context: Context, key: String) {
        val sanitized = FinnhubAuthInterceptor.sanitizeApiKey(key)
        inMemoryOverrideKey = sanitized
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FINNHUB_TOKEN, sanitized).apply()
    }

    fun clearSavedApiKey(context: Context) {
        inMemoryOverrideKey = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FINNHUB_TOKEN).apply()
    }

    fun setInMemoryOverride(key: String?) {
        inMemoryOverrideKey = key?.let { FinnhubAuthInterceptor.sanitizeApiKey(it) }
    }

    fun sanitizeApiKey(key: String): String = FinnhubAuthInterceptor.sanitizeApiKey(key)
}
