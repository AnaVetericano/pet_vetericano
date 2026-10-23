package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.petvetericano.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar el modo oscuro ANTES de inflar cualquier vista
        aplicarModoOscuro()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLoginWelcome.setOnClickListener { login() }
        binding.btnSignUpWelcome.setOnClickListener { signUp() }
    }

    // Lee la preferencia guardada y aplica el tema en toda la app
    private fun aplicarModoOscuro() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun login() {
        val intent = Intent(this, inicio_sesion::class.java)
        startActivity(intent)
    }

    private fun signUp() {
        val intent = Intent(this, Registro::class.java)
        startActivity(intent)
    }
}