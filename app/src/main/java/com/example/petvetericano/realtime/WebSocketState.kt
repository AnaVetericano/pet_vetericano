package com.example.petvetericano.realtime

import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Estados observables de la conexión WebSocket nativa de Vetericano.
 */
sealed class WebSocketState {
    data object Desconectado : WebSocketState()
    data object Conectando : WebSocketState()
    data object Autenticando : WebSocketState()
    data class Autenticado(
        val userId: Int,
        val idRol: Int,
        val tokenExp: Long
    ) : WebSocketState()

    data class Reconectando(
        val intento: Int,
        val esperaMs: Long
    ) : WebSocketState()

    data class ErrorPermanente(
        val codigo: Int,
        val motivo: String
    ) : WebSocketState()
}

enum class PoliticaCierre {
    NO_RECONECTAR_NORMAL,
    REFRESCAR_Y_RECONECTAR,
    REVOCAR_SESION,
    ERROR_PERMANENTE,
    RECONECTAR_BACKOFF
}

object WebSocketProtocolo {
    const val HEADER_CLIENTE_NATIVO = "X-Vetericano-Client"
    const val SCHEMA_VERSION_V1 = "1"

    const val CIERRE_NORMAL = 1000
    const val CIERRE_MENSAJE_MUY_GRANDE = 1009
    const val CIERRE_ERROR_INTERNO = 1011
    const val CIERRE_SATURADO = 1013
    const val CIERRE_AUTH = 4001
    const val CIERRE_EXPIRADO = 4002
    const val CIERRE_REVOCADO = 4003
    const val CIERRE_ORIGEN_RECHAZADO = 4403

    const val AUTH_TIMEOUT_MS = 10_000L
    const val HEARTBEAT_INTERVALO_MS = 25_000L
    const val PONG_TIMEOUT_MS = 10_000L
    const val BACKOFF_BASE_MS = 1_000L
    const val BACKOFF_MAX_MS = 30_000L
    const val MAX_REINTENTOS_CONSECUTIVOS = 12

    /** Clasifica el código de cierre del servidor según CONTRATO_NOTIFICACIONES_V1 §2. */
    fun clasificarCierre(codigo: Int, cierreIniciadoPorCliente: Boolean = false): PoliticaCierre {
        if (cierreIniciadoPorCliente && codigo == CIERRE_NORMAL) {
            return PoliticaCierre.NO_RECONECTAR_NORMAL
        }
        return when (codigo) {
            CIERRE_REVOCADO -> PoliticaCierre.REVOCAR_SESION
            CIERRE_EXPIRADO -> PoliticaCierre.REFRESCAR_Y_RECONECTAR
            CIERRE_AUTH -> PoliticaCierre.REFRESCAR_Y_RECONECTAR
            CIERRE_ORIGEN_RECHAZADO, CIERRE_MENSAJE_MUY_GRANDE -> PoliticaCierre.ERROR_PERMANENTE
            CIERRE_NORMAL, CIERRE_ERROR_INTERNO, CIERRE_SATURADO -> PoliticaCierre.RECONECTAR_BACKOFF
            else -> PoliticaCierre.RECONECTAR_BACKOFF
        }
    }

    /** Backoff exponencial acotado [1s..30s] con jitter ±20% (factorJitter en [-0.2, 0.2]). */
    fun calcularBackoffMs(
        intento: Int,
        factorJitter: Double = Random.nextDouble(-0.2, 0.2)
    ): Long {
        val paso = intento.coerceAtLeast(1) - 1
        val exponente = min(paso, 10)
        val base = min(BACKOFF_BASE_MS * (1L shl exponente), BACKOFF_MAX_MS)
        val factorAcotado = factorJitter.coerceIn(-0.2, 0.2)
        val resultado = (base * (1.0 + factorAcotado)).roundToLong()
        return resultado.coerceIn(500L, BACKOFF_MAX_MS)
    }

    /**
     * Valida la URL del WebSocket:
     * - En release (`permitirCleartext=false`) exige estrictamente `wss://`.
     * - En debug permite `ws://` o `wss://`.
     * - Rechaza URLs vacías, placeholders no configurados o URLs con tokens en query string.
     */
    fun validarUrl(url: String?, permitirCleartext: Boolean): String? {
        val limpia = url?.trim().orEmpty()
        if (limpia.isEmpty()) return null
        if (limpia.contains("REEMPLAZAR", ignoreCase = true)) return null
        if (limpia.contains("?")) return null
        val lower = limpia.lowercase()
        val esquemaValido = if (permitirCleartext) {
            lower.startsWith("ws://") || lower.startsWith("wss://")
        } else {
            lower.startsWith("wss://")
        }
        return if (esquemaValido) limpia else null
    }
}
