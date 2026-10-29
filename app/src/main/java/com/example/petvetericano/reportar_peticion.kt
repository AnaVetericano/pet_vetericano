package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.petvetericano.databinding.ActivityReportarPeticionUnificadaBinding
import com.example.petvetericano.models.TipoPeticionResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.ui.reporte.ReporteCiudadanoViewModel
import com.example.petvetericano.ui.reporte.TipoReporteCategoria
import com.example.petvetericano.ui.reporte.TipoReporteItem
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

class reportar_peticion : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReportarPeticionUnificadaBinding
    private val viewModel: ReporteCiudadanoViewModel by viewModels()

    private var mMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var listaTipos: List<TipoPeticionResponse> = emptyList()

    private val prefsCache = "ReporteTiposCachePrefs"
    private val keyCache = "ReporteTiposCache"

    private val prefsMapa = "MapaOffline"
    private val keyBusqueda = "borrador_busqueda"
    private val keyLat = "borrador_lat"
    private val keyLng = "borrador_lng"
    private val keyHasLoc = "borrador_has_loc"

    private val prefsReportes = "MisReportesPrefs"
    private val keyIdTipoSeleccionado = "ID_TIPO_SELECCIONADO"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            obtenerUbicacionActual()
        } else {
            Toast.makeText(this, R.string.reporte_ux_error_permiso_ubicacion, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionUnificadaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        configurarInsets()
        configurarVistas()
        configurarMapa()
        observarEstado()
        cargarTiposDesdeApi()
    }

    private fun configurarInsets() {
        val initHeaderTop = binding.headerContainer.paddingTop
        val initBottomPadding = binding.bottomControlsContainer.paddingBottom

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
                initBottomPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun configurarVistas() {
        binding.btndev.setOnClickListener {
            startActivity(Intent(this, bienvenida::class.java))
            finish()
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
            seleccionarTipoOCatalogar("Otros")
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

        val prefs = getSharedPreferences(prefsMapa, MODE_PRIVATE)
        val textoGuardado = prefs.getString(keyBusqueda, "")
        if (!textoGuardado.isNullOrBlank()) {
            binding.etSearch.setText(textoGuardado)
        }
    }

    private fun configurarMapa() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun observarEstado() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderizarSeleccionTipo(state.tipoSeleccionado)

                    if (state.isLoadingUbicacion) {
                        binding.pbDireccion.visibility = View.VISIBLE
                        binding.tvDireccion.setText(R.string.reporte_offline_cargando_direccion)
                    } else {
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

                    if (state.puedeAvanzarPaso1) {
                        binding.tvErrorValidacion.visibility = View.GONE
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
        val strokeSelectedPx = dpToPx(3)
        val strokeDefaultPx = dpToPx(1)

        fun actualizarCard(card: MaterialCardView, seleccionado: Boolean, tituloRes: Int) {
            card.isChecked = seleccionado
            card.strokeWidth = if (seleccionado) strokeSelectedPx else strokeDefaultPx
            card.strokeColor = if (seleccionado) colorPrimary else colorStroke
            val baseTexto = getString(tituloRes)
            card.contentDescription = if (seleccionado) {
                getString(R.string.reporte_ux_cd_tipo_seleccionado, baseTexto)
            } else {
                baseTexto
            }
        }

        val cat = tipo?.categoriaVisual
        actualizarCard(binding.cardHerido, cat == TipoReporteCategoria.ANIMAL_HERIDO, R.string.lbl_animal_herido_o_en_peligro)
        actualizarCard(binding.cardMaltrato, cat == TipoReporteCategoria.MALTRATO_ANIMAL, R.string.lbl_maltrato_animal)
        actualizarCard(binding.cardCalle, cat == TipoReporteCategoria.CONDICION_CALLE, R.string.lbl_animal_en_condicion_de_calle)
        actualizarCard(binding.cardOtros, cat == TipoReporteCategoria.OTROS, R.string.lbl_otros)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.setOnMapClickListener { latLng ->
            onUbicacionElegida(latLng)
        }

        val state = viewModel.uiState.value
        if (state.ubicacionSeleccionadaPorUsuario && state.latitud != null && state.longitud != null) {
            val userLatLng = LatLng(state.latitud, state.longitud)
            actualizarMarcador(userLatLng, moverCamara = true)
        } else {
            val prefs = getSharedPreferences(prefsMapa, MODE_PRIVATE)
            val hasLoc = prefs.getBoolean(keyHasLoc, false)

            if (hasLoc) {
                val lat = prefs.getFloat(keyLat, 0f).toDouble()
                val lng = prefs.getFloat(keyLng, 0f).toDouble()
                val savedLatLng = LatLng(lat, lng)
                onUbicacionElegida(savedLatLng)
            } else {
                val bogota = LatLng(4.570868, -74.297333)
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(bogota, 11f))
            }
        }
    }

    private fun onUbicacionElegida(latLng: LatLng) {
        actualizarMarcador(latLng, moverCamara = true)
        viewModel.resolverDireccion(this, latLng.latitude, latLng.longitude)
    }

    private fun actualizarMarcador(latLng: LatLng, moverCamara: Boolean) {
        mMap?.clear()
        mMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(getString(R.string.reporte_ux_marcador_ubicacion))
        )
        if (moverCamara) {
            mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    private fun verificarPermisosUbicacion() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                obtenerUbicacionActual()
            }
            else -> {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun seleccionarTipoOCatalogar(nombreBuscado: String) {
        val seleccionado = viewModel.seleccionarTipoPorNombre(nombreBuscado, listaTipos)
        if (!seleccionado) {
            Toast.makeText(this, R.string.reporte_offline_error_cargar_tipos, Toast.LENGTH_SHORT).show()
            if (listaTipos.isEmpty()) {
                cargarTiposDesdeApi()
            }
        }
    }

    private fun obtenerUbicacionActual() {
        viewModel.iniciarCargaUbicacion()
        lifecycleScope.launch {
            var ubicacion: Location? = null
            val cts = CancellationTokenSource()
            try {
                ubicacion = withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        cont.invokeOnCancellation { cts.cancel() }
                        try {
                            fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                cts.token
                            ).addOnSuccessListener { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }.addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }.addOnCanceledListener {
                                if (cont.isActive) cont.resume(null)
                            }
                        } catch (_: SecurityException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            } catch (_: Exception) {
                ubicacion = null
            } finally {
                cts.cancel()
            }

            if (ubicacion == null) {
                ubicacion = try {
                    suspendCancellableCoroutine<Location?> { cont ->
                        try {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { loc ->
                                    if (cont.isActive) cont.resume(loc)
                                }
                                .addOnFailureListener {
                                    if (cont.isActive) cont.resume(null)
                                }
                                .addOnCanceledListener {
                                    if (cont.isActive) cont.resume(null)
                                }
                        } catch (_: SecurityException) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (ubicacion != null) {
                val currentLatLng = LatLng(ubicacion.latitude, ubicacion.longitude)
                onUbicacionElegida(currentLatLng)
            } else {
                viewModel.cancelarCargaUbicacion()
                Toast.makeText(
                    this@reportar_peticion,
                    R.string.reporte_ux_error_obtener_ubicacion,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun ejecutarBusqueda() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        if (query.isNotEmpty()) {
            viewModel.buscarDireccion(this, query) { coords ->
                if (coords != null) {
                    actualizarMarcador(coords, moverCamara = true)
                } else {
                    Toast.makeText(this, R.string.reporte_ux_error_direccion_no_encontrada, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
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

    private fun avanzarAlPaso2() {
        val state = viewModel.uiState.value
        val faltaTipo = state.tipoSeleccionado == null
        val faltaUbicacion = !state.ubicacionSeleccionadaPorUsuario || state.latitud == null || state.longitud == null

        if (faltaTipo || faltaUbicacion) {
            val errorRes = when {
                faltaTipo && faltaUbicacion -> R.string.reporte_ux_error_falta_ambos
                faltaTipo -> R.string.reporte_ux_error_falta_tipo
                else -> R.string.reporte_ux_error_falta_ubicacion
            }
            val mensaje = getString(errorRes)
            binding.tvErrorValidacion.text = mensaje
            binding.tvErrorValidacion.visibility = View.VISIBLE
            binding.tvErrorValidacion.announceForAccessibility(mensaje)
            return
        }

        binding.tvErrorValidacion.visibility = View.GONE

        val tipo = state.tipoSeleccionado ?: return

        getSharedPreferences(prefsMapa, MODE_PRIVATE).edit().clear().apply()

        getSharedPreferences(prefsReportes, MODE_PRIVATE)
            .edit()
            .putInt(keyIdTipoSeleccionado, tipo.idTipo)
            .apply()

        val puntoReferencia = binding.etSearch.text?.toString()?.trim().orEmpty()

        val intent = Intent(this, reportar_peticionnn::class.java).apply {
            putExtra("TIPO_REPORTE", tipo.nombreRemoto)
            putExtra("LATITUD", state.latitud)
            putExtra("LONGITUD", state.longitud)
            putExtra("PUNTO_REFERENCIA", puntoReferencia)
            putExtra("ID_TIPO_SELECCIONADO", tipo.idTipo)
        }
        startActivity(intent)
    }

    private fun cargarTiposDesdeApi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.obtenerTiposPeticion()
                if (response.isSuccessful && response.body() != null) {
                    val remotos = response.body()!!
                    listaTipos = remotos
                    guardarEnCache(remotos)
                } else {
                    cargarDeCacheOFallar()
                }
            } catch (_: Exception) {
                cargarDeCacheOFallar()
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

    private suspend fun cargarDeCacheOFallar() {
        val cached = obtenerTiposDeCache()
        if (cached.isNotEmpty()) {
            listaTipos = cached
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@reportar_peticion,
                    R.string.reporte_offline_error_cargar_tipos,
                    Toast.LENGTH_SHORT
                ).show()
            }
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
}
