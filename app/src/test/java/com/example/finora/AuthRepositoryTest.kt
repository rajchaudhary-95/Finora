package com.example.finora

import com.example.finora.data.db.dao.UserDao
import com.example.finora.data.db.entities.User
import com.example.finora.repository.AuthRepository
import com.example.finora.util.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var fakeUserDao: FakeUserDao
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        fakeUserDao = FakeUserDao()
        fakeSessionManager = FakeSessionManager()
        authRepository = AuthRepository(fakeUserDao, fakeSessionManager)
    }

    @Test
    fun register_successfulUserCreation() = runBlocking {
        val result = authRepository.register("Alice Smith", "alice@example.com", "password123")
        assertTrue(result.isSuccess)

        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("Alice Smith", user?.name)
        assertEquals("alice@example.com", user?.email)
        assertNotEquals("password123", user?.passwordHash)
        assertEquals(AuthRepository.hashPassword("password123"), user?.passwordHash)

        // Verify session was saved
        assertTrue(authRepository.isLoggedIn())
        assertEquals("Alice Smith", authRepository.getCurrentUserName())
        assertEquals("alice@example.com", authRepository.getCurrentUserEmail())
    }

    @Test
    fun register_failsOnEmptyName() = runBlocking {
        val result = authRepository.register("   ", "alice@example.com", "password123")
        assertTrue(result.isFailure)
        assertEquals("Name cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun register_failsOnInvalidEmail() = runBlocking {
        val result = authRepository.register("Alice Smith", "invalid-email", "password123")
        assertTrue(result.isFailure)
        assertEquals("Please enter a valid email address", result.exceptionOrNull()?.message)
    }

    @Test
    fun register_failsOnShortPassword() = runBlocking {
        val result = authRepository.register("Alice Smith", "alice@example.com", "12345")
        assertTrue(result.isFailure)
        assertEquals("Password must be at least 6 characters", result.exceptionOrNull()?.message)
    }

    @Test
    fun register_failsOnDuplicateEmail() = runBlocking {
        authRepository.register("Alice", "alice@example.com", "password123")
        val result = authRepository.register("Alice Two", "ALICE@EXAMPLE.COM", "newpassword456")
        assertTrue(result.isFailure)
        assertEquals("An account with this email already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun login_successfulAuthentication() = runBlocking {
        authRepository.register("Bob", "bob@example.com", "secretPass")
        authRepository.logout()
        assertFalse(authRepository.isLoggedIn())

        val loginResult = authRepository.login("bob@example.com", "secretPass")
        assertTrue(loginResult.isSuccess)
        assertTrue(authRepository.isLoggedIn())
        assertEquals("Bob", authRepository.getCurrentUserName())
        assertEquals("bob@example.com", authRepository.getCurrentUserEmail())
    }

    @Test
    fun login_failsOnNonExistentEmail() = runBlocking {
        val result = authRepository.login("nobody@example.com", "secretPass")
        assertTrue(result.isFailure)
        assertEquals("No account found with this email", result.exceptionOrNull()?.message)
    }

    @Test
    fun login_failsOnIncorrectPassword() = runBlocking {
        authRepository.register("Bob", "bob@example.com", "correctPassword")
        authRepository.logout()

        val result = authRepository.login("bob@example.com", "wrongPassword")
        assertTrue(result.isFailure)
        assertEquals("Incorrect password", result.exceptionOrNull()?.message)
        assertFalse(authRepository.isLoggedIn())
    }

    @Test
    fun logout_clearsUserSession() = runBlocking {
        authRepository.register("Charlie", "charlie@example.com", "mypassword")
        assertTrue(authRepository.isLoggedIn())

        authRepository.logout()
        assertFalse(authRepository.isLoggedIn())
        assertEquals("", authRepository.getCurrentUserName())
        assertEquals("", authRepository.getCurrentUserEmail())
    }

    @Test
    fun passwordHash_isDeterministic() {
        val hash1 = AuthRepository.hashPassword("mySecret123")
        val hash2 = AuthRepository.hashPassword("mySecret123")
        val hash3 = AuthRepository.hashPassword("otherSecret")

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
        assertEquals(64, hash1.length) // SHA-256 is 64 hex characters
    }

    // Fakes for Unit Testing
    private class FakeUserDao : UserDao {
        private val users = mutableListOf<User>()
        private var idCounter = 1

        override suspend fun insert(user: User): Long {
            val userWithId = user.copy(id = idCounter++)
            users.add(userWithId)
            return userWithId.id.toLong()
        }

        override suspend fun getByEmail(email: String): User? {
            return users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
        }

        override suspend fun getById(id: Int): User? {
            return users.firstOrNull { it.id == id }
        }

        override suspend fun getUserCount(): Int {
            return users.size
        }
    }

    private class FakeSessionManager : SessionManager {
        private var loggedIn = false
        private var userId = -1
        private var userName = ""
        private var userEmail = ""

        override fun saveSession(user: User) {
            loggedIn = true
            userId = user.id
            userName = user.name
            userEmail = user.email
        }

        override fun isLoggedIn(): Boolean = loggedIn
        override fun getCurrentUserId(): Int = userId
        override fun getCurrentUserName(): String = userName
        override fun getCurrentUserEmail(): String = userEmail

        override fun clearSession() {
            loggedIn = false
            userId = -1
            userName = ""
            userEmail = ""
        }
    }
}
