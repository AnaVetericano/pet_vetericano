package com.example.petvetericano.realtime

import android.content.Context
import android.util.Log
import com.example.petvetericano.BuildConfig
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.NotificacionesRepository
import com.example.petvetericano.offline.NetworkUtils
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gestor del ciclo de vida WebSocket para Vetericano Android.
 *
 * - Mantiene una sola conexión por sesión autenticada mientras la app está en primer plano.
 * - Al pasar a segundo plano cierra con código 1000 (`background`); FCM cubre segundo plano.
 * - Al hacer logout cierra con código 1000 (`logout`), cancela reconexiones y limpia deduplicación.
 * - Maneja timeout de autenticación (10s), heartbeat `ping`/`pong` (25s/10s), refresh de JWT
 *   ante `4002`/`4001` (máx. 1 intento por ciclo sin bucles) y revocación inmediata ante `4003`.
 * - Tras autenticar en una reconexión dispara recuperación REST obligatoria de NP pendientes.
 */
object WebSocketConnectionManager {

    private const val TAG = "VetericanoWsManager"

    /** Abstracción de entorno para permitir pruebas unitarias completas en JVM sin emulador. */
    interface EntornoWs {
        val habilitado: Boolean
        val wsUrl: String
        val clientId: String
        val permitirCleartext: Boolean
        fun haySesion(): Boolean
        fun obtenerUserId(): Int
        fun obtenerAccessToken(): String
        fun obtenerRefreshToken(): String
        fun hayRedDisponible(): Boolean
        fun refrescarAccessToken(): String?
        fun marcarSesionRevocada(refreshToken: String)
        fun recuperarNotificacionesRest()
        fun procesarEvento(evento: WebSocketEvent): Boolean
        fun logDebug(mensaje: String)
    }

    private val lock = Any()
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cliente: VetericanoWebSocketClient = VetericanoWebSocketClient()
    private var entorno: EntornoWs? = null

    @Volatile
    private var enPrimerPlano = false

    @Volatile
    private var cierreIniciadoPorCliente = false

    @Volatile
    private var usuarioConectadoId = -1

    @Volatile
    private var intentosReconexion = 0

    @Volatile
    private var intentosRefresh = 0

    @Volatile
    private var yaConectoAntes = false

    /** Tras `4003`/`sesion.revocada` no se reconecta hasta un nuevo login (contrato §2). */
    @Volatile
    private var sesionRevocada = false

    private var jobAuthTimeout: Job? = null
    private var jobHeartbeat: Job? = null
    private var jobPongTimeout: Job? = null
    private var jobReconexion: Job? = null

