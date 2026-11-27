package com.example.petvetericano.notificaciones

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.petvetericano.MainActivity
import com.example.petvetericano.Registro
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.SplashActivity
import com.example.petvetericano.bienvenida
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.models.DesactivarDispositivoRequest
import com.example.petvetericano.models.RegistrarDispositivoRequest
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.offline.AvisoNotificacionesHelper
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Token FCM del dispositivo (CONTRATO_NOTIFICACIONES_V1 §6/§7).
 * - Login correcto (email o Google) y `onNewToken` → `POST /api/notificaciones/dispositivos/`.
 * - Primer plano → registra de nuevo solo si cambió el token o el usuario.
 * - Logout → `POST /api/notificaciones/dispositivos/desactivar/` (best-effort, sin bloquear el logout) y
 *   después invalida el token local de FCM.
 * - Tras el login solicita `POST_NOTIFICATIONS` (API 33+) sin bloquear el flujo.
 * Nunca registra el token completo (solo los últimos 6 caracteres y solo en debug).
 */
object FcmTokenManager {

    private const val PREFS = "VetericanoFcmPrefs"
    private const val KEY_HUELLA_REGISTRO = "huella_registro_v1"
    private const val TIMEOUT_MS = 15_000L
    private const val REQUEST_CODE_PERMISO = 7_302

    /** Ámbito de la app para trabajo de red que no debe atarse a una Activity. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializa registro/desactivación (p. ej. logout seguido de un login inmediato). */
    private val mutexToken = Mutex()

    @Volatile
    private var solicitarPermisoPendiente = false

    @Volatile
    private var abrirBandejaPendiente = false

    /** Clave de la NP tocada en la bandeja del sistema: la bandeja la marca leída al abrirse. */
    @Volatile
    private var claveBandejaPendiente: String? = null

