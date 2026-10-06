package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityCorregirDireccionBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class CorregirDireccionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityCorregirDireccionBinding
    private var mMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLatLng: LatLng? = null

    // Popayán por defecto
    private val popayanDefault = LatLng(2.4419, -76.6063)

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

        binding = ActivityCorregirDireccionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar fragmento de mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Si se recibe una dirección inicial, colocarla en el buscador
        val direccionPrevia = intent.getStringExtra("DIRECCION_ACTUAL")
        if (!direccionPrevia.isNullOrEmpty()) {
            binding.etSearch.setText(direccionPrevia)
        }

        // Botón retroceder
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Botón mi ubicación
        binding.btnLocation.setOnClickListener {
            verificarPermisosUbicacion()
        }

        // Evento de búsqueda en el teclado
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                buscarDireccion()
                true
            } else {
                false
            }
        }

        // Botón Guardar Dirección
        binding.btnGuardarDireccion.setOnClickListener {
            if (selectedLatLng != null) {
                val textoDireccion = binding.etSearch.text.toString().trim()
                val direccionFinal = if (textoDireccion.isNotEmpty()) {
                    textoDireccion
                } else {
                    String.format(Locale.US, "Lat: %.6f, Lng: %.6f", selectedLatLng!!.latitude, selectedLatLng!!.longitude)
                }

                val resultIntent = Intent().apply {
                    putExtra("NUEVA_DIRECCION", direccionFinal)
                    putExtra("NUEVA_LAT", selectedLatLng!!.latitude)
                    putExtra("NUEVA_LNG", selectedLatLng!!.longitude)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "Por favor seleccione un punto en el mapa", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.uiSettings?.isZoomControlsEnabled = true
        mMap?.uiSettings?.isCompassEnabled = true

        val direccionPrevia = intent.getStringExtra("DIRECCION_ACTUAL")
        if (!direccionPrevia.isNullOrBlank()) {
            buscarDireccionInicial(direccionPrevia)
        } else {
            actualizarMarcador(popayanDefault, actualizarTexto = false)
        }

        // Selección interactiva por clic en el mapa
        mMap?.setOnMapClickListener { latLng ->
            actualizarMarcador(latLng, actualizarTexto = true)
        }
    }

    private fun actualizarMarcador(latLng: LatLng, actualizarTexto: Boolean) {
        selectedLatLng = latLng
        mMap?.clear()
        mMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Ubicación seleccionada")
        )
        mMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 16f)
        )

        if (actualizarTexto) {
            obtenerNombreDireccion(latLng)
        }
    }

    private fun obtenerNombreDireccion(latLng: LatLng) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val dir = addresses[0].getAddressLine(0)
                        binding.etSearch.setText(dir)
                    }
                }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val dir = addresses[0].getAddressLine(0)
                    binding.etSearch.setText(dir)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buscarDireccion() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            val geocoder = Geocoder(this, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query, 1) { addresses ->
                    runOnUiThread {
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            actualizarMarcador(latLng, actualizarTexto = false)
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
                        actualizarMarcador(latLng, actualizarTexto = false)
                    } else {
                        Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al buscar dirección", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buscarDireccionInicial(query: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(query, 1) { addresses ->
                runOnUiThread {
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        actualizarMarcador(latLng, actualizarTexto = false)
                    } else {
                        actualizarMarcador(popayanDefault, actualizarTexto = false)
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
                    actualizarMarcador(latLng, actualizarTexto = false)
                } else {
                    actualizarMarcador(popayanDefault, actualizarTexto = false)
                }
            } catch (e: Exception) {
                actualizarMarcador(popayanDefault, actualizarTexto = false)
            }
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

    private fun obtenerUbicacionActual() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        actualizarMarcador(currentLatLng, actualizarTexto = true)
                    } else {
                        Toast.makeText(this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
