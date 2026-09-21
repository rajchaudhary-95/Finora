package com.example.finora.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.finora.MainActivity
import com.example.finora.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check session and route after 1200ms
        lifecycleScope.launch {
            delay(1200)
            val sessionManager = com.example.finora.util.UserSessionManager.getInstance(this@SplashActivity)
            val targetClass = if (sessionManager.isLoggedIn()) {
                MainActivity::class.java
            } else {
                com.example.finora.ui.auth.LoginActivity::class.java
            }
            val intent = Intent(this@SplashActivity, targetClass)
            startActivity(intent)
            finish()
        }
    }
}
