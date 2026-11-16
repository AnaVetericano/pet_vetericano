package com.example.petvetericano.ui.reporte

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import com.example.petvetericano.BuildConfig
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoRequest
import com.example.petvetericano.models.TipoPeticionResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.offline.CloudinaryUploadHelper
import com.example.petvetericano.offline.NetworkUtils
import com.example.petvetericano.offline.ReporteEncolador
import com.example.petvetericano.offline.ResultadoSubida
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG_DUPLICADOS_IA = "DuplicadosDINOv2"
private const val CLOUDINARY_PRECHECK_TIMEOUT_MS = 25_000L
private const val DETECTAR_DUPLICADOS_TIMEOUT_MS = 28_000L
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
private const val KEY_EVIDENCIAS_URLS = "key_reporte_evidencias_urls"
private const val KEY_AUTORIZAR_FOTOS_DUPLICADOS = "key_reporte_autorizar_fotos_duplicados"
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
        val urlsRemotas = savedStateHandle.get<ArrayList<String>>(KEY_EVIDENCIAS_URLS)
        val autorizarFotosDuplicados = savedStateHandle.get<Boolean>(KEY_AUTORIZAR_FOTOS_DUPLICADOS) ?: false
        val pendPath = savedStateHandle.get<String>(KEY_PENDING_CAM_PATH)
        val pendUri = savedStateHandle.get<String>(KEY_PENDING_CAM_URI)
        val pendIsVideo = savedStateHandle.get<Boolean>(KEY_PENDING_CAM_IS_VIDEO) ?: false

        val evidenciasIniciales = mutableListOf<EvidenciaItemLocal>()
        if (paths != null && isVideos != null && paths.size == isVideos.size) {
            for (i in paths.indices) {
                val f = File(paths[i])
                if (f.exists() && f.length() > 0L) {
                    val esVid = isVideos[i]
                    val urlGuardada = urlsRemotas?.getOrNull(i)?.takeIf { it.isNotBlank() }
                    evidenciasIniciales.add(
                        EvidenciaItemLocal(
                            id = f.name,
                            localPath = f.absolutePath,
                            uriString = "",
                            esVideo = esVid,
                            urlRemota = urlGuardada
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
                autorizarFotosDuplicados = autorizarFotosDuplicados,
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

    fun actualizarAutorizarFotosDuplicados(autorizado: Boolean) {
        savedStateHandle[KEY_AUTORIZAR_FOTOS_DUPLICADOS] = autorizado
        _uiState.update { it.copy(autorizarFotosDuplicados = autorizado) }
    }

    private fun sincronizarEvidenciasSavedState(lista: List<EvidenciaItemLocal>) {
        val paths = ArrayList(lista.map { it.localPath })
        val isVideos = lista.map { it.esVideo }.toBooleanArray()
        val urls = ArrayList(lista.map { it.urlRemota.orEmpty() })
        savedStateHandle[KEY_EVIDENCIAS_PATHS] = paths
        savedStateHandle[KEY_EVIDENCIAS_IS_VIDEO] = isVideos
        savedStateHandle[KEY_EVIDENCIAS_URLS] = urls
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
        savedStateHandle.remove<ArrayList<String>>(KEY_EVIDENCIAS_URLS)
        savedStateHandle.remove<Boolean>(KEY_AUTORIZAR_FOTOS_DUPLICADOS)
        limpiarPendingCamera()
        _uiState.value = ReporteDraftState()
    }

    /**
     * Consulta preventivamente el endpoint ciudadano de duplicados (fail-open ante fallo o sin red).
     * Si el reporte incluye fotografias, reutiliza o sube primero la foto principal a Cloudinary
     * guardando la URL remota en el borrador para evitar cargas duplicadas en ReporteSyncWorker.
     */
    suspend fun verificarDuplicadosAntesDeConfirmar(context: Context): AlertaDuplicadoUiModel? {
        val estadoActual = _uiState.value
        if (estadoActual.estaVerificandoDuplicados || estadoActual.estaEnviando) {
            Log.w(
                TAG_DUPLICADOS_IA,
                "Verificacion omitida: verificando=${estadoActual.estaVerificandoDuplicados}, enviando=${estadoActual.estaEnviando}"
            )
            return null
        }

        val appContext = context.applicationContext
        if (!NetworkUtils.hayRedDisponible(appContext)) {
            Log.i(TAG_DUPLICADOS_IA, "Sin red validada; continuando en modo offline (fail-open).")
            return null
        }

        val token = SharedPreferencesManager(appContext).getAccessToken()
            .ifEmpty { RetrofitClient.authToken ?: "" }
            .trim()
        if (token.isEmpty()) {
            Log.w(TAG_DUPLICADOS_IA, "Token JWT ausente en verificacion preventiva (fail-open).")
            return null
        }

        val lat = estadoActual.latitud
        val lng = estadoActual.longitud
        val gpsValido = lat != null && lng != null && (kotlin.math.abs(lat) > 1e-6 || kotlin.math.abs(lng) > 1e-6)
        val descripcionLimpia = estadoActual.descripcion.trim()
        val fotosLocales = estadoActual.evidencias.filter { !it.esVideo && File(it.localPath).exists() }
        val fotosConUrlRemota = estadoActual.evidencias.filter {
            !it.esVideo && !it.urlRemota.isNullOrBlank() && it.urlRemota!!.trim().startsWith("http")
        }
        if (!gpsValido && fotosLocales.isEmpty() && fotosConUrlRemota.isEmpty()) {
            Log.i(
                TAG_DUPLICADOS_IA,
                "Criterios insuficientes para verificar (fotos=0, gpsValido=false, descLen=${descripcionLimpia.length})."
            )
            return null
        }

        _uiState.update { it.copy(estaVerificandoDuplicados = true) }
        val tInicio = SystemClock.elapsedRealtime()
        return try {
            RetrofitClient.init(appContext)
            RetrofitClient.authToken = token

            // Reutilizar URLs ya subidas y subir fotos nuevas pendientes (hasta 3 imagenes por consulta)
            val urlsFotos = mutableListOf<String>()
            for (fotoLocal in fotosLocales) {
                if (urlsFotos.size >= 3) break
                val urlExistente = fotoLocal.urlRemota?.trim()?.takeIf { it.startsWith("http") }
                if (urlExistente != null) {
                    urlsFotos.add(urlExistente)
                    continue
                }
                val tCloud = SystemClock.elapsedRealtime()
                val urlSubida = withTimeoutOrNull(CLOUDINARY_PRECHECK_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        CloudinaryUploadHelper.ensureInitialized(appContext)
                        when (val res = CloudinaryUploadHelper.subir(appContext, fotoLocal.localPath, esVideo = false)) {
                            is ResultadoSubida.Exito -> res.secureUrl.trim().takeIf { it.startsWith("http") }
                            is ResultadoSubida.ErrorRed -> {
                                Log.w(TAG_DUPLICADOS_IA, "ErrorRed Cloudinary pre-check: ${res.mensaje}")
                                null
                            }
                            is ResultadoSubida.ErrorPermanente -> {
                                Log.w(TAG_DUPLICADOS_IA, "ErrorPermanente Cloudinary pre-check: ${res.mensaje}")
                                null
                            }
                            is ResultadoSubida.ArchivoNoExiste -> {
                                Log.w(TAG_DUPLICADOS_IA, "Archivo local no encontrado para pre-check Cloudinary.")
                                null
                            }
                        }
                    }
                }
                val msCloud = SystemClock.elapsedRealtime() - tCloud
                if (urlSubida != null) {
                    urlsFotos.add(urlSubida)
                    actualizarUrlRemotaEvidencia(fotoLocal.id, urlSubida)
                    Log.i(TAG_DUPLICADOS_IA, "Pre-subida Cloudinary OK en ${msCloud}ms (fotos=${urlsFotos.size}).")
                } else {
                    Log.w(TAG_DUPLICADOS_IA, "Pre-subida Cloudinary fallo/timeout tras ${msCloud}ms.")
                }
            }

            for (ev in fotosConUrlRemota) {
                if (urlsFotos.size >= 3) break
                val urlLimpia = ev.urlRemota?.trim() ?: continue
                if (!urlsFotos.contains(urlLimpia)) {
                    urlsFotos.add(urlLimpia)
                }
            }

            if (fotosLocales.isNotEmpty() && urlsFotos.isEmpty() && !gpsValido) {
                Log.w(TAG_DUPLICADOS_IA, "No se obtuvo URL de Cloudinary para las ${fotosLocales.size} foto(s) y no hay GPS válido; fail-open.")
                return null
            }
            if (fotosLocales.isNotEmpty() && urlsFotos.isEmpty() && gpsValido) {
                Log.w(TAG_DUPLICADOS_IA, "Sin URL Cloudinary en pre-check; se consultará detectar-duplicados-ciudadano solo por proximidad GPS.")
            }

            val request = DetectarDuplicadosCiudadanoRequest(
                descripcion = descripcionLimpia.takeIf { it.isNotEmpty() },
                latitud = if (gpsValido) lat else null,
                longitud = if (gpsValido) lng else null,
                foto = urlsFotos.firstOrNull(),
                fotos = urlsFotos.takeIf { it.isNotEmpty() }
            )

            Log.i(
                TAG_DUPLICADOS_IA,
                "Consultando detectar-duplicados-ciudadano en ${BuildConfig.API_BASE_URL} (fotos=${urlsFotos.size}, gpsValido=$gpsValido)."
            )

            val tBackend = SystemClock.elapsedRealtime()
            val response = withTimeoutOrNull(DETECTAR_DUPLICADOS_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.detectarDuplicadosCiudadano(request)
                }
            }
            val msBackend = SystemClock.elapsedRealtime() - tBackend
            if (response == null) {
                Log.w(TAG_DUPLICADOS_IA, "Timeout (${DETECTAR_DUPLICADOS_TIMEOUT_MS}ms) esperando detectar-duplicados-ciudadano; fail-open.")
                return null
            }

            if (!response.isSuccessful) {
                Log.w(
                    TAG_DUPLICADOS_IA,
                    "Respuesta HTTP no exitosa de detectar-duplicados-ciudadano: code=${response.code()} en ${msBackend}ms."
                )
                return null
            }

            val body = response.body()
            val alerta = DuplicadoCiudadanoPolicy.extraerAlertaVisual(body)
            Log.i(
                TAG_DUPLICADOS_IA,
                "Respuesta HTTP ${response.code()} en ${msBackend}ms (totalMs=${SystemClock.elapsedRealtime() - tInicio}): " +
                    "posible=${body?.posible_duplicado}, candidatos=${body?.total_candidatos}, " +
                    "pct=${alerta?.porcentajeSimilitudVisual}, tieneFoto=${alerta?.fotoAutorizadaUrl != null}"
            )
            alerta
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG_DUPLICADOS_IA, "Excepcion en verificacion preventiva (${e.javaClass.simpleName}): ${e.message}")
            null
        } finally {
            _uiState.update { it.copy(estaVerificandoDuplicados = false) }
        }
    }

    private fun actualizarUrlRemotaEvidencia(evidenciaId: String, urlRemota: String) {
        _uiState.update { state ->
            val nuevaLista = state.evidencias.map { ev ->
                if (ev.id == evidenciaId) ev.copy(urlRemota = urlRemota) else ev
            }
            sincronizarEvidenciasSavedState(nuevaLista)
            state.copy(evidencias = nuevaLista)
        }
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

            val evidenciasValidas = state.evidencias.filter { File(it.localPath).exists() }
            val evidenciasList = evidenciasValidas.map { Pair(it.localPath, it.esVideo) }
            val urlsRemotasPorPath = evidenciasValidas
                .mapNotNull { ev ->
                    ev.urlRemota?.trim()?.takeIf { it.isNotEmpty() }?.let { ev.localPath to it }
                }
                .toMap()

            val idLocal = withContext(Dispatchers.IO + NonCancellable) {
                ReporteEncolador.encolar(
                    context = appContext,
                    idTipo = tipo.idTipo,
                    tipoNombre = tipo.nombreRemoto,
                    descripcion = state.descripcion.trim(),
                    direccion = dirFinal,
                    latitud = latitud,
                    longitud = longitud,
                    evidencias = evidenciasList,
                    urlsRemotasPorPath = urlsRemotasPorPath,
                    autorizarFotosDuplicados = state.autorizarFotosDuplicados
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
