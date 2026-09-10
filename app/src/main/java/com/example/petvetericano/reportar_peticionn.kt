package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionnBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class reportar_peticionn : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityReportarPeticionnBinding
    private var mMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLatLng: LatLng? = null

    // Nombres para la libreta offline del mapa y las coordenadas
    private val PREFS_MAPA = "MapaOffline"
    private val KEY_BUSQUEDA = "borrador_busqueda"
    private val KEY_LAT = "borrador_lat"
    private val KEY_LNG = "borrador_lng"
    private val KEY_HAS_LOC = "borrador_has_loc"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            obtenerUbicacionActual()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionnBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ATRAPAMOS EL DATO DEL RELEVO
        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment

        mapFragment.getMapAsync(this)

        // RECUPERAR TEXTO DE LA BÚSQUEDA SI EXISTE
        val prefs = getSharedPreferences(PREFS_MAPA, MODE_PRIVATE)
        val textoGuardado = prefs.getString(KEY_BUSQUEDA, "")
        if (!textoGuardado.isNullOrEmpty()) {
            binding.etSearch.setText(textoGuardado)
        }

        binding.btnBack.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.btnLocation.setOnClickListener {
            verificarPermisosUbicacion()
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {

                buscarDireccion()
                true
            } else {
                false
            }
        }

        binding.btnContinue.setOnClickListener {
            if (selectedLatLng != null) {
                val referenciaText = binding.etSearch.text.toString().trim()

                // Limpiamos el borrador del mapa al avanzar con éxito
                getSharedPreferences(PREFS_MAPA, MODE_PRIVATE).edit().clear().apply()

                val intent = Intent(this, reportar_peticionnn::class.java).apply {
                    putExtra("TIPO_REPORTE", tipoReporte)
                    putExtra("LATITUD", selectedLatLng?.latitude)
                    putExtra("LONGITUD", selectedLatLng?.longitude)
                    putExtra("PUNTO_REFERENCIA", referenciaText)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Por favor selecciona una ubicación en el mapa", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // GUARDAR TEXTO Y COORDENADAS AUTOMÁTICAMENTE AL PAUSAR LA APP
    override fun onPause() {
        super.onPause()
        val textoActual = binding.etSearch.text.toString()
        val prefs = getSharedPreferences(PREFS_MAPA, MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString(KEY_BUSQUEDA, textoActual)

        // Guardamos las coordenadas si el usuario ya marcó un punto
        selectedLatLng?.let {
            editor.putBoolean(KEY_HAS_LOC, true)
            editor.putFloat(KEY_LAT, it.latitude.toFloat())
            editor.putFloat(KEY_LNG, it.longitude.toFloat())
        }
        editor.apply()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // REVISAR SI TENEMOS UNA UBICACIÓN GUARDADA EN LA CACHÉ
        val prefs = getSharedPreferences(PREFS_MAPA, MODE_PRIVATE)
        val hasLoc = prefs.getBoolean(KEY_HAS_LOC, false)

        if (hasLoc) {
            val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
            val lng = prefs.getFloat(KEY_LNG, 0f).toDouble()
            val savedLatLng = LatLng(lat, lng)
            actualizarMarcador(savedLatLng)
        } else {
            val defaultLocation = LatLng(4.570868, -74.297333)
            actualizarMarcador(defaultLocation)
        }

        mMap?.setOnMapClickListener { latLng ->
            actualizarMarcador(latLng)
        }
    }

    private fun actualizarMarcador(latLng: LatLng) {
        selectedLatLng = latLng
        mMap?.clear()
        mMap?.addMarker(
            MarkerOptions().position(latLng).title("Ubicación seleccionada")
        )
        mMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 16f)
        )
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
                locationPermissionRequest.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }

    private fun obtenerUbicacionActual() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(
                            location.latitude,
                            location.longitude
                        )
                        actualizarMarcador(currentLatLng)
                    } else {
                        Toast.makeText(this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun buscarDireccion() {
        val query = binding.etSearch.text.toString().trim()

        if (query.isNotEmpty()) {
            val geocoder = Geocoder(this, Locale.getDefault())

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query, 1) { addresses ->
                    runOnUiThread {
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            actualizarMarcador(latLng)
                        } else {
                            Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        actualizarMarcador(latLng)
                    } else {
                        Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al buscar dirección", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

//este es el mapa