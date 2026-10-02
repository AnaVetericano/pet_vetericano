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
import com.example.petvetericano.models.DetallePeticionResponse
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                val response = RetrofitClient.apiService.obtenerDetallePeticion("Bearer $token", id)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val detalle = response.body()!!
                        bindData(detalle)
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

    private fun bindData(detalle: DetallePeticionResponse) {
        direccionActual = detalle.solicitanteDireccion

        // Cabecera
        binding.tvCodigoPeticion.text = detalle.codigo
        binding.tvBadgeEstado.text = detalle.estado

        // Solicitante
        binding.tvNombreSolicitante.text = detalle.solicitanteNombre
        binding.tvTelefonoSolicitante.text = if (detalle.solicitanteTelefono.isNotEmpty()) detalle.solicitanteTelefono else "No registrado"
        binding.tvDireccionSolicitante.text = if (detalle.solicitanteDireccion.isNotEmpty()) detalle.solicitanteDireccion else "No registrada"
        binding.tvComunaSolicitante.text = if (detalle.solicitanteComuna.isNotEmpty()) detalle.solicitanteComuna else "N/A"

        // Animal
        binding.tvEspecieAnimal.text = if (detalle.especie.isNotEmpty()) detalle.especie else "No especificada"
        binding.tvMotivoAnimal.text = if (detalle.motivo.isNotEmpty()) detalle.motivo else "Sin motivo"
        binding.tvFechaAsignada.text = if (detalle.fechaAsignada.isNotEmpty()) detalle.fechaAsignada else "Pendiente"

        // Observaciones
        binding.tvObservaciones.text = if (detalle.observaciones.isNotEmpty()) detalle.observaciones else "Sin observaciones registradas."

        // Ubicación texto inferior
        val comunaTexto = if (detalle.solicitanteComuna.isNotEmpty()) ", com. ${detalle.solicitanteComuna}" else ""
        binding.tvUbicacionTexto.text = "${detalle.solicitanteDireccion}$comunaTexto".ifEmpty { "Ubicación en campo" }
    }
}