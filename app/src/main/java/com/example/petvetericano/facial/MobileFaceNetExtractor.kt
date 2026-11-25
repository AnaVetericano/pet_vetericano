package com.example.petvetericano.facial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Extractor on-device de embeddings faciales (192D) usando MobileFaceNet (.tflite) con LiteRT.
 *
 * Tensor verificado en assets/mobilefacenet.tflite:
 * - Entrada: [1, 112, 112, 3] FLOAT32 (RGB normalizado a [-1, 1] mediante (c - 128) / 128)
 * - Salida:  [1, 192] FLOAT32 (se aplica normalización L2 antes de devolver)
 */
class MobileFaceNetExtractor(context: Context) : Closeable {

    companion object {
        const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val BYTES_PER_FLOAT = 4
        private const val CHANNELS_RGB = 3
    }

    private val lock = Any()
    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer
    private val pixelBuffer = IntArray(FacialMath.INPUT_SIZE_PX * FacialMath.INPUT_SIZE_PX)

    init {
        val modelBuffer = cargarModeloDesdeAssets(context.applicationContext)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
        }
        interpreter = Interpreter(modelBuffer, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        require(
            inputShape.size == 4 &&
                inputShape[1] == FacialMath.INPUT_SIZE_PX &&
                inputShape[2] == FacialMath.INPUT_SIZE_PX &&
                inputShape[3] == CHANNELS_RGB
        ) {
            "Forma de entrada inesperada en $MODEL_ASSET_NAME: ${inputShape.contentToString()}"
        }
        require(outputShape.size == 2 && outputShape[1] == FacialMath.EMBEDDING_DIM) {
            "Forma de salida inesperada en $MODEL_ASSET_NAME: ${outputShape.contentToString()}"
        }

        inputBuffer = ByteBuffer.allocateDirect(
            1 * FacialMath.INPUT_SIZE_PX * FacialMath.INPUT_SIZE_PX * CHANNELS_RGB * BYTES_PER_FLOAT
        ).order(ByteOrder.nativeOrder())
    }

    /**
     * Recorta el rostro detectado del frame ya orientado verticalmente, lo escala a 112x112
     * y devuelve el embedding L2-normalizado de 192 dimensiones.
     */
    fun extraerEmbedding(frameUpright: Bitmap, boundingBox: Rect): FloatArray {
        val cropRect = FacialMath.computeSquareFaceCropRect(
            boxLeft = boundingBox.left,
            boxTop = boundingBox.top,
            boxRight = boundingBox.right,
            boxBottom = boundingBox.bottom,
            imageWidth = frameUpright.width,
            imageHeight = frameUpright.height
        )

        val cropped = Bitmap.createBitmap(
            frameUpright,
            cropRect.left,
            cropRect.top,
            cropRect.width,
            cropRect.height
        )
        val scaled = try {
            if (cropped.width == FacialMath.INPUT_SIZE_PX && cropped.height == FacialMath.INPUT_SIZE_PX) {
                cropped
            } else {
                Bitmap.createScaledBitmap(
                    cropped,
                    FacialMath.INPUT_SIZE_PX,
                    FacialMath.INPUT_SIZE_PX,
                    true
                )
            }
        } finally {
            // Si createScaledBitmap creó un nuevo bitmap, reciclaremos cropped después.
        }

        return try {
            synchronized(lock) {
                llenarBufferEntrada(scaled)
                val output = Array(1) { FloatArray(FacialMath.EMBEDDING_DIM) }
                interpreter.run(inputBuffer, output)
                FacialMath.l2Normalize(output[0])
            }
        } finally {
            if (scaled !== cropped && !scaled.isRecycled) {
                scaled.recycle()
            }
            if (!cropped.isRecycled) {
                cropped.recycle()
            }
        }
    }

    private fun llenarBufferEntrada(bitmap112: Bitmap) {
        inputBuffer.rewind()
        bitmap112.getPixels(
            pixelBuffer,
            0,
            FacialMath.INPUT_SIZE_PX,
            0,
            0,
            FacialMath.INPUT_SIZE_PX,
            FacialMath.INPUT_SIZE_PX
        )
        for (pixel in pixelBuffer) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            inputBuffer.putFloat((r - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((g - IMAGE_MEAN) / IMAGE_STD)
            inputBuffer.putFloat((b - IMAGE_MEAN) / IMAGE_STD)
        }
        inputBuffer.rewind()
    }

    private fun cargarModeloDesdeAssets(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_ASSET_NAME).use { afd ->
            FileInputStream(afd.fileDescriptor).use { fis ->
                return fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            try {
                interpreter.close()
            } catch (_: Exception) {
            }
        }
    }
}
