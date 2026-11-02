package com.example.petvetericano.ui.reporte

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val LIMITE_VIDEO_BYTES: Long = 45L * 1024 * 1024
const val LIMITE_VIDEO_DURACION_MS: Long = 16_000L

class CaptureVideoConLimites : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return super.createIntent(context, input).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 15)
            putExtra(MediaStore.EXTRA_SIZE_LIMIT, LIMITE_VIDEO_BYTES)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
    }
}

sealed class ResultadoValidacionVideo {
    object Valido : ResultadoValidacionVideo()
    object ExcedeTamano : ResultadoValidacionVideo()
    object ExcedeDuracion : ResultadoValidacionVideo()
    object ErrorLectura : ResultadoValidacionVideo()
}

object ReporteMediaHelper {

    fun obtenerDirectorioDrafts(context: Context): File {
        val dir = File(context.filesDir, "reporte_drafts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun purgarDraftsAntiguos(context: Context) {
        val dir = File(context.filesDir, "reporte_drafts")
        if (dir.exists() && dir.isDirectory) {
            val ahora = System.currentTimeMillis()
            val limiteMs = 7L * 24 * 60 * 60 * 1000L
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (ahora - file.lastModified() > limiteMs)) {
                    file.delete()
                }
            }
        }
    }

    fun crearArchivoCamaraDraft(context: Context, esVideo: Boolean): Pair<File, Uri> {
        val dir = obtenerDirectorioDrafts(context)
        val prefix = if (esVideo) "draft_video_" else "draft_foto_"
        val ext = if (esVideo) ".mp4" else ".jpg"
        val file = File(dir, prefix + System.currentTimeMillis() + ext)
        file.parentFile?.mkdirs()
        file.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        return Pair(file, uri)
    }

    suspend fun copiarUriADraft(context: Context, uri: Uri, esVideo: Boolean): File? = withContext(Dispatchers.IO) {
        try {
            val dir = obtenerDirectorioDrafts(context)
            val prefix = if (esVideo) "draft_video_" else "draft_foto_"
            val ext = if (esVideo) ".mp4" else ".jpg"
            val localFile = File(dir, prefix + System.currentTimeMillis() + ext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!esVideo) {
                optimizarImagen(localFile)
            }
            localFile
        } catch (e: Exception) {
            android.util.Log.e("ReporteMediaHelper", "Error copiarUriADraft", e)
            null
        }
    }

    suspend fun optimizarImagen(archivo: File): File = withContext(Dispatchers.Default) {
        try {
            if (!archivo.exists() || archivo.length() == 0L) return@withContext archivo

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(archivo.absolutePath, boundsOptions)

            val maxDimension = 1920
            var sampleSize = 1
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDimension || (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(archivo.absolutePath, decodeOptions) ?: return@withContext archivo

            val orientacionExif = try {
                val exif = ExifInterface(archivo.absolutePath)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientacionExif) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                val rotado = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotado !== rawBitmap) rawBitmap.recycle()
                rotado
            } else {
                rawBitmap
            }

            val tempFile = File(archivo.parentFile, "opt_${archivo.name}")
            FileOutputStream(tempFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            finalBitmap.recycle()

            if (tempFile.exists() && tempFile.length() > 0L) {
                tempFile.copyTo(archivo, overwrite = true)
                tempFile.delete()
            }
            archivo
        } catch (e: Exception) {
            android.util.Log.e("ReporteMediaHelper", "Error optimizarImagen", e)
            archivo
        }
    }

    suspend fun validarVideo(archivo: File): ResultadoValidacionVideo = withContext(Dispatchers.IO) {
        if (!archivo.exists() || archivo.length() == 0L) {
            return@withContext ResultadoValidacionVideo.ErrorLectura
        }
        if (archivo.length() > LIMITE_VIDEO_BYTES) {
            archivo.delete()
            return@withContext ResultadoValidacionVideo.ExcedeTamano
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(archivo.absolutePath)
            val duracionStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duracionMs = duracionStr?.toLongOrNull() ?: 0L
            if (duracionMs <= 0L) {
                archivo.delete()
                return@withContext ResultadoValidacionVideo.ErrorLectura
            }
            if (duracionMs > LIMITE_VIDEO_DURACION_MS) {
                archivo.delete()
                return@withContext ResultadoValidacionVideo.ExcedeDuracion
            }
        } catch (_: Exception) {
            archivo.delete()
            return@withContext ResultadoValidacionVideo.ErrorLectura
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        ResultadoValidacionVideo.Valido
    }
}