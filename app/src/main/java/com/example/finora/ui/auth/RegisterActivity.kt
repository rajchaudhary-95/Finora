package com.example.finora.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.finora.MainActivity
import com.example.finora.data.db.FinoraDatabase
import com.example.finora.databinding.ActivityRegisterBinding
import com.example.finora.repository.AuthRepository
import com.example.finora.util.UserSessionManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = FinoraDatabase.getInstance(this)
        val sessionManager = UserSessionManager.getInstance(this)
        authRepository = AuthRepository(db.userDao(), sessionManager)

        binding.btnRegister.setOnClickListener {
            performRegister()
        }

        binding.tvBtnLogin.setOnClickListener {
            finish()
        }
    }

    private fun performRegister() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (name.isBlank()) {
            showError("Please enter your full name")
            return
        }

        if (email.isBlank()) {
            showError("Please enter your email address")
            return
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters long")
            return
        }

        if (password != confirmPassword) {
            showError("Passwords do not match")
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            val result = authRepository.register(name, email, password)
            setLoading(false)

            result.onSuccess {
                val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }.onFailure { error ->
                showError(error.message ?: "Registration failed. Please try again.")
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
        binding.btnRegister.text = if (isLoading) "" else "Create Account"
        if (isLoading) {
            binding.tvError.visibility = View.GONE
        }
    }
}
