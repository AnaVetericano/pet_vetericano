package com.example.petvetericano.ui.reporte

import com.example.petvetericano.models.CandidatoDuplicadoCiudadano
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import kotlin.math.roundToInt

/**
 * Resultado depurado y seguro para privacidad listo para renderizar en el dialogo preventivo.
 */
data class AlertaDuplicadoUiModel(
    val mensajeResumen: String?,
    val fotoAutorizadaUrl: String?,
    val porcentajeSimilitudVisual: Double?,
    val similitudVisualLabel: String?,
    val cercania: String?,
    val similitudTexto: String?,
    val metodoImagen: String?,
    val advertenciaMetricaVisual: String?,
    val esCertezaIdentidad: Boolean
)

/**
 * Reglas puras de privacidad y fail-open para deteccion preventiva de duplicados.
 */
object DuplicadoCiudadanoPolicy {

    const val ADVERTENCIA_METRICA_DEFECTO =
        "Porcentaje calculado como métrica visual orientativa, no como certeza automática."

    /**
     * Convierte la respuesta del endpoint en un modelo UI solo si existe posible duplicado.
     * Nunca expone URLs cuando [CandidatoDuplicadoCiudadano.tiene_fotografia] es falso o la URL es vacia.
     */
    fun extraerAlertaVisual(response: DetectarDuplicadosCiudadanoResponse?): AlertaDuplicadoUiModel? {
        if (response == null || !response.posible_duplicado) return null
        val candidato = response.posibles_duplicados.firstOrNull() ?: return null
        val fotoSegura = obtenerUrlFotoAutorizada(candidato)

        return AlertaDuplicadoUiModel(
            mensajeResumen = response.mensaje?.trim()?.takeIf { it.isNotEmpty() },
            fotoAutorizadaUrl = fotoSegura,
            porcentajeSimilitudVisual = candidato.porcentaje_similitud_visual,
            similitudVisualLabel = candidato.similitud_visual?.trim()?.takeIf { it.isNotEmpty() },
            cercania = candidato.cercania?.trim()?.takeIf { it.isNotEmpty() },
            similitudTexto = candidato.similitud?.trim()?.takeIf { it.isNotEmpty() },
            metodoImagen = candidato.metodo_imagen?.trim()?.takeIf { it.isNotEmpty() },
            advertenciaMetricaVisual = candidato.advertencia_metrica_visual?.trim()?.takeIf { it.isNotEmpty() }
                ?: ADVERTENCIA_METRICA_DEFECTO,
            esCertezaIdentidad = candidato.es_certeza_identidad == true
        )
    }

    /**
     * Devuelve la URL de fotografia unicamente cuando el backend permitio devolverla
     * y cumple con esquema HTTP(S).
     */
    fun obtenerUrlFotoAutorizada(candidato: CandidatoDuplicadoCiudadano): String? {
        if (!candidato.tiene_fotografia) return null
        val url = candidato.foto?.trim().orEmpty()
        if (url.isEmpty()) return null
        val esHttp = url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)
        return if (esHttp) url else null
    }

    /**
     * Formatea el porcentaje visual (0..100) evitando mostrar precision excesiva en la alerta.
     */
    fun formatearPorcentajeVisual(porcentaje: Double?): String? {
        if (porcentaje == null || porcentaje.isNaN() || porcentaje.isInfinite()) return null
        val acotado = porcentaje.coerceIn(0.0, 100.0)
        return if ((acotado * 10.0).roundToInt() % 10 == 0) {
            "${acotado.roundToInt()} %"
        } else {
            val redondeado = (acotado * 10.0).roundToInt() / 10.0
            "$redondeado %"
        }
    }
}
