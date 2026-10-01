package com.example.petvetericano

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityUpdateStatusBinding

class UpdateStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateStatusBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar el layout usando View Binding
        binding = ActivityUpdateStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Botón de retroceso en la cabecera
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 2. Acción del botón principal de actualizar
        binding.btnActualizarEstado.setOnClickListener {
            procesarActualizacionEstado()
        }
    }

    private fun procesarActualizacionEstado() {
        // Capturar el texto de la observación opcional
        val observacion = binding.etObservacion.text.toString().trim()

        // Identificar cuál RadioButton fue seleccionado en el RadioGroup
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

        // Simulación de éxito o punto de conexión con tu Backend (Django REST Framework)
        // Aquí enviarías 'nuevoEstado' y 'observacion' mediante tu servicio de red o API.
        Toast.makeText(this, "Estado actualizado a: $nuevoEstado", Toast.LENGTH_LONG).show()

        // Cierra la pantalla y regresa al listado o detalle
        finish()
    }
}