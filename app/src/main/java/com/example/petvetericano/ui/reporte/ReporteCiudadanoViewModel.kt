package com.example.petvetericano.ui.reporte

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petvetericano.R
import com.example.petvetericano.models.TipoPeticionResponse
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

private const val KEY_ID_TIPO = "key_reporte_id_tipo"
private const val KEY_NOMBRE_TIPO = "key_reporte_nombre_tipo"
private const val KEY_LATITUD = "key_reporte_latitud"
private const val KEY_LONGITUD = "key_reporte_longitud"
private const val KEY_DIRECCION = "key_reporte_direccion"
private const val KEY_UBICACION_USUARIO = "key_reporte_ubicacion_usuario"

class ReporteCiudadanoViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState: MutableStateFlow<ReporteDraftState>
    val uiState: StateFlow<ReporteDraftState>

    init {
        val idTipo = savedStateHandle.get<Int>(KEY_ID_TIPO)
        val nombreTipo = savedStateHandle.get<String>(KEY_NOMBRE_TIPO)
        val lat = savedStateHandle.get<Double>(KEY_LATITUD)
        val lng = savedStateHandle.get<Double>(KEY_LONGITUD)
        val dir = savedStateHandle.get<String>(KEY_DIRECCION)
        val ubicacionUsuario = savedStateHandle.get<Boolean>(KEY_UBICACION_USUARIO) ?: false

        val tipoInicial = if (idTipo != null && !nombreTipo.isNullOrBlank()) {
            TipoReporteItem(
                idTipo = idTipo,
                nombreRemoto = nombreTipo.trim(),
                categoriaVisual = TipoReporteCategoria.desdeNombre(nombreTipo)
            )
        } else {
            null
        }

        _uiState = MutableStateFlow(
            ReporteDraftState(
                tipoSeleccionado = tipoInicial,
                latitud = lat,
                longitud = lng,
                direccionTexto = dir,
                ubicacionSeleccionadaPorUsuario = ubicacionUsuario
            )
        )
        uiState = _uiState.asStateFlow()
    }

    fun seleccionarTipo(item: TipoReporteItem) {
        savedStateHandle[KEY_ID_TIPO] = item.idTipo
        savedStateHandle[KEY_NOMBRE_TIPO] = item.nombreRemoto
        _uiState.update {
            it.copy(
                tipoSeleccionado = item,
                errorValidacionResId = null
            )
        }
    }

    fun seleccionarTipoPorNombre(nombreBuscado: String, catalogo: List<TipoPeticionResponse>): Boolean {
        if (catalogo.isEmpty()) return false
        val buscadoNorm = normalizarNombre(nombreBuscado)
        val encontrado = catalogo.firstOrNull { normalizarNombre(it.nombre) == buscadoNorm } ?: return false
        val item = TipoReporteItem(
            idTipo = encontrado.id_tipo,
            nombreRemoto = encontrado.nombre.trim(),
            categoriaVisual = TipoReporteCategoria.desdeNombre(nombreBuscado)
        )
        seleccionarTipo(item)
        return true
    }

    fun iniciarCargaUbicacion() {
        _uiState.update { it.copy(isLoadingUbicacion = true) }
    }

    fun cancelarCargaUbicacion() {
        _uiState.update { it.copy(isLoadingUbicacion = false) }
    }

    fun establecerUbicacion(lat: Double, lng: Double, direccion: String?) {
        savedStateHandle[KEY_LATITUD] = lat
        savedStateHandle[KEY_LONGITUD] = lng
        savedStateHandle[KEY_DIRECCION] = direccion
        savedStateHandle[KEY_UBICACION_USUARIO] = true

        _uiState.update {
            it.copy(
                latitud = lat,
                longitud = lng,
                direccionTexto = direccion,
                ubicacionSeleccionadaPorUsuario = true,
                isLoadingUbicacion = false,
                errorValidacionResId = null
            )
        }
    }

    fun resolverDireccion(context: Context, lat: Double, lng: Double) {
        _uiState.update {
            it.copy(
                latitud = lat,
                longitud = lng,
                direccionTexto = null,
                ubicacionSeleccionadaPorUsuario = true,
                isLoadingUbicacion = true,
                errorValidacionResId = null
            )
        }
        viewModelScope.launch {
            val direccionResuelta = obtenerDireccionIO(context.applicationContext, lat, lng)?.takeIf { it.isNotBlank() }
            establecerUbicacion(lat, lng, direccionResuelta)
        }
    }

    fun buscarDireccion(context: Context, query: String, onResultado: (LatLng?) -> Unit) {
        if (query.isBlank()) {
            onResultado(null)
            return
        }
        _uiState.update { it.copy(isLoadingUbicacion = true) }
        viewModelScope.launch {
            val coords = buscarDireccionIO(context.applicationContext, query)
            if (coords != null) {
                val dirTexto = obtenerDireccionIO(context.applicationContext, coords.latitude, coords.longitude)?.takeIf { it.isNotBlank() }
                establecerUbicacion(coords.latitude, coords.longitude, dirTexto)
                withContext(Dispatchers.Main) {
                    onResultado(coords)
                }
            } else {
                _uiState.update { it.copy(isLoadingUbicacion = false) }
                withContext(Dispatchers.Main) {
                    onResultado(null)
                }
            }
        }
    }

    private suspend fun obtenerDireccionIO(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val linea = addresses.firstOrNull()?.getAddressLine(0)
                                if (continuation.isActive) continuation.resume(linea)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    addresses?.firstOrNull()?.getAddressLine(0)
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun buscarDireccionIO(context: Context, query: String): LatLng? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val primera = addresses.firstOrNull()
                                val resultado = if (primera != null) {
                                    LatLng(primera.latitude, primera.longitude)
                                } else {
                                    null
                                }
                                if (continuation.isActive) continuation.resume(resultado)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)
                    val primera = addresses?.firstOrNull()
                    if (primera != null) LatLng(primera.latitude, primera.longitude) else null
                }
            } catch (_: Exception) {
                null
            }
        }

    fun limpiarBorrador() {
        savedStateHandle.remove<Int>(KEY_ID_TIPO)
        savedStateHandle.remove<String>(KEY_NOMBRE_TIPO)
        savedStateHandle.remove<Double>(KEY_LATITUD)
        savedStateHandle.remove<Double>(KEY_LONGITUD)
        savedStateHandle.remove<String>(KEY_DIRECCION)
        savedStateHandle.remove<Boolean>(KEY_UBICACION_USUARIO)
        _uiState.value = ReporteDraftState()
    }
}