    /** Inicializa el manager con el entorno real de Android (llamar desde `PetVetericanoApp.onCreate`). */
    fun init(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            entorno = crearEntornoAndroid(app)
        }
    }

    /** Permite inyectar dobles en pruebas unitarias (solo tests). */
    internal fun configurarParaPruebas(
        entornoPrueba: EntornoWs,
        clientePrueba: VetericanoWebSocketClient = VetericanoWebSocketClient(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        synchronized(lock) {
            cancelarTareas()
            cliente.cerrar(WebSocketProtocolo.CIERRE_NORMAL, "reset_test")
            cliente = clientePrueba
            entorno = entornoPrueba
            scope = CoroutineScope(SupervisorJob() + dispatcher)
            enPrimerPlano = false
            cierreIniciadoPorCliente = false
            usuarioConectadoId = -1
            intentosReconexion = 0
            intentosRefresh = 0
            yaConectoAntes = false
            sesionRevocada = false
            WebSocketRepository.limpiarDeduplicacion()
            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
        }
    }

    /** Llamado desde `ProcessLifecycleOwner.onStart`. */
    fun onForeground() {
        synchronized(lock) {
            enPrimerPlano = true
            intentarConectarInterno(forzarNuevoUsuario = false)
        }
    }

    /** Llamado desde `ProcessLifecycleOwner.onStop`. */
    fun onBackground() {
        synchronized(lock) {
            enPrimerPlano = false
            cerrarControlado("background")
        }
    }

    /** Llamado justo después de guardar sesión en login exitoso (email o Google). */
    fun onLoginExitoso(context: Context? = null) {
        if (context != null && entorno == null) {
            init(context)
        }
        synchronized(lock) {
            enPrimerPlano = true
            intentosReconexion = 0
            intentosRefresh = 0
            yaConectoAntes = false
            sesionRevocada = false
            intentarConectarInterno(forzarNuevoUsuario = true)
        }
    }

    /** Llamado desde `SharedPreferencesManager.clearSession()`. */
    fun onLogout() {
        synchronized(lock) {
            usuarioConectadoId = -1
            intentosReconexion = 0
            intentosRefresh = 0
            yaConectoAntes = false
            sesionRevocada = false
            cerrarControlado("logout")
            WebSocketRepository.limpiarDeduplicacion()
        }
    }

    private fun cerrarControlado(motivo: String) {
        cierreIniciadoPorCliente = true
        cancelarTareas()
        cliente.cerrar(WebSocketProtocolo.CIERRE_NORMAL, motivo)
        WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
    }

    private fun intentarConectarInterno(forzarNuevoUsuario: Boolean) {
        val env = entorno ?: return
        if (!env.habilitado) {
            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
            return
        }
        if (!enPrimerPlano || !env.haySesion()) {
            return
        }
        if (sesionRevocada) {
            env.logDebug("WS no reconecta: sesión revocada por el servidor (requiere nuevo login)")
            return
        }

        val userIdActual = env.obtenerUserId()
        if (userIdActual <= 0) return

        // Si cambió de usuario, cerrar la conexión previa y limpiar deduplicación.
        if (usuarioConectadoId > 0 && usuarioConectadoId != userIdActual) {
            cerrarControlado("cambio_usuario")
            WebSocketRepository.limpiarDeduplicacion()
        } else if (!forzarNuevoUsuario && cliente.tieneSocketActivo()) {
            // Ya hay una conexión activa o en curso para este mismo usuario: evitar duplicados.
            return
        }

        val urlValida = WebSocketProtocolo.validarUrl(env.wsUrl, env.permitirCleartext)
        if (urlValida == null) {
            env.logDebug("WS deshabilitado por URL no válida o no segura para este build")
            WebSocketRepository.actualizarEstado(
                WebSocketState.ErrorPermanente(
                    WebSocketProtocolo.CIERRE_ORIGEN_RECHAZADO,
                    "URL WebSocket no configurada o insegura"
                )
            )
            return
        }

        if (!env.hayRedDisponible()) {
            programarReconexionInterno()
            return
        }

        val token = env.obtenerAccessToken().trim()
        if (token.isEmpty()) {
            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
            return
        }

        cancelarTareas()
        cierreIniciadoPorCliente = false
        usuarioConectadoId = userIdActual
        WebSocketRepository.actualizarEstado(WebSocketState.Conectando)

        val iniciado = cliente.conectar(
            wsUrl = urlValida,
            tokenJwt = token,
            clientId = env.clientId,
            permitirCleartext = env.permitirCleartext,
            listener = crearListener(env)
        )
        if (!iniciado) {
            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
        }
    }

    private fun crearListener(env: EntornoWs): VetericanoWebSocketClient.Listener =
        object : VetericanoWebSocketClient.Listener {
            override fun alAbrir() {
                synchronized(lock) {
                    WebSocketRepository.actualizarEstado(WebSocketState.Autenticando)
                    jobAuthTimeout?.cancel()
                    jobAuthTimeout = scope.launch {
                        delay(WebSocketProtocolo.AUTH_TIMEOUT_MS)
                        synchronized(lock) {
                            if (WebSocketRepository.estado.value is WebSocketState.Autenticando) {
                                env.logDebug("Timeout esperando auth_ok del servidor WS")
                                cliente.cerrar(WebSocketProtocolo.CIERRE_AUTH, "auth_timeout")
                                programarReconexionInterno()
                            }
                        }
                    }
                }
            }

            override fun alRecibirMensaje(mensaje: MensajeServidorWs) {
                when (mensaje) {
                    is MensajeServidorWs.AuthOk -> alAutenticar(env, mensaje)
                    is MensajeServidorWs.Pong -> synchronized(lock) { jobPongTimeout?.cancel() }
                    is MensajeServidorWs.Evento -> alRecibirEvento(env, mensaje.evento)
                }
            }

            override fun alRecibirPayloadInvalido() {
                env.logDebug("Mensaje WS ignorado: formato o schema_version no válido")
            }

            override fun alCerrar(codigo: Int, motivo: String) {
                manejarCierre(env, codigo)
            }

            override fun alFallar(codigoHttp: Int?, error: Throwable) {
                synchronized(lock) {
                    cancelarTareas()
                    if (cierreIniciadoPorCliente || !enPrimerPlano || !env.haySesion()) {
                        WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
                        return
                    }
                    if (codigoHttp == 403 || codigoHttp == 404) {
                        env.logDebug("Handshake WS rechazado permanentemente (HTTP $codigoHttp)")
                        WebSocketRepository.actualizarEstado(
                            WebSocketState.ErrorPermanente(codigoHttp, "Handshake rechazado HTTP $codigoHttp")
                        )
                        return
                    }
                    env.logDebug("Fallo de red WS (${error.javaClass.simpleName}); programando reconexión")
                    programarReconexionInterno()
                }
            }
        }

    private fun alAutenticar(env: EntornoWs, authOk: MensajeServidorWs.AuthOk) {
        val debeRecuperarRest: Boolean
        synchronized(lock) {
            jobAuthTimeout?.cancel()
            intentosReconexion = 0
            intentosRefresh = 0
            debeRecuperarRest = yaConectoAntes
            yaConectoAntes = true
            WebSocketRepository.actualizarEstado(
                WebSocketState.Autenticado(authOk.userId, authOk.idRol, authOk.tokenExp)
            )
            iniciarHeartbeatInterno(env)
        }
        if (debeRecuperarRest) {
            env.recuperarNotificacionesRest()
        }
    }

    private fun alRecibirEvento(env: EntornoWs, evento: WebSocketEvent) {
        env.procesarEvento(evento)
        if (evento.eventType == "sesion.revocada") {
            synchronized(lock) {
                sesionRevocada = true
                cerrarControlado("sesion_revocada")
            }
            env.marcarSesionRevocada(env.obtenerRefreshToken())
        }
    }

    private fun manejarCierre(env: EntornoWs, codigo: Int) {
        val politica: PoliticaCierre
        synchronized(lock) {
            cancelarTareas()
            politica = WebSocketProtocolo.clasificarCierre(codigo, cierreIniciadoPorCliente)
            if (!enPrimerPlano || !env.haySesion()) {
                WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
                return
            }
        }

        when (politica) {
            PoliticaCierre.NO_RECONECTAR_NORMAL -> {
                WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
            }

            PoliticaCierre.REVOCAR_SESION -> {
                synchronized(lock) {
                    sesionRevocada = true
                    WebSocketRepository.actualizarEstado(
                        WebSocketState.ErrorPermanente(codigo, "Sesión revocada por servidor")
                    )
                }
                env.marcarSesionRevocada(env.obtenerRefreshToken())
            }

            PoliticaCierre.ERROR_PERMANENTE -> {
                WebSocketRepository.actualizarEstado(
                    WebSocketState.ErrorPermanente(codigo, "Conexión rechazada permanentemente ($codigo)")
                )
            }

            PoliticaCierre.REFRESCAR_Y_RECONECTAR -> {
                val puedeRefrescar = synchronized(lock) {
                    if (intentosRefresh >= 1) {
                        WebSocketRepository.actualizarEstado(
                            WebSocketState.ErrorPermanente(codigo, "Autenticación WS rechazada tras renovar token")
                        )
                        false
                    } else {
                        intentosRefresh += 1
                        true
                    }
                }
                if (!puedeRefrescar) return
                scope.launch {
                    val nuevoToken = env.refrescarAccessToken()
                    synchronized(lock) {
                        if (!nuevoToken.isNullOrBlank() && enPrimerPlano && env.haySesion()) {
                            intentarConectarInterno(forzarNuevoUsuario = true)
                        } else {
                            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
                        }
                    }
                }
            }

            PoliticaCierre.RECONECTAR_BACKOFF -> {
                synchronized(lock) {
                    programarReconexionInterno()
                }
            }
        }
    }

    private fun iniciarHeartbeatInterno(env: EntornoWs) {
        jobHeartbeat?.cancel()
        jobHeartbeat = scope.launch {
            while (true) {
                delay(WebSocketProtocolo.HEARTBEAT_INTERVALO_MS)
                val enviado = synchronized(lock) {
                    if (WebSocketRepository.estado.value !is WebSocketState.Autenticado) {
                        return@launch
                    }
                    cliente.enviarPing()
                }
                if (!enviado) {
                    synchronized(lock) {
                        cliente.cerrar(WebSocketProtocolo.CIERRE_ERROR_INTERNO, "ping_failed")
                        programarReconexionInterno()
                    }
                    return@launch
                }
                synchronized(lock) {
                    jobPongTimeout?.cancel()
                    jobPongTimeout = scope.launch {
                        delay(WebSocketProtocolo.PONG_TIMEOUT_MS)
                        synchronized(lock) {
                            if (WebSocketRepository.estado.value is WebSocketState.Autenticado) {
                                env.logDebug("Timeout esperando pong del servidor WS; reconectando")
                                cliente.cerrar(WebSocketProtocolo.CIERRE_ERROR_INTERNO, "pong_timeout")
                                programarReconexionInterno()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun programarReconexionInterno() {
        val env = entorno ?: return
        if (!enPrimerPlano || !env.haySesion() || !env.habilitado) {
            WebSocketRepository.actualizarEstado(WebSocketState.Desconectado)
            return
        }
        if (intentosReconexion >= WebSocketProtocolo.MAX_REINTENTOS_CONSECUTIVOS) {
            WebSocketRepository.actualizarEstado(
                WebSocketState.ErrorPermanente(
                    WebSocketProtocolo.CIERRE_ERROR_INTERNO,
                    "Límite de reconexiones alcanzado; degradado a FCM + REST"
                )
            )
            return
        }
        intentosReconexion += 1
        val espera = WebSocketProtocolo.calcularBackoffMs(intentosReconexion)
        WebSocketRepository.actualizarEstado(WebSocketState.Reconectando(intentosReconexion, espera))
        jobReconexion?.cancel()
        jobReconexion = scope.launch {
            delay(espera)
            synchronized(lock) {
                if (enPrimerPlano && env.haySesion()) {
                    intentarConectarInterno(forzarNuevoUsuario = true)
                }
            }
        }
    }

    private fun cancelarTareas() {
        jobAuthTimeout?.cancel()
        jobAuthTimeout = null
        jobHeartbeat?.cancel()
        jobHeartbeat = null
        jobPongTimeout?.cancel()
        jobPongTimeout = null
        jobReconexion?.cancel()
        jobReconexion = null
    }

    private fun crearEntornoAndroid(app: Context): EntornoWs = object : EntornoWs {
        override val habilitado: Boolean get() = BuildConfig.WS_HABILITADO
        override val wsUrl: String get() = BuildConfig.WS_URL
        override val clientId: String get() = BuildConfig.WS_CLIENT_ID
        override val permitirCleartext: Boolean get() = BuildConfig.DEBUG

        override fun haySesion(): Boolean = NotificacionesRepository.haySesion(app)

        override fun obtenerUserId(): Int = SharedPreferencesManager(app).getUserId()

        override fun obtenerAccessToken(): String = SharedPreferencesManager(app).getAccessToken()

        override fun obtenerRefreshToken(): String = SharedPreferencesManager(app).getRefreshToken()

        override fun hayRedDisponible(): Boolean = NetworkUtils.hayRedDisponible(app)

        override fun refrescarAccessToken(): String? {
            val prefs = SharedPreferencesManager(app)
            val refresh = prefs.getRefreshToken().trim()
            if (refresh.isEmpty() || refresh == SesionExpiradaManager.refreshMuerto) return null
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val bodyJson = "{\"refresh\":\"$refresh\"}"
            val req = Request.Builder()
                .url("${RetrofitClient.BASE_URL}token/refresh/")
                .post(bodyJson.toRequestBody(mediaType))
                .build()
            return try {
                RetrofitClient.cleanClient.newCall(req).execute().use { resp ->
                    if (resp.code == 400 || resp.code == 401) {
                        SesionExpiradaManager.marcarSesionMuerta(refresh)
                        return null
                    }
                    if (!resp.isSuccessful) return null
                    val raw = resp.body?.string().orEmpty()
                    val json = JsonParser.parseString(raw).asJsonObject
                    val access = json.get("access")?.asString?.trim().orEmpty()
                    if (access.isEmpty()) return null
                    val nuevoRefresh = json.get("refresh")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                    prefs.saveAccessToken(access)
                    if (nuevoRefresh.isNotEmpty()) prefs.saveRefreshToken(nuevoRefresh)
                    RetrofitClient.authToken = access
                    access
                }
            } catch (_: Exception) {
                null
            }
        }

        override fun marcarSesionRevocada(refreshToken: String) {
            if (refreshToken.isNotBlank()) {
                SesionExpiradaManager.marcarSesionMuerta(refreshToken)
            }
        }

        override fun recuperarNotificacionesRest() {
            NotificacionesRepository.recuperarPendientesEnSegundoPlano(app, forzar = true)
        }

        override fun procesarEvento(evento: WebSocketEvent): Boolean =
            WebSocketRepository.procesarEventoEntrante(app, evento)

        override fun logDebug(mensaje: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, mensaje)
        }
    }
}
