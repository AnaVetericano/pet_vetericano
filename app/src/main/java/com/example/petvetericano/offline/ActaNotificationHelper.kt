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
import com.example.petvetericano.ActaAtencionActivity
import com.example.petvetericano.R
import com.example.petvetericano.inicio_sesion

/**
 * Helper centralizado para la emisi?n de notificaciones de sincronizaci?n offline de actas.
 *
 * Administra el canal propio "acta_offline_sync", verifica permisos en tiempo de ejecuci?n (POST_NOTIFICATIONS)
 * y asegura que las notificaciones no lancen excepciones ni colisionen entre peticiones.
 */
object ActaNotificationHelper {

    private const val TAG = "ActaNotificationHelper"
    private const val CHANNEL_ID = "acta_offline_sync"

    private const val NOTIFICATION_ID_BASE_EXITO = 100_000
    private const val NOTIFICATION_ID_BASE_REVISION = 200_000
    private const val NOTIFICATION_ID_SESION = 999_999

    private const val REQUEST_CODE_BASE_REVISION = 1_000
    private const val REQUEST_CODE_SESION = 9_999

    /**
     * Crea el canal de notificaciones de forma idempotente en dispositivos con Android 8.0 (API 26) o superior.
     */
    private fun crearCanalNotificacionesSiAplica(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val name = context.getString(R.string.acta_offline_channel_nombre)
                val descriptionText = context.getString(R.string.acta_offline_channel_descripcion)
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
     * Comprueba si la aplicaci?n tiene permiso para emitir notificaciones y si est?n habilitadas globalmente.
     * Retorna `false` de forma segura sin lanzar excepciones.
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
     * Convierte de forma segura un recurso Drawable a un [Bitmap] para su uso en [NotificationCompat.Builder.setLargeIcon].
     */
    private fun obtenerBitmapDrawable(context: Context, @DrawableRes drawableResId: Int): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            drawable.toBitmap(width, height)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo decodificar el drawable a Bitmap", e)
            null
        }
    }

    /**
     * Construye la base com?n del builder de notificaci?n garantizando ?cono peque?o, color de marca,
     * canal e importancia por defecto.
     */
    private fun construirBaseBuilder(
        context: Context,
        titulo: String,
        texto: String
    ): NotificationCompat.Builder {
        crearCanalNotificacionesSiAplica(context)
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
     * Envuelve la llamada a [NotificationManagerCompat.notify] en un bloque try/catch para atrapar [SecurityException].
     */
    private fun emitirNotificacion(
        context: Context,
        notificationId: Int,
        notification: Notification
    ) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo emitir la notificaci?n debido a SecurityException", e)
        }
    }

    /**
     * Emite una notificaci?n de ?xito tras sincronizar un acta sin advertencias ni conflictos.
     */
    fun notificarExito(context: Context, nroRadicado: String, idPeticion: Int) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.acta_offline_notif_exito_titulo)
        val texto = context.getString(R.string.acta_offline_notif_exito_texto, nroRadicado, idPeticion)
        val notifId = NOTIFICATION_ID_BASE_EXITO + idPeticion

        val builder = construirBaseBuilder(context, titulo, texto)
        obtenerBitmapDrawable(context, R.drawable.ic_check)?.let {
            builder.setLargeIcon(it)
        }

        emitirNotificacion(context, notifId, builder.build())
    }

    /**
     * Emite una notificaci?n de ?xito con advertencia cuando el acta fue guardada pero hubo alertas o faltantes de stock.
     */
    fun notificarExitoConAlertaStock(
        context: Context,
        nroRadicado: String,
        idPeticion: Int,
        medicamentos: String
    ) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.acta_offline_notif_stock_alerta_titulo)
        val texto = context.getString(R.string.acta_offline_notif_stock_alerta_texto, nroRadicado, medicamentos)
        val notifId = NOTIFICATION_ID_BASE_EXITO + idPeticion

        val builder = construirBaseBuilder(context, titulo, texto)
        obtenerBitmapDrawable(context, R.drawable.warning_icon4)?.let {
            builder.setLargeIcon(it)
        }

        emitirNotificacion(context, notifId, builder.build())
    }

    /**
     * Emite una notificaci?n indicando que un acta requiere revisi?n.
     * Al tocar la notificaci?n, se abre [ActaAtencionActivity] para la petici?n indicada.
     */
    fun notificarRequiereRevision(
        context: Context,
        nroRadicado: String,
        idPeticion: Int,
        motivo: String
    ) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.acta_offline_notif_atencion_datos_titulo)
        val texto = context.getString(R.string.acta_offline_notif_atencion_datos_texto, nroRadicado, motivo)
        val notifId = NOTIFICATION_ID_BASE_REVISION + idPeticion
        val requestCode = REQUEST_CODE_BASE_REVISION + idPeticion

        val intent = Intent(context, ActaAtencionActivity::class.java).apply {
            putExtra("PETICION_ID", idPeticion)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
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
     * Emite una notificaci?n cuando la sesi?n expir? mientras exist?an actas pendientes de env?o.
     * Al tocar la notificaci?n, se redirige a la pantalla de [inicio_sesion] limpiando la pila de navegaci?n.
     */
    fun notificarSesionExpirada(context: Context) {
        if (!puedeNotificar(context)) return

        val titulo = context.getString(R.string.acta_offline_notif_atencion_sesion_titulo)
        val texto = context.getString(R.string.acta_offline_notif_atencion_sesion_texto)

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
