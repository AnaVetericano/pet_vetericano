package com.example.petvetericano

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityUpdateStatusBinding
import com.example.petvetericano.models.ActualizarEstadoRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateStatusBinding
    private var peticionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peticionId = intent.getIntExtra("PETICION_ID", -1)
        val codigo = intent.getStringExtra("PETICION_CODIGO") ?: ""
        val estadoActual = intent.getStringExtra("ESTADO_ACTUAL") ?: "En evaluación"

        if (codigo.isNotEmpty()) {
            binding.tvCasoCodigo.text = codigo
        }
        binding.tvBadgeEstadoActual.text = estadoActual

        // Preseleccionar según estado actual
        when (estadoActual.lowercase()) {
            "en evaluación", "en evaluacion" -> binding.rbEnEvaluacion.isChecked = true
            "en tratamiento" -> binding.rbEnTratamiento.isChecked = true
            "en observación", "en observacion" -> binding.rbEnObservacion.isChecked = true
            "alta médica", "alta medica" -> binding.rbAltaMedica.isChecked = true
            "fallecido" -> binding.rbFallecido.isChecked = true
            "transferido a otro centro" -> binding.rbTransferido.isChecked = true
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnActualizarEstado.setOnClickListener {
            procesarActualizacionEstado()
        }
    }

    private fun procesarActualizacionEstado() {
        if (peticionId == -1) {
            Toast.makeText(this, "ID de petición inválido", Toast.LENGTH_SHORT).show()
            return
        }

        val observacion = binding.etObservacion.text.toString().trim()
        val estadoSeleccionadoId = binding.rgEstados.checkedRadioButtonId

        val nuevoEstado = when (estadoSeleccionadoId) {
            binding.rbEnEvaluacion.id -> "En evaluación"
            binding.rbEnTratamiento.id -> "En tratamiento"
            binding.rbEnObservacion.id -> "En observación"
            binding.rbAltaMedica.id -> "Alta médica"
            binding.rbFallecido.id -> "Fallecido"
            binding.rbTransferido.id -> "Transferido a otro centro"
            else -> ""
        }

        if (nuevoEstado.isEmpty()) {
            Toast.makeText(this, "Por favor selecciona un nuevo estado", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

        if (token.isEmpty()) {
            Toast.makeText(this, "Debe iniciar sesión para continuar", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnActualizarEstado.isEnabled = false
        binding.btnActualizarEstado.text = "Actualizando..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = ActualizarEstadoRequest(
                    estado = nuevoEstado,
                    observacion = if (observacion.isNotEmpty()) observacion else null
                )
                val response = RetrofitClient.apiService.actualizarEstadoPeticion(
                    token = "Bearer $token",
                    id = peticionId,
                    request = request
                )

                withContext(Dispatchers.Main) {
                    binding.btnActualizarEstado.isEnabled = true
                    binding.btnActualizarEstado.text = "Actualizar estado"

                    if (response.isSuccessful) {
                        Toast.makeText(this@UpdateStatusActivity, "Estado actualizado a: $nuevoEstado", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@UpdateStatusActivity, "Error al actualizar estado: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnActualizarEstado.isEnabled = true
                    binding.btnActualizarEstado.text = "Actualizar estado"
                    Toast.makeText(this@UpdateStatusActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}