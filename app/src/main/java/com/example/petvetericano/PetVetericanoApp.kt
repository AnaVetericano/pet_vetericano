package com.example.petvetericano

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager

/**
 * Clase principal de Application para PetVetericano.
 * Inicializa y aplica las preferencias de tema (Modo Claro / Modo Oscuro)
 * de forma global antes de que se infle cualquier Activity.
 */
class PetVetericanoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        SesionExpiradaManager.init(this)
        aplicarModoOscuroGlobal()
    }

    private fun aplicarModoOscuroGlobal() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
