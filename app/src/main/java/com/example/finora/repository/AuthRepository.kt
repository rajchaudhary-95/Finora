package com.example.finora.repository

import com.example.finora.data.db.dao.UserDao
import com.example.finora.data.db.entities.User
import com.example.finora.util.SessionManager
import com.example.finora.util.UserSessionManager
import java.security.MessageDigest

/**
 * Repository handling user registration, authentication, and session state.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {

    private val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()

    suspend fun register(name: String, email: String, password: String): Result<User> {
        val trimmedName = name.trim()
        val cleanEmail = email.trim().lowercase()

        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty"))
        }

        if (cleanEmail.isBlank() || !emailPattern.matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }

        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        val existingUser = userDao.getByEmail(cleanEmail)
        if (existingUser != null) {
            return Result.failure(IllegalArgumentException("An account with this email already exists"))
        }

        val passwordHash = hashPassword(password)
        val user = User(
            name = trimmedName,
            email = cleanEmail,
            passwordHash = passwordHash
        )

        val id = userDao.insert(user)
        val createdUser = user.copy(id = id.toInt())
        sessionManager.saveSession(createdUser)
        return Result.success(createdUser)
    }

    suspend fun login(email: String, password: String): Result<User> {
        val cleanEmail = email.trim().lowercase()

        if (cleanEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email cannot be empty"))
        }

        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty"))
        }

        val user = userDao.getByEmail(cleanEmail)
            ?: return Result.failure(IllegalArgumentException("No account found with this email"))

        val inputHash = hashPassword(password)
        if (inputHash != user.passwordHash) {
            return Result.failure(IllegalArgumentException("Incorrect password"))
        }

        sessionManager.saveSession(user)
        return Result.success(user)
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    fun getCurrentUserName(): String = sessionManager.getCurrentUserName()

    fun getCurrentUserEmail(): String = sessionManager.getCurrentUserEmail()

    companion object {
        fun hashPassword(password: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
