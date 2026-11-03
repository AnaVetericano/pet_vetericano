package com.example.petvetericano.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Helper para compactar firmas en Base64 recortando el espacio blanco sobrante
 * y reduciendo la resolución máxima a 480 px de ancho de forma proporcional.
 */
object FirmaCompactor {

    private const val PREFIX = "data:image/png;base64,"
    private const val MARGEN_PX = 12
    private const val MAX_WIDTH_PX = 480
    private const val UMBRAL_BLANCO = 250

    /**
     * Compacta la firma en Base64. Ante cualquier excepción, OutOfMemoryError
     * o si el resultado no reduce el tamaño original, devuelve la cadena original.
     */
    fun compactar(dataUri: String?): String? {
        if (dataUri.isNullOrEmpty()) return dataUri
        if (!dataUri.startsWith(PREFIX)) return dataUri

        var originalBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null

        return try {
            val base64Data = dataUri.substring(PREFIX.length)
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return dataUri

            val width = originalBitmap.width
            val height = originalBitmap.height
            if (width <= 0 || height <= 0) return dataUri

            val pixels = IntArray(width * height)
            originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val pixel = pixels[rowOffset + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // Considera no blanco si algún canal R, G o B < 250
                    if (r < UMBRAL_BLANCO || g < UMBRAL_BLANCO || b < UMBRAL_BLANCO) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            // Si no hay píxeles no blancos (lienzo totalmente blanco), devuelve el original
            if (maxX < minX || maxY < minY) {
                return dataUri
            }

            // Recorta a ese rectángulo con un margen de 12 px por lado (sin salirse del bitmap)
            val cropLeft = max(0, minX - MARGEN_PX)
            val cropTop = max(0, minY - MARGEN_PX)
            val cropRight = min(width - 1, maxX + MARGEN_PX)
            val cropBottom = min(height - 1, maxY + MARGEN_PX)

            val cropWidth = cropRight - cropLeft + 1
            val cropHeight = cropBottom - cropTop + 1

            if (cropWidth <= 0 || cropHeight <= 0) {
                return dataUri
            }

            croppedBitmap = Bitmap.createBitmap(originalBitmap, cropLeft, cropTop, cropWidth, cropHeight)

            // Si el ancho recortado supera 480 px, reduce proporcionalmente a 480 px (filtro bilineal). Nunca amplía.
            val bitmapToExport: Bitmap = if (cropWidth > MAX_WIDTH_PX) {
                val scaledHeight = max(1, (cropHeight.toFloat() * MAX_WIDTH_PX / cropWidth.toFloat()).roundToInt())
                scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, MAX_WIDTH_PX, scaledHeight, true)
                scaledBitmap
            } else {
                croppedBitmap
            }

            val outputStream = ByteArrayOutputStream()
            bitmapToExport.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val nuevoBase64 = PREFIX + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // Si el resultado no es más corto que el original, devuelve el original
            if (nuevoBase64.length >= dataUri.length) {
                dataUri
            } else {
                nuevoBase64
            }
        } catch (_: Throwable) {
            dataUri
        } finally {
            try {
                if (scaledBitmap != null && scaledBitmap != croppedBitmap && !scaledBitmap.isRecycled) {
                    scaledBitmap.recycle()
                }
                if (croppedBitmap != null && croppedBitmap != originalBitmap && !croppedBitmap.isRecycled) {
                    croppedBitmap.recycle()
                }
                if (originalBitmap != null && !originalBitmap.isRecycled) {
                    originalBitmap.recycle()
                }
            } catch (_: Throwable) { }
        }
    }
}
