package com.example.petvetericano.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

// =====================================================================
// Notificaciones v1 (CONTRATO_NOTIFICACIONES_V1 §5, §6 y §7).
// Modelos REST + mapeos puros (sin dependencias de Android, probados en app/src/test).
// =====================================================================

/**
 * Ítem unificado de notificación (contrato §5). Es la forma del REST central y la de `data` de NP.
 * - `clave`: `pet-<id>` o `sis-<id>`; identifica la notificación para leerla y deduplicarla.
 * - Fechas: ISO local sin zona (America/Bogota), p. ej. `2026-11-17T10:00:00`.
 * - `referencia`: en REST es un objeto JSON (o null). Se declara como [JsonElement] porque Gson
 *   entrega `JsonNull` cuando viene `null`; usar [referenciaObjeto] para leerla.
 */
data class NotificacionItem(
    @SerializedName("clave") val clave: String? = null,
    @SerializedName("origen") val origen: String? = null,
    @SerializedName("id_notificacion") val idNotificacion: Long? = null,
    @SerializedName("event_type") val eventType: String? = null,
    @SerializedName("modulo") val modulo: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("titulo") val titulo: String? = null,
    @SerializedName("mensaje") val mensaje: String? = null,
    @SerializedName("prioridad") val prioridad: String? = null,
    @SerializedName("leida") val leida: Boolean = false,
    @SerializedName("fecha_creacion") val fechaCreacion: String? = null,
    @SerializedName("fecha_lectura") val fechaLectura: String? = null,
    @SerializedName("referencia") val referencia: JsonElement? = null
) {
    val referenciaObjeto: JsonObject?
        get() = referencia?.takeIf { it.isJsonObject }?.asJsonObject
}

/** `GET /api/notificaciones/` → `{"total":N,"no_leidas":N,"resultados":[ítem…]}` */
data class NotificacionesListaResponse(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("no_leidas") val noLeidas: Int = 0,
    @SerializedName("resultados") val resultados: List<NotificacionItem>? = null
)

/** `GET /api/notificaciones/contador/` → `{"no_leidas":N,"por_modulo":{...}}` */
data class NotificacionesContadorResponse(
    @SerializedName("no_leidas") val noLeidas: Int = 0,
    @SerializedName("por_modulo") val porModulo: Map<String, Int>? = null
)

/** `PATCH /api/notificaciones/leer-todas/` → `{"actualizadas":N}` */
data class LeerTodasNotificacionesResponse(
    @SerializedName("actualizadas") val actualizadas: Int = 0
)

/** `POST /api/notificaciones/dispositivos/` */
data class RegistrarDispositivoRequest(
    @SerializedName("token") val token: String,
    @SerializedName("plataforma") val plataforma: String = PLATAFORMA_ANDROID
) {
    companion object {
        const val PLATAFORMA_ANDROID = "android"
    }

    // Nunca exponer el token completo si alguien registra el objeto en un log.
    override fun toString(): String = "RegistrarDispositivoRequest(token=…${token.takeLast(6)}, plataforma=$plataforma)"
}

data class RegistrarDispositivoResponse(
    @SerializedName("registrado") val registrado: Boolean = false
)

/** `POST /api/notificaciones/dispositivos/desactivar/` */
data class DesactivarDispositivoRequest(
    @SerializedName("token") val token: String
) {
    override fun toString(): String = "DesactivarDispositivoRequest(token=…${token.takeLast(6)})"
}

data class DesactivarDispositivoResponse(
    @SerializedName("desactivado") val desactivado: Boolean = false
)

/**
 * Notificación lista para mostrarse como notificación local del sistema.
 * Se construye desde `data` de FCM (§7: `referencia` llega como string JSON) o desde un ítem REST (§5).
 */
data class NotificacionPush(
    val clave: String?,
    val titulo: String,
    val mensaje: String,
    val eventType: String? = null,
    val modulo: String? = null,
    val schemaVersion: String? = null,
    val referencia: JsonObject? = null
)

/** Mapeos y reglas puras de notificaciones (sin Android). */
object NotificacionesMapper {

    const val TITULO_POR_DEFECTO = "Vetericano"

