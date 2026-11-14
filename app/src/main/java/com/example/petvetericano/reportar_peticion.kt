package com.example.petvetericano

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.petvetericano.ui.reporte.DestinoEdicion
import com.example.petvetericano.ui.reporte.ResultadoEnvio
import com.example.petvetericano.ui.reporte.VisorEvidenciaDialogFragment
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityReportarPeticionUnificadaBinding
import com.example.petvetericano.databinding.BottomSheetAdjuntarEvidenciaBinding
import com.example.petvetericano.models.TipoPeticionResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.ui.reporte.BottomSheetConfirmarReporte
import com.example.petvetericano.ui.reporte.BottomSheetPosiblesDuplicados
import com.example.petvetericano.ui.reporte.CaptureVideoConLimites
import com.example.petvetericano.ui.reporte.EvidenciaItemLocal
import com.example.petvetericano.ui.reporte.EventoDuplicados
import com.example.petvetericano.ui.reporte.MiniaturasAdapter
import com.example.petvetericano.ui.reporte.ReporteCiudadanoViewModel
import com.example.petvetericano.ui.reporte.ReporteDraftState
import com.example.petvetericano.ui.reporte.ReporteMediaHelper
import com.example.petvetericano.ui.reporte.ResultadoValidacionVideo
import com.example.petvetericano.ui.reporte.TipoReporteCategoria
import com.example.petvetericano.ui.reporte.TipoReporteItem
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

