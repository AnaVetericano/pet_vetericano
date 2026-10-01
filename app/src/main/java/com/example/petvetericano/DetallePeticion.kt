package com.example.petvetericano

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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

        // Botones de acción inferiores
        binding.btnActualizarEstado.setOnClickListener {
            // TODO: Lógica para actualizar estado
        }

        binding.btnAtender.setOnClickListener {
            // Iniciar actividad de Acta de Atención
            val intent = android.content.Intent(this, ActaAtencionActivity::class.java)
            intent.putExtra("PETICION_ID", peticionId)
            startActivity(intent)
        }

        binding.btnVerRuta.setOnClickListener {
            // TODO: Lógica para abrir mapa
        }
    }

    private fun obtenerDetallePeticion(id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.obtenerDetallePeticion(id)

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
        // Cabecera
        binding.tvCodigoPeticion.text = detalle.codigo
        binding.tvBadgeEstado.text = detalle.estado

        // Solicitante
        binding.tvNombreSolicitante.text = detalle.solicitanteNombre
        binding.tvTelefonoSolicitante.text = detalle.solicitanteTelefono
        binding.tvDireccionSolicitante.text = detalle.solicitanteDireccion
        binding.tvComunaSolicitante.text = detalle.solicitanteComuna

        // Animal
        binding.tvEspecieAnimal.text = detalle.especie
        binding.tvMotivoAnimal.text = detalle.motivo
        binding.tvFechaAsignada.text = detalle.fechaAsignada

        // Observaciones
        binding.tvObservaciones.text = detalle.observaciones

        // Ubicación texto inferior
        binding.tvUbicacionTexto.text = "${detalle.solicitanteDireccion}, com. ${detalle.solicitanteComuna}"
    }
}