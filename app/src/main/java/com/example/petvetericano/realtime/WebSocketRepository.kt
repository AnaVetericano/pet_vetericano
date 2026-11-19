package com.example.petvetericano.realtime

import android.content.Context
import com.example.petvetericano.models.NotificacionesMapper
import com.example.petvetericano.notificaciones.NotificacionesRepository
import com.example.petvetericano.notificaciones.VetericanoMessagingService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio central de eventos WebSocket (singleton).
 * - Expone `estado` (`StateFlow`) y `eventos` (`SharedFlow`) a las Activities observadoras.
 * - Deduplica por `event_id` (acotado a 500) y coordina por `clave` canónica (`pet-*`/`sis-*`)
 *   con `NotificacionesRepository` y `VetericanoMessagingService` para evitar duplicados FCM + WS.
 * - No crea notificaciones locales para eventos `AO` ni `AS`, ni procesa eventos irrelevantes.
 */
object WebSocketRepository {

    const val MAX_EVENT_IDS = 500

    /** Adaptador de efectos secundarios Android (inyectable en pruebas unitarias JVM). */
    interface EfectosNotificacion {
        fun yaMostrada(clave: String): Boolean
        fun marcarMostrada(clave: String)
        fun mostrarNotificacionNp(evento: WebSocketEvent): Boolean
        fun notificarBandeja()
        fun cancelarNotificacion(clave: String)
        fun cancelarTodasNotificaciones()
    }

    private val lockDedup = Any()
    private val eventIdsVistos = LinkedHashSet<String>()

    private val _estado = MutableStateFlow<WebSocketState>(WebSocketState.Desconectado)
    val estado: StateFlow<WebSocketState> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val eventos: SharedFlow<WebSocketEvent> = _eventos.asSharedFlow()

    internal fun actualizarEstado(nuevo: WebSocketState) {
        _estado.value = nuevo
    }

    /** Limpia el historial en memoria de `event_id` (llamar en logout o cambio de usuario). */
    fun limpiarDeduplicacion() {
        synchronized(lockDedup) {
            eventIdsVistos.clear()
        }
    }

    /** Registra un `event_id` en el conjunto acotado; devuelve `false` si ya fue procesado. */
    fun registrarEventIdSiNuevo(eventId: String): Boolean {
        val limpio = eventId.trim()
        if (limpio.isEmpty()) return false
        synchronized(lockDedup) {
            if (!eventIdsVistos.add(limpio)) {
                return false
            }
            while (eventIdsVistos.size > MAX_EVENT_IDS) {
                val primero = eventIdsVistos.iterator().next()
                eventIdsVistos.remove(primero)
            }
            return true
        }
    }

    /**
     * Procesa un evento entrante desde WebSocket usando el entorno real de Android.
     * Devuelve `true` si el evento fue aceptado y emitido a los observadores.
     */
    fun procesarEventoEntrante(context: Context, evento: WebSocketEvent): Boolean {
        val app = context.applicationContext
        val efectos = object : EfectosNotificacion {
            override fun yaMostrada(clave: String): Boolean =
                NotificacionesRepository.yaMostrada(app, clave)

            override fun marcarMostrada(clave: String) {
                NotificacionesRepository.marcarMostradas(app, listOf(clave))
            }

            override fun mostrarNotificacionNp(evento: WebSocketEvent): Boolean {
                val item = evento.itemNp ?: return false
                return VetericanoMessagingService.mostrarNotificacion(app, NotificacionesMapper.desdeItem(item))
            }

            override fun notificarBandeja() {
                NotificacionesRepository.notificarNuevaNotificacion()
            }

            override fun cancelarNotificacion(clave: String) {
                VetericanoMessagingService.cancelar(app, clave)
            }

            override fun cancelarTodasNotificaciones() {
                VetericanoMessagingService.cancelarTodas(app)
            }
        }
        return procesarEventoConEfectos(evento, efectos)
    }

    /**
     * Núcleo puro de procesamiento de eventos (testeable sin Android).
     * Devuelve `true` si el evento fue nuevo, relevante y emitido al `SharedFlow`.
     */
    internal fun procesarEventoConEfectos(
        evento: WebSocketEvent,
        efectos: EfectosNotificacion
    ): Boolean {
        if (!EventosMovilesRelevantes.esRelevante(evento.eventType)) {
            return false
        }
        if (!registrarEventIdSiNuevo(evento.eventId)) {
            return false
        }

        when {
            evento.eventType == "sesion.revocada" -> {
                _eventos.tryEmit(evento)
                return true
            }

            evento.eventType == "notificacion.leida" -> {
                evento.claveCanonica?.let { efectos.cancelarNotificacion(it) }
                efectos.notificarBandeja()
                _eventos.tryEmit(evento)
                return true
            }

            evento.eventType == "notificacion.todas_leidas" -> {
                efectos.cancelarTodasNotificaciones()
                efectos.notificarBandeja()
                _eventos.tryEmit(evento)
                return true
            }

            evento.categoria == CategoriaEventoWs.NP -> {
                val clave = evento.claveCanonica
                val yaExistiaEnSistema = clave != null && efectos.yaMostrada(clave)
                if (!yaExistiaEnSistema && evento.itemNp != null) {
                    if (efectos.mostrarNotificacionNp(evento) && clave != null) {
                        efectos.marcarMostrada(clave)
                    }
                }
                efectos.notificarBandeja()
                _eventos.tryEmit(evento)
                return true
            }

            // AO y AS relevantes: nunca crean notificación persistente local; solo disparan refresco REST.
            evento.categoria == CategoriaEventoWs.AO || evento.categoria == CategoriaEventoWs.AS -> {
                _eventos.tryEmit(evento)
                return true
            }

            else -> return false
        }
    }
}
