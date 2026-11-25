package com.example.petvetericano.facial

import kotlin.math.abs

/**
 * Máquina de estados pura para prueba de vida (liveness) anti-suplantación:
 * 1) Centrar rostro de frente y mantener estable -> captura muestra #1
 * 2) Parpadear (ojos cerrados -> ojos abiertos) -> captura muestra #2
 * 3) Girar suavemente la cabeza hacia un lado y volver al centro -> captura muestra #3
 *
 * Trabaja con métricas extraídas por ML Kit Face Detection y es 100% determinista para tests JVM.
 */
class LivenessStateMachine(
    private val holdCenterMs: Long = DEFAULT_HOLD_CENTER_MS,
    private val holdReturnMs: Long = DEFAULT_HOLD_RETURN_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxGapBeforeResetMs: Long = DEFAULT_MAX_GAP_RESET_MS
) {

    enum class PasoLiveness {
        CENTRAR_ROSTRO,
        PARPADEAR,
        GIRAR_CABEZA,
        VOLVER_CENTRO,
        COMPLETADO
    }

    enum class AvisoCalidadRostro {
        NINGUNO,
        SIN_ROSTRO,
        MULTIPLES_ROSTROS,
        MUY_LEJOS,
        MUY_CERCA,
        DESCENTRADO,
        INCLINADO
    }

    data class FaceFrameMetrics(
        val faceCount: Int,
        val faceWidthRatio: Float = 0f,
        val centerOffsetX: Float = 0f,
        val centerOffsetY: Float = 0f,
        val headEulerAngleY: Float = 0f,
        val headEulerAngleZ: Float = 0f,
        val leftEyeOpenProb: Float? = null,
        val rightEyeOpenProb: Float? = null,
        val timestampMs: Long
    )

    data class LivenessDecision(
        val pasoActual: PasoLiveness,
        val aviso: AvisoCalidadRostro,
        val capturarMuestra: Boolean,
        val muestrasCapturadas: Int,
        val totalMuestras: Int,
        val progreso: Float,
        val completado: Boolean,
        val expirado: Boolean
    )

    companion object {
        const val TOTAL_MUESTRAS = 3
        const val DEFAULT_HOLD_CENTER_MS = 350L
        const val DEFAULT_HOLD_RETURN_MS = 200L
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_GAP_RESET_MS = 2_000L

        private const val MIN_FACE_WIDTH_RATIO = 0.22f
        private const val MAX_FACE_WIDTH_RATIO = 0.85f
        private const val MAX_CENTER_OFFSET_X = 0.24f
        private const val MAX_CENTER_OFFSET_Y = 0.26f
        private const val MAX_FRONTAL_YAW_DEG = 14f
        private const val MAX_RETURN_YAW_DEG = 12f
        private const val MAX_FRONTAL_ROLL_DEG = 16f
        private const val MIN_TURN_YAW_DEG = 15f
        private const val EYE_CLOSED_THRESHOLD = 0.30f
        private const val EYE_OPEN_THRESHOLD = 0.55f
    }

    private var paso: PasoLiveness = PasoLiveness.CENTRAR_ROSTRO
    private var inicioSesionMs: Long? = null
    private var ultimoRostroValidoMs: Long? = null
    private var inicioEstabilidadMs: Long? = null
    private var vioOjosCerrados: Boolean = false
    private var muestrasCapturadas: Int = 0

    fun reset() {
        paso = PasoLiveness.CENTRAR_ROSTRO
        inicioSesionMs = null
        ultimoRostroValidoMs = null
        inicioEstabilidadMs = null
        vioOjosCerrados = false
        muestrasCapturadas = 0
    }

    fun procesarFrame(metrics: FaceFrameMetrics): LivenessDecision {
        if (paso == PasoLiveness.COMPLETADO) {
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }

        val inicio = inicioSesionMs ?: metrics.timestampMs.also { inicioSesionMs = it }
        if (metrics.timestampMs - inicio >= timeoutMs) {
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = true)
        }

        if (metrics.faceCount == 0) {
            manejarAusenciaRostro(metrics.timestampMs)
            return construirDecision(AvisoCalidadRostro.SIN_ROSTRO, capturar = false, expirado = false)
        }
        if (metrics.faceCount > 1) {
            inicioEstabilidadMs = null
            vioOjosCerrados = false
            return construirDecision(AvisoCalidadRostro.MULTIPLES_ROSTROS, capturar = false, expirado = false)
        }

        ultimoRostroValidoMs = metrics.timestampMs

        val avisoBasico = evaluarTamanoYPosicion(metrics)
        if (avisoBasico != AvisoCalidadRostro.NINGUNO) {
            inicioEstabilidadMs = null
            return construirDecision(avisoBasico, capturar = false, expirado = false)
        }

        return when (paso) {
            PasoLiveness.CENTRAR_ROSTRO -> procesarPasoCentrar(metrics)
            PasoLiveness.PARPADEAR -> procesarPasoParpadear(metrics)
            PasoLiveness.GIRAR_CABEZA -> procesarPasoGirar(metrics)
            PasoLiveness.VOLVER_CENTRO -> procesarPasoVolverCentro(metrics)
            PasoLiveness.COMPLETADO -> construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }
    }

    private fun manejarAusenciaRostro(ahoraMs: Long) {
        inicioEstabilidadMs = null
        val ultimo = ultimoRostroValidoMs ?: return
        if (ahoraMs - ultimo > maxGapBeforeResetMs) {
            vioOjosCerrados = false
        }
    }

    private fun evaluarTamanoYPosicion(m: FaceFrameMetrics): AvisoCalidadRostro {
        if (m.faceWidthRatio < MIN_FACE_WIDTH_RATIO) return AvisoCalidadRostro.MUY_LEJOS
        if (m.faceWidthRatio > MAX_FACE_WIDTH_RATIO) return AvisoCalidadRostro.MUY_CERCA
        if (abs(m.centerOffsetX) > MAX_CENTER_OFFSET_X || abs(m.centerOffsetY) > MAX_CENTER_OFFSET_Y) {
            return AvisoCalidadRostro.DESCENTRADO
        }
        return AvisoCalidadRostro.NINGUNO
    }

    private fun esFrontal(m: FaceFrameMetrics, maxYaw: Float): Boolean {
        return abs(m.headEulerAngleY) <= maxYaw && abs(m.headEulerAngleZ) <= MAX_FRONTAL_ROLL_DEG
    }

    private fun ojosAbiertos(m: FaceFrameMetrics): Boolean {
        val left = m.leftEyeOpenProb ?: return false
        val right = m.rightEyeOpenProb ?: return false
        return left >= EYE_OPEN_THRESHOLD && right >= EYE_OPEN_THRESHOLD
    }

    private fun ojosCerrados(m: FaceFrameMetrics): Boolean {
        val left = m.leftEyeOpenProb ?: return false
        val right = m.rightEyeOpenProb ?: return false
        return left <= EYE_CLOSED_THRESHOLD && right <= EYE_CLOSED_THRESHOLD
    }

    private fun procesarPasoCentrar(m: FaceFrameMetrics): LivenessDecision {
        if (!esFrontal(m, MAX_FRONTAL_YAW_DEG)) {
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.INCLINADO, capturar = false, expirado = false)
        }
        if (!ojosAbiertos(m)) {
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }

        val inicioEst = inicioEstabilidadMs ?: m.timestampMs.also { inicioEstabilidadMs = it }
        if (m.timestampMs - inicioEst >= holdCenterMs) {
            muestrasCapturadas = 1
            paso = PasoLiveness.PARPADEAR
            inicioEstabilidadMs = null
            vioOjosCerrados = false
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = true, expirado = false)
        }
        return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
    }

    private fun procesarPasoParpadear(m: FaceFrameMetrics): LivenessDecision {
        if (!esFrontal(m, MAX_FRONTAL_YAW_DEG + 4f)) {
            return construirDecision(AvisoCalidadRostro.INCLINADO, capturar = false, expirado = false)
        }
        if (!vioOjosCerrados) {
            if (ojosCerrados(m)) {
                vioOjosCerrados = true
            }
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }

        if (ojosAbiertos(m) && esFrontal(m, MAX_FRONTAL_YAW_DEG)) {
            muestrasCapturadas = 2
            paso = PasoLiveness.GIRAR_CABEZA
            vioOjosCerrados = false
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = true, expirado = false)
        }
        return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
    }

    private fun procesarPasoGirar(m: FaceFrameMetrics): LivenessDecision {
        if (abs(m.headEulerAngleY) >= MIN_TURN_YAW_DEG) {
            paso = PasoLiveness.VOLVER_CENTRO
            inicioEstabilidadMs = null
        }
        return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
    }

    private fun procesarPasoVolverCentro(m: FaceFrameMetrics): LivenessDecision {
        if (!esFrontal(m, MAX_RETURN_YAW_DEG)) {
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }
        if (!ojosAbiertos(m)) {
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
        }

        val inicioEst = inicioEstabilidadMs ?: m.timestampMs.also { inicioEstabilidadMs = it }
        if (m.timestampMs - inicioEst >= holdReturnMs) {
            muestrasCapturadas = TOTAL_MUESTRAS
            paso = PasoLiveness.COMPLETADO
            inicioEstabilidadMs = null
            return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = true, expirado = false)
        }
        return construirDecision(AvisoCalidadRostro.NINGUNO, capturar = false, expirado = false)
    }

    private fun construirDecision(
        aviso: AvisoCalidadRostro,
        capturar: Boolean,
        expirado: Boolean
    ): LivenessDecision {
        val progreso = when (paso) {
            PasoLiveness.CENTRAR_ROSTRO -> if (inicioEstabilidadMs != null) 0.15f else 0.05f
            PasoLiveness.PARPADEAR -> if (vioOjosCerrados) 0.50f else 0.33f
            PasoLiveness.GIRAR_CABEZA -> 0.66f
            PasoLiveness.VOLVER_CENTRO -> 0.85f
            PasoLiveness.COMPLETADO -> 1.0f
        }
        return LivenessDecision(
            pasoActual = paso,
            aviso = aviso,
            capturarMuestra = capturar,
            muestrasCapturadas = muestrasCapturadas,
            totalMuestras = TOTAL_MUESTRAS,
            progreso = progreso,
            completado = paso == PasoLiveness.COMPLETADO,
            expirado = expirado
        )
    }
}
