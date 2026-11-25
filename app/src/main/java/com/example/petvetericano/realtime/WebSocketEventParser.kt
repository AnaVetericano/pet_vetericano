package com.example.petvetericano.realtime

import com.example.petvetericano.models.NotificacionItem
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Serialización y parseo puro (JVM-safe) del protocolo WebSocket de Vetericano v1.
 * Rechaza payloads inválidos o versiones incompatibles devolviendo `null` sin lanzar excepciones.
 */
object WebSocketEventParser {

    private val gson = Gson()

    /** Frame inicial obligatorio: `{"type":"auth","token":"<JWT>"}`. */
    fun construirFrameAuth(token: String): String {
        val obj = JsonObject()
        obj.addProperty("type", "auth")
        obj.addProperty("token", token.trim())
        return gson.toJson(obj)
    }

    /** Frame heartbeat de aplicación: `{"type":"ping"}`. */
    fun construirFramePing(): String {
        val obj = JsonObject()
        obj.addProperty("type", "ping")
        return gson.toJson(obj)
    }

    /** Parsea un mensaje JSON del servidor; devuelve `null` si es inválido o incompatible. */
    fun parsearMensaje(rawJson: String?): MensajeServidorWs? {
        if (rawJson.isNullOrBlank()) return null
        val root = try {
            val parsed = JsonParser.parseString(rawJson)
            if (parsed.isJsonObject) parsed.asJsonObject else return null
        } catch (_: Exception) {
            return null
        }

        return when (texto(root, "type")) {
            "auth_ok" -> parsearAuthOk(root)
            "pong" -> MensajeServidorWs.Pong
            "event" -> parsearEvento(root)?.let { MensajeServidorWs.Evento(it) }
            else -> null
        }
    }

    private fun parsearAuthOk(root: JsonObject): MensajeServidorWs.AuthOk? {
        val userId = entero(root, "user_id") ?: return null
        if (userId <= 0) return null
        val idRol = entero(root, "id_rol") ?: -1
        val tokenExp = largo(root, "token_exp") ?: 0L
        return MensajeServidorWs.AuthOk(userId = userId, idRol = idRol, tokenExp = tokenExp)
    }

    private fun parsearEvento(root: JsonObject): WebSocketEvent? {
        val schemaVersion = texto(root, "schema_version") ?: return null
        if (schemaVersion != WebSocketProtocolo.SCHEMA_VERSION_V1) return null

        val eventId = texto(root, "event_id") ?: return null
        val eventType = texto(root, "event_type") ?: return null
        val categoria = CategoriaEventoWs.desdeTexto(texto(root, "categoria")) ?: return null
        val modulo = texto(root, "modulo") ?: ""
        val timestamp = texto(root, "timestamp") ?: ""

        val dataElem: JsonElement? = root.get("data")
        val dataObj = if (dataElem != null && dataElem.isJsonObject) dataElem.asJsonObject else JsonObject()

        val referencia = dataObj.get("referencia")?.takeIf { it.isJsonObject }?.asJsonObject

        val itemNp = if (categoria == CategoriaEventoWs.NP) {
            try {
                gson.fromJson(dataObj, NotificacionItem::class.java)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        return WebSocketEvent(
            schemaVersion = schemaVersion,
            eventId = eventId,
            eventType = eventType,
            categoria = categoria,
            modulo = modulo,
            timestamp = timestamp,
            data = dataObj,
            itemNp = itemNp,
            titulo = texto(dataObj, "titulo") ?: itemNp?.titulo,
            mensaje = texto(dataObj, "mensaje") ?: itemNp?.mensaje,
            nivel = texto(dataObj, "nivel"),
            accion = texto(dataObj, "accion"),
            motivoRevocacion = texto(dataObj, "motivo"),
            claveLeida = texto(dataObj, "clave"),
            referencia = referencia ?: itemNp?.referenciaObjeto
        )
    }

    private fun texto(obj: JsonObject, clave: String): String? {
        val elem = obj.get(clave) ?: return null
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isString) return null
        return elem.asString.trim().takeIf { it.isNotEmpty() }
    }

    private fun entero(obj: JsonObject, clave: String): Int? {
        val elem = obj.get(clave) ?: return null
        if (!elem.isJsonPrimitive) return null
        return try {
            elem.asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun largo(obj: JsonObject, clave: String): Long? {
        val elem = obj.get(clave) ?: return null
        if (!elem.isJsonPrimitive) return null
        return try {
            elem.asLong
        } catch (_: Exception) {
            null
        }
    }
}
