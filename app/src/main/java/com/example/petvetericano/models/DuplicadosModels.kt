package com.example.petvetericano.models

import java.util.Locale

enum class NivelCercania {
    MUY_CERCA,
    CERCA,
    NO_DISPONIBLE
}

enum class NivelSimilitud {
    ALTA,
    MEDIA,
    NO_DISPONIBLE
}

data class DetectarDuplicadosCiudadanoRequest(
    val descripcion: String,
    val latitud: Double,
    val longitud: Double
)

data class DetectarDuplicadosCiudadanoResponse(
    val advertencia_duplicado: Boolean = false,
    val posible_duplicado: Boolean = false,
    val total_candidatos: Int = 0,
    val mensaje: String? = null,
    val posibles_duplicados: List<CandidatoDuplicado>? = null
)

data class CandidatoDuplicado(
    val coincidencia_texto: Boolean = false,
    val coincidencia_gps: Boolean = false,
    val cercania: String? = null,
    val similitud: String? = null,
    val foto: String? = null,
    val tiene_fotografia: Boolean = false
) {
    val nivelCercania: NivelCercania
        get() = when (cercania?.trim()?.lowercase(Locale.ROOT)) {
            "muy_cerca" -> NivelCercania.MUY_CERCA
            "cerca" -> NivelCercania.CERCA
            else -> NivelCercania.NO_DISPONIBLE
        }

    val nivelSimilitud: NivelSimilitud
        get() = when (similitud?.trim()?.lowercase(Locale.ROOT)) {
            "alta" -> NivelSimilitud.ALTA
            "media" -> NivelSimilitud.MEDIA
            else -> NivelSimilitud.NO_DISPONIBLE
        }

    val fotoSegura: String?
        get() = foto?.trim()?.takeIf { it.startsWith("https://") }
}
