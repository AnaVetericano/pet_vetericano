package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityMainBinding
import com.example.petvetericano.databinding.ActivityRegistroBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
        }


        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnLoginWelcome.setOnClickListener { Login() }
        binding.btnSignUpWelcome.setOnClickListener { SignUp() }

    }

    private fun Login(){
        intent = Intent(this, inicio_sesion::class.java)
        startActivity(intent)
    }
    private fun SignUp(){
        intent = Intent(this, Registro::class.java)
        startActivity(intent)
    }
}