package com.example.petvetericano.notificaciones

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.petvetericano.BuildConfig
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.models.LeerTodasNotificacionesResponse
import com.example.petvetericano.models.NotificacionItem
import com.example.petvetericano.models.NotificacionesListaResponse
import com.example.petvetericano.models.NotificacionesMapper
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import retrofit2.Response

/** Sin sesión guardada: no se llama al backend. */
class SinSesionException : IllegalStateException("Sin sesión")

/** Respuesta HTTP no exitosa del REST de notificaciones. */
class NotificacionesHttpException(val codigo: Int) : Exception("HTTP $codigo")

/**
 * Acceso al REST central de notificaciones (§6) y recuperación obligatoria por REST (§7):
 * al pasar a primer plano y tras el login se piden las NP no leídas y se muestran como notificación
 * local las que no se hayan mostrado ya (conjunto acotado de claves en SharedPreferences).
 * Sin sesión no hace nada; los errores de red son silenciosos (solo log en debug).
 */
object NotificacionesRepository {

    private const val TAG = "NotificacionesRepo"
    private const val PREFS = "VetericanoNotificacionesPrefs"
    private const val KEY_CLAVES_MOSTRADAS = "claves_mostradas_v1"

    const val MAX_CLAVES_MOSTRADAS = 300
    /** Si hay más pendientes que esto se muestra una sola notificación de resumen. */
    const val MAX_NOTIFICACIONES_INDIVIDUALES = 3
    private const val LIMITE_RECUPERACION = 50
    private const val INTERVALO_MIN_RECUPERACION_MS = 15_000L

    private val lockMostradas = Any()
    private val mutexRecuperacion = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var ultimaRecuperacionMs = 0L

    /** La bandeja abierta se suscribe para recargar cuando llega una NP nueva. */
    @Volatile
    var alLlegarNotificacion: (() -> Unit)? = null

    // ------------------------------------------------------------------ sesión

    fun haySesion(context: Context): Boolean {
        val prefs = SharedPreferencesManager(context.applicationContext)
        return prefs.getAccessToken().isNotBlank() && prefs.getUserId() > 0
    }

    /** "Bearer <access>" de la sesión guardada, o null si no hay sesión. Nunca se registra en logs. */
    internal fun cabeceraAuth(context: Context): String? {
        val token = SharedPreferencesManager(context.applicationContext).getAccessToken().trim()
        return if (token.isEmpty()) null else "Bearer $token"
    }

    // ------------------------------------------------------------------ REST §6

    suspend fun listar(
        context: Context,
        soloNoLeidas: Boolean,
        limit: Int = LIMITE_RECUPERACION
    ): Result<NotificacionesListaResponse> = llamar(context) { auth ->
        RetrofitClient.notificacionesApi.listar(
            token = auth,
            soloNoLeidas = if (soloNoLeidas) true else null,
            limit = limit,
            offset = 0
        )
    }

    suspend fun marcarLeida(context: Context, clave: String): Result<NotificacionItem> =
        llamar(context) { auth -> RetrofitClient.notificacionesApi.leer(auth, clave) }

    suspend fun marcarTodasLeidas(context: Context): Result<LeerTodasNotificacionesResponse> =
        llamar(context) { auth -> RetrofitClient.notificacionesApi.leerTodas(auth) }

