package com.example.petvetericano.facial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class FacialRecognitionUnitTest {

    @Test
    fun mobileFaceNetAsset_existeYTieneCabeceraTfl3Valida() {
        val candidatos = listOf(
            File("src/main/assets/${MobileFaceNetExtractor.MODEL_ASSET_NAME}"),
            File("app/src/main/assets/${MobileFaceNetExtractor.MODEL_ASSET_NAME}")
        )
        val asset = candidatos.firstOrNull { it.exists() }
        assertTrue("Debe existir assets/${MobileFaceNetExtractor.MODEL_ASSET_NAME}", asset != null)
        val bytes = asset!!.readBytes()
        assertTrue("El modelo debe pesar > 1 MB", bytes.size > 1_000_000)
        val magic = String(bytes, 4, 4, Charsets.US_ASCII)
        assertEquals("TFL3", magic)
    }

    @Test
    fun facialMath_normalizacionSimilitudYMatchingDistinguenMismoRostroDeImpostor() {
        val base = FloatArray(FacialMath.EMBEDDING_DIM) { i -> ((i % 13) - 6).toFloat() / 10f }
        val muestra1 = base.copyOf()
        val muestra2 = FloatArray(FacialMath.EMBEDDING_DIM) { i -> base[i] + if (i % 5 == 0) 0.04f else -0.02f }
        val muestra3 = FloatArray(FacialMath.EMBEDDING_DIM) { i -> base[i] + if (i % 7 == 0) -0.03f else 0.02f }

        val norm = FacialMath.l2Normalize(base)
        val normLen = sqrt(norm.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1.0f, normLen, 1e-4f)

        val muestras = listOf(muestra1, muestra2, muestra3)
        assertTrue(FacialMath.validateEnrollmentConsistency(muestras))

        val centroid = FacialMath.averageAndNormalize(muestras)

        // Mismo usuario con pequeña variación de luz
        val mismoUsuario = FloatArray(FacialMath.EMBEDDING_DIM) { i -> base[i] + if (i % 3 == 0) 0.05f else -0.04f }
        val resMismo = FacialMath.evaluateMatch(mismoUsuario, centroid, muestras)
        assertTrue("Debe aceptar al mismo usuario (sim=${resMismo.similitudCentroide})", resMismo.coincide)
        assertTrue(resMismo.similitudCentroide > 0.90f)

        // Otra persona (vector pseudo-ortogonal)
        val impostor = FloatArray(FacialMath.EMBEDDING_DIM) { i -> if (i % 2 == 0) -base[i] else base[FacialMath.EMBEDDING_DIM - 1 - i] }
        val resImpostor = FacialMath.evaluateMatch(impostor, centroid, muestras)
        assertFalse("Debe rechazar a otra persona (sim=${resImpostor.similitudCentroide})", resImpostor.coincide)
        assertTrue(resImpostor.similitudCentroide < FacialMath.DEFAULT_COSINE_THRESHOLD)
    }

    @Test
    fun facialMath_validateEnrollmentConsistency_rechazaMuestrasInconsistentes() {
        val v1 = FloatArray(FacialMath.EMBEDDING_DIM) { i -> if (i < 96) 1f else 0f }
        val v2 = FloatArray(FacialMath.EMBEDDING_DIM) { i -> if (i >= 96) 1f else 0f }
        assertFalse(FacialMath.validateEnrollmentConsistency(listOf(v1, v2)))
    }

    @Test
    fun facialMath_computeSquareFaceCropRect_mantieneCuadradoDentroDeLimites() {
        val rect = FacialMath.computeSquareFaceCropRect(
            boxLeft = 10,
            boxTop = 5,
            boxRight = 110,
            boxBottom = 145,
            imageWidth = 480,
            imageHeight = 640
        )
        assertEquals(rect.width, rect.height)
        assertTrue(rect.left >= 0 && rect.top >= 0)
        assertTrue(rect.left + rect.width <= 480)
        assertTrue(rect.top + rect.height <= 640)
    }

    @Test
    fun livenessStateMachine_bloqueaFotoEstaticaYCompletaConParpadeoYGiroReal() {
        val machine = LivenessStateMachine(holdCenterMs = 300L, holdReturnMs = 200L, timeoutMs = 20_000L)
        var t = 1000L

        fun frameFrontal(
            ojos: Float = 0.92f,
            yaw: Float = 0f,
            roll: Float = 0f,
            faceCount: Int = 1
        ): LivenessStateMachine.LivenessDecision {
            t += 150L
            return machine.procesarFrame(
                LivenessStateMachine.FaceFrameMetrics(
                    faceCount = faceCount,
                    faceWidthRatio = 0.45f,
                    centerOffsetX = 0.02f,
                    centerOffsetY = -0.03f,
                    headEulerAngleY = yaw,
                    headEulerAngleZ = roll,
                    leftEyeOpenProb = ojos,
                    rightEyeOpenProb = ojos,
                    timestampMs = t
                )
            )
        }

        // Paso 1: mantener rostro centrado -> captura #1 y avanza a PARPADEAR
        val d1 = frameFrontal()
        assertFalse(d1.capturarMuestra)
        frameFrontal()
        val dCap1 = frameFrontal()
        assertTrue("Debe capturar muestra #1 tras sostener rostro centrado", dCap1.capturarMuestra)
        assertEquals(LivenessStateMachine.PasoLiveness.PARPADEAR, dCap1.pasoActual)

        // Foto estática (ojos siempre abiertos) nunca debe avanzar de PARPADEAR
        repeat(6) {
            val estatico = frameFrontal(ojos = 0.95f)
            assertFalse(estatico.capturarMuestra)
            assertEquals(LivenessStateMachine.PasoLiveness.PARPADEAR, estatico.pasoActual)
        }

        // Paso 2: parpadeo real (ojos cerrados -> ojos abiertos) -> captura #2 y avanza a GIRAR_CABEZA
        val cerrados = frameFrontal(ojos = 0.10f)
        assertFalse(cerrados.capturarMuestra)
        val dCap2 = frameFrontal(ojos = 0.90f)
        assertTrue("Debe capturar muestra #2 tras parpadeo", dCap2.capturarMuestra)
        assertEquals(LivenessStateMachine.PasoLiveness.GIRAR_CABEZA, dCap2.pasoActual)

        // Paso 3: girar cabeza >= 15° -> pasa a VOLVER_CENTRO -> volver frontal y sostener -> captura #3 y COMPLETADO
        val giro = frameFrontal(yaw = 20f)
        assertEquals(LivenessStateMachine.PasoLiveness.VOLVER_CENTRO, giro.pasoActual)
        assertFalse(giro.capturarMuestra)

        val regreso1 = frameFrontal(yaw = 2f)
        assertFalse(regreso1.completado)
        val regreso2 = frameFrontal(yaw = 1f)
        assertFalse(regreso2.completado)
        val dCap3 = frameFrontal(yaw = 0f)
        assertTrue("Debe capturar muestra #3 al volver al centro", dCap3.capturarMuestra)
        assertTrue("Debe completar el flujo de liveness", dCap3.completado)
        assertEquals(LivenessStateMachine.TOTAL_MUESTRAS, dCap3.muestrasCapturadas)
        assertTrue(abs(dCap3.progreso - 1.0f) < 1e-4f)
    }

    @Test
    fun livenessStateMachine_detectaMultiplesRostrosYTimeout() {
        val machine = LivenessStateMachine(timeoutMs = 1_000L)
        val multi = machine.procesarFrame(
            LivenessStateMachine.FaceFrameMetrics(faceCount = 2, timestampMs = 100L)
        )
        assertEquals(LivenessStateMachine.AvisoCalidadRostro.MULTIPLES_ROSTROS, multi.aviso)
        assertFalse(multi.completado)

        val expirado = machine.procesarFrame(
            LivenessStateMachine.FaceFrameMetrics(faceCount = 0, timestampMs = 1_500L)
        )
        assertTrue(expirado.expirado)
    }

    @Test
    fun facialStorageManager_enmascararCorreoProtegePrivacidad() {
        assertEquals("ju***@gmail.com", FacialStorageManager.enmascararCorreo("JuanPerez@Gmail.com"))
        assertEquals("a***@vet.co", FacialStorageManager.enmascararCorreo("ab@vet.co"))
    }
}
