package com.example.petvetericano.dynamicform.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.databinding.BottomSheetActaDictadoVozBinding
import com.example.petvetericano.dynamicform.ActaFormDefinitionRegistry
import com.example.petvetericano.dynamicform.FieldType
import com.example.petvetericano.models.DictadoVozResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.voice.RecordingStopResult
import com.example.petvetericano.voice.VoiceRecorderHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * BottomSheet interactivo para dictado por voz y revisión de borrador asistido por IA (Gemini).
 * Sobrevive a recreación de configuración, comunica resultados vía Fragment Result API
 * y exige confirmación humana antes de aplicar o sobrescribir valores en el acta.
 */
class ActaVoiceDictationBottomSheet : BottomSheetDialogFragment() {

    private enum class UiState {
        RECORDING,
        PROCESSING,
        REVIEW,
        ERROR
    }

    private var _binding: BottomSheetActaDictadoVozBinding? = null
    private val binding get() = _binding!!

    private lateinit var recorderHelper: VoiceRecorderHelper
    private var adapter: CamposRevisionAdapter? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var processingJob: Job? = null
    private var activeTempAudioFile: File? = null

    private var currentUiState: UiState = UiState.RECORDING
    private var currentResponse: DictadoVozResponse? = null
    private var currentRevisionItems: ArrayList<CampoRevisionItem> = arrayListOf()
    private var currentErrorTitle: String = ""
    private var currentErrorMessage: String = ""
    private var valoresActuales: HashMap<String, String> = hashMapOf()
    private var isTransitioningAction = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        marcarPermisoSolicitado()
        if (isGranted) {
            iniciarGrabacion()
        } else {
            manejarPermisoDenegado()
        }
    }

    companion object {
        const val TAG = "ActaVoiceDictationBottomSheet"
        const val REQUEST_KEY = "dictado_voz_acta_result"
        const val RESULT_CAMPOS = "campos"
        const val RESULT_CONFIANZAS = "confianzas"
        const val RESULT_SOBRESCRIBIR = "sobrescribir"

        private const val ARG_VALORES_ACTUALES = "arg_valores_actuales"
        private const val KEY_UI_STATE = "state_ui"
        private const val KEY_RESPONSE = "state_response"
        private const val KEY_ITEMS = "state_items"
        private const val KEY_ERROR_TITLE = "state_error_title"
        private const val KEY_ERROR_MSG = "state_error_msg"
        private const val PREFS_DICTADO = "DictadoVozPrefs"
        private const val KEY_MIC_REQUESTED = "mic_permission_requested"

        fun newInstance(
            valoresActuales: HashMap<String, String> = hashMapOf()
        ): ActaVoiceDictationBottomSheet {
            return ActaVoiceDictationBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_VALORES_ACTUALES, valoresActuales)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val argMap = arguments?.getSerializable(ARG_VALORES_ACTUALES) as? HashMap<String, String>
        if (argMap != null) {
            valoresActuales = argMap
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.widget.FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        return dialog
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
        recorderHelper = VoiceRecorderHelper(requireContext().applicationContext)

        setupUI()
        restaurarEstado(savedInstanceState)
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

        binding.btnCancelarProcesamiento.setOnClickListener {
            cancelarProcesamientoYVolverAGrabar()
        }

        binding.btnDescartarBorrador.setOnClickListener {
            mostrarEstadoGrabacion()
        }

        binding.btnContinuarManualRevision.setOnClickListener {
            cancelarYSalir()
        }

        binding.btnReintentarError.setOnClickListener {
            mostrarEstadoGrabacion()
        }

        binding.btnContinuarManualError.setOnClickListener {
            cancelarYSalir()
        }

        binding.btnAplicarBorrador.setOnClickListener {
            confirmarYAplicarBorrador()
        }
    }

    private fun restaurarEstado(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            mostrarEstadoGrabacion()
            return
        }

        val stateName = savedInstanceState.getString(KEY_UI_STATE, UiState.RECORDING.name)
        val restoredState = try {
            UiState.valueOf(stateName)
        } catch (_: IllegalArgumentException) {
            UiState.RECORDING
        }

        @Suppress("DEPRECATION")
        currentResponse = savedInstanceState.getSerializable(KEY_RESPONSE) as? DictadoVozResponse
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val savedItems = savedInstanceState.getSerializable(KEY_ITEMS) as? ArrayList<CampoRevisionItem>
        if (savedItems != null) {
            currentRevisionItems = savedItems
        }
        currentErrorTitle = savedInstanceState.getString(KEY_ERROR_TITLE, "")
        currentErrorMessage = savedInstanceState.getString(KEY_ERROR_MSG, "")

        when (restoredState) {
            UiState.REVIEW -> {
                if (currentRevisionItems.isNotEmpty()) {
                    renderizarRevision(currentResponse, currentRevisionItems)
                } else {
                    mostrarEstadoGrabacion()
                }
            }
            UiState.ERROR -> {
                if (currentErrorTitle.isNotBlank() && currentErrorMessage.isNotBlank()) {
                    mostrarEstadoError(currentErrorTitle, currentErrorMessage)
                } else {
                    mostrarEstadoGrabacion()
                }
            }
            UiState.PROCESSING, UiState.RECORDING -> {
                mostrarEstadoGrabacion()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val stateToSave = if (currentUiState == UiState.PROCESSING) UiState.RECORDING else currentUiState
        outState.putString(KEY_UI_STATE, stateToSave.name)
        outState.putSerializable(KEY_RESPONSE, currentResponse)
        outState.putSerializable(KEY_ITEMS, currentRevisionItems)
        outState.putString(KEY_ERROR_TITLE, currentErrorTitle)
        outState.putString(KEY_ERROR_MSG, currentErrorMessage)
    }

    private fun toggleGrabacion() {
        if (isTransitioningAction || currentUiState == UiState.PROCESSING) return
        if (recorderHelper.isRecording()) {
            detenerYProcesarGrabacion()
        } else {
            verificarPermisosYIniciar()
        }
    }

    private fun verificarPermisosYIniciar() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarGrabacion()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun marcarPermisoSolicitado() {
        requireContext()
            .getSharedPreferences(PREFS_DICTADO, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MIC_REQUESTED, true)
            .apply()
    }

    private fun fuePermisoSolicitadoAntes(): Boolean {
        return requireContext()
            .getSharedPreferences(PREFS_DICTADO, Context.MODE_PRIVATE)
            .getBoolean(KEY_MIC_REQUESTED, false)
    }

    private fun manejarPermisoDenegado() {
        if (!isAdded) return
        val puedeMostrarExplicacion = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        val denegadoPermanentemente = !puedeMostrarExplicacion && fuePermisoSolicitadoAntes()

        if (denegadoPermanentemente) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reporte_ux_dictado_permiso_titulo)
                .setMessage(R.string.reporte_ux_dictado_permiso_permanente_mensaje)
                .setPositiveButton(R.string.reporte_ux_dictado_btn_ir_ajustes) { _, _ ->
                    abrirAjustesDeLaApp()
                }
                .setNegativeButton(R.string.reporte_ux_dictado_btn_continuar_manual) { _, _ ->
                    dismiss()
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reporte_ux_dictado_permiso_titulo)
                .setMessage(R.string.reporte_ux_dictado_permiso_mensaje)
                .setPositiveButton(R.string.reporte_ux_dictado_btn_reintentar) { _, _ ->
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setNegativeButton(R.string.reporte_ux_dictado_btn_continuar_manual) { _, _ ->
                    dismiss()
                }
                .show()
        }
    }

    private fun abrirAjustesDeLaApp() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (_: Exception) {
            mostrarEstadoError(
                getString(R.string.reporte_ux_dictado_permiso_titulo),
                getString(R.string.reporte_ux_dictado_permiso_permanente_mensaje)
            )
        }
    }

    private fun iniciarGrabacion() {
        if (isTransitioningAction) return
        isTransitioningAction = true
        try {
            val audioFile = File(requireContext().cacheDir, "temp_dictado_acta_${System.currentTimeMillis()}.m4a")
            activeTempAudioFile = audioFile
            recorderHelper.startRecording(audioFile)

            aplicarUiGrabando()
            iniciarTimer()
        } catch (_: Exception) {
            limpiarArchivoTemporal()
            restaurarUiGrabacionIdle()
            mostrarEstadoError(
                getString(R.string.reporte_ux_dictado_error_mic_titulo),
                getString(R.string.reporte_ux_dictado_error_mic_msg)
            )
        } finally {
            isTransitioningAction = false
        }
    }

    private fun detenerYProcesarGrabacion(autoStoppedByMaxLimit: Boolean = false) {
        if (isTransitioningAction) return
        isTransitioningAction = true
        try {
            detenerTimer()
            detenerAnimacionPulso()
            restaurarUiGrabacionIdle()

            when (val result = recorderHelper.stopRecording()) {
                is RecordingStopResult.Success -> {
                    activeTempAudioFile = result.file
                    if (autoStoppedByMaxLimit && _binding != null) {
                        binding.tvMensajeProcesando.text = getString(R.string.reporte_ux_dictado_auto_detenido)
                    } else if (_binding != null) {
                        binding.tvMensajeProcesando.text = getString(R.string.reporte_ux_dictado_procesando_titulo)
                    }
                    enviarAudioABackend(result.file)
                }
                is RecordingStopResult.TooShort -> {
                    activeTempAudioFile = null
                    mostrarEstadoError(
                        getString(R.string.reporte_ux_dictado_error_corto_titulo),
                        getString(R.string.reporte_ux_dictado_error_corto_msg)
                    )
                }
                is RecordingStopResult.SilenceDetected -> {
                    activeTempAudioFile = null
                    mostrarEstadoError(
                        getString(R.string.reporte_ux_dictado_error_silencio_titulo),
                        getString(R.string.reporte_ux_dictado_error_silencio_msg)
                    )
                }
                is RecordingStopResult.CorruptOrFailed -> {
                    activeTempAudioFile = null
                    mostrarEstadoError(
                        getString(R.string.reporte_ux_dictado_error_corrupto_titulo),
                        getString(R.string.reporte_ux_dictado_error_corrupto_msg)
                    )
                }
                is RecordingStopResult.NotRecording -> {
                    activeTempAudioFile = null
                }
            }
        } finally {
            isTransitioningAction = false
        }
    }

    private fun aplicarUiGrabando() {
        val b = _binding ?: return
        val ctx = requireContext()
        b.cardBotonMic.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.radix_tomato_bg))
        b.cardBotonMic.strokeColor = ContextCompat.getColor(ctx, R.color.radix_tomato_solid)
        b.ivMicPrincipal.setColorFilter(ContextCompat.getColor(ctx, R.color.radix_tomato_solid))
        b.btnAccionGrabacion.text = getString(R.string.reporte_ux_dictado_btn_detener)
        b.btnAccionGrabacion.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.radix_tomato_solid)
        b.tvEstadoGrabacion.text = getString(R.string.reporte_ux_dictado_estado_grabando)
        b.tvAvisoGrabacion.visibility = View.GONE
        b.pbNivelAudio.progress = 5
        iniciarAnimacionPulso()
    }

    private fun restaurarUiGrabacionIdle() {
        val b = _binding ?: return
        val ctx = context ?: return
        detenerAnimacionPulso()
        b.cardBotonMic.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_soft))
        b.cardBotonMic.strokeColor = ContextCompat.getColor(ctx, R.color.primary)
        b.ivMicPrincipal.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
        b.btnAccionGrabacion.text = getString(R.string.reporte_ux_dictado_btn_iniciar)
        b.btnAccionGrabacion.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.primary)
        b.tvEstadoGrabacion.text = getString(R.string.reporte_ux_dictado_estado_listo)
        b.tvAvisoGrabacion.visibility = View.GONE
        b.pbNivelAudio.progress = 0
    }

    private fun iniciarAnimacionPulso() {
        val b = _binding ?: return
        detenerAnimacionPulso()
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.08f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.08f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(b.cardBotonMic, scaleX, scaleY).apply {
            duration = 650L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun detenerAnimacionPulso() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        _binding?.cardBotonMic?.apply {
            scaleX = 1.0f
            scaleY = 1.0f
        }
    }

    private fun iniciarTimer() {
        detenerTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                val b = _binding ?: return
                if (!recorderHelper.isRecording()) return

                val ms = recorderHelper.getDurationMs()
                val totalSec = (ms / 1000L).toInt()
                val min = totalSec / 60
                val sec = totalSec % 60
                b.tvTiempoGrabacion.text = String.format(Locale.US, "%02d:%02d", min, sec)

                val amp = recorderHelper.getMaxAmplitude()
                val levelPct = ((amp / 16000f) * 100f).toInt().coerceIn(4, 100)
                b.pbNivelAudio.setProgressCompat(levelPct, true)

                if (ms >= VoiceRecorderHelper.MAX_DURATION_MS) {
                    detenerYProcesarGrabacion(autoStoppedByMaxLimit = true)
                    return
                }

                if (ms >= VoiceRecorderHelper.WARNING_DURATION_MS) {
                    b.tvAvisoGrabacion.text = getString(R.string.reporte_ux_dictado_aviso_tiempo_limite)
                    b.tvAvisoGrabacion.visibility = View.VISIBLE
                }

                timerHandler.postDelayed(this, 100L)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun detenerTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun enviarAudioABackend(audioFile: File) {
        if (currentUiState == UiState.PROCESSING) return
        mostrarEstadoProcesando()

        val token = SharedPreferencesManager(requireContext()).getAccessToken()
        val tokenHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        processingJob?.cancel()
        processingJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mediaType = "audio/mp4".toMediaTypeOrNull()
                val requestFile = audioFile.asRequestBody(mediaType)
                val audioPart = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
                val formIdBody = ActaFormDefinitionRegistry.FORM_ID_ACTA_ATENCION
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val formVersionBody = ActaFormDefinitionRegistry.FORM_VERSION_ACTA_ATENCION
                    .toString()
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.dictadoApiService.procesarDictadoVoz(
                        token = tokenHeader,
                        audio = audioPart,
                        formId = formIdBody,
                        formVersion = formVersionBody
                    )
                }

                if (!isAdded || _binding == null) return@launch

                if (response.isSuccessful && response.body() != null) {
                    procesarRespuestaExitosa(response.body()!!)
                } else {
                    val errorParsed = parseErrorBodySafely(response.errorBody()?.string())
                    val (titulo, mensaje) = mapearErrorServidor(errorParsed?.errorCode, response.code())
                    mostrarEstadoError(titulo, mensaje)
                }
            } catch (_: CancellationException) {
                // Cancelado intencionalmente por el usuario o ciclo de vida
            } catch (exc: Exception) {
                if (!isAdded || _binding == null) return@launch
                val (titulo, mensaje) = mapearExcepcionRed(exc)
                mostrarEstadoError(titulo, mensaje)
            } finally {
                limpiarArchivoTemporal(audioFile)
            }
        }
    }

    private fun parseErrorBodySafely(rawError: String?): DictadoVozResponse? {
        if (rawError.isNullOrBlank()) return null
        return try {
            Gson().fromJson(rawError, DictadoVozResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun mapearErrorServidor(errorCode: String?, httpCode: Int): Pair<String, String> {
        return when (errorCode) {
            "ia_no_configurada" -> Pair(
                getString(R.string.reporte_ux_dictado_error_no_config_titulo),
                getString(R.string.reporte_ux_dictado_error_no_config_msg)
            )
            "timeout" -> Pair(
                getString(R.string.reporte_ux_dictado_error_timeout_titulo),
                getString(R.string.reporte_ux_dictado_error_timeout_msg)
            )
            "ia_saturada" -> Pair(
                getString(R.string.reporte_ux_dictado_error_saturado_titulo),
                getString(R.string.reporte_ux_dictado_error_saturado_msg)
            )
            "audio_invalido" -> Pair(
                getString(R.string.reporte_ux_dictado_error_audio_invalido_titulo),
                getString(R.string.reporte_ux_dictado_error_audio_invalido_msg)
            )
            "sin_datos" -> Pair(
                getString(R.string.reporte_ux_dictado_error_sin_datos_titulo),
                getString(R.string.reporte_ux_dictado_error_sin_datos_msg)
            )
            "permiso_denegado" -> Pair(
                getString(R.string.reporte_ux_dictado_error_sesion_titulo),
                getString(R.string.reporte_ux_dictado_error_sesion_msg)
            )
            else -> when (httpCode) {
                401, 403 -> Pair(
                    getString(R.string.reporte_ux_dictado_error_sesion_titulo),
                    getString(R.string.reporte_ux_dictado_error_sesion_msg)
                )
                400, 413 -> Pair(
                    getString(R.string.reporte_ux_dictado_error_audio_invalido_titulo),
                    getString(R.string.reporte_ux_dictado_error_audio_invalido_msg)
                )
                429, 503 -> Pair(
                    getString(R.string.reporte_ux_dictado_error_saturado_titulo),
                    getString(R.string.reporte_ux_dictado_error_saturado_msg)
                )
                408, 504 -> Pair(
                    getString(R.string.reporte_ux_dictado_error_timeout_titulo),
                    getString(R.string.reporte_ux_dictado_error_timeout_msg)
                )
                else -> Pair(
                    getString(R.string.reporte_ux_dictado_error_generico_titulo),
                    getString(R.string.reporte_ux_dictado_error_generico_msg)
                )
            }
        }
    }

    private fun mapearExcepcionRed(exc: Exception): Pair<String, String> {
        return when (exc) {
            is SocketTimeoutException -> Pair(
                getString(R.string.reporte_ux_dictado_error_timeout_titulo),
                getString(R.string.reporte_ux_dictado_error_timeout_msg)
            )
            is UnknownHostException, is ConnectException, is IOException -> Pair(
                getString(R.string.reporte_ux_dictado_error_red_titulo),
                getString(R.string.reporte_ux_dictado_error_red_msg)
            )
            else -> Pair(
                getString(R.string.reporte_ux_dictado_error_generico_titulo),
                getString(R.string.reporte_ux_dictado_error_generico_msg)
            )
        }
    }

    private fun procesarRespuestaExitosa(response: DictadoVozResponse) {
        if (response.status == "error") {
            val (titulo, mensaje) = mapearErrorServidor(response.errorCode, 200)
            mostrarEstadoError(titulo, mensaje)
            return
        }

        val camposRecibidos = response.fields ?: emptyMap()
        if (camposRecibidos.isEmpty()) {
            val (titulo, mensaje) = mapearErrorServidor("sin_datos", 200)
            mostrarEstadoError(titulo, mensaje)
            return
        }

        val def = ActaFormDefinitionRegistry.getActaAtencionDefinition()
        val sectionOrder = mutableMapOf<String, Pair<Int, String>>()
        var orderIdx = 0
        for (section in def.sections) {
            for (field in section.fields) {
                sectionOrder[field.id] = Pair(orderIdx++, section.title)
            }
        }

        val rawItems = mutableListOf<Triple<Int, CampoRevisionItem, Unit>>()
        for ((fieldId, campoVoice) in camposRecibidos) {
            val valor = campoVoice.value ?: continue
            val fieldDef = def.findField(fieldId)
            val label = fieldDef?.label ?: fieldId
            val isBool = fieldDef?.type == FieldType.BOOLEAN ||
                fieldDef?.type == FieldType.SWITCH ||
                fieldDef?.type == FieldType.CHECKBOX ||
                valor is Boolean

            val valueStr = if (isBool) {
                if (campoVoice.asBoolean()) {
                    getString(R.string.reporte_ux_dictado_si)
                } else {
                    getString(R.string.reporte_ux_dictado_no)
                }
            } else {
                campoVoice.asString().trim()
            }
            if (valueStr.isBlank()) continue

            val existingRaw = valoresActuales[fieldId]?.trim().orEmpty()
            val hasConflict = existingRaw.isNotEmpty() && !sonValoresEquivalentes(existingRaw, valueStr, isBool)

            val orderInfo = sectionOrder[fieldId]
            val sortKey = orderInfo?.first ?: Int.MAX_VALUE
            val sectionTitle = orderInfo?.second ?: getString(R.string.reporte_ux_dictado_seccion_general)

            val item = CampoRevisionItem(
                fieldId = fieldId,
                label = label,
                sectionTitle = sectionTitle,
                showSectionHeader = false,
                valueStr = valueStr,
                originalVoiceStr = valueStr,
                existingValueStr = if (hasConflict) existingRaw else null,
                hasConflict = hasConflict,
                isAccepted = !hasConflict,
                confidence = campoVoice.confidence,
                isBoolean = isBool
            )
            rawItems.add(Triple(sortKey, item, Unit))
        }

        if (rawItems.isEmpty()) {
            val (titulo, mensaje) = mapearErrorServidor("sin_datos", 200)
            mostrarEstadoError(titulo, mensaje)
            return
        }

        rawItems.sortBy { it.first }
        val groupedItems = arrayListOf<CampoRevisionItem>()
        var lastSection = ""
        for ((_, item, _) in rawItems) {
            val showHeader = item.sectionTitle != lastSection
            lastSection = item.sectionTitle
            groupedItems.add(item.copy(showSectionHeader = showHeader))
        }

        currentResponse = response
        currentRevisionItems = groupedItems
        renderizarRevision(response, groupedItems)
    }

    private fun sonValoresEquivalentes(actual: String, voz: String, isBool: Boolean): Boolean {
        if (isBool) {
            val aTrue = actual.equals("Sí", true) || actual.equals("Si", true) ||
                actual.equals("true", true) || actual == "1"
            val vTrue = voz.equals("Sí", true) || voz.equals("Si", true) ||
                voz.equals("true", true) || voz == "1"
            return aTrue == vTrue
        }
        return actual.trim().equals(voz.trim(), ignoreCase = true)
    }

    private fun renderizarRevision(response: DictadoVozResponse?, items: ArrayList<CampoRevisionItem>) {
        val b = _binding ?: return

        b.tvResumenNarracion.text = response?.extractedSummary?.takeIf { it.isNotBlank() }
            ?: getString(R.string.reporte_ux_dictado_resumen_fallback, items.size)

        val transcripcion = response?.transcripcion?.trim().orEmpty()
        if (transcripcion.isNotEmpty()) {
            b.cardTranscripcionLiteral.visibility = View.VISIBLE
            b.tvTranscripcionLiteral.text = transcripcion
        } else {
            b.cardTranscripcionLiteral.visibility = View.GONE
        }

        val warnings = response?.warnings?.filter { it.isNotBlank() }.orEmpty()
        if (warnings.isNotEmpty()) {
            b.cardAdvertenciasDictado.visibility = View.VISIBLE
            b.tvAdvertenciasDictado.text = warnings.joinToString("\n• ", prefix = "• ")
        } else {
            b.cardAdvertenciasDictado.visibility = View.GONE
        }

        adapter = CamposRevisionAdapter(items) { acceptedCount ->
            actualizarBotonConfirmar(acceptedCount)
        }
        b.rvCamposExtraidos.layoutManager = LinearLayoutManager(requireContext())
        b.rvCamposExtraidos.adapter = adapter

        val maxHeightPx = (resources.displayMetrics.heightPixels * 0.38f).toInt()
            .coerceAtLeast((200 * resources.displayMetrics.density).toInt())
        val lp = b.rvCamposExtraidos.layoutParams
        if (lp != null) {
            lp.height = maxHeightPx
            b.rvCamposExtraidos.layoutParams = lp
        }

        actualizarBotonConfirmar(adapter?.getAcceptedCount() ?: 0)
        mostrarEstadoRevision()
    }

    private fun actualizarBotonConfirmar(acceptedCount: Int) {
        val b = _binding ?: return
        val enabled = acceptedCount > 0
        b.btnAplicarBorrador.isEnabled = enabled
        b.btnAplicarBorrador.alpha = if (enabled) 1.0f else 0.5f
        b.btnAplicarBorrador.text = if (enabled) {
            getString(R.string.reporte_ux_dictado_btn_confirmar, acceptedCount)
        } else {
            getString(R.string.reporte_ux_dictado_btn_confirmar_deshabilitado)
        }
    }

    private fun confirmarYAplicarBorrador() {
        val currentAdapter = adapter ?: return
        val acceptedItems = currentAdapter.getItems().filter { it.isAccepted }
        if (acceptedItems.isEmpty()) return

        val mapaValores = hashMapOf<String, Serializable?>()
        val mapaConfianzas = hashMapOf<String, Float?>()
        val sobrescribirIds = arrayListOf<String>()

        for (item in acceptedItems) {
            val str = item.valueStr.trim()
            val finalVal: Serializable = if (item.isBoolean) {
                str.equals("Sí", ignoreCase = true) ||
                    str.equals("Si", ignoreCase = true) ||
                    str.equals("true", ignoreCase = true) ||
                    str == "1"
            } else {
                str
            }
            mapaValores[item.fieldId] = finalVal
            mapaConfianzas[item.fieldId] = item.confidence
            if (item.hasConflict) {
                sobrescribirIds.add(item.fieldId)
            }
        }

        val resultBundle = Bundle().apply {
            putSerializable(RESULT_CAMPOS, mapaValores)
            putSerializable(RESULT_CONFIANZAS, mapaConfianzas)
            putStringArrayList(RESULT_SOBRESCRIBIR, sobrescribirIds)
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, resultBundle)
        dismiss()
    }

    private fun mostrarEstadoGrabacion() {
        currentUiState = UiState.RECORDING
        val b = _binding ?: return
        b.layoutGrabacion.visibility = View.VISIBLE
        b.layoutProcesando.visibility = View.GONE
        b.layoutRevisionBorrador.visibility = View.GONE
        b.layoutError.visibility = View.GONE
        b.tvTiempoGrabacion.text = getString(R.string.reporte_ux_dictado_tiempo_inicial)
        restaurarUiGrabacionIdle()
    }

    private fun mostrarEstadoProcesando() {
        currentUiState = UiState.PROCESSING
        val b = _binding ?: return
        b.layoutGrabacion.visibility = View.GONE
        b.layoutProcesando.visibility = View.VISIBLE
        b.layoutRevisionBorrador.visibility = View.GONE
        b.layoutError.visibility = View.GONE
    }

    private fun mostrarEstadoRevision() {
        currentUiState = UiState.REVIEW
        val b = _binding ?: return
        b.layoutGrabacion.visibility = View.GONE
        b.layoutProcesando.visibility = View.GONE
        b.layoutRevisionBorrador.visibility = View.VISIBLE
        b.layoutError.visibility = View.GONE
    }

    private fun mostrarEstadoError(titulo: String, mensaje: String) {
        currentUiState = UiState.ERROR
        currentErrorTitle = titulo
        currentErrorMessage = mensaje
        val b = _binding ?: return
        b.layoutGrabacion.visibility = View.GONE
        b.layoutProcesando.visibility = View.GONE
        b.layoutRevisionBorrador.visibility = View.GONE
        b.layoutError.visibility = View.VISIBLE

        b.tvTituloError.text = titulo
        b.tvMensajeError.text = mensaje
    }

    private fun cancelarProcesamientoYVolverAGrabar() {
        processingJob?.cancel()
        processingJob = null
        limpiarArchivoTemporal()
        mostrarEstadoGrabacion()
    }

    private fun cancelarYSalir() {
        detenerTimer()
        detenerAnimacionPulso()
        processingJob?.cancel()
        processingJob = null
        recorderHelper.cancelRecording()
        limpiarArchivoTemporal()
        dismiss()
    }

    private fun limpiarArchivoTemporal(file: File? = activeTempAudioFile) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
        if (file == activeTempAudioFile) {
            activeTempAudioFile = null
        }
    }

    override fun onPause() {
        super.onPause()
        if (::recorderHelper.isInitialized && recorderHelper.isRecording()) {
            detenerTimer()
            recorderHelper.cancelRecording()
            limpiarArchivoTemporal()
            restaurarUiGrabacionIdle()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::recorderHelper.isInitialized && recorderHelper.isRecording()) {
            detenerTimer()
            recorderHelper.cancelRecording()
            limpiarArchivoTemporal()
            restaurarUiGrabacionIdle()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detenerTimer()
        detenerAnimacionPulso()
        processingJob?.cancel()
        processingJob = null
        if (::recorderHelper.isInitialized) {
            recorderHelper.cancelRecording()
        }
        limpiarArchivoTemporal()
        _binding = null
    }
}
