package com.example.petvetericano

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Clase principal de Application para PetVetericano.
 * Inicializa y aplica las preferencias de tema (Modo Claro / Modo Oscuro)
 * de forma global antes de que se infle cualquier Activity.
 */
class PetVetericanoApp : Application() {

    override fun onCreate() {
        super.onCreate()
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
