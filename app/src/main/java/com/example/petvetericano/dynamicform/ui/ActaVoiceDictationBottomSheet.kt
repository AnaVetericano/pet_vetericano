package com.example.petvetericano.dynamicform.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.databinding.BottomSheetActaDictadoVozBinding
import com.example.petvetericano.dynamicform.ActaFormDefinitionRegistry
import com.example.petvetericano.models.DictadoVozResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.voice.VoiceRecorderHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * BottomSheet interactivo para dictado por voz y revisión de borrador asistido por IA (Gemini).
 * Cumple con el flujo de confirmación humana obligatoria previo a impactar el formulario.
 */
class ActaVoiceDictationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetActaDictadoVozBinding? = null
    private val binding get() = _binding!!

    private lateinit var recorderHelper: VoiceRecorderHelper
    private var adapter: CamposRevisionAdapter? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private var onBorradorConfirmado: ((campos: Map<String, Any?>, confianzas: Map<String, Float?>) -> Unit)? = null

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            iniciarGrabacion()
        } else {
            Toast.makeText(requireContext(), "Se requiere permiso de micrófono para dictar.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "ActaVoiceDictationBottomSheet"

        fun newInstance(
            onConfirmado: (campos: Map<String, Any?>, confianzas: Map<String, Float?>) -> Unit
        ): ActaVoiceDictationBottomSheet {
            return ActaVoiceDictationBottomSheet().apply {
                this.onBorradorConfirmado = onConfirmado
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetActaDictadoVozBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recorderHelper = VoiceRecorderHelper(requireContext())

        setupUI()
    }

    private fun setupUI() {
        binding.btnCerrarSheet.setOnClickListener {
            cancelarYSalir()
        }

        binding.btnCancelarGrabacion.setOnClickListener {
            cancelarYSalir()
        }

        binding.cardBotonMic.setOnClickListener {
            toggleGrabacion()
        }

        binding.btnAccionGrabacion.setOnClickListener {
            toggleGrabacion()
        }

        binding.btnDescartarBorrador.setOnClickListener {
            mostrarEstadoGrabacion()
        }

        binding.btnReintentarError.setOnClickListener {
            mostrarEstadoGrabacion()
        }

        binding.btnAplicarBorrador.setOnClickListener {
            confirmarYAplicarBorrador()
        }

        mostrarEstadoGrabacion()
    }

    private fun toggleGrabacion() {
        if (recorderHelper.isRecording()) {
            detenerYProcesarGrabacion()
        } else {
            verificarPermisosYIniciar()
        }
    }

    private fun verificarPermisosYIniciar() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarGrabacion()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarGrabacion() {
        try {
            val audioFile = File(requireContext().cacheDir, "temp_dictado_acta_${System.currentTimeMillis()}.m4a")
            recorderHelper.startRecording(audioFile)

            binding.cardBotonMic.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.radix_tomato_bg))
            binding.cardBotonMic.strokeColor = ContextCompat.getColor(requireContext(), R.color.radix_tomato_solid)
            binding.ivMicPrincipal.setColorFilter(ContextCompat.getColor(requireContext(), R.color.radix_tomato_solid))
            binding.btnAccionGrabacion.text = "Detener Grabación"
            binding.btnAccionGrabacion.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.radix_tomato_solid))
            binding.tvEstadoGrabacion.text = "Grabando narración... Hable con claridad cerca del micrófono"

            iniciarTimer()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al iniciar grabación: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detenerYProcesarGrabacion() {
        detenerTimer()
        val audioFile = recorderHelper.stopRecording()

        binding.cardBotonMic.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_soft))
        binding.cardBotonMic.strokeColor = ContextCompat.getColor(requireContext(), R.color.primary)
        binding.ivMicPrincipal.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
        binding.btnAccionGrabacion.text = "Iniciar Grabación"
        binding.btnAccionGrabacion.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))

        if (audioFile == null || !audioFile.exists() || audioFile.length() < 100) {
            Toast.makeText(requireContext(), "La grabación fue muy corta o vacía.", Toast.LENGTH_SHORT).show()
            mostrarEstadoGrabacion()
            return
        }

        enviarAudioABackend(audioFile)
    }

    private fun iniciarTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (recorderHelper.isRecording()) {
                    val ms = recorderHelper.getDurationMs()
                    val totalSec = (ms / 1000).toInt()
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    binding.tvTiempoGrabacion.text = String.format("%02d:%02d", min, sec)
                    timerHandler.postDelayed(this, 500)
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun detenerTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun enviarAudioABackend(audioFile: File) {
        mostrarEstadoProcesando()

        val token = SharedPreferencesManager(requireContext()).getAccessToken()
        val tokenHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mediaType = "audio/mp4".toMediaTypeOrNull()
                val requestFile = audioFile.asRequestBody(mediaType)
                val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
                val formIdBody = ActaFormDefinitionRegistry.FORM_ID_ACTA_ATENCION.toRequestBody("text/plain".toMediaTypeOrNull())
                val formVersionBody = ActaFormDefinitionRegistry.FORM_VERSION_ACTA_ATENCION.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = RetrofitClient.apiService.procesarDictadoVoz(
                    token = tokenHeader,
                    audio = audioPart,
                    formId = formIdBody,
                    formVersion = formVersionBody
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        audioFile.delete()
                        procesarRespuestaExitosa(body)
                    } else {
                        val errorText = response.errorBody()?.string() ?: "Error en el servidor (${response.code()})"
                        mostrarEstadoError("Error en el servicio de IA", errorText)
                    }
                }
            } catch (exc: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarEstadoError("Problema de conexión", "No se pudo comunicar con el servidor: ${exc.localizedMessage}")
                }
            }
        }
    }

    private fun procesarRespuestaExitosa(response: DictadoVozResponse) {
        if (response.status == "error") {
            mostrarEstadoError("Aviso de IA", response.message ?: "El servicio de IA no pudo procesar la narración.")
            return
        }

        val camposRecibidos = response.fields ?: emptyMap()
        if (camposRecibidos.isEmpty()) {
            mostrarEstadoError("Sin datos detectados", "No se identificaron datos específicos del formulario en el audio.")
            return
        }

        // Construir lista para revisión
        val def = ActaFormDefinitionRegistry.getActaAtencionDefinition()
        val itemsRevision = mutableListOf<CampoRevisionItem>()

        for ((fieldId, campoVoice) in camposRecibidos) {
            val valor = campoVoice.value
            if (valor == null) continue

            val fieldDef = def.findField(fieldId)
            val label = fieldDef?.label ?: fieldId

            itemsRevision.add(
                CampoRevisionItem(
                    fieldId = fieldId,
                    label = label,
                    valueStr = campoVoice.asString(),
                    confidence = campoVoice.confidence,
                    isBoolean = (fieldDef?.type == com.example.petvetericano.dynamicform.FieldType.BOOLEAN ||
                            fieldDef?.type == com.example.petvetericano.dynamicform.FieldType.SWITCH)
                )
            )
        }

        if (itemsRevision.isEmpty()) {
            mostrarEstadoError("Sin datos detectados", "La narración no incluyó campos reconocibles del acta.")
            return
        }

        // Mostrar resumen
        binding.tvResumenNarracion.text = response.extractedSummary
            ?: "Se extrajeron ${itemsRevision.size} campos a partir de la narración de audio."

        adapter = CamposRevisionAdapter(itemsRevision)
        binding.rvCamposExtraidos.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCamposExtraidos.adapter = adapter

        mostrarEstadoRevision()
    }

    private fun confirmarYAplicarBorrador() {
        val adapter = adapter ?: return
        val items = adapter.getItems()

        val mapaValores = mutableMapOf<String, Any?>()
        val mapaConfianzas = mutableMapOf<String, Float?>()

        for (item in items) {
            val str = item.valueStr.trim()
            val finalVal: Any = if (item.isBoolean) {
                str.equals("Sí", ignoreCase = true) || str.equals("true", ignoreCase = true) || str.equals("1")
            } else {
                str
            }
            mapaValores[item.fieldId] = finalVal
            mapaConfianzas[item.fieldId] = item.confidence
        }

        onBorradorConfirmado?.invoke(mapaValores, mapaConfianzas)
        dismiss()
    }

    private fun mostrarEstadoGrabacion() {
        binding.layoutGrabacion.visibility = View.VISIBLE
        binding.layoutProcesando.visibility = View.GONE
        binding.layoutRevisionBorrador.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.tvTiempoGrabacion.text = "00:00"
    }

    private fun mostrarEstadoProcesando() {
        binding.layoutGrabacion.visibility = View.GONE
        binding.layoutProcesando.visibility = View.VISIBLE
        binding.layoutRevisionBorrador.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun mostrarEstadoRevision() {
        binding.layoutGrabacion.visibility = View.GONE
        binding.layoutProcesando.visibility = View.GONE
        binding.layoutRevisionBorrador.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
    }

    private fun mostrarEstadoError(titulo: String, mensaje: String) {
        binding.layoutGrabacion.visibility = View.GONE
        binding.layoutProcesando.visibility = View.GONE
        binding.layoutRevisionBorrador.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE

        binding.tvTituloError.text = titulo
        binding.tvMensajeError.text = mensaje
    }

    private fun cancelarYSalir() {
        detenerTimer()
        recorderHelper.cancelRecording()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detenerTimer()
        recorderHelper.cancelRecording()
        _binding = null
    }
}
