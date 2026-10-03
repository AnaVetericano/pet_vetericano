package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
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

        peticionId = intent.getIntExtra("PETICION_ID", -1)

        binding.btnBack.setOnClickListener { finish() }

        if (peticionId != -1) {
            obtenerDetallePeticion(peticionId)
        } else {
            Toast.makeText(this, "Error: ID de petición no válido", Toast.LENGTH_SHORT).show()
        }

        binding.btnActualizarEstado.setOnClickListener {
            val intent = Intent(this, UpdateStatusActivity::class.java).apply {
                putExtra("PETICION_ID", peticionId)
                putExtra("PETICION_CODIGO", binding.tvCodigoPeticion.text.toString())
                putExtra("ESTADO_ACTUAL", binding.tvBadgeEstado.text.toString())
            }
            updateStatusLauncher.launch(intent)
        }

        binding.btnAtender.setOnClickListener {
            val intent = Intent(this, ActaAtencionActivity::class.java).apply {
                putExtra("PETICION_ID", peticionId)
            }
            actaLauncher.launch(intent)
        }

        binding.btnVerRuta.setOnClickListener {
            if (direccionActual.isNotEmpty()) {
                val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(direccionActual)}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
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
        direccionActual = peticion.ubicacion_direccion.orEmpty()

        binding.tvCodigoPeticion.text = peticion.numero_radicado?.takeIf { it.isNotBlank() } ?: "N/A"
        binding.tvBadgeEstado.text = peticion.estado?.takeIf { it.isNotBlank() } ?: "Sin estado"
        binding.tvMotivoAnimal.text = peticion.tipo?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin motivo"
        binding.tvFechaAsignada.text = formatearFecha(peticion.fecha_asignacion)
        binding.tvObservaciones.text = peticion.descripcion?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin observaciones registradas."
        binding.tvUbicacionTexto.text = peticion.ubicacion_direccion?.trim()?.takeIf { it.isNotEmpty() } ?: "Ubicación no registrada"

        // 🖼️ GESTIÓN Y CARGA DE LA FOTO DESDE LA BASE DE DATOS
        val fotoUrl = peticion.foto
        if (!fotoUrl.isNullOrBlank()) {
            binding.cardFotoEvidencia.visibility = View.VISIBLE

            // Ajusta la URL base de tu servidor si Django devuelve rutas relativas (/media/...)
            val urlFinal = if (fotoUrl.startsWith("http")) {
                fotoUrl
            } else {
                "https://tu-servidor.com$fotoUrl"
            }

            Glide.with(this)
                .load(urlFinal)
                .into(binding.ivPeticionFoto)
        } else {
            binding.cardFotoEvidencia.visibility = View.GONE
        }
    }

    private fun formatearFecha(fechaISO: String?): String {
        if (fechaISO.isNullOrBlank()) return "Pendiente"
        return try {
            val limpio = fechaISO.substringBefore(".").take(19)
            val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val salida = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-ES"))
            entrada.parse(limpio)?.let { salida.format(it) } ?: limpio
        } catch (e: Exception) {
            fechaISO
        }
    }
}