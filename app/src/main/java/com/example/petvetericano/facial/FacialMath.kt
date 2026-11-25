package com.example.petvetericano.facial

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Utilidades matemáticas puras para embeddings faciales de MobileFaceNet (192D).
 * Sin dependencias de Android para permitir pruebas unitarias rápidas en JVM.
 */
object FacialMath {

    const val INPUT_SIZE_PX = 112
    const val EMBEDDING_DIM = 192

    /**
     * Umbral de similitud coseno sobre vectores L2-normalizados.
     * 0.68 equivale a una distancia euclidiana <= 0.80 en la hiperesfera unitaria:
     * estricto contra impostores y estable ante variaciones moderadas de iluminación.
     */
    const val DEFAULT_COSINE_THRESHOLD = 0.68f
    const val MIN_SAMPLE_COSINE_THRESHOLD = 0.66f
    const val MIN_ENROLLMENT_CONSISTENCY = 0.72f

    private const val NORM_EPSILON = 1e-6f

    data class CropRect(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    data class ResultadoComparacionFacial(
        val coincide: Boolean,
        val similitudCentroide: Float,
        val mejorSimilitudMuestra: Float,
        val distanciaEuclidiana: Float,
        val umbralAplicado: Float
    )

    /**
     * Normaliza un vector a norma L2 unitaria (||v||_2 = 1).
     * Lanza [IllegalArgumentException] si el vector está vacío, tiene valores no finitos o norma cero.
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty()) { "El embedding facial no puede estar vacío." }
        var sumSq = 0.0
        for (v in vector) {
            require(v.isFinite()) { "El embedding facial contiene valores no finitos." }
            sumSq += (v.toDouble() * v.toDouble())
        }
        val norm = sqrt(sumSq).toFloat()
        require(norm > NORM_EPSILON) { "El embedding facial tiene norma cero." }

        val out = FloatArray(vector.size)
        for (i in vector.indices) {
            out[i] = vector[i] / norm
        }
        return out
    }

    /**
     * Calcula la similitud coseno entre dos embeddings (rango [-1, 1]).
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.isNotEmpty() && a.size == b.size) {
            "Los embeddings deben tener la misma dimensión (${a.size} vs ${b.size})."
        }
        val na = l2Normalize(a)
        val nb = l2Normalize(b)
        var dot = 0.0
        for (i in na.indices) {
            dot += na[i].toDouble() * nb[i].toDouble()
        }
        return dot.toFloat().coerceIn(-1f, 1f)
    }

    /**
     * Distancia euclidiana L2 entre dos vectores previamente normalizados a longitud unitaria.
     */
    fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        require(a.isNotEmpty() && a.size == b.size) {
            "Los embeddings deben tener la misma dimensión (${a.size} vs ${b.size})."
        }
        val na = l2Normalize(a)
        val nb = l2Normalize(b)
        var sumSq = 0.0
        for (i in na.indices) {
            val diff = na[i].toDouble() - nb[i].toDouble()
            sumSq += diff * diff
        }
        return sqrt(sumSq).toFloat()
    }

    /**
     * Promedia una lista de embeddings y re-normaliza el centroide resultante a norma L2 = 1.
     */
    fun averageAndNormalize(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Se requiere al menos un embedding para promediar." }
        val dim = vectors.first().size
        require(dim > 0) { "La dimensión del embedding debe ser mayor que cero." }
        val acc = DoubleArray(dim)
        for (vec in vectors) {
            val normVec = l2Normalize(vec)
            require(normVec.size == dim) { "Todos los embeddings deben compartir la misma dimensión." }
            for (i in 0 until dim) {
                acc[i] += normVec[i].toDouble()
            }
        }
        val avg = FloatArray(dim) { i -> (acc[i] / vectors.size).toFloat() }
        return l2Normalize(avg)
    }

    /**
     * Valida que las muestras capturadas durante el enrolamiento correspondan coherentemente
     * al mismo rostro (evita guardar capturas borrosas o corruptas).
     */
    fun validateEnrollmentConsistency(
        samples: List<FloatArray>,
        minPairwiseCosine: Float = MIN_ENROLLMENT_CONSISTENCY
    ): Boolean {
        if (samples.size < 2) return samples.size == 1
        return try {
            val normalized = samples.map { l2Normalize(it) }
            for (i in 0 until normalized.size) {
                for (j in i + 1 until normalized.size) {
                    if (cosineSimilarity(normalized[i], normalized[j]) < minPairwiseCosine) {
                        return false
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Evalúa si un embedding candidato coincide con el perfil facial enrolado.
     */
    fun evaluateMatch(
        candidate: FloatArray,
        centroid: FloatArray,
        samples: List<FloatArray> = emptyList(),
        threshold: Float = DEFAULT_COSINE_THRESHOLD,
        minSampleThreshold: Float = MIN_SAMPLE_COSINE_THRESHOLD
    ): ResultadoComparacionFacial {
        val normCandidate = l2Normalize(candidate)
        val normCentroid = l2Normalize(centroid)
        val simCentroide = cosineSimilarity(normCandidate, normCentroid)
        val dist = euclideanDistance(normCandidate, normCentroid)
        val mejorMuestra = if (samples.isEmpty()) {
            simCentroide
        } else {
            samples.maxOf { cosineSimilarity(normCandidate, it) }
        }
        val pasaCentroide = simCentroide >= threshold
        val pasaMuestra = samples.isEmpty() || mejorMuestra >= minSampleThreshold
        return ResultadoComparacionFacial(
            coincide = pasaCentroide && pasaMuestra,
            similitudCentroide = simCentroide,
            mejorSimilitudMuestra = mejorMuestra,
            distanciaEuclidiana = dist,
            umbralAplicado = threshold
        )
    }

    /**
     * Calcula un recorte cuadrado centrado sobre la caja detectada por ML Kit con margen
     * proporcional para evitar deformar la relación de aspecto al escalar a 112x112.
     */
    fun computeSquareFaceCropRect(
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        imageWidth: Int,
        imageHeight: Int,
        paddingFraction: Float = 0.16f
    ): CropRect {
        require(imageWidth > 0 && imageHeight > 0) { "Dimensiones de imagen inválidas." }
        val rawW = max(1, boxRight - boxLeft)
        val rawH = max(1, boxBottom - boxTop)
        val centerX = (boxLeft + boxRight) / 2.0f
        val centerY = (boxTop + boxBottom) / 2.0f

        val baseSide = max(rawW, rawH).toFloat()
        val paddedSide = (baseSide * (1f + paddingFraction * 2f)).roundToInt()
        val maxSide = min(imageWidth, imageHeight)
        val side = paddedSide.coerceIn(1, maxSide)

        var left = (centerX - side / 2.0f).roundToInt()
        var top = (centerY - side / 2.0f).roundToInt()

        left = left.coerceIn(0, imageWidth - side)
        top = top.coerceIn(0, imageHeight - side)

        return CropRect(left = left, top = top, width = side, height = side)
    }
}
