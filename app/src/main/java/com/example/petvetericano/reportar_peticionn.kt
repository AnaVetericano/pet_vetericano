package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
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
        // Usamos intent.getStringExtra para obtener el texto que nos mandó la tarjeta.
        // Si no usamos esto, el dato "Herido", "Maltrato" o "En calle" se pierde para siempre en esta pantalla.
        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment

        mapFragment.getMapAsync(this)

        binding.btnBack.setOnClickListener {
            // OJO: Si usas Intent para volver atrás, creas una nueva instancia de la pantalla anterior
            // apilando pantallas infinitamente en la memoria.
            // Lo ideal aquí sería usar finish() en lugar de un Intent, pero respeto tu lógica original.
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.btnLocation.setOnClickListener {
            verificarPermisosUbicacion()
        }

        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            buscarDireccion()
            true // El true indica que el evento del teclado fue consumido y no debe propagarse. Si no está, el teclado podría comportarse erráticamente.
        }

        binding.btnContinue.setOnClickListener {
            if (selectedLatLng != null) {
                // MODIFICACIÓN CLAVE: Configuramos el Intent hacia tu TERCERA pantalla.
                // Usamos .apply para poder inyectar múltiples extras sin repetir el nombre de la variable.
                val intent = Intent(this, reportar_peticionnn::class.java).apply {

                    // 1. RE-EMPACAMOS EL TIPO DE REPORTE
                    // Si omites esto, tu pantalla final de confirmación nunca sabrá qué animal se está reportando.
                    putExtra("TIPO_REPORTE", tipoReporte)

                    // 2. EMPACAMOS LAS COORDENADAS
                    // selectedLatLng tiene latitude y longitude (ambos son Double).
                    // Si el usuario marcó el mapa, enviamos esos números exactos para guardarlos en la base de datos o mostrarlos luego.
                    putExtra("LATITUD", selectedLatLng?.latitude)
                    putExtra("LONGITUD", selectedLatLng?.longitude)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Por favor selecciona una ubicación en el mapa", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val defaultLocation = LatLng(4.570868, -74.297333)
        actualizarMarcador(defaultLocation)

        mMap?.setOnMapClickListener { latLng ->
            actualizarMarcador(latLng)
        }
    }

    private fun actualizarMarcador(latLng: LatLng) {
        selectedLatLng = latLng
        mMap?.clear() // Limpia marcadores anteriores. Si no se usa, el mapa se llenaría de pines infinitos con cada clic.
        mMap?.addMarker(
            MarkerOptions().position(latLng).title("Ubicación seleccionada")
        )
        mMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                latLng,
                16f // 16f es el nivel de zoom. Sin esto, la cámara podría quedar a nivel global y no se vería la calle.
            )
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
        // .trim() elimina espacios en blanco al inicio y al final.
        // Si el usuario escribe "  Calle 123  ", .trim() lo convierte en "Calle 123".
        // Si no se usa, el Geocoder podría fallar al procesar espacios invisibles.
        val query = binding.etSearch.text.toString().trim()

        if (query.isNotEmpty()) {
            val geocoder = Geocoder(
                this,
                Locale.getDefault()
            )
            try {
                // Busca la dirección y devuelve máximo 1 resultado (por el parámetro '1').
                val addresses = geocoder.getFromLocationName(query, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val latLng = LatLng(
                        address.latitude,
                        address.longitude
                    )
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