    /** Llamar una vez desde PetVetericanoApp.onCreate. */
    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(CallbacksNotificaciones)
    }

    /** Llamar justo después de guardar la sesión de un login correcto (email/contraseña o Google). */
    fun onLoginExitoso(context: Context) {
        val app = context.applicationContext
        solicitarPermisoPendiente = true
        scope.launch {
            registrarTokenActual(app, forzar = true)
            NotificacionesRepository.recuperarYMostrarPendientes(app, forzar = true)
        }
    }

    /** Al volver a primer plano: registra solo si cambió el token o el usuario desde el último registro OK. */
    fun asegurarRegistroEnSegundoPlano(context: Context) {
        val app = context.applicationContext
        if (!NotificacionesRepository.haySesion(app)) return
        scope.launch { registrarTokenActual(app, forzar = false) }
    }

    /** Desde `onNewToken` (ya corre en un hilo de trabajo del SDK de FCM). */
    fun registrarDesdeServicio(context: Context, token: String) {
        val app = context.applicationContext
        runBlocking {
            mutexToken.withLock {
                withTimeoutOrNull(TIMEOUT_MS) { registrarSinLock(app, token, forzar = true) }
            }
        }
    }

    /**
     * Logout: captura la sesión ANTES de que se borre y desactiva el token en segundo plano.
     * No bloquea ni falla el logout si no hay red o el backend no responde.
     */
    @Suppress("DEPRECATION") // deleteToken(): ver nota en obtenerTokenFcm()
    fun desactivarEnLogout(context: Context) {
        val app = context.applicationContext
        val auth = NotificacionesRepository.cabeceraAuth(app)
        solicitarPermisoPendiente = false
        abrirBandejaPendiente = false
        claveBandejaPendiente = null
        prefs(app).edit().remove(KEY_HUELLA_REGISTRO).apply()
        scope.launch {
            mutexToken.withLock {
                withTimeoutOrNull(TIMEOUT_MS) {
                    val token = obtenerTokenFcm() ?: return@withTimeoutOrNull
                    if (auth == null) return@withTimeoutOrNull
                    try {
                        val respuesta = RetrofitClient.notificacionesApi.desactivarDispositivo(
                            auth,
                            DesactivarDispositivoRequest(token)
                        )
                        NotificacionesRepository.logDebug(
                            "Desactivar token (…${sufijo(token)}): HTTP ${respuesta.code()}"
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        NotificacionesRepository.logDebug("Desactivar token: error de red ${e.javaClass.simpleName}")
                    }
                }
                // Si la desactivación no llegó al backend, invalidar el token en FCM evita que esta cuenta
                // siga recibiendo avisos en el dispositivo (el backend lo verá UNREGISTERED y lo desactivará).
                withTimeoutOrNull(TIMEOUT_MS) {
                    try {
                        FirebaseMessaging.getInstance().deleteToken().esperar()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        NotificacionesRepository.logDebug("deleteToken falló: ${e.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    private suspend fun registrarTokenActual(app: Context, forzar: Boolean) {
        mutexToken.withLock {
            withTimeoutOrNull(TIMEOUT_MS) {
                val token = obtenerTokenFcm() ?: return@withTimeoutOrNull
                registrarSinLock(app, token, forzar)
            }
        }
    }

    private suspend fun registrarSinLock(app: Context, token: String, forzar: Boolean) {
        val auth = NotificacionesRepository.cabeceraAuth(app) ?: return
        val idUsuario = SharedPreferencesManager(app).getUserId()
        if (idUsuario <= 0 || token.isBlank()) return
        val huella = huellaRegistro(token, idUsuario)
        if (!forzar && prefs(app).getString(KEY_HUELLA_REGISTRO, null) == huella) return
        try {
            val respuesta = RetrofitClient.notificacionesApi.registrarDispositivo(
                auth,
                RegistrarDispositivoRequest(token)
            )
            if (respuesta.isSuccessful) {
                prefs(app).edit().putString(KEY_HUELLA_REGISTRO, huella).apply()
            }
            NotificacionesRepository.logDebug("Registrar token (…${sufijo(token)}): HTTP ${respuesta.code()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NotificacionesRepository.logDebug("Registrar token: error de red ${e.javaClass.simpleName}")
        }
    }

    // getToken()/deleteToken() están marcados obsoletos en firebase-messaging 25.1 (nuevo register()/unregister(),
    // opt-in con la meta-data `firebase_messaging_installation_id_enabled`). Sin esa meta-data siguen siendo
    // la vía del token FCM clásico que usa el contrato §7.
    @Suppress("DEPRECATION")
    private suspend fun obtenerTokenFcm(): String? = try {
        FirebaseMessaging.getInstance().token.esperar()?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NotificacionesRepository.logDebug("No se pudo obtener el token FCM: ${e.javaClass.simpleName}")
        null
    }

    /** Solo para logs: últimos 6 caracteres. */
    internal fun sufijo(token: String): String = token.takeLast(6)

    /** Huella (SHA-256) de usuario+token para no guardar el token en claro. */
    internal fun huellaRegistro(token: String, idUsuario: Int): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$idUsuario|$token".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun esPantallaDeAcceso(activity: Activity): Boolean =
        activity is SplashActivity || activity is MainActivity || activity is inicio_sesion || activity is Registro

    /**
     * - Tras el login pide `POST_NOTIFICATIONS` en la primera pantalla de la sesión (no bloquea si lo niegan).
     *   En `bienvenida` se respeta su aviso educativo propio cuando corresponde mostrarlo.
     * - Si la app se abre tocando una NP que mostró el sistema (segundo plano), marca su clave como mostrada
     *   y abre la bandeja de notificaciones.
     */
    private object CallbacksNotificaciones : Application.ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (savedInstanceState != null) return
            val clave = try {
                val extras = activity.intent?.extras ?: return
                // Extras que añade FCM al abrir la app desde una notificación mostrada por el sistema.
                val esLanzamientoFcm = extras.containsKey("google.message_id") ||
                    extras.containsKey("google.sent_time")
                if (esLanzamientoFcm) extras.getString("clave")?.trim() else null
            } catch (_: Exception) {
                null
            }
            if (clave.isNullOrEmpty()) return
            NotificacionesRepository.marcarMostradas(activity.applicationContext, listOf(clave))
            if (activity !is NotificacionesActivity) {
                abrirBandejaPendiente = true
                claveBandejaPendiente = clave
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is NotificacionesActivity) {
                abrirBandejaPendiente = false
                claveBandejaPendiente = null
                return
            }
            if (esPantallaDeAcceso(activity)) return
            if (!NotificacionesRepository.haySesion(activity)) return

            if (abrirBandejaPendiente) {
                abrirBandejaPendiente = false
                val clave = claveBandejaPendiente
                claveBandejaPendiente = null
                activity.startActivity(
                    Intent(activity, NotificacionesActivity::class.java).apply {
                        if (clave != null) putExtra(VetericanoMessagingService.EXTRA_CLAVE, clave)
                    }
                )
                return
            }

            if (solicitarPermisoPendiente) {
                solicitarPermisoPendiente = false
                solicitarPermisoSiFalta(activity)
            }
        }

        private fun solicitarPermisoSiFalta(activity: Activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val concedido = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (concedido) return
            // bienvenida ya muestra su aviso educativo y pide el permiso desde él.
            if (activity is bienvenida && AvisoNotificacionesHelper.debeMostrarAviso(activity)) return
            activity.window?.decorView?.post {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_PERMISO
                    )
                }
            }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}

/** Espera un Task de Play Services sin depender de kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.esperar(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { tarea ->
        if (!cont.isActive) return@addOnCompleteListener
        if (tarea.isSuccessful) {
            cont.resume(tarea.result)
        } else {
            cont.resumeWithException(tarea.exception ?: IllegalStateException("Task de Play Services fallida"))
        }
    }
}
