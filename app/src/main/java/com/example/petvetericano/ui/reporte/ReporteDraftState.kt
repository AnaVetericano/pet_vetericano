package com.example.petvetericano.ui.reporte

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.petvetericano.R
import java.text.Normalizer
import java.util.Locale

fun normalizarNombre(texto: String?): String {
    if (texto.isNullOrBlank()) return ""
    val trimmed = texto.trim()
    val lower = trimmed.lowercase(Locale.ROOT)
    val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
    val withoutDiacritics = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    return withoutDiacritics.replace("\\s+".toRegex(), " ")
}

enum class TipoReporteCategoria(
    val nombreClave: String,
    @get:StringRes val tituloRes: Int,
    @get:DrawableRes val iconoRes: Int
) {
    ANIMAL_HERIDO("Animal Herido", R.string.lbl_animal_herido_o_en_peligro, R.drawable.warning_icon),
    MALTRATO_ANIMAL("Maltrato animal", R.string.lbl_maltrato_animal, R.drawable.warning_icon2),
    CONDICION_CALLE("Animal en condicion de calle", R.string.lbl_animal_en_condicion_de_calle, R.drawable.warning_icon3),
    OTROS("Otro", R.string.lbl_otros, R.drawable.warning_icon4),
    DESCONOCIDO("", R.string.lbl_otros, R.drawable.baseline_pets_24);

    companion object {
        fun desdeNombre(nombre: String?): TipoReporteCategoria {
            if (nombre.isNullOrBlank()) return DESCONOCIDO
            val buscado = normalizarNombre(nombre)
            return entries.firstOrNull { normalizarNombre(it.nombreClave) == buscado }
                ?: DESCONOCIDO
        }
    }
}

data class TipoReporteItem(
    val idTipo: Int,
    val nombreRemoto: String,
    val categoriaVisual: TipoReporteCategoria
)

data class EvidenciaItemLocal(
    val id: String,
    val localPath: String,
    val uriString: String,
    val esVideo: Boolean,
    val duracionSegundos: Int = 0,
    val tamanoBytes: Long = 0L
)

data class ReporteDraftState(
    val pasoActual: Int = 1,

    // Pantalla 1: Tipo y Ubicacion
    val tipoSeleccionado: TipoReporteItem? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val direccionTexto: String? = null,
    @get:StringRes val direccionFallbackResId: Int = R.string.reporte_ux_direccion_sin_conexion,
    val ubicacionSeleccionadaPorUsuario: Boolean = false,

    // Pantalla 2: Descripcion y Multimedia (preparado para R2)
    val descripcion: String = "",
    val evidencias: List<EvidenciaItemLocal> = emptyList(),

    // Captura pendiente para SavedStateHandle (preparado para R2)
    val pendingCameraFilePath: String? = null,
    val pendingCameraUriString: String? = null,
    val pendingCameraEsVideo: Boolean = false,

    // Control de UI
    val isLoadingUbicacion: Boolean = false,
    val estaEnviando: Boolean = false,
    val hayConexionInternet: Boolean = true,
    @get:StringRes val errorValidacionResId: Int? = null
) {
    val fotosCount: Int get() = evidencias.count { !it.esVideo }
    val videosCount: Int get() = evidencias.count { it.esVideo }

    val puedeAgregarFoto: Boolean get() = fotosCount < 5
    val puedeAgregarVideo: Boolean get() = videosCount < 1

    val puedeAvanzarPaso1: Boolean
        get() = tipoSeleccionado != null && ubicacionSeleccionadaPorUsuario && latitud != null && longitud != null

    val puedeRevisarPaso2: Boolean
        get() = descripcion.isNotBlank() && fotosCount <= 5 && videosCount <= 1
}
