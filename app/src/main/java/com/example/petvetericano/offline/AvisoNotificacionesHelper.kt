package com.example.petvetericano.offline

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.petvetericano.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Helper para evaluar y mostrar el aviso educativo de activación de notificaciones
 * para el peticionario/ciudadano.
 */
object AvisoNotificacionesHelper {

    private const val PREFS_NAME = "aviso_notificaciones_reportes"
    private const val KEY_ULTIMA_VEZ = "key_ultima_vez_mostrado"
    private const val KEY_TOTAL_VECES = "key_total_veces_mostrado"
    private const val KEY_PERMISO_SOLICITADO_PREVIO = "key_permiso_solicitado_previo"

    private const val CHANNEL_ID = "reporte_offline_sync"
    private const val MAX_VECES = 3
    private const val INTERVALO_DIAS_MS = 7L * 24L * 60L * 60L * 1000L

    /**
     * Determina si las notificaciones están desactivadas para el canal o a nivel global de la app.
     */
    fun estanNotificacionesDesactivadas(context: Context): Boolean {
        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val canal = manager?.getNotificationChannel(CHANNEL_ID)
            if (canal != null && canal.importance == NotificationManager.IMPORTANCE_NONE) {
                return true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedido = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!concedido) {
                return true
            }
        }

        return false
    }

    /**
     * Evalúa si corresponde mostrar el diálogo según reglas de negocio:
     * notificaciones desactivadas, menos de 3 muestras en total y al menos 7 días desde la última.
     */
    fun debeMostrarAviso(context: Context): Boolean {
        if (!estanNotificacionesDesactivadas(context)) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val totalVeces = prefs.getInt(KEY_TOTAL_VECES, 0)
        if (totalVeces >= MAX_VECES) {
            return false
        }

        val ultimaVez = prefs.getLong(KEY_ULTIMA_VEZ, 0L)
        val ahora = System.currentTimeMillis()
        if (ahora - ultimaVez < INTERVALO_DIAS_MS) {
            return false
        }

        return true
    }

    /**
     * Registra la fecha actual y suma al conteo de muestras en SharedPreferences.
     */
    fun registrarMuestra(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val veces = prefs.getInt(KEY_TOTAL_VECES, 0)
        prefs.edit()
            .putLong(KEY_ULTIMA_VEZ, System.currentTimeMillis())
            .putInt(KEY_TOTAL_VECES, veces + 1)
            .apply()
    }

    /**
     * Muestra el diálogo Material explicativo si se cumplen las condiciones y la Activity está activa.
     */
    fun mostrarAvisoSiCorresponde(
        activity: AppCompatActivity,
        onSolicitarPermiso: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!debeMostrarAviso(activity)) return

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.notif_aviso_dialog_titulo)
            .setMessage(R.string.notif_aviso_dialog_mensaje)
            .setPositiveButton(R.string.notif_aviso_btn_activar) { dialog, _ ->
                dialog.dismiss()
                registrarMuestra(activity)
                ejecutarAccionActivar(activity, onSolicitarPermiso)
            }
            .setNegativeButton(R.string.notif_aviso_btn_ahora_no) { dialog, _ ->
                dialog.dismiss()
                registrarMuestra(activity)
            }
            .setOnCancelListener {
                registrarMuestra(activity)
            }
            .show()
    }

    private fun ejecutarAccionActivar(
        activity: AppCompatActivity,
        onSolicitarPermiso: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val yaSolicitado = prefs.getBoolean(KEY_PERMISO_SOLICITADO_PREVIO, false)
            val puedeMostrarRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)

            if (yaSolicitado && !puedeMostrarRationale) {
                // Denegado antes permanentemente: abrir ajustes del sistema
                abrirAjustesNotificaciones(activity)
            } else {
                prefs.edit().putBoolean(KEY_PERMISO_SOLICITADO_PREVIO, true).apply()
                onSolicitarPermiso()
            }
        } else {
            abrirAjustesNotificaciones(activity)
        }
    }

    /**
     * Abre los ajustes de notificación de la aplicación en el sistema operativo.
     */
    fun abrirAjustesNotificaciones(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
