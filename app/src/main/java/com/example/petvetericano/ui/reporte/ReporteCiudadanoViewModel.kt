package com.example.petvetericano.ui.reporte

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.StringRes
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
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import com.example.petvetericano.offline.ReporteEncolador
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

private const val KEY_ID_TIPO = "key_reporte_id_tipo"
private const val KEY_NOMBRE_TIPO = "key_reporte_nombre_tipo"
private const val KEY_LATITUD = "key_reporte_latitud"
private const val KEY_LONGITUD = "key_reporte_longitud"
private const val KEY_DIRECCION = "key_reporte_direccion"
private const val KEY_UBICACION_USUARIO = "key_reporte_ubicacion_usuario"

private const val KEY_PASO_ACTUAL = "key_reporte_paso_actual"
private const val KEY_DESCRIPCION = "key_reporte_descripcion"
private const val KEY_EVIDENCIAS_PATHS = "key_reporte_evidencias_paths"
private const val KEY_EVIDENCIAS_IS_VIDEO = "key_reporte_evidencias_is_video"
private const val KEY_PENDING_CAM_PATH = "key_reporte_pending_cam_path"
private const val KEY_PENDING_CAM_URI = "key_reporte_pending_cam_uri"
private const val KEY_PENDING_CAM_IS_VIDEO = "key_reporte_pending_cam_is_video"

class ReporteCiudadanoViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState: MutableStateFlow<ReporteDraftState>
    val uiState: StateFlow<ReporteDraftState>

    private val _resultadoEnvioChannel = Channel<ResultadoEnvio>(Channel.BUFFERED)
    val resultadoEnvio = _resultadoEnvioChannel.receiveAsFlow()

    init {
        val idTipo = savedStateHandle.get<Int>(KEY_ID_TIPO)
        val nombreTipo = savedStateHandle.get<String>(KEY_NOMBRE_TIPO)
        val lat = savedStateHandle.get<Double>(KEY_LATITUD)
        val lng = savedStateHandle.get<Double>(KEY_LONGITUD)
        val dir = savedStateHandle.get<String>(KEY_DIRECCION)
        val ubicacionUsuario = savedStateHandle.get<Boolean>(KEY_UBICACION_USUARIO) ?: false

        val paso = savedStateHandle.get<Int>(KEY_PASO_ACTUAL) ?: 1
        val desc = savedStateHandle.get<String>(KEY_DESCRIPCION) ?: ""
        val paths = savedStateHandle.get<ArrayList<String>>(KEY_EVIDENCIAS_PATHS)
        val isVideos = savedStateHandle.get<BooleanArray>(KEY_EVIDENCIAS_IS_VIDEO)
        val pendPath = savedStateHandle.get<String>(KEY_PENDING_CAM_PATH)
        val pendUri = savedStateHandle.get<String>(KEY_PENDING_CAM_URI)
        val pendIsVideo = savedStateHandle.get<Boolean>(KEY_PENDING_CAM_IS_VIDEO) ?: false

        val evidenciasIniciales = mutableListOf<EvidenciaItemLocal>()
        if (paths != null && isVideos != null && paths.size == isVideos.size) {
            for (i in paths.indices) {
                val f = File(paths[i])
                if (f.exists() && f.length() > 0L) {
                    val esVid = isVideos[i]
                    evidenciasIniciales.add(
                        EvidenciaItemLocal(
                            id = f.name,
                            localPath = f.absolutePath,
                            uriString = "",
                            esVideo = esVid
                        )
                    )
                }
            }
        }

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
                pasoActual = paso,
                tipoSeleccionado = tipoInicial,
                latitud = lat,
                longitud = lng,
                direccionTexto = dir,
                ubicacionSeleccionadaPorUsuario = ubicacionUsuario,
                descripcion = desc,
                evidencias = evidenciasIniciales,
                pendingCameraFilePath = pendPath,
                pendingCameraUriString = pendUri,
                pendingCameraEsVideo = pendIsVideo
            )
        )
        uiState = _uiState.asStateFlow()
    }

    fun avanzarPaso2() {
        savedStateHandle[KEY_PASO_ACTUAL] = 2
        _uiState.update { it.copy(pasoActual = 2) }
    }

    fun volverPaso1() {
        savedStateHandle[KEY_PASO_ACTUAL] = 1
        _uiState.update { it.copy(pasoActual = 1) }
    }

    fun actualizarDescripcion(texto: String) {
        savedStateHandle[KEY_DESCRIPCION] = texto
        _uiState.update { it.copy(descripcion = texto) }
    }

    fun guardarPendingCamera(filePath: String, uriString: String, esVideo: Boolean) {
        savedStateHandle[KEY_PENDING_CAM_PATH] = filePath
        savedStateHandle[KEY_PENDING_CAM_URI] = uriString
        savedStateHandle[KEY_PENDING_CAM_IS_VIDEO] = esVideo
        _uiState.update {
            it.copy(
                pendingCameraFilePath = filePath,
                pendingCameraUriString = uriString,
                pendingCameraEsVideo = esVideo
            )
        }
    }

    fun limpiarPendingCamera() {
        savedStateHandle.remove<String>(KEY_PENDING_CAM_PATH)
        savedStateHandle.remove<String>(KEY_PENDING_CAM_URI)
        savedStateHandle.remove<Boolean>(KEY_PENDING_CAM_IS_VIDEO)
        _uiState.update {
            it.copy(
                pendingCameraFilePath = null,
                pendingCameraUriString = null,
                pendingCameraEsVideo = false
            )
        }
    }

    fun agregarEvidencia(item: EvidenciaItemLocal) {
        _uiState.update { state ->
            val nuevaLista = state.evidencias + item
            sincronizarEvidenciasSavedState(nuevaLista)
            state.copy(evidencias = nuevaLista)
        }
    }

    fun eliminarEvidencia(id: String): EvidenciaItemLocal? {
        val itemAEliminar = _uiState.value.evidencias.firstOrNull { it.id == id }
        _uiState.update { state ->
            val nuevaLista = state.evidencias.filterNot { it.id == id }
            sincronizarEvidenciasSavedState(nuevaLista)
            state.copy(evidencias = nuevaLista)
        }
        return itemAEliminar
    }

    fun cargarBorradorPersistente(desc: String, lista: List<EvidenciaItemLocal>) {
        savedStateHandle[KEY_DESCRIPCION] = desc
        sincronizarEvidenciasSavedState(lista)
        _uiState.update {
            it.copy(
                descripcion = desc,
                evidencias = lista
            )
        }
    }

    private fun sincronizarEvidenciasSavedState(lista: List<EvidenciaItemLocal>) {
        val paths = ArrayList(lista.map { it.localPath })
        val isVideos = lista.map { it.esVideo }.toBooleanArray()
        savedStateHandle[KEY_EVIDENCIAS_PATHS] = paths
        savedStateHandle[KEY_EVIDENCIAS_IS_VIDEO] = isVideos
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

    fun iniciarCargaUbicacion(@StringRes mensajeResId: Int = R.string.reporte_offline_cargando_direccion) {
        _uiState.update {
            it.copy(
                isLoadingUbicacion = true,
                mensajeCargaUbicacionResId = mensajeResId
            )
        }
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
                mensajeCargaUbicacionResId = R.string.reporte_offline_cargando_direccion,
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
        _uiState.update {
            it.copy(
                isLoadingUbicacion = true,
                mensajeCargaUbicacionResId = R.string.reporte_offline_cargando_direccion
            )
        }
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
        savedStateHandle.remove<Int>(KEY_PASO_ACTUAL)
        savedStateHandle.remove<String>(KEY_DESCRIPCION)
        savedStateHandle.remove<ArrayList<String>>(KEY_EVIDENCIAS_PATHS)
        savedStateHandle.remove<BooleanArray>(KEY_EVIDENCIAS_IS_VIDEO)
        limpiarPendingCamera()
        _uiState.value = ReporteDraftState()
    }

    fun enviarReporte(context: Context) {
        if (_uiState.value.estaEnviando) return
        val state = _uiState.value
        val tipo = state.tipoSeleccionado ?: return
        if (tipo.idTipo <= 0) return

        _uiState.update { it.copy(estaEnviando = true) }

        viewModelScope.launch {
            val appContext = context.applicationContext
            val latitud = state.latitud ?: 0.0
            val longitud = state.longitud ?: 0.0
            val dirFinal = when {
                !state.direccionTexto.isNullOrBlank() -> state.direccionTexto
                latitud != 0.0 && longitud != 0.0 -> appContext.getString(
                    R.string.reporte_offline_direccion_coordenadas,
                    latitud,
                    longitud
                )
                else -> appContext.getString(R.string.reporte_offline_ubicacion_no_seleccionada)
            }

            val evidenciasList = state.evidencias
                .filter { File(it.localPath).exists() }
                .map { Pair(it.localPath, it.esVideo) }

            val idLocal = withContext(Dispatchers.IO + NonCancellable) {
                ReporteEncolador.encolar(
                    context = appContext,
                    idTipo = tipo.idTipo,
                    tipoNombre = tipo.nombreRemoto,
                    descripcion = state.descripcion.trim(),
                    direccion = dirFinal,
                    latitud = latitud,
                    longitud = longitud,
                    evidencias = evidenciasList
                )
            }

            if (idLocal != null && idLocal > 0L) {
                _resultadoEnvioChannel.send(ResultadoEnvio.Exito(idLocal))
            } else {
                _uiState.update { it.copy(estaEnviando = false) }
                _resultadoEnvioChannel.send(ResultadoEnvio.Error)
            }
        }
    }
}
