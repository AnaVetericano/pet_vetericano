package com.example.petvetericano.realtime

import com.example.petvetericano.models.NotificacionItem
import com.google.gson.JsonObject

/**
 * Categorías oficiales del sobre WebSocket (CONTRATO_NOTIFICACIONES_V1 §1 y §3).
 */
enum class CategoriaEventoWs {
    NP,
    AO,
    AS;

    companion object {
        fun desdeTexto(valor: String?): CategoriaEventoWs? = when (valor?.trim()?.uppercase()) {
            "NP" -> NP
            "AO" -> AO
            "AS" -> AS
            else -> null
        }
    }
}

/**
 * Evento normalizado del servidor WebSocket.
 */
data class WebSocketEvent(
    val schemaVersion: String,
    val eventId: String,
    val eventType: String,
    val categoria: CategoriaEventoWs,
    val modulo: String,
    val timestamp: String,
    val data: JsonObject,
    val itemNp: NotificacionItem? = null,
    val titulo: String? = null,
    val mensaje: String? = null,
    val nivel: String? = null,
    val accion: String? = null,
    val motivoRevocacion: String? = null,
    val claveLeida: String? = null,
    val referencia: JsonObject? = null
) {
    /** Clave canónica de notificación persistente (`pet-<id>` o `sis-<id>`) si existe. */
    val claveCanonica: String?
        get() = itemNp?.clave?.trim()?.takeIf { it.isNotEmpty() }
            ?: claveLeida?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Mensajes aceptados desde el servidor WebSocket (CONTRATO_NOTIFICACIONES_V1 §2).
 */
sealed class MensajeServidorWs {
    data class AuthOk(
        val userId: Int,
        val idRol: Int,
        val tokenExp: Long
    ) : MensajeServidorWs()

    data object Pong : MensajeServidorWs()

    data class Evento(val evento: WebSocketEvent) : MensajeServidorWs()
}

/**
 * Subconjunto real del catálogo de 30 eventos relevantes para los flujos de la app Android.
 * Los eventos ajenos al alcance móvil (p. ej. admin.sede_actualizada) se ignoran sin error.
 */
object EventosMovilesRelevantes {

    val EVENTOS_PETICIONES = setOf(
        "peticion.creada",
        "peticion.urgente",
        "peticion.asignada",
        "peticion.reasignada",
        "peticion.desasignada",
        "peticion.asignacion_actualizada",
        "peticion.estado_actualizado",
        "peticion.posible_duplicado",
        "peticion.duplicado_resuelto",
        "peticion.seguimiento_creado"
    )

    val EVENTOS_KIT_INVENTARIO = setOf(
        "kit.stock_actualizado",
        "kit.alerta_stock_vencimiento",
        "kit.solicitud_creada",
        "kit.solicitud_resuelta",
        "kit.solicitud_cancelada",
        "kit.conflicto_sincronizacion",
        "catalogo.medicamentos_actualizado"
    )

    val EVENTOS_SOLICITUDES_KIT = setOf(
        "kit.solicitud_creada",
        "kit.solicitud_resuelta",
        "kit.solicitud_cancelada",
        "kit.stock_actualizado"
    )

    val EVENTOS_VOLUNTARIADO = setOf(
        "voluntariado.postulacion_creada",
        "voluntariado.postulacion_resuelta",
        "voluntariado.evento_actualizado"
    )

    val EVENTOS_CONTROL_SESION_Y_LECTURA = setOf(
        "sesion.revocada",
        "notificacion.leida",
        "notificacion.todas_leidas"
    )

    val TODOS_RELEVANTES: Set<String> =
        EVENTOS_PETICIONES + EVENTOS_KIT_INVENTARIO + EVENTOS_VOLUNTARIADO + EVENTOS_CONTROL_SESION_Y_LECTURA

    fun esRelevante(eventType: String): Boolean = eventType in TODOS_RELEVANTES
}