    /**
     * `data` de FCM (§7) → [NotificacionPush].
     * Título y cuerpo salen del bloque `notification` y, si no viene, de `data` (`titulo`/`mensaje`).
     * Devuelve null si no hay nada que mostrar.
     */
    fun desdeDataFcm(
        data: Map<String, String>,
        tituloNotificacion: String?,
        cuerpoNotificacion: String?
    ): NotificacionPush? {
        val clave = limpio(data["clave"])
        val titulo = primerNoVacio(tituloNotificacion, data["titulo"], data["title"])
        val mensaje = primerNoVacio(cuerpoNotificacion, data["mensaje"], data["body"])
        if (clave == null && titulo == null && mensaje == null) return null
        return NotificacionPush(
            clave = clave,
            titulo = titulo ?: TITULO_POR_DEFECTO,
            mensaje = mensaje.orEmpty(),
            eventType = limpio(data["event_type"]),
            modulo = limpio(data["modulo"]),
            schemaVersion = limpio(data["schema_version"]),
            referencia = parsearReferencia(data["referencia"])
        )
    }

    /** `referencia` de FCM es un string JSON (`"{\"id_solicitud\":5}"`). Inválido o no-objeto → null. */
    fun parsearReferencia(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return try {
            val elemento = JsonParser.parseString(raw)
            if (elemento.isJsonObject) elemento.asJsonObject else null
        } catch (_: Exception) {
            null
        }
    }

    /** Ítem REST (§5) → [NotificacionPush] para la recuperación por REST. */
    fun desdeItem(item: NotificacionItem): NotificacionPush = NotificacionPush(
        clave = limpio(item.clave),
        titulo = limpio(item.titulo) ?: TITULO_POR_DEFECTO,
        mensaje = limpio(item.mensaje).orEmpty(),
        eventType = limpio(item.eventType),
        modulo = limpio(item.modulo),
        schemaVersion = null,
        referencia = item.referenciaObjeto
    )

    /**
     * Recuperación REST: NP no leídas, con `clave`, que no se hayan mostrado ya.
     * Sin duplicados y conservando el orden recibido (`fecha_creacion` desc).
     */
    fun filtrarPendientesDeMostrar(
        items: List<NotificacionItem>,
        clavesMostradas: Set<String>
    ): List<NotificacionItem> {
        val vistas = HashSet<String>()
        return items.filter { item ->
            val clave = limpio(item.clave)
            !item.leida && clave != null && clave !in clavesMostradas && vistas.add(clave)
        }
    }

    /**
     * Conjunto acotado de claves mostradas: añade al final (orden de inserción), sin duplicados,
     * y conserva como máximo [max] (descarta las más antiguas).
     */
    fun agregarAcotado(actuales: List<String>, nuevas: Collection<String>, max: Int): List<String> {
        val conjunto = LinkedHashSet<String>()
        actuales.forEach { c -> limpio(c)?.let { conjunto.add(it) } }
        nuevas.forEach { c ->
            limpio(c)?.let {
                conjunto.remove(it)
                conjunto.add(it)
            }
        }
        val lista = conjunto.toList()
        return if (max >= 0 && lista.size > max) lista.takeLast(max) else lista
    }

    /** Serialización simple para SharedPreferences (las claves no contienen saltos de línea). */
    fun serializarClaves(claves: List<String>): String = claves.joinToString("\n")

    fun deserializarClaves(texto: String?): List<String> =
        texto.orEmpty().split('\n').mapNotNull { limpio(it) }

    /** Fecha ISO local sin zona → "17 nov 2026, 10:00". Si no se puede leer, devuelve el texto original. */
    fun formatearFecha(iso: String?): String {
        val texto = iso?.trim().orEmpty()
        if (texto.length < 16) return texto
        return try {
            val normalizado = texto.substring(0, 16).replace(' ', 'T')
            val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { isLenient = false }
            val fecha = entrada.parse(normalizado) ?: return texto
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-CO")).format(fecha)
        } catch (_: Exception) {
            texto
        }
    }

    /** "peticiones" → "Peticiones" (etiqueta corta para la bandeja). */
    fun etiquetaModulo(modulo: String?): String {
        val m = limpio(modulo) ?: return ""
        return m.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    fun esUrgente(item: NotificacionItem): Boolean =
        item.prioridad?.trim()?.equals("urgente", ignoreCase = true) == true ||
            item.tipo?.trim()?.equals("PETICION_URGENTE", ignoreCase = true) == true

    private fun primerNoVacio(vararg valores: String?): String? = valores.firstNotNullOfOrNull { limpio(it) }

    private fun limpio(valor: String?): String? = valor?.trim()?.takeIf { it.isNotEmpty() }
}
