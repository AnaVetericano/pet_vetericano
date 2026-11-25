package com.example.petvetericano.facial

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.BuildConfig
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.databinding.ActivityReconocimientoFacialBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/**
 * Pantalla de cámara frontal para enrolamiento y verificación facial 100% on-device:
 * CameraX + ML Kit Face Detection (prueba de vida) + MobileFaceNet (.tflite).
 */
class ReconocimientoFacialActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReconocimientoFacial"
        const val EXTRA_MODO = "EXTRA_MODO_FACIAL"
        const val EXTRA_RESULTADO_MENSAJE = "EXTRA_RESULTADO_MENSAJE"
        const val MODO_ENROLAR = "ENROLAR"
        const val MODO_VERIFICAR = "VERIFICAR"

        fun crearIntentEnrolar(context: Context): Intent {
            return Intent(context, ReconocimientoFacialActivity::class.java).apply {
                putExtra(EXTRA_MODO, MODO_ENROLAR)
            }
        }

        fun crearIntentVerificar(context: Context): Intent {
            return Intent(context, ReconocimientoFacialActivity::class.java).apply {
                putExtra(EXTRA_MODO, MODO_VERIFICAR)
            }
        }
    }

    private lateinit var binding: ActivityReconocimientoFacialBinding
    private lateinit var modo: String
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    @Volatile
    private var faceExtractor: MobileFaceNetExtractor? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private val livenessMachine = LivenessStateMachine()
    private val embeddingsCapturados = mutableListOf<FloatArray>()
    private val escaneoPausado = AtomicBoolean(false)
    private val procesandoFinal = AtomicBoolean(false)

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                inicializarMotorYCamara()
            } else {
                mostrarDialogoPermisoCamara()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReconocimientoFacialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modo = intent.getStringExtra(EXTRA_MODO) ?: MODO_VERIFICAR
        cameraExecutor = Executors.newSingleThreadExecutor()

        val detectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.20f)
            .build()
        faceDetector = FaceDetection.getClient(detectorOptions)

        configurarUiInicial()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            inicializarMotorYCamara()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun configurarUiInicial() {
        val esEnrolar = modo == MODO_ENROLAR
        binding.tvTituloFacial.setText(
            if (esEnrolar) R.string.facial_titulo_enrolar else R.string.facial_titulo_verificar
        )
        binding.tvSubtituloFacial.setText(
            if (esEnrolar) R.string.facial_subtitulo_enrolar else R.string.facial_subtitulo_verificar
        )
        binding.tvPasoBadge.text = "Paso 1 de ${LivenessStateMachine.TOTAL_MUESTRAS}"
        binding.faceOvalOverlay.actualizarEstado(FaceOvalOverlayView.EstadoMarco.NEUTRO, 0.05f)

        binding.btnCerrarFacial.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnReintentarFacial.setOnClickListener {
            reiniciarEscaneo()
        }
    }

    private fun inicializarMotorYCamara() {
        mostrarCapaProcesando(true, "Preparando cámara y motor facial…")
        lifecycleScope.launch {
            val motor = withContext(Dispatchers.IO) {
                try {
                    MobileFaceNetExtractor(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Error inicializando MobileFaceNet: ${e.message}", e)
                    null
                }
            }

            if (isFinishing || isDestroyed) {
                motor?.close()
                return@launch
            }

            if (motor == null) {
                mostrarCapaProcesando(false)
                mostrarErrorYPermitirReintento(getString(R.string.facial_error_modelo))
                return@launch
            }

            faceExtractor = motor
            mostrarCapaProcesando(false)
            iniciarCamaraFrontal()
        }
    }

    private fun iniciarCamaraFrontal() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                if (isFinishing || isDestroyed) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = binding.previewView.surfaceProvider }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                analizarFrame(imageProxy)
                            }
                        }

                    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(this, selector, preview, imageAnalysis)
                } catch (e: Exception) {
                    Log.e(TAG, "Error iniciando CameraX: ${e.message}", e)
                    mostrarErrorYPermitirReintento("No se pudo abrir la cámara frontal. Intenta nuevamente.")
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analizarFrame(imageProxy: ImageProxy) {
        if (escaneoPausado.get() || procesandoFinal.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        faceDetector.process(inputImage)
            .addOnSuccessListener(cameraExecutor) { faces ->
                try {
                    procesarDeteccionEnHiloCamara(
                        imageProxy = imageProxy,
                        faces = faces,
                        uprightWidth = uprightWidth,
                        uprightHeight = uprightHeight,
                        rotationDegrees = rotationDegrees
                    )
                } finally {
                    imageProxy.close()
                }
            }
            .addOnFailureListener(cameraExecutor) { e ->
                Log.w(TAG, "Fallo puntual de ML Kit Face Detection: ${e.message}")
                imageProxy.close()
            }
    }

    private fun procesarDeteccionEnHiloCamara(
        imageProxy: ImageProxy,
        faces: List<Face>,
        uprightWidth: Int,
        uprightHeight: Int,
        rotationDegrees: Int
    ) {
        if (escaneoPausado.get() || procesandoFinal.get()) return

        val ahoraMs = SystemClock.elapsedRealtime()
        val metrics = if (faces.size == 1 && uprightWidth > 0 && uprightHeight > 0) {
            val face = faces[0]
            val box = face.boundingBox
            LivenessStateMachine.FaceFrameMetrics(
                faceCount = 1,
                faceWidthRatio = box.width().toFloat() / uprightWidth.toFloat(),
                centerOffsetX = (box.centerX().toFloat() / uprightWidth.toFloat()) - 0.5f,
                centerOffsetY = (box.centerY().toFloat() / uprightHeight.toFloat()) - 0.5f,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                leftEyeOpenProb = face.leftEyeOpenProbability,
                rightEyeOpenProb = face.rightEyeOpenProbability,
                timestampMs = ahoraMs
            )
        } else {
            LivenessStateMachine.FaceFrameMetrics(
                faceCount = faces.size,
                timestampMs = ahoraMs
            )
        }

        val decision = livenessMachine.procesarFrame(metrics)

        if (decision.expirado) {
            escaneoPausado.set(true)
            runOnUiThread {
                mostrarErrorYPermitirReintento(getString(R.string.facial_error_timeout))
            }
            return
        }

        if (decision.capturarMuestra && faces.size == 1) {
            val extractor = faceExtractor
            if (extractor != null) {
                try {
                    val rawBitmap = imageProxy.toBitmap()
                    val uprightBitmap = rotarBitmapSiNecesario(rawBitmap, rotationDegrees)
                    try {
                        val embedding = extractor.extraerEmbedding(uprightBitmap, faces[0].boundingBox)
                        embeddingsCapturados.add(embedding)
                    } finally {
                        if (uprightBitmap !== rawBitmap && !uprightBitmap.isRecycled) {
                            uprightBitmap.recycle()
                        }
                        if (!rawBitmap.isRecycled) {
                            rawBitmap.recycle()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo extraer embedding de la captura: ${e.message}")
                }
            }
        }

        runOnUiThread {
            actualizarUiDesdeDecision(decision)
        }

        if (decision.completado && procesandoFinal.compareAndSet(false, true)) {
            escaneoPausado.set(true)
            val copias = embeddingsCapturados.map { it.copyOf() }
            runOnUiThread {
                finalizarEscaneo(copias)
            }
        }
    }

    private fun rotarBitmapSiNecesario(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun actualizarUiDesdeDecision(decision: LivenessStateMachine.LivenessDecision) {
        if (isFinishing || isDestroyed) return

        val pasoNumero = when (decision.pasoActual) {
            LivenessStateMachine.PasoLiveness.CENTRAR_ROSTRO -> 1
            LivenessStateMachine.PasoLiveness.PARPADEAR -> 2
            LivenessStateMachine.PasoLiveness.GIRAR_CABEZA,
            LivenessStateMachine.PasoLiveness.VOLVER_CENTRO,
            LivenessStateMachine.PasoLiveness.COMPLETADO -> 3
        }
        binding.tvPasoBadge.text = "Paso $pasoNumero de ${decision.totalMuestras}"

        val instruccionRes = when (decision.pasoActual) {
            LivenessStateMachine.PasoLiveness.CENTRAR_ROSTRO ->
                if (decision.progreso >= 0.14f) R.string.facial_paso_mantener else R.string.facial_paso_centrar
            LivenessStateMachine.PasoLiveness.PARPADEAR -> R.string.facial_paso_parpadear
            LivenessStateMachine.PasoLiveness.GIRAR_CABEZA -> R.string.facial_paso_girar
            LivenessStateMachine.PasoLiveness.VOLVER_CENTRO -> R.string.facial_paso_volver_centro
            LivenessStateMachine.PasoLiveness.COMPLETADO -> R.string.facial_paso_procesando
        }
        binding.tvInstruccionFacial.setText(instruccionRes)

        val avisoRes = when (decision.aviso) {
            LivenessStateMachine.AvisoCalidadRostro.NINGUNO -> null
            LivenessStateMachine.AvisoCalidadRostro.SIN_ROSTRO -> R.string.facial_aviso_sin_rostro
            LivenessStateMachine.AvisoCalidadRostro.MULTIPLES_ROSTROS -> R.string.facial_aviso_multiples_rostros
            LivenessStateMachine.AvisoCalidadRostro.MUY_LEJOS -> R.string.facial_aviso_muy_lejos
            LivenessStateMachine.AvisoCalidadRostro.MUY_CERCA -> R.string.facial_aviso_muy_cerca
            LivenessStateMachine.AvisoCalidadRostro.DESCENTRADO -> R.string.facial_aviso_descentrado
            LivenessStateMachine.AvisoCalidadRostro.INCLINADO -> R.string.facial_aviso_inclinado
        }

        if (avisoRes != null) {
            binding.tvAvisoFacial.setText(avisoRes)
            binding.tvAvisoFacial.visibility = View.VISIBLE
        } else {
            binding.tvAvisoFacial.visibility = View.GONE
        }

        val estadoMarco = when {
            decision.completado -> FaceOvalOverlayView.EstadoMarco.EXITO
            avisoRes != null -> FaceOvalOverlayView.EstadoMarco.ALERTA
            decision.progreso >= 0.14f -> FaceOvalOverlayView.EstadoMarco.ACTIVO
            else -> FaceOvalOverlayView.EstadoMarco.NEUTRO
        }
        binding.faceOvalOverlay.actualizarEstado(estadoMarco, decision.progreso)
        binding.progressLiveness.setProgressCompat((decision.progreso * 100).toInt(), true)
    }

    private fun finalizarEscaneo(muestras: List<FloatArray>) {
        mostrarCapaProcesando(true, getString(R.string.facial_paso_procesando))

        lifecycleScope.launch {
            if (modo == MODO_ENROLAR) {
                procesarEnrolamiento(muestras)
            } else {
                procesarVerificacion(muestras)
            }
        }
    }

    private suspend fun procesarEnrolamiento(muestras: List<FloatArray>) {
        val resultado = withContext(Dispatchers.Default) {
            if (muestras.size < 2 || !FacialMath.validateEnrollmentConsistency(muestras)) {
                return@withContext Pair(false, getString(R.string.facial_error_consistencia))
            }

            val prefs = SharedPreferencesManager(applicationContext)
            val userId = prefs.getUserId()
            val email = prefs.getUserEmail().trim()
            val idRol = prefs.getUserRole()
            val refreshToken = prefs.getRefreshToken().trim()

            if (userId <= 0 || email.isEmpty() || idRol <= 0 || refreshToken.isEmpty()) {
                return@withContext Pair(false, "Tu sesión actual no es válida. Inicia sesión nuevamente.")
            }

            val centroid = FacialMath.averageAndNormalize(muestras)
            val perfil = FacialStorageManager.PerfilFacialGuardado(
                userId = userId,
                email = email,
                nombre = prefs.getUserName(),
                apellido = prefs.getUserLastName(),
                telefono = prefs.getUserPhone(),
                fotoPerfil = prefs.getProfileImagePath(),
                matriculaProfesional = prefs.getUserMatriculaProfesional(),
                identificacion = prefs.getUserIdentification(),
                idRol = idRol,
                refreshToken = refreshToken,
                centroid = centroid,
                samples = muestras,
                enrolledAtMillis = System.currentTimeMillis()
            )
            val guardado = FacialStorageManager(applicationContext).guardarPerfilFacial(perfil)
            if (guardado) {
                Pair(true, getString(R.string.facial_enrolado_ok))
            } else {
                Pair(false, "No se pudo guardar la información biométrica cifrada en este dispositivo.")
            }
        }

        if (isFinishing || isDestroyed) return
        mostrarCapaProcesando(false)

        if (resultado.first) {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_RESULTADO_MENSAJE, resultado.second)
            )
            finish()
        } else {
            mostrarErrorYPermitirReintento(resultado.second)
        }
    }

    private suspend fun procesarVerificacion(muestras: List<FloatArray>) {
        val storage = FacialStorageManager(applicationContext)
        val resultado = withContext(Dispatchers.Default) {
            val perfil = storage.obtenerPerfilFacial()
                ?: return@withContext Triple(false, getString(R.string.facial_no_configurado), false)

            if (muestras.isEmpty()) {
                return@withContext Triple(false, getString(R.string.facial_error_consistencia), false)
            }

            val candidato = FacialMath.averageAndNormalize(muestras)
            val match = FacialMath.evaluateMatch(
                candidate = candidato,
                centroid = perfil.centroid,
                samples = perfil.samples
            )

            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Evaluación facial -> coincide=${match.coincide} " +
                        "simCentroide=${"%.3f".format(match.similitudCentroide)} " +
                        "mejorMuestra=${"%.3f".format(match.mejorSimilitudMuestra)} " +
                        "distL2=${"%.3f".format(match.distanciaEuclidiana)} " +
                        "umbral=${match.umbralAplicado}"
                )
            }

            if (match.coincide) {
                storage.limpiarIntentosFallidos()
                Triple(true, "", false)
            } else {
                val bloqueoMs = storage.registrarIntentoFallido()
                if (bloqueoMs > 0L) {
                    val seg = ceil(bloqueoMs / 1000.0).toInt().coerceAtLeast(1)
                    Triple(false, getString(R.string.facial_error_bloqueo, seg), true)
                } else {
                    Triple(false, getString(R.string.facial_error_no_coincide), false)
                }
            }
        }

        if (isFinishing || isDestroyed) return
        mostrarCapaProcesando(false)

        if (resultado.first) {
            setResult(RESULT_OK)
            finish()
        } else if (resultado.third) {
            // Bloqueado temporalmente tras demasiados intentos fallidos: devolver mensaje al login
            setResult(
                RESULT_CANCELED,
                Intent().putExtra(EXTRA_RESULTADO_MENSAJE, resultado.second)
            )
            finish()
        } else {
            mostrarErrorYPermitirReintento(resultado.second)
        }
    }

    private fun mostrarErrorYPermitirReintento(mensaje: String) {
        escaneoPausado.set(true)
        binding.faceOvalOverlay.actualizarEstado(FaceOvalOverlayView.EstadoMarco.ALERTA, 0f)
        binding.tvInstruccionFacial.text = mensaje
        binding.tvAvisoFacial.visibility = View.GONE
        binding.btnReintentarFacial.visibility = View.VISIBLE
    }

    private fun reiniciarEscaneo() {
        embeddingsCapturados.clear()
        livenessMachine.reset()
        procesandoFinal.set(false)
        escaneoPausado.set(false)
        binding.btnReintentarFacial.visibility = View.GONE
        binding.tvAvisoFacial.visibility = View.GONE
        binding.tvPasoBadge.text = "Paso 1 de ${LivenessStateMachine.TOTAL_MUESTRAS}"
        binding.tvInstruccionFacial.setText(R.string.facial_paso_centrar)
        binding.progressLiveness.setProgressCompat(5, true)
        binding.faceOvalOverlay.actualizarEstado(FaceOvalOverlayView.EstadoMarco.NEUTRO, 0.05f)
    }

    private fun mostrarCapaProcesando(mostrar: Boolean, texto: String? = null) {
        binding.layoutProcesandoFacial.visibility = if (mostrar) View.VISIBLE else View.GONE
        if (!texto.isNullOrBlank()) {
            binding.tvProcesandoFacial.text = texto
        }
    }

    private fun mostrarDialogoPermisoCamara() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.facial_permiso_camara_titulo)
            .setMessage(R.string.facial_permiso_camara_mensaje)
            .setCancelable(false)
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
                setResult(RESULT_CANCELED)
                finish()
            }
            .setPositiveButton(R.string.facial_btn_ir_ajustes) { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        escaneoPausado.set(true)
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        try {
            faceDetector.close()
        } catch (_: Exception) {
        }
        try {
            faceExtractor?.close()
        } catch (_: Exception) {
        }
        try {
            cameraExecutor.shutdown()
        } catch (_: Exception) {
        }
    }
}