class reportar_peticion : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReportarPeticionUnificadaBinding
    private val viewModel: ReporteCiudadanoViewModel by viewModels()

    private var mMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var listaTipos: List<TipoPeticionResponse> = emptyList()
    private var isCargandoTipos = false
    private var tipoPendiente: String? = null
    private var esErrorValidacionVisible = false
    private var esAvisoUbicacionAproximadaVisible = false
    private var isObteniendoUbicacion = false
    private var ctsUbicacion: CancellationTokenSource? = null
    private var ubicacionJob: Job? = null
    private var reporteEncoladoExitoso = false

    fun marcarReporteEncoladoExitoso() {
        reporteEncoladoExitoso = true
        binding.etSearch.text?.clear()
        binding.etDescripcion.text?.clear()
    }

    private val prefsCache = "ReporteTiposCachePrefs"
    private val keyCache = "ReporteTiposCache"

    private val prefsMapa = "MapaOffline"
    private val keyBusqueda = "borrador_busqueda"
    private val keyLat = "borrador_lat"
    private val keyLng = "borrador_lng"
    private val keyHasLoc = "borrador_has_loc"

    private val prefsReportes = "MisReportesPrefs"
    private val keyIdTipoSeleccionado = "ID_TIPO_SELECCIONADO"
    private val keyTipoReporte = "TIPO_REPORTE"

    private val prefsMultimedia = "ReporteOfflineMultimedia"
    private val keyBorradorDesc = "borrador_descripcion"
    private val keyBorradorPaths = "borrador_paths_v2"
    private val keyBorradorIsVideo = "borrador_is_video_v2"

    private lateinit var miniaturasAdapter: MiniaturasAdapter
    private var pendingMediaEsVideo: Boolean = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            obtenerUbicacionActual()
        } else {
            isObteniendoUbicacion = false
            Toast.makeText(this, R.string.reporte_ux_error_permiso_ubicacion, Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val pendPath = viewModel.uiState.value.pendingCameraFilePath
        if (success && !pendPath.isNullOrBlank()) {
            val file = File(pendPath)
            lifecycleScope.launch {
                val optimizado = ReporteMediaHelper.optimizarImagen(file)
                val item = EvidenciaItemLocal(
                    id = optimizado.name,
                    localPath = optimizado.absolutePath,
                    uriString = viewModel.uiState.value.pendingCameraUriString.orEmpty(),
                    esVideo = false
                )
                viewModel.agregarEvidencia(item)
                viewModel.limpiarPendingCamera()
                guardarMultimediaEnPrefs()
            }
        } else {
            viewModel.limpiarPendingCamera()
        }
    }

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val file = ReporteMediaHelper.copiarUriADraft(this@reportar_peticion, it, esVideo = false)
                if (file != null) {
                    val item = EvidenciaItemLocal(
                        id = file.name,
                        localPath = file.absolutePath,
                        uriString = it.toString(),
                        esVideo = false
                    )
                    viewModel.agregarEvidencia(item)
                    guardarMultimediaEnPrefs()
                }
            }
        }
    }

    private val recordVideoLauncher = registerForActivityResult(
        CaptureVideoConLimites()
    ) { success ->
        val pendPath = viewModel.uiState.value.pendingCameraFilePath
        if (success && !pendPath.isNullOrBlank()) {
            val file = File(pendPath)
            lifecycleScope.launch {
                when (ReporteMediaHelper.validarVideo(file)) {
                    ResultadoValidacionVideo.Valido -> {
                        val item = EvidenciaItemLocal(
                            id = file.name,
                            localPath = file.absolutePath,
                            uriString = viewModel.uiState.value.pendingCameraUriString.orEmpty(),
                            esVideo = true
                        )
                        viewModel.agregarEvidencia(item)
                        viewModel.limpiarPendingCamera()
                        guardarMultimediaEnPrefs()
                    }
                    ResultadoValidacionVideo.ExcedeTamano -> {
                        viewModel.limpiarPendingCamera()
                        mostrarDialogoErrorVideo(
                            R.string.reporte_offline_video_pesado_titulo,
                            R.string.reporte_offline_video_pesado_mensaje
                        )
                    }
                    ResultadoValidacionVideo.ExcedeDuracion -> {
                        viewModel.limpiarPendingCamera()
                        mostrarDialogoErrorVideo(
                            R.string.reporte_ux_video_largo_titulo,
                            R.string.reporte_ux_error_video_excede_duracion
                        )
                    }
                    ResultadoValidacionVideo.ErrorLectura -> {
                        viewModel.limpiarPendingCamera()
                        mostrarDialogoErrorVideo(
                            R.string.reporte_ux_error_video_invalido_titulo,
                            R.string.reporte_ux_error_video_invalido
                        )
                    }
                }
            }
        } else {
            viewModel.limpiarPendingCamera()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch {
                val file = ReporteMediaHelper.copiarUriADraft(this@reportar_peticion, it, esVideo = true)
                if (file != null) {
                    when (ReporteMediaHelper.validarVideo(file)) {
                        ResultadoValidacionVideo.Valido -> {
                            val item = EvidenciaItemLocal(
                                id = file.name,
                                localPath = file.absolutePath,
                                uriString = it.toString(),
                                esVideo = true
                            )
                            viewModel.agregarEvidencia(item)
                            guardarMultimediaEnPrefs()
                        }
                        ResultadoValidacionVideo.ExcedeTamano -> {
                            mostrarDialogoErrorVideo(
                                R.string.reporte_offline_video_pesado_titulo,
                                R.string.reporte_offline_video_pesado_mensaje
                            )
                        }
                        ResultadoValidacionVideo.ExcedeDuracion -> {
                            mostrarDialogoErrorVideo(
                                R.string.reporte_ux_video_largo_titulo,
                                R.string.reporte_ux_error_video_excede_duracion
                            )
                        }
                        ResultadoValidacionVideo.ErrorLectura -> {
                            mostrarDialogoErrorVideo(
                                R.string.reporte_ux_error_video_invalido_titulo,
                                R.string.reporte_ux_error_video_invalido
                            )
                        }
                    }
                }
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingMediaEsVideo) {
                lanzarGrabacionVideo()
            } else {
                lanzarCapturaFoto()
            }
        } else {
            Toast.makeText(this, R.string.reporte_ux_error_permiso_camara, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionUnificadaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ReporteMediaHelper.purgarDraftsAntiguos(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        configurarInsets()
        configurarVistas()
        configurarRecyclerViewMiniaturas()
        configurarMapa()
        configurarNavegacionAtras()
        observarEstado()
        cargarTipos()

        if (savedInstanceState == null) {
            restaurarBorradorDesdePrefs()
        } else {
            val desc = viewModel.uiState.value.descripcion
            if (desc.isNotBlank() && binding.etDescripcion.text.isNullOrBlank()) {
                binding.etDescripcion.setText(desc)
            }
        }
    }

    private fun configurarInsets() {
        val initHeaderTop = binding.headerContainer.paddingTop
        val initBottomPaso1 = binding.bottomControlsContainer.paddingBottom
        val initBottomPaso2 = binding.bottomControlsContainerPaso2.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                initHeaderTop + systemBars.top,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )
            binding.bottomControlsContainer.setPadding(
                binding.bottomControlsContainer.paddingLeft,
                binding.bottomControlsContainer.paddingTop,
                binding.bottomControlsContainer.paddingRight,
                initBottomPaso1 + systemBars.bottom
            )
            binding.bottomControlsContainerPaso2.setPadding(
                binding.bottomControlsContainerPaso2.paddingLeft,
                binding.bottomControlsContainerPaso2.paddingTop,
                binding.bottomControlsContainerPaso2.paddingRight,
                initBottomPaso2 + systemBars.bottom
            )
            insets
        }
    }

    private fun configurarVistas() {
        binding.btndev.setOnClickListener {
            if (viewModel.uiState.value.pasoActual == 2) {
                volverAlPaso1()
            } else {
                startActivity(Intent(this, bienvenida::class.java))
                finish()
            }
        }

        binding.cardHerido.setOnClickListener {
            seleccionarTipoOCatalogar("Animal Herido")
        }

        binding.cardMaltrato.setOnClickListener {
            seleccionarTipoOCatalogar("Maltrato animal")
        }

        binding.cardCalle.setOnClickListener {
            seleccionarTipoOCatalogar("Animal en condicion de calle")
        }

        binding.cardOtros.setOnClickListener {
            seleccionarTipoOCatalogar(TipoReporteCategoria.OTROS.nombreClave)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                ejecutarBusqueda()
                true
            } else {
                false
            }
        }

        binding.btnLocation.setOnClickListener {
            verificarPermisosUbicacion()
        }

        binding.btnContinue.setOnClickListener {
            avanzarAlPaso2()
        }

        binding.btnAdjuntarFoto.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = false)
        }

        binding.btnAdjuntarVideo.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = true)
        }

        binding.etDescripcion.addTextChangedListener { text ->
            val texto = text?.toString().orEmpty()
            viewModel.actualizarDescripcion(texto)
            getSharedPreferences(prefsMultimedia, MODE_PRIVATE)
                .edit()
                .putString(keyBorradorDesc, texto)
                .apply()
            if (texto.isNotBlank()) {
                binding.tilDescripcion.error = null
            }
        }

        binding.btnRevisarReporte.setOnClickListener {
            validarYRevisarReporte()
        }

        binding.cbAutorizaFotoDuplicados.setOnCheckedChangeListener { _, marcado ->
            viewModel.actualizarAutorizacionFotoDuplicados(marcado)
        }

        val prefs = getSharedPreferences(prefsMapa, MODE_PRIVATE)
        val textoGuardado = prefs.getString(keyBusqueda, "")
        if (!textoGuardado.isNullOrBlank()) {
            binding.etSearch.setText(textoGuardado)
        }
    }

    private fun configurarRecyclerViewMiniaturas() {
        miniaturasAdapter = MiniaturasAdapter(
            onFotoClick = { item ->
                VisorEvidenciaDialogFragment.nuevaInstancia(item.localPath)
                    .show(supportFragmentManager, VisorEvidenciaDialogFragment.TAG)
            },
            onVideoClick = { item ->
                reproducirVideo(item)
            },
            onEliminarClick = { item ->
                eliminarEvidencia(item)
            }
        )
        binding.rvMiniaturas.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvMiniaturas.adapter = miniaturasAdapter
    }

    private fun configurarNavegacionAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.uiState.value.pasoActual == 2) {
                    volverAlPaso1()
                } else {
                    startActivity(Intent(this@reportar_peticion, bienvenida::class.java))
                    finish()
                }
            }
        })
    }

    private fun configurarMapa() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun observarEstado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.resultadoEnvio.collect { resultado ->
                        when (resultado) {
                            is ResultadoEnvio.Exito -> {
                                consumirEnvioExitoso(resultado.idLocal)
                            }
                            is ResultadoEnvio.Error -> {
                                Toast.makeText(
                                    this@reportar_peticion,
                                    R.string.reporte_offline_error_guardar_reporte,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventosDuplicados.collect { evento ->
                        when (evento) {
                            EventoDuplicados.ContinuarConfirmacion -> {
                                val sheet = supportFragmentManager.findFragmentByTag(
                                    BottomSheetPosiblesDuplicados.TAG
                                ) as? BottomSheetDialogFragment
                                sheet?.dismissAllowingStateLoss()
                                abrirConfirmacionReporte()
                            }
                            EventoDuplicados.MostrarAdvertencia -> {
                                if (viewModel.uiState.value.candidatosDuplicados.isNotEmpty()) {
                                    val existente = supportFragmentManager.findFragmentByTag(
                                        BottomSheetPosiblesDuplicados.TAG
                                    )
                                    if (existente == null && !supportFragmentManager.isStateSaved) {
                                        BottomSheetPosiblesDuplicados().show(
                                            supportFragmentManager,
                                            BottomSheetPosiblesDuplicados.TAG
                                        )
                                    }
                                }
                            }
                            EventoDuplicados.MantenerBorrador -> {
                                val sheet = supportFragmentManager.findFragmentByTag(
                                    BottomSheetPosiblesDuplicados.TAG
                                ) as? BottomSheetDialogFragment
                                sheet?.dismissAllowingStateLoss()
                                Snackbar.make(
                                    binding.root,
                                    R.string.reporte_dup_mensaje_ya_existe,
                                    Snackbar.LENGTH_LONG
                                ).apply {
                                    setTextMaxLines(4)
                                }.show()
                            }
                        }
                    }
                }

                viewModel.uiState.collect { state ->
                    binding.btnRevisarReporte.isEnabled = !state.verificandoDuplicados
                    binding.btnRevisarReporte.setText(
                        if (state.verificandoDuplicados) {
                            R.string.reporte_dup_verificando
                        } else {
                            R.string.reporte_ux_btn_revisar_reporte
                        }
                    )

                    renderizarSeleccionTipo(state.tipoSeleccionado)

                    // Control de contenedores paso 1 y paso 2
                    if (state.pasoActual == 2) {
                        binding.containerPaso1.visibility = View.GONE
                        binding.bottomControlsContainer.visibility = View.GONE
                        binding.containerPaso2.visibility = View.VISIBLE
                    } else {
                        binding.containerPaso2.visibility = View.GONE
                        binding.containerPaso1.visibility = View.VISIBLE
                        binding.bottomControlsContainer.visibility = View.VISIBLE
                    }

                    // Miniaturas y contador de evidencias
                    miniaturasAdapter.submitList(state.evidencias)
                    if (state.evidencias.isEmpty()) {
                        binding.rvMiniaturas.visibility = View.GONE
                    } else {
                        binding.rvMiniaturas.visibility = View.VISIBLE
                    }
                    binding.layoutAutorizaFoto.visibility = if (state.fotosCount > 0) View.VISIBLE else View.GONE
                    if (binding.cbAutorizaFotoDuplicados.isChecked != state.autorizaFotoDuplicados) {
                        binding.cbAutorizaFotoDuplicados.isChecked = state.autorizaFotoDuplicados
                    }

                    if (state.fotosCount >= 5 && state.videosCount >= 1) {
                        binding.tvAyudaMultimedia.setText(R.string.reporte_ux_limite_multimedia)
                        binding.btnAdjuntarFoto.isEnabled = false
                        binding.btnAdjuntarVideo.isEnabled = false
                        binding.btnAdjuntarFoto.alpha = 0.5f
                        binding.btnAdjuntarVideo.alpha = 0.5f
                    } else {
                        binding.tvAyudaMultimedia.setText(R.string.reporte_ux_ayuda_multimedia)
                        binding.btnAdjuntarFoto.isEnabled = state.puedeAgregarFoto
                        binding.btnAdjuntarVideo.isEnabled = state.puedeAgregarVideo
                        binding.btnAdjuntarFoto.alpha = if (state.puedeAgregarFoto) 1.0f else 0.5f
                        binding.btnAdjuntarVideo.alpha = if (state.puedeAgregarVideo) 1.0f else 0.5f
                    }

                    if (state.isLoadingUbicacion) {
                        binding.pbDireccion.visibility = View.VISIBLE
                        binding.tvDireccion.setText(state.mensajeCargaUbicacionResId)
                    } else {
                        isObteniendoUbicacion = false
                        binding.pbDireccion.visibility = View.GONE
                        if (!state.direccionTexto.isNullOrBlank()) {
                            binding.tvDireccion.text = state.direccionTexto
                        } else if (state.ubicacionSeleccionadaPorUsuario && state.latitud != null && state.longitud != null) {
                            binding.tvDireccion.text = getString(
                                R.string.reporte_ux_direccion_fallback_coordenadas,
                                getString(R.string.reporte_ux_direccion_sin_conexion),
                                state.latitud,
                                state.longitud
                            )
                        } else {
                            binding.tvDireccion.setText(R.string.reporte_ux_direccion_no_seleccionada)
                        }
                    }

                    val errorRes = obtenerErrorValidacionResId(state)
                    if (errorRes == null) {
                        esErrorValidacionVisible = false
                        if (esAvisoUbicacionAproximadaVisible) {
                            val colorMuted = ContextCompat.getColor(this@reportar_peticion, R.color.text_muted)
                            mostrarAviso(getString(R.string.reporte_ux_ubicacion_aproximada), colorMuted)
                        } else {
                            ocultarAviso()
                        }
                    } else if (esErrorValidacionVisible) {
                        val colorError = ContextCompat.getColor(this@reportar_peticion, R.color.error_dark)
                        mostrarAviso(getString(errorRes), colorError)
                    }

                    if (state.ubicacionSeleccionadaPorUsuario && state.latitud != null && state.longitud != null) {
                        val pos = LatLng(state.latitud, state.longitud)
                        actualizarMarcador(pos, moverCamara = false)
                    }
                }
            }
        }
    }

    private fun renderizarSeleccionTipo(tipo: TipoReporteItem?) {
        val colorPrimary = ContextCompat.getColor(this, R.color.blue)
        val colorStroke = ContextCompat.getColor(this, R.color.card_stroke)
        val colorBackgroundSelected = ContextCompat.getColor(this, R.color.primary_light)
        val colorBackgroundDefault = ContextCompat.getColor(this, R.color.card_background)
        val strokeSelectedPx = dpToPx(3)
        val strokeDefaultPx = dpToPx(1)

        fun actualizarCard(card: MaterialCardView, seleccionado: Boolean, tituloRes: Int) {
            card.isChecked = seleccionado
            card.strokeWidth = if (seleccionado) strokeSelectedPx else strokeDefaultPx
            card.strokeColor = if (seleccionado) colorPrimary else colorStroke
            card.setCardBackgroundColor(if (seleccionado) colorBackgroundSelected else colorBackgroundDefault)
            val baseTexto = getString(tituloRes)
            card.contentDescription = if (seleccionado) {
                getString(R.string.reporte_ux_cd_tipo_seleccionado, baseTexto)
            } else {
                baseTexto
            }
        }

        val cat = tipo?.categoriaVisual
        actualizarCard(binding.cardHerido, cat == TipoReporteCategoria.ANIMAL_HERIDO, R.string.reporte_ux_tipo_herido)
        actualizarCard(binding.cardMaltrato, cat == TipoReporteCategoria.MALTRATO_ANIMAL, R.string.reporte_ux_tipo_maltrato)
        actualizarCard(binding.cardCalle, cat == TipoReporteCategoria.CONDICION_CALLE, R.string.reporte_ux_tipo_calle)
        actualizarCard(binding.cardOtros, cat == TipoReporteCategoria.OTROS, R.string.reporte_ux_tipo_otros)
    }

    private fun seleccionarTipoOCatalogar(nombreBuscado: String) {
        val asignado = viewModel.seleccionarTipoPorNombre(nombreBuscado, listaTipos)
        if (!asignado) {
            tipoPendiente = nombreBuscado
            cargarTipos()
        }
    }

    private fun aplicarTipoPendienteSiExiste() {
        val pendiente = tipoPendiente ?: return
        if (listaTipos.isNotEmpty()) {
            val asignado = viewModel.seleccionarTipoPorNombre(pendiente, listaTipos)
            if (asignado) {
                tipoPendiente = null
                ocultarAviso()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.uiSettings?.isZoomControlsEnabled = true
        mMap?.uiSettings?.isCompassEnabled = true

        val bogota = LatLng(4.6097, -74.0817)

        val state = viewModel.uiState.value
        if (state.ubicacionSeleccionadaPorUsuario && state.latitud != null && state.longitud != null) {
            val pos = LatLng(state.latitud, state.longitud)
            actualizarMarcador(pos, moverCamara = true)
        } else {
            val prefs = getSharedPreferences(prefsMapa, MODE_PRIVATE)
            val hasLoc = prefs.getBoolean(keyHasLoc, false)
            if (hasLoc) {
                val lat = prefs.getFloat(keyLat, 0f).toDouble()
                val lng = prefs.getFloat(keyLng, 0f).toDouble()
                if (lat != 0.0 && lng != 0.0) {
                    val pos = LatLng(lat, lng)
                    viewModel.establecerUbicacion(lat, lng, null)
                    viewModel.resolverDireccion(this, lat, lng)
                    actualizarMarcador(pos, moverCamara = true)
                } else {
                    mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
                }
            } else {
                mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
            }
        }

        mMap?.setOnMapClickListener { latLng ->
            cancelarBusquedaUbicacionEnCurso()
            actualizarMarcador(latLng, moverCamara = false)
            viewModel.resolverDireccion(this, latLng.latitude, latLng.longitude)
        }
    }

    private fun actualizarMarcador(posicion: LatLng, moverCamara: Boolean) {
        mMap?.clear()
        mMap?.addMarker(
            MarkerOptions()
                .position(posicion)
                .title(getString(R.string.reporte_ux_marcador_ubicacion))
        )
        if (moverCamara) {
            mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(posicion, 15f))
        }
    }

    private fun verificarPermisosUbicacion() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            obtenerUbicacionActual()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun cancelarBusquedaUbicacionEnCurso() {
        ctsUbicacion?.cancel()
        ctsUbicacion = null
        ubicacionJob?.cancel()
        ubicacionJob = null
        isObteniendoUbicacion = false
    }

    private fun ocultarAvisoAproximada() {
        if (esAvisoUbicacionAproximadaVisible) {
            esAvisoUbicacionAproximadaVisible = false
            ocultarAviso()
        }
    }

    private fun obtenerUbicacionActual() {
        if (isObteniendoUbicacion) return
        isObteniendoUbicacion = true

        viewModel.iniciarCargaUbicacion(R.string.reporte_ux_obteniendo_ubicacion)

        val cts = CancellationTokenSource()
        ctsUbicacion = cts

        ubicacionJob = lifecycleScope.launch {
            try {
                var mostroUbicacion = false
                val inicioMs = SystemClock.elapsedRealtime()

                val deferredLast = async(Dispatchers.IO) {
                    try {
                        val fine = ContextCompat.checkSelfPermission(this@reportar_peticion, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(this@reportar_peticion, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!fine && !coarse) return@async null

                        suspendCancellableCoroutine<Location?> { cont ->
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                                .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                val lastLoc = withTimeoutOrNull(400) { deferredLast.await() }

                if (lastLoc != null) {
                    val pos = LatLng(lastLoc.latitude, lastLoc.longitude)
                    mostroUbicacion = true
                    actualizarMarcador(pos, moverCamara = true)
                    viewModel.resolverDireccion(this@reportar_peticion, pos.latitude, pos.longitude)

                    val edadMs = System.currentTimeMillis() - lastLoc.time
                    val esRecienteYPrecisa = (edadMs < 60_000L) && (lastLoc.hasAccuracy() && lastLoc.accuracy <= 50f)
                    if (!esRecienteYPrecisa) {
                        esAvisoUbicacionAproximadaVisible = true
                        val colorMuted = ContextCompat.getColor(this@reportar_peticion, R.color.text_muted)
                        mostrarAviso(getString(R.string.reporte_ux_ubicacion_aproximada), colorMuted)
                    } else {
                        ocultarAvisoAproximada()
                    }
                }

                val tiempoConsumido = SystemClock.elapsedRealtime() - inicioMs
                val tiempoRestante = (5000L - tiempoConsumido).coerceAtLeast(1000L)

                val freshLoc = withTimeoutOrNull(tiempoRestante) {
                    try {
                        val fine = ContextCompat.checkSelfPermission(this@reportar_peticion, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(this@reportar_peticion, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!fine && !coarse) return@withTimeoutOrNull null

                        val request = CurrentLocationRequest.Builder()
                            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                            .setDurationMillis(tiempoRestante)
                            .setMaxUpdateAgeMillis(10_000)
                            .build()

                        suspendCancellableCoroutine<Location?> { cont ->
                            fusedLocationClient.getCurrentLocation(request, cts.token)
                                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                                .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                if (freshLoc != null) {
                    ocultarAvisoAproximada()
                    val pos = LatLng(freshLoc.latitude, freshLoc.longitude)
                    mostroUbicacion = true
                    actualizarMarcador(pos, moverCamara = true)
                    viewModel.resolverDireccion(this@reportar_peticion, pos.latitude, pos.longitude)
                } else if (!mostroUbicacion) {
                    ocultarAvisoAproximada()
                    isObteniendoUbicacion = false
                    viewModel.cancelarCargaUbicacion()
                    Toast.makeText(
                        this@reportar_peticion,
                        R.string.reporte_ux_error_obtener_ubicacion,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
            } finally {
                isObteniendoUbicacion = false
                cts.cancel()
                if (ctsUbicacion === cts) {
                    ctsUbicacion = null
                }
            }
        }
    }

    private fun ejecutarBusqueda() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        if (query.isNotEmpty()) {
            cancelarBusquedaUbicacionEnCurso()
            viewModel.buscarDireccion(this, query) { coords ->
                if (coords != null) {
                    actualizarMarcador(coords, moverCamara = true)
                } else {
                    Toast.makeText(this, R.string.reporte_ux_error_direccion_no_encontrada, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelarBusquedaUbicacionEnCurso()
    }

    override fun onPause() {
        super.onPause()
        if (reporteEncoladoExitoso) return
        val textoActual = binding.etSearch.text?.toString().orEmpty()
        val prefs = getSharedPreferences(prefsMapa, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(keyBusqueda, textoActual)

        val state = viewModel.uiState.value
        if (state.ubicacionSeleccionadaPorUsuario && state.latitud != null && state.longitud != null) {
            editor.putBoolean(keyHasLoc, true)
            editor.putFloat(keyLat, state.latitud.toFloat())
            editor.putFloat(keyLng, state.longitud.toFloat())
        }
        editor.apply()
    }

    @StringRes
    private fun obtenerErrorValidacionResId(state: ReporteDraftState): Int? {
        val faltaTipo = state.tipoSeleccionado == null
        val faltaUbicacion = !state.ubicacionSeleccionadaPorUsuario || state.latitud == null || state.longitud == null
        return when {
            faltaTipo && faltaUbicacion -> R.string.reporte_ux_error_falta_ambos
            faltaTipo -> R.string.reporte_ux_error_falta_tipo
            faltaUbicacion -> R.string.reporte_ux_error_falta_ubicacion
            else -> null
        }
    }

    private fun mostrarAviso(texto: CharSequence, @ColorInt color: Int) {
        val textoAnterior = binding.tvErrorValidacion.text?.toString().orEmpty()
        val estabaVisible = binding.tvErrorValidacion.visibility == View.VISIBLE
        binding.tvErrorValidacion.setTextColor(color)
        binding.tvErrorValidacion.text = texto
        binding.tvErrorValidacion.visibility = View.VISIBLE
        if (!estabaVisible || textoAnterior != texto.toString()) {
            binding.tvErrorValidacion.announceForAccessibility(texto)
        }
    }

    private fun ocultarAviso() {
        binding.tvErrorValidacion.visibility = View.GONE
    }

    private fun avanzarAlPaso2() {
        val state = viewModel.uiState.value
        val errorRes = obtenerErrorValidacionResId(state)

        if (errorRes != null) {
            esErrorValidacionVisible = true
            val colorError = ContextCompat.getColor(this, R.color.error_dark)
            mostrarAviso(getString(errorRes), colorError)
            return
        }

        esErrorValidacionVisible = false
        ocultarAviso()

        val tipo = state.tipoSeleccionado ?: return

        getSharedPreferences(prefsMapa, MODE_PRIVATE).edit().clear().apply()

        getSharedPreferences(prefsReportes, MODE_PRIVATE)
            .edit()
            .putInt(keyIdTipoSeleccionado, tipo.idTipo)
            .putString(keyTipoReporte, tipo.nombreRemoto)
            .apply()

        viewModel.avanzarPaso2()
    }

    private fun volverAlPaso1() {
        viewModel.volverPaso1()
    }

    private fun mostrarBottomSheetEvidencia(esVideo: Boolean) {
        val state = viewModel.uiState.value
        if (esVideo && !state.puedeAgregarVideo) return
        if (!esVideo && !state.puedeAgregarFoto) return

        val bottomSheet = BottomSheetDialog(this, R.style.ThemeOverlay_Vetericano_BottomSheetDialog)
        val sheetBinding = BottomSheetAdjuntarEvidenciaBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        if (esVideo) {
            sheetBinding.ivSheetHeaderIcon.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvSheetTitulo.setText(R.string.lbl_grabar_video)
            sheetBinding.tvSheetSubtitulo.setText(R.string.lbl_selecciona_el_origen_para_documentar)

            sheetBinding.ivIconCamara.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvTituloCamara.setText(R.string.lbl_grabar_video)
            sheetBinding.tvSubtituloCamara.setText(R.string.reporte_offline_sheet_video_nota)

            sheetBinding.ivIconGaleria.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvTituloGaleria.setText(R.string.lbl_video)
            sheetBinding.tvSubtituloGaleria.setText(R.string.lbl_selecciona_el_origen_para_documentar)

            sheetBinding.tvSheetInfoNota.setText(R.string.reporte_offline_sheet_video_nota)
        } else {
            sheetBinding.ivSheetHeaderIcon.setImageResource(R.drawable.camera)
            sheetBinding.tvSheetTitulo.setText(R.string.btn_adjuntar_foto)
            sheetBinding.tvSheetSubtitulo.setText(R.string.lbl_selecciona_el_origen_para_documentar)

            sheetBinding.ivIconCamara.setImageResource(R.drawable.camera)
            sheetBinding.tvTituloCamara.setText(R.string.lbl_tomar_foto_con_la_camara)
            sheetBinding.tvSubtituloCamara.setText(R.string.lbl_selecciona_el_origen_para_documentar)

            sheetBinding.ivIconGaleria.setImageResource(R.drawable.ic_photo_library)
            sheetBinding.tvTituloGaleria.setText(R.string.lbl_foto)
            sheetBinding.tvSubtituloGaleria.setText(R.string.lbl_selecciona_el_origen_para_documentar)

            sheetBinding.tvSheetInfoNota.setText(R.string.lbl_optimizacion_automatica_las_fotos_se)
        }

        sheetBinding.cardOpcionCamara.setOnClickListener {
            bottomSheet.dismiss()
            verificarPermisoYLanzar(esVideo = esVideo)
        }

        sheetBinding.cardOpcionGaleria.setOnClickListener {
            bottomSheet.dismiss()
            if (esVideo) {
                pickVideoLauncher.launch("video/*")
            } else {
                pickPhotoLauncher.launch("image/*")
            }
        }

        sheetBinding.btnCerrarSheet.setOnClickListener { bottomSheet.dismiss() }
        sheetBinding.btnCancelarSheet.setOnClickListener { bottomSheet.dismiss() }

        bottomSheet.show()
    }

    private fun verificarPermisoYLanzar(esVideo: Boolean) {
        pendingMediaEsVideo = esVideo
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (esVideo) lanzarGrabacionVideo() else lanzarCapturaFoto()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun lanzarCapturaFoto() {
        try {
            val (file, uri) = ReporteMediaHelper.crearArchivoCamaraDraft(this, esVideo = false)
            viewModel.guardarPendingCamera(file.absolutePath, uri.toString(), esVideo = false)
            takePhotoLauncher.launch(uri)
        } catch (_: Exception) {
            viewModel.limpiarPendingCamera()
        }
    }

    private fun lanzarGrabacionVideo() {
        try {
            val (file, uri) = ReporteMediaHelper.crearArchivoCamaraDraft(this, esVideo = true)
            viewModel.guardarPendingCamera(file.absolutePath, uri.toString(), esVideo = true)
            recordVideoLauncher.launch(uri)
        } catch (_: Exception) {
            viewModel.limpiarPendingCamera()
        }
    }

    private fun eliminarEvidencia(item: EvidenciaItemLocal) {
        viewModel.eliminarEvidencia(item.id)
        val file = File(item.localPath)
        val draftDir = ReporteMediaHelper.obtenerDirectorioDrafts(this)
        if (file.exists() && file.parentFile?.absolutePath == draftDir.absolutePath) {
            file.delete()
        }
        guardarMultimediaEnPrefs()
    }

    fun reproducirVideo(item: EvidenciaItemLocal) {
        try {
            val file = File(item.localPath)
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.acta_offline_reproducir_video_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDialogoErrorVideo(@StringRes tituloRes: Int, @StringRes mensajeRes: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(tituloRes)
            .setMessage(mensajeRes)
            .setPositiveButton(R.string.btn_aceptar, null)
            .show()
    }

    private fun guardarMultimediaEnPrefs() {
        val prefs = getSharedPreferences(prefsMultimedia, MODE_PRIVATE)
        val editor = prefs.edit()
        val paths = mutableListOf<String>()
        val isVideo = mutableListOf<String>()

        val state = viewModel.uiState.value
        for (item in state.evidencias) {
            val file = File(item.localPath)
            if (file.exists() && file.length() > 0L) {
                paths.add(file.absolutePath)
                isVideo.add(if (item.esVideo) "1" else "0")
            }
        }
        editor.putString(keyBorradorPaths, paths.joinToString(";"))
        editor.putString(keyBorradorIsVideo, isVideo.joinToString(";"))
        editor.apply()
    }

    private fun restaurarBorradorDesdePrefs() {
        val prefs = getSharedPreferences(prefsMultimedia, MODE_PRIVATE)
        val descGuardada = prefs.getString(keyBorradorDesc, "") ?: ""
        val pathsStr = prefs.getString(keyBorradorPaths, "") ?: ""
        val isVideoStr = prefs.getString(keyBorradorIsVideo, "") ?: ""

        val listaRecuperada = mutableListOf<EvidenciaItemLocal>()
        val pathsValidos = mutableListOf<String>()
        val isVideoValidos = mutableListOf<String>()

        if (pathsStr.isNotBlank()) {
            val paths = pathsStr.split(";")
            val isVideos = isVideoStr.split(";")
            for (i in paths.indices) {
                val path = paths[i]
                if (path.isNotBlank()) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0L) {
                        val esVid = if (i < isVideos.size) isVideos[i] == "1" else false
                        listaRecuperada.add(
                            EvidenciaItemLocal(
                                id = f.name,
                                localPath = f.absolutePath,
                                uriString = "",
                                esVideo = esVid
                            )
                        )
                        pathsValidos.add(path)
                        isVideoValidos.add(if (esVid) "1" else "0")
                    }
                }
            }
        }

        if (pathsValidos.size != (if (pathsStr.isBlank()) 0 else pathsStr.split(";").size)) {
            prefs.edit()
                .putString(keyBorradorPaths, pathsValidos.joinToString(";"))
                .putString(keyBorradorIsVideo, isVideoValidos.joinToString(";"))
                .apply()
        }

        viewModel.cargarBorradorPersistente(descGuardada, listaRecuperada)
        if (descGuardada.isNotBlank()) {
            binding.etDescripcion.setText(descGuardada)
        }
    }

    private fun validarYRevisarReporte() {
        val state = viewModel.uiState.value
        val descripcion = state.descripcion.trim()

        if (descripcion.isEmpty()) {
            binding.tilDescripcion.error = getString(R.string.reporte_offline_error_descripcion_vacia)
            binding.tilDescripcion.requestFocus()
            return
        }
        binding.tilDescripcion.error = null

        val tipo = state.tipoSeleccionado
        if (tipo == null) {
            Toast.makeText(this, R.string.reporte_ux_error_tipo_no_definido, Toast.LENGTH_SHORT).show()
            return
        }

        if (!state.ubicacionSeleccionadaPorUsuario || state.latitud == null || state.longitud == null) {
            Toast.makeText(this, R.string.reporte_ux_error_falta_ubicacion, Toast.LENGTH_SHORT).show()
            return
        }

        if (RetrofitClient.authToken.isNullOrBlank()) {
            SharedPreferencesManager(this).getAccessToken().trim().takeIf { it.isNotEmpty() }?.let {
                RetrofitClient.authToken = it
            }
        }
        viewModel.verificarDuplicadosAntesDeRevisar(hayConexionInternet(this))
    }

    private fun abrirConfirmacionReporte() {
        val existente = supportFragmentManager.findFragmentByTag(BottomSheetConfirmarReporte.TAG)
        if (existente == null && !supportFragmentManager.isStateSaved) {
            BottomSheetConfirmarReporte().show(supportFragmentManager, BottomSheetConfirmarReporte.TAG)
        }
    }

    private fun consumirEnvioExitoso(idLocal: Long) {
        if (reporteEncoladoExitoso) return
        marcarReporteEncoladoExitoso()

        val sheet = supportFragmentManager.findFragmentByTag(BottomSheetConfirmarReporte.TAG) as? BottomSheetDialogFragment
        sheet?.dismissAllowingStateLoss()

        viewModel.limpiarBorrador()

        getSharedPreferences("MisReportesPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("MapaOffline", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("ReporteOfflineMultimedia", Context.MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("ReporteOffline", Context.MODE_PRIVATE).edit().clear().apply()

        try {
            val draftDir = File(filesDir, "reporte_drafts")
            draftDir.listFiles()?.forEach { f ->
                if (f.isFile) f.delete()
            }
        } catch (_: Exception) {
        }

        val conn = hayConexionInternet(this)
        val nuevoIntent = Intent(this, reporte_enviado::class.java).apply {
            putExtra("ID_LOCAL", idLocal)
            putExtra("HAY_CONEXION", conn)
        }
        startActivity(nuevoIntent)
        finish()
    }

    private fun hayConexionInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun navegarDesdeModal(destino: DestinoEdicion) {
        when (destino) {
            DestinoEdicion.TIPO -> {
                volverAlPaso1()
                binding.containerPaso1.post {
                    binding.cardHerido.requestFocus()
                }
            }
            DestinoEdicion.UBICACION -> {
                volverAlPaso1()
                binding.containerPaso1.post {
                    binding.cvMap.requestFocus()
                }
            }
            DestinoEdicion.DESCRIPCION -> {
                binding.containerPaso1.visibility = View.GONE
                binding.bottomControlsContainer.visibility = View.GONE
                binding.containerPaso2.visibility = View.VISIBLE
                binding.etDescripcion.post {
                    binding.etDescripcion.requestFocus()
                    binding.etDescripcion.text?.let {
                        binding.etDescripcion.setSelection(it.length)
                    }
                }
            }
            DestinoEdicion.ADJUNTOS -> {
                binding.containerPaso1.visibility = View.GONE
                binding.bottomControlsContainer.visibility = View.GONE
                binding.containerPaso2.visibility = View.VISIBLE
                binding.nsvPaso2.post {
                    binding.nsvPaso2.smoothScrollTo(0, binding.layoutBotonesMultimedia.top)
                    binding.btnAdjuntarFoto.requestFocus()
                }
            }
        }
    }

    private fun cargarTipos() {
        if (isCargandoTipos) return
        isCargandoTipos = true

        lifecycleScope.launch {
            if (listaTipos.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { obtenerTiposDeCache() }
                if (cached.isNotEmpty()) {
                    listaTipos = cached
                    aplicarTipoPendienteSiExiste()
                }
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.obtenerTiposPeticion()
                }
                if (response.isSuccessful && response.body() != null) {
                    val remotos = response.body()!!
                    listaTipos = remotos
                    withContext(Dispatchers.IO) { guardarEnCache(remotos) }
                    aplicarTipoPendienteSiExiste()
                } else if (listaTipos.isEmpty()) {
                    if (tipoPendiente != null) {
                        val colorError = ContextCompat.getColor(this@reportar_peticion, R.color.error_dark)
                        mostrarAviso(getString(R.string.reporte_ux_error_red_tipos), colorError)
                    }
                }
            } catch (_: Exception) {
                if (listaTipos.isEmpty()) {
                    if (tipoPendiente != null) {
                        val colorError = ContextCompat.getColor(this@reportar_peticion, R.color.error_dark)
                        mostrarAviso(getString(R.string.reporte_ux_error_red_tipos), colorError)
                    }
                }
            } finally {
                isCargandoTipos = false
            }
        }
    }

    private fun guardarEnCache(tipos: List<TipoPeticionResponse>) {
        try {
            val jsonArray = JSONArray()
            for (item in tipos) {
                val obj = JSONObject().apply {
                    put("id_tipo", item.id_tipo)
                    put("nombre", item.nombre)
                }
                jsonArray.put(obj)
            }
            getSharedPreferences(prefsCache, MODE_PRIVATE)
                .edit()
                .putString(keyCache, jsonArray.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun obtenerTiposDeCache(): List<TipoPeticionResponse> {
        val raw = getSharedPreferences(prefsCache, MODE_PRIVATE)
            .getString(keyCache, null) ?: return emptyList()
        val list = mutableListOf<TipoPeticionResponse>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TipoPeticionResponse(
                        id_tipo = obj.getInt("id_tipo"),
                        nombre = obj.getString("nombre"),
                        descripcion = null
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
