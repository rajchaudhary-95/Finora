package com.example.finora.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.finora.MainActivity
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.databinding.ActivityLoginBinding
import com.example.finora.repository.AuthRepository
import com.example.finora.util.UserSessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = FinoraDatabase.getInstance(this)
        val sessionManager = UserSessionManager.getInstance(this)
        authRepository = AuthRepository(db.userDao(), sessionManager)

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvBtnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (email.isBlank()) {
            showError("Please enter your email address")
            return
        }

        if (password.isBlank()) {
            showError("Please enter your password")
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = authRepository.login(email, password)
            setLoading(false)

            result.onSuccess {
                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }.onFailure { error ->
                showError(error.message ?: "Authentication failed. Please try again.")
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "" else "Sign In"
        if (isLoading) {
            binding.tvError.visibility = View.GONE
        }
    }
}
