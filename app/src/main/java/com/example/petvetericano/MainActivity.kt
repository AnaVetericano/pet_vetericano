package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.petvetericano.databinding.ActivityMainBinding
import com.example.petvetericano.network.RetrofitClient

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar el modo oscuro ANTES de inflar cualquier vista
        aplicarModoOscuro()

        super.onCreate(savedInstanceState)

        val prefs = SharedPreferencesManager(this)
        val accessToken = prefs.getAccessToken()
        val refreshToken = prefs.getRefreshToken()
        val userId = prefs.getUserId()
        val userRole = prefs.getUserRole()

        val tieneToken = accessToken.isNotEmpty() || refreshToken.isNotEmpty()
        val rolValido = userRole > 0

        if (tieneToken && userId > 0 && rolValido) {
            if (accessToken.isNotEmpty()) {
                RetrofitClient.authToken = accessToken
            }
            val destino = if (userRole == inicio_sesion.ROL_VETERINARIO) {
                Intent(this, dahsboard_veterianrio::class.java)
            } else {
                Intent(this, bienvenida::class.java)
            }
            destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(destino)
            finish()
            return
        }

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
