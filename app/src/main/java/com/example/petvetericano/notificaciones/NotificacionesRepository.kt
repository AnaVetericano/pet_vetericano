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
    private val clavesEnVuelo = mutableSetOf<String>()
    private val mutexRecuperacion = Mutex()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Almacenamiento acotado de claves mostradas (inyectable en pruebas JVM de concurrencia). */
    internal interface AlmacenClavesMostradas {
        fun leer(): List<String>
        fun guardar(claves: List<String>)
    }

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
                porMostrar.forEach { item ->
                    mostrarUnaVez(app, item.clave) {
                        VetericanoMessagingService.mostrarNotificacion(app, NotificacionesMapper.desdeItem(item))
                    }
                }
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

    /**
     * Operación atómica compartida por FCM, WebSocket y recuperación REST para presentar una NP
     * como máximo una vez por clave canónica (`sis-*` / `pet-*`).
     *
     * - Comprueba y reserva la clave bajo `lockMostradas` antes de invocar `mostrar()`.
     * - Si otro hilo ya mostró o está mostrando la misma clave, omite la invocación.
     * - Persiste la clave en SharedPreferences solo tras una presentación exitosa (`true`).
     * - Si `mostrar()` devuelve `false` o lanza excepción, libera la reserva para permitir reintento.
     */
    fun mostrarUnaVez(context: Context, clave: String?, mostrar: () -> Boolean): Boolean {
        val app = context.applicationContext
        val almacen = object : AlmacenClavesMostradas {
            override fun leer(): List<String> = leerListaMostradas(app)
            override fun guardar(claves: List<String>) {
                prefs(app).edit()
                    .putString(KEY_CLAVES_MOSTRADAS, NotificacionesMapper.serializarClaves(claves))
                    .apply()
            }
        }
        return mostrarUnaVezInterno(clave, almacen, MAX_CLAVES_MOSTRADAS, mostrar)
    }

    internal fun mostrarUnaVezInterno(
        clave: String?,
        almacen: AlmacenClavesMostradas,
        maxClaves: Int = MAX_CLAVES_MOSTRADAS,
        mostrar: () -> Boolean
    ): Boolean {
        val claveLimpia = clave?.trim()?.takeIf { it.isNotEmpty() } ?: return mostrar()
        val reservada = synchronized(lockMostradas) {
            if (claveLimpia in clavesEnVuelo || claveLimpia in almacen.leer()) {
                false
            } else {
                clavesEnVuelo.add(claveLimpia)
                true
            }
        }
        if (!reservada) {
            logDebug("NP $claveLimpia ya mostrada; se omite")
            return false
        }
        return try {
            val mostrada = mostrar()
            if (mostrada) {
                synchronized(lockMostradas) {
                    val nuevas = NotificacionesMapper.agregarAcotado(
                        almacen.leer(),
                        listOf(claveLimpia),
                        maxClaves
                    )
                    almacen.guardar(nuevas)
                }
            }
            mostrada
        } finally {
            synchronized(lockMostradas) {
                clavesEnVuelo.remove(claveLimpia)
            }
        }
    }

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
        if (!BuildConfig.DEBUG) return
        try {
            Log.d(TAG, mensaje)
        } catch (_: RuntimeException) {
            // En pruebas unitarias JVM android.util.Log no está mockeado.
        }
    }
}
