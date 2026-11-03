package com.example.petvetericano.offline

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.petvetericano.R
import com.example.petvetericano.bienvenida
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.reporte_enviado

/**
 * Helper centralizado para la emisión de notificaciones de sincronización offline de reportes ciudadanos.
 * Administra el canal propio "reporte_offline_sync", verifica permisos en runtime y captura SecurityException.
 */
object ReporteNotificationHelper {

    private const val TAG = "ReporteNotifHelper"
    private const val CHANNEL_ID = "reporte_offline_sync"

    private const val NOTIFICATION_ID_BASE_EXITO = 300_000
    private const val NOTIFICATION_ID_BASE_ATENCION = 400_000
    private const val NOTIFICATION_ID_SESION = 999_998

    private const val REQUEST_CODE_BASE_EXITO = 31_000
    private const val REQUEST_CODE_BASE_ATENCION = 41_000
    private const val REQUEST_CODE_SESION = 91_998

    /**
     * Crea el canal de notificaciones propio en Android 8.0 (API 26) o superior.
     */
    private fun crearCanalSiAplica(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val name = context.getString(R.string.reporte_offline_channel_nombre)
                val descriptionText = context.getString(R.string.reporte_offline_channel_descripcion)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    name,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = descriptionText
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Comprueba si la aplicación tiene permiso para emitir notificaciones y si están habilitadas globalmente.
     */
    private fun puedeNotificar(context: Context): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tienePermiso = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!tienePermiso) {
                return false
            }
        }
        return true
    }

    /**
     * Decodifica un recurso Drawable a Bitmap para su uso en setLargeIcon.
     */
    private fun obtenerBitmapDrawable(context: Context, @DrawableRes drawableResId: Int): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            drawable.toBitmap(width, height)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar drawable a Bitmap: ${e.message}")
            null
        }
    }

    /**
     * Construye la base común del builder de notificación garantizando ícono pequeño, color de marca, canal e importancia.
     */
    private fun construirBaseBuilder(
        context: Context,
        titulo: String,
        texto: String
    ): NotificationCompat.Builder {
        crearCanalSiAplica(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    /**
     * Envuelve la llamada a NotificationManagerCompat.notify en try/catch para atrapar SecurityException.
     */
    private fun emitirNotificacion(
        context: Context,
        notificationId: Int,
        notification: Notification
    ) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo emitir la notificación debido a SecurityException: ${e.message}")
        }
    }

    /**
     * Emite notificación de éxito tras sincronizarse el reporte en el backend.
     * ID: 300_000 + idLocal.
     */
    fun notificarExito(context: Context, idLocal: Long, nroRadicado: String?, idPeticion: Int) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.reporte_offline_notif_exito_titulo)
        val texto = if (nroRadicado.isNullOrBlank()) {
            context.getString(R.string.reporte_offline_notif_exito_texto_sin_radicado)
        } else {
            context.getString(R.string.reporte_offline_notif_exito_texto, nroRadicado)
        }
        val notifId = (NOTIFICATION_ID_BASE_EXITO + idLocal).toInt()

        val intent = Intent(context, reporte_enviado::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ID_LOCAL", idLocal)
            putExtra("HAY_CONEXION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = construirBaseBuilder(context, titulo, texto)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        obtenerBitmapDrawable(context, R.drawable.ic_check)?.let {
            builder.setLargeIcon(it)
        }

        emitirNotificacion(context, notifId, builder.build())
    }

    /**
     * Emite notificación cuando el reporte requiere atención manual del ciudadano.
     * ID: 400_000 + idLocal.
     */
    fun notificarRequiereAtencion(context: Context, idLocal: Long, mensaje: String) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.reporte_offline_notif_atencion_titulo)
        val texto = context.getString(R.string.reporte_offline_notif_atencion_texto, idLocal, mensaje)
        val notifId = (NOTIFICATION_ID_BASE_ATENCION + idLocal).toInt()

        val intent = Intent(context, bienvenida::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (REQUEST_CODE_BASE_ATENCION + idLocal).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = construirBaseBuilder(context, titulo, texto)
            .setContentIntent(pendingIntent)

        obtenerBitmapDrawable(context, R.drawable.warning_icon4)?.let {
            builder.setLargeIcon(it)
        }

        emitirNotificacion(context, notifId, builder.build())
    }

    /**
     * Emite notificación propia de sesión expirada para reportes ciudadanos con ID 999_998.
     */
    fun notificarSesionExpirada(context: Context) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.reporte_offline_notif_atencion_sesion_titulo)
        val texto = context.getString(R.string.reporte_offline_notif_atencion_sesion_texto)

        val intent = Intent(context, inicio_sesion::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SESION,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = construirBaseBuilder(context, titulo, texto)
            .setContentIntent(pendingIntent)

        obtenerBitmapDrawable(context, R.drawable.warning_icon4)?.let {
            builder.setLargeIcon(it)
        }

        emitirNotificacion(context, NOTIFICATION_ID_SESION, builder.build())
    }
}