    private suspend fun <T> llamar(
        context: Context,
        bloque: suspend (String) -> Response<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val auth = cabeceraAuth(context) ?: return@withContext Result.failure(SinSesionException())
        try {
            val respuesta = bloque(auth)
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Result.success(cuerpo)
            } else {
                Result.failure(NotificacionesHttpException(respuesta.code()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------ recuperación REST §7

    /** Lanza la recuperación sin bloquear (primer plano / tras login). */
    fun recuperarPendientesEnSegundoPlano(context: Context, forzar: Boolean = false) {
        val app = context.applicationContext
        if (!haySesion(app)) return
        FcmTokenManager.scope.launch { recuperarYMostrarPendientes(app, forzar) }
    }

    /**
     * `GET /api/notificaciones/?solo_no_leidas=true` y muestra como notificación local las NP no leídas
     * cuya clave no se haya mostrado ya. Con más de [MAX_NOTIFICACIONES_INDIVIDUALES] muestra un resumen.
     */
    suspend fun recuperarYMostrarPendientes(context: Context, forzar: Boolean = false) {
        val app = context.applicationContext
        if (!haySesion(app)) return
        val ahora = SystemClock.elapsedRealtime()
        if (!forzar && ultimaRecuperacionMs != 0L && ahora - ultimaRecuperacionMs < INTERVALO_MIN_RECUPERACION_MS) return
        if (!mutexRecuperacion.tryLock()) return
        try {
            ultimaRecuperacionMs = ahora
            val lista = listar(app, soloNoLeidas = true).getOrElse { e ->
                logDebug("Recuperación REST sin resultado: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                return
            }
            val pendientes = NotificacionesMapper.filtrarPendientesDeMostrar(
                lista.resultados.orEmpty(),
                leerClavesMostradas(app)
            )
            if (pendientes.isEmpty()) return
            val porMostrar = descartarYaVisiblesEnBandeja(app, pendientes)
            if (porMostrar.isEmpty()) return

            if (porMostrar.size <= MAX_NOTIFICACIONES_INDIVIDUALES) {
                val mostradas = porMostrar.filter { item ->
                    VetericanoMessagingService.mostrarNotificacion(app, NotificacionesMapper.desdeItem(item))
                }
                marcarMostradas(app, mostradas.mapNotNull { it.clave })
            } else if (VetericanoMessagingService.mostrarResumen(app, porMostrar.size)) {
                marcarMostradas(app, porMostrar.mapNotNull { it.clave })
            }
            notificarNuevaNotificacion()
        } finally {
            mutexRecuperacion.unlock()
        }
    }

    /**
     * Las NP que FCM ya mostró con la app en segundo plano no pasan por onMessageReceived. Si siguen en la
     * bandeja del sistema (mismo título y texto, §7: title=titulo, body=mensaje) se marcan como mostradas
     * para no duplicarlas.
     */
    private fun descartarYaVisiblesEnBandeja(context: Context, items: List<NotificacionItem>): List<NotificacionItem> {
        val firmasActivas = try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return items
            manager.activeNotifications.mapNotNull { sbn ->
                val extras = sbn.notification?.extras ?: return@mapNotNull null
                val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                    ?: return@mapNotNull null
                val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                firma(titulo, texto)
            }.toSet()
        } catch (e: Exception) {
            logDebug("No se pudieron leer las notificaciones activas: ${e.javaClass.simpleName}")
            return items
        }
        if (firmasActivas.isEmpty()) return items
        val (yaVisibles, resto) = items.partition { item ->
            firma(item.titulo?.trim().orEmpty(), item.mensaje?.trim().orEmpty()) in firmasActivas
        }
        if (yaVisibles.isNotEmpty()) marcarMostradas(context, yaVisibles.mapNotNull { it.clave })
        return resto
    }

    private fun firma(titulo: String, texto: String): String = "$titulo\u0000$texto"

    // ------------------------------------------------------------------ claves mostradas (acotado)

    fun leerClavesMostradas(context: Context): Set<String> = synchronized(lockMostradas) {
        leerListaMostradas(context).toSet()
    }

    fun yaMostrada(context: Context, clave: String): Boolean = clave.trim() in leerClavesMostradas(context)

    fun marcarMostradas(context: Context, claves: Collection<String>) {
        if (claves.isEmpty()) return
        synchronized(lockMostradas) {
            val nuevas = NotificacionesMapper.agregarAcotado(leerListaMostradas(context), claves, MAX_CLAVES_MOSTRADAS)
            prefs(context).edit()
                .putString(KEY_CLAVES_MOSTRADAS, NotificacionesMapper.serializarClaves(nuevas))
                .apply()
        }
    }

    private fun leerListaMostradas(context: Context): List<String> =
        NotificacionesMapper.deserializarClaves(prefs(context).getString(KEY_CLAVES_MOSTRADAS, ""))

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ utilidades

    internal fun notificarNuevaNotificacion() {
        val listener = alLlegarNotificacion ?: return
        mainHandler.post { listener() }
    }

    internal fun logDebug(mensaje: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, mensaje)
    }
}
