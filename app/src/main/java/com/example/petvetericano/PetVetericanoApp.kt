package com.example.petvetericano

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.notificaciones.NotificacionesRepository
import com.example.petvetericano.notificaciones.VetericanoMessagingService

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
        configurarNotificaciones()
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

    /**
     * Notificaciones (contrato v1 §7): canal `vetericano_notificaciones`, permiso tras el login y
     * recuperación REST obligatoria cada vez que la app pasa a primer plano (sin sesión no hace nada).
     */
    private fun configurarNotificaciones() {
        crearCanalNotificaciones()
        FcmTokenManager.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                FcmTokenManager.asegurarRegistroEnSegundoPlano(this@PetVetericanoApp)
                NotificacionesRepository.recuperarPendientesEnSegundoPlano(this@PetVetericanoApp)
            }
        })
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            VetericanoMessagingService.CANAL_ID,
            getString(R.string.notif_canal_nombre),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_canal_descripcion)
        }
        // Idempotente: si ya existe solo actualiza nombre y descripción.
        manager.createNotificationChannel(canal)
    }
}
