package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityDetallePeticionBinding
import com.example.petvetericano.models.PeticionListResponse
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DetallePeticion : AppCompatActivity() {

    private lateinit var binding: ActivityDetallePeticionBinding
    private var peticionId: Int = -1
    private var direccionActual: String = ""

    private val updateStatusLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Recargar detalle tras actualizar estado
            if (peticionId != -1) {
                obtenerDetallePeticion(peticionId)
            }
        }
    }

    private val actaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (peticionId != -1) {
                obtenerDetallePeticion(peticionId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDetallePeticionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. Recibir el ID de la petición enviada desde la lista
        peticionId = intent.getIntExtra("PETICION_ID", -1)

        // Botón de retroceso
        binding.btnBack.setOnClickListener { finish() }

        // 2. Consumir los datos desde la API si el ID es válido
        if (peticionId != -1) {
            obtenerDetallePeticion(peticionId)
        } else {
            Toast.makeText(this, "Error: ID de petición no válido", Toast.LENGTH_SHORT).show()
        }

        // Botón Actualizar Estado
        binding.btnActualizarEstado.setOnClickListener {
            val intent = Intent(this, UpdateStatusActivity::class.java).apply {
                putExtra("PETICION_ID", peticionId)
                putExtra("PETICION_CODIGO", binding.tvCodigoPeticion.text.toString())
                putExtra("ESTADO_ACTUAL", binding.tvBadgeEstado.text.toString())
            }
            updateStatusLauncher.launch(intent)
        }

        // Botón Atender (Acta de atención en campo)
        binding.btnAtender.setOnClickListener {
            val intent = Intent(this, ActaAtencionActivity::class.java).apply {
                putExtra("PETICION_ID", peticionId)
            }
            actaLauncher.launch(intent)
        }

        // Botón Ver Ruta en Mapa
        binding.btnVerRuta.setOnClickListener {
            if (direccionActual.isNotEmpty()) {
                val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(direccionActual)}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // Fallback a navegador
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(direccionActual)}"))
                    startActivity(browserIntent)
                }
            } else {
                Toast.makeText(this, "No hay dirección disponible para esta petición", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun obtenerDetallePeticion(id: Int) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // El backend NO expone un endpoint de detalle de petición
                // (GET /peticiones/{id}/ responde 404). Se reutiliza el listado
                // existente y se filtra por id de petición.
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val peticion = response.body()?.firstOrNull { it.id_peticion == id }
                        if (peticion != null) {
                            bindData(peticion)
                        } else {
                            Toast.makeText(this@DetallePeticion, "No se encontró la petición", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@DetallePeticion, "No se pudo cargar la información", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetallePeticion, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindData(peticion: PeticionListResponse) {
        // Dirección usada por el botón "Ver ruta"
        direccionActual = peticion.ubicacion_direccion.orEmpty()

        // Cabecera
        binding.tvCodigoPeticion.text = peticion.numero_radicado?.takeIf { it.isNotBlank() } ?: "N/A"
        binding.tvBadgeEstado.text = peticion.estado?.takeIf { it.isNotBlank() } ?: "Sin estado"

        // Animal: el animal aún no ha sido creado, por eso NO se muestra especie.
        // "Motivo" corresponde al tipo de petición (tabla id_tipo), ej. "Animal Herido".
        binding.tvMotivoAnimal.text = peticion.tipo?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin motivo"
        binding.tvFechaAsignada.text = formatearFecha(peticion.fecha_asignacion)

        // Observaciones (descripción de la petición)
        binding.tvObservaciones.text = peticion.descripcion?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin observaciones registradas."

        // Ubicación
        binding.tvUbicacionTexto.text = peticion.ubicacion_direccion?.trim()?.takeIf { it.isNotEmpty() } ?: "Ubicación no registrada"
    }

    private fun formatearFecha(fechaISO: String?): String {
        if (fechaISO.isNullOrBlank()) return "Pendiente"
        return try {
            // El backend envía ISO-8601, ej: 2026-10-02T01:43:01.388313-05:00
            val limpio = fechaISO.substringBefore(".").take(19)
            val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val salida = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))
            entrada.parse(limpio)?.let { salida.format(it) } ?: limpio
        } catch (e: Exception) {
            fechaISO
        }
    }
}