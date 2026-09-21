package com.example.finora.util

import android.content.Context
import android.content.SharedPreferences
import com.example.finora.data.db.entities.User

/**
 * Contract for managing the current logged-in user's session state.
 */
interface SessionManager {
    fun saveSession(user: User)
    fun isLoggedIn(): Boolean
    fun getCurrentUserId(): Int
    fun getCurrentUserName(): String
    fun getCurrentUserEmail(): String
    fun clearSession()
}

/**
 * SharedPreferences implementation of SessionManager.
 */
class UserSessionManager(context: Context) : SessionManager {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "finora_user_session"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_USER_EMAIL = "key_user_email"

        @Volatile
        private var INSTANCE: UserSessionManager? = null

        fun getInstance(context: Context): UserSessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserSessionManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    override fun saveSession(user: User) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .apply()
    }

    override fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    override fun getCurrentUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    override fun getCurrentUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    override fun getCurrentUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    override fun clearSession() {
        prefs.edit().clear().apply()
    }
}
