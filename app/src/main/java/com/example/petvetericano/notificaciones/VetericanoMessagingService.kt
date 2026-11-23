package com.example.petvetericano.notificaciones

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.petvetericano.R
import com.example.petvetericano.models.NotificacionPush
import com.example.petvetericano.models.NotificacionesMapper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio FCM (CONTRATO_NOTIFICACIONES_V1 §7, solo NP).
 * - Primer plano: `onMessageReceived` muestra la notificación local en el canal `vetericano_notificaciones`.
 * - Segundo plano / app cerrada: el sistema muestra el bloque `notification`; la recuperación REST cubre el resto.
 * - `onNewToken`: registra el token si hay sesión.
 */
class VetericanoMessagingService : FirebaseMessagingService() {

    // firebase-messaging 25.1 marca onNewToken/getToken como obsoletos en favor de register()/onRegistered,
    // pero ese modo solo se activa con la meta-data `firebase_messaging_installation_id_enabled`; sin ella
    // (caso de esta app y del contrato §7, token FCM clásico) el SDK sigue entregando el token por aquí.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext
        NotificacionesRepository.logDebug("Nuevo token FCM (…${FcmTokenManager.sufijo(token)})")
        if (!NotificacionesRepository.haySesion(app)) return
        // Se ejecuta en un hilo de trabajo del SDK: registro síncrono con tiempo máximo.
        FcmTokenManager.registrarDesdeServicio(app, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = applicationContext
        // Sin sesión no se muestra nada (p. ej. mensaje para una cuenta que ya cerró sesión).
        if (!NotificacionesRepository.haySesion(app)) {
            NotificacionesRepository.logDebug("Mensaje FCM ignorado: no hay sesión")
            return
        }
        val push = NotificacionesMapper.desdeDataFcm(
            data = message.data,
            tituloNotificacion = message.notification?.title,
            cuerpoNotificacion = message.notification?.body
        ) ?: return

        NotificacionesRepository.mostrarUnaVez(app, push.clave) {
            mostrarNotificacion(app, push)
        }
        NotificacionesRepository.notificarNuevaNotificacion()
    }

    companion object {
        /** Canal del contrato §7 (lo crea PetVetericanoApp en API 26+). */
        const val CANAL_ID = "vetericano_notificaciones"
        const val EXTRA_CLAVE = "com.example.petvetericano.notificaciones.EXTRA_CLAVE"
        private const val TAG_NOTIFICACION = "vetericano_np"
        private const val TAG_RESUMEN = "vetericano_np_resumen"
        private const val ID_RESUMEN = 7_301

        fun idNotificacion(clave: String?): Int =
            clave?.hashCode() ?: (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

        fun puedeNotificar(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

        /** Muestra una NP como notificación local; al tocarla abre [NotificacionesActivity]. */
        fun mostrarNotificacion(context: Context, push: NotificacionPush): Boolean {
            if (!puedeNotificar(context)) return false
            val id = idNotificacion(push.clave)
            val builder = construirBase(context, push.titulo, push.mensaje)
                .setContentIntent(intentBandeja(context, id, push.clave))
            return emitir(context, TAG_NOTIFICACION, id, builder)
        }

        /** Resumen cuando la recuperación REST encuentra muchas NP pendientes. */
        fun mostrarResumen(context: Context, cantidad: Int): Boolean {
            if (!puedeNotificar(context)) return false
            val titulo = context.getString(R.string.notif_resumen_titulo)
            val texto = context.getString(R.string.notif_resumen_texto, cantidad)
            val builder = construirBase(context, titulo, texto)
                .setNumber(cantidad)
                .setContentIntent(intentBandeja(context, ID_RESUMEN, null))
            return emitir(context, TAG_RESUMEN, ID_RESUMEN, builder)
        }

        /** Quita de la bandeja del sistema la notificación local de una clave (al marcarla leída). */
        fun cancelar(context: Context, clave: String) {
            try {
                NotificationManagerCompat.from(context).cancel(TAG_NOTIFICACION, idNotificacion(clave))
            } catch (_: Exception) {
            }
        }

        /** Quita todas las notificaciones locales de NP (al marcar todas como leídas). */
        fun cancelarTodas(context: Context) {
            try {
                val manager = NotificationManagerCompat.from(context)
                manager.activeNotifications
                    .filter { it.tag == TAG_NOTIFICACION || it.tag == TAG_RESUMEN }
                    .forEach { manager.cancel(it.tag, it.id) }
            } catch (_: Exception) {
            }
        }

        private fun construirBase(context: Context, titulo: String, texto: String): NotificationCompat.Builder =
            NotificationCompat.Builder(context, CANAL_ID)
                .setSmallIcon(R.drawable.ic_info_circle)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

        private fun intentBandeja(context: Context, requestCode: Int, clave: String?): PendingIntent {
            val intent = Intent(context, NotificacionesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (clave != null) putExtra(EXTRA_CLAVE, clave)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // El permiso se comprueba en puedeNotificar(); si se revoca entre medias se captura SecurityException.
        @SuppressLint("MissingPermission")
        private fun emitir(context: Context, tag: String, id: Int, builder: NotificationCompat.Builder): Boolean =
            try {
                NotificationManagerCompat.from(context).notify(tag, id, builder.build())
                true
            } catch (e: SecurityException) {
                NotificacionesRepository.logDebug("Sin permiso para notificar: ${e.javaClass.simpleName}")
                false
            }
    }
}
