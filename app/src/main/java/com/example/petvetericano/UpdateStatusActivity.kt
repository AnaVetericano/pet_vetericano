package com.example.petvetericano

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityUpdateStatusBinding
import com.example.petvetericano.models.ActualizarEstadoRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateStatusBinding
    private var peticionId: Int = -1
    private var estadoActualOriginal: String = "En evaluación"
    private var selectedEstado: String = ""

    // Lista de pares (CardView, RadioButton, Estado, ColorActivo, ColorFondoActivo)
    private data class EstadoOptionItem(
        val card: MaterialCardView,
        val radio: RadioButton,
        val estadoNombre: String,
        val strokeColorActive: Int,
        val bgColorActive: Int
    )

    private val optionItems = mutableListOf<EstadoOptionItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peticionId = intent.getIntExtra("PETICION_ID", -1)
        val codigo = intent.getStringExtra("PETICION_CODIGO") ?: ""
        estadoActualOriginal = intent.getStringExtra("ESTADO_ACTUAL") ?: "En evaluación"

        if (codigo.isNotEmpty()) {
            binding.tvCasoCodigo.text = codigo
        }
        binding.tvBadgeEstadoActual.text = estadoActualOriginal
        actualizarEstiloBadgeActual(estadoActualOriginal)

        binding.ivBack.setOnClickListener {
            finish()
        }

        configurarOpcionesEstados()
        aplicarReglasDeNegocioSegunEstadoActual()

        binding.btnActualizarEstado.setOnClickListener {
            procesarActualizacionEstado()
        }
    }

    private fun configurarOpcionesEstados() {
        val colorPrimary = ContextCompat.getColor(this, R.color.blue)
        val colorPrimarySoft = ContextCompat.getColor(this, R.color.primary_soft)
        val colorAltaStroke = ContextCompat.getColor(this, R.color.badge_alta_text)
        val colorAltaBg = ContextCompat.getColor(this, R.color.badge_alta_bg)
        val colorTransferStroke = ContextCompat.getColor(this, R.color.badge_transferido_text)
        val colorTransferBg = ContextCompat.getColor(this, R.color.badge_transferido_bg)
        val colorFallecidoStroke = ContextCompat.getColor(this, R.color.error)
        val colorFallecidoBg = ContextCompat.getColor(this, R.color.badge_urgente_bg)

        optionItems.clear()
        optionItems.add(
            EstadoOptionItem(
                card = binding.cardEnObservacion,
                radio = binding.rbEnObservacion,
                estadoNombre = "En observación",
                strokeColorActive = colorPrimary,
                bgColorActive = colorPrimarySoft
            )
        )
        optionItems.add(
            EstadoOptionItem(
                card = binding.cardEnTratamiento,
                radio = binding.rbEnTratamiento,
                estadoNombre = "En tratamiento",
                strokeColorActive = colorPrimary,
                bgColorActive = colorPrimarySoft
            )
        )
        optionItems.add(
            EstadoOptionItem(
                card = binding.cardAltaMedica,
                radio = binding.rbAltaMedica,
                estadoNombre = "Alta médica",
                strokeColorActive = colorAltaStroke,
                bgColorActive = colorAltaBg
            )
        )
        optionItems.add(
            EstadoOptionItem(
                card = binding.cardTransferido,
                radio = binding.rbTransferido,
                estadoNombre = "Transferido a otro centro",
                strokeColorActive = colorTransferStroke,
                bgColorActive = colorTransferBg
            )
        )
        optionItems.add(
            EstadoOptionItem(
                card = binding.cardFallecido,
                radio = binding.rbFallecido,
                estadoNombre = "Fallecido",
                strokeColorActive = colorFallecidoStroke,
                bgColorActive = colorFallecidoBg
            )
        )

        // Registrar click listeners únicos por tarjeta (100% exclusión mutua)
        for (item in optionItems) {
            val listener = View.OnClickListener {
                if (item.card.isEnabled) {
                    seleccionarEstado(item.estadoNombre)
                }
            }
            item.card.setOnClickListener(listener)
            item.radio.setOnClickListener(listener)
        }
    }

    private fun seleccionarEstado(nuevoEstado: String) {
        selectedEstado = nuevoEstado

        val colorStrokeDefault = ContextCompat.getColor(this, R.color.card_stroke)
        val colorWhite = ContextCompat.getColor(this, R.color.white)
        val density = resources.displayMetrics.density

        for (item in optionItems) {
            val isSelected = item.estadoNombre.equals(nuevoEstado, ignoreCase = true)
            item.radio.isChecked = isSelected

            if (isSelected) {
                item.card.strokeColor = item.strokeColorActive
                item.card.strokeWidth = (2 * density).toInt()
                item.card.setCardBackgroundColor(item.bgColorActive)
            } else {
                item.card.strokeColor = colorStrokeDefault
                item.card.strokeWidth = (1 * density).toInt()
                item.card.setCardBackgroundColor(colorWhite)
            }
        }

        actualizarEtiquetasObservacion(nuevoEstado)
    }

    private fun actualizarEtiquetasObservacion(estado: String) {
        val estNorm = estado.trim().lowercase()
        when {
            estNorm.contains("fallecid") -> {
                binding.tvTituloObservacion.text = "Causa del deceso * (Obligatoria)"
                binding.tvHelperObservacion.text = "Especifica causa clínica, hallazgos y circunstancias del deceso."
                binding.etObservacion.hint = "Ingresa la causa clínica detallada del fallecimiento..."
            }
            estNorm.contains("transferid") -> {
                binding.tvTituloObservacion.text = "Centro de destino y motivo * (Obligatorio)"
                binding.tvHelperObservacion.text = "Especifica el centro receptor, especialista o motivo de remisión."
                binding.etObservacion.hint = "Ej: Remitido a Clínica Veterinaria Central por requerir UCI y cirujano..."
            }
            estNorm.contains("alta") -> {
                binding.tvTituloObservacion.text = "Indicaciones de alta médica (Opcional)"
                binding.tvHelperObservacion.text = "Condición de entrega y recomendaciones de cuidado posterior."
                binding.etObservacion.hint = "Recomendaciones posteriores o indicaciones al cuidador..."
            }
            else -> {
                binding.tvTituloObservacion.text = "Observación clínica (Opcional)"
                binding.tvHelperObservacion.text = "Describe el motivo o detalles del cambio de evolución médica."
                binding.etObservacion.hint = "Describe los motivos del cambio de estado..."
            }
        }
    }

    private fun aplicarReglasDeNegocioSegunEstadoActual() {
        val estadoNorm = estadoActualOriginal.trim().lowercase()

        val isCasoCerrado = estadoNorm.contains("alta") ||
                estadoNorm.contains("fallecid") ||
                estadoNorm.contains("transferid")

        if (isCasoCerrado) {
            // El caso ya está cerrado o terminal
            binding.layoutAvisoCerrado.visibility = View.VISIBLE
            binding.tvMensajeCerrado.text = "Este caso ya se encuentra finalizado como '$estadoActualOriginal'. No se permiten nuevas modificaciones para garantizar la trazabilidad médica y legal."
            binding.layoutContenedorOpciones.visibility = View.GONE
            binding.btnActualizarEstado.isEnabled = false
            binding.btnActualizarEstado.text = "Caso finalizado"
            binding.btnActualizarEstado.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_muted))
            return
        }

        // Si el estado es "En evaluación" o "Pendiente"
        if (estadoNorm.contains("evaluac") || estadoNorm.contains("pendient")) {
            binding.layoutTipEvaluacion.visibility = View.VISIBLE

            // Restricción: No se puede dar Alta médica directamente desde En evaluación
            binding.cardAltaMedica.isEnabled = false
            binding.cardAltaMedica.alpha = 0.45f
            binding.tvDescAltaMedica.text = "No disponible: Requiere atención o tratamiento clínico previo."

            // Sugerir opción por defecto lógica (ej. En observación)
            seleccionarEstado("En observación")
            return
        }

        // Si ya está en tratamiento u observación
        if (estadoNorm.contains("tratamient")) {
            seleccionarEstado("En tratamiento")
        } else if (estadoNorm.contains("observac")) {
            seleccionarEstado("En observación")
        } else {
            seleccionarEstado("En tratamiento")
        }
    }

    private fun procesarActualizacionEstado() {
        if (peticionId == -1) {
            Toast.makeText(this, "ID de petición inválido", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedEstado.isEmpty()) {
            Toast.makeText(this, "Por favor selecciona un nuevo estado", Toast.LENGTH_SHORT).show()
            return
        }

        val observacion = binding.etObservacion.text.toString().trim()
        val estadoNorm = selectedEstado.lowercase()

        // Validaciones obligatorias de campos según el estado elegido
        if (estadoNorm.contains("fallecid") && observacion.length < 8) {
            Toast.makeText(this, "Debes ingresar la causa del deceso en las observaciones", Toast.LENGTH_LONG).show()
            binding.etObservacion.requestFocus()
            return
        }

        if (estadoNorm.contains("transferid") && observacion.length < 8) {
            Toast.makeText(this, "Debes especificar el centro de destino y el motivo del traslado", Toast.LENGTH_LONG).show()
            binding.etObservacion.requestFocus()
            return
        }

        // Para estados críticos o finales, requerir confirmación explícita
        when {
            estadoNorm.contains("fallecid") -> {
                mostrarDialogoConfirmacion(
                    titulo = "⚠️ Confirmar reporte de fallecimiento",
                    mensaje = "Estás a punto de registrar al paciente como FALLECIDO.\n\nEsta acción es irreversible y finalizará definitivamente el caso clínico. ¿Deseas continuar?",
                    botonPositivo = "Confirmar fallecimiento"
                ) {
                    enviarActualizacionAlServidor(selectedEstado, observacion)
                }
            }
            estadoNorm.contains("alta") -> {
                mostrarDialogoConfirmacion(
                    titulo = "Confirmar alta médica",
                    mensaje = "¿Confirmas que el paciente se encuentra recuperado y listo para recibir el alta médica?",
                    botonPositivo = "Confirmar alta"
                ) {
                    enviarActualizacionAlServidor(selectedEstado, observacion)
                }
            }
            estadoNorm.contains("transferid") -> {
                mostrarDialogoConfirmacion(
                    titulo = "Confirmar traslado a otro centro",
                    mensaje = "¿Confirmas la derivación y traslado del paciente al centro indicado en las observaciones?",
                    botonPositivo = "Confirmar traslado"
                ) {
                    enviarActualizacionAlServidor(selectedEstado, observacion)
                }
            }
            else -> {
                // Evolución ordinaria
                enviarActualizacionAlServidor(selectedEstado, observacion)
            }
        }
    }

    private fun mostrarDialogoConfirmacion(
        titulo: String,
        mensaje: String,
        botonPositivo: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(botonPositivo) { dialog, _ ->
                dialog.dismiss()
                onConfirm()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun enviarActualizacionAlServidor(nuevoEstado: String, observacion: String) {
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
                        val errorBody = response.errorBody()?.string() ?: ""
                        val mensajeError = try {
                            val json = org.json.JSONObject(errorBody)
                            json.optString("error", json.optString("detail", "Error al actualizar estado: ${response.code()}"))
                        } catch (_: Exception) {
                            "Error al actualizar estado: ${response.code()}"
                        }
                        Toast.makeText(this@UpdateStatusActivity, mensajeError, Toast.LENGTH_LONG).show()
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

    private fun actualizarEstiloBadgeActual(estado: String) {
        val st = estado.lowercase()
        val (colorTexto, colorFondo) = when {
            st.contains("fallecido") -> {
                Color.parseColor("#B91C1C") to Color.parseColor("#FEE2E2")
            }
            st.contains("evaluac") || st.contains("pendient") -> {
                Color.parseColor("#B45309") to Color.parseColor("#FEF3C7")
            }
            st.contains("tratamient") -> {
                Color.parseColor("#1D4ED8") to Color.parseColor("#DBEAFE")
            }
            st.contains("observac") -> {
                Color.parseColor("#0F766E") to Color.parseColor("#CCFBF1")
            }
            st.contains("alta") -> {
                Color.parseColor("#047857") to Color.parseColor("#D1FAE5")
            }
            st.contains("transferid") -> {
                Color.parseColor("#6D28D9") to Color.parseColor("#EDE9FE")
            }
            else -> {
                Color.parseColor("#4B5563") to Color.parseColor("#F3F4F6")
            }
        }

        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 8
            setColor(colorFondo)
        }

        binding.tvBadgeEstadoActual.background = backgroundDrawable
        binding.tvBadgeEstadoActual.setTextColor(colorTexto)
    }
}