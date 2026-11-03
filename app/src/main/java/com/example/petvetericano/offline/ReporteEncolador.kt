package com.example.petvetericano.offline

import android.content.Context
import com.example.petvetericano.SharedPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

object ReporteEncolador {

    suspend fun encolar(
        context: Context,
        idTipo: Int,
        tipoNombre: String?,
        descripcion: String,
        direccion: String?,
        latitud: Double,
        longitud: Double,
        evidencias: List<Pair<String, Boolean>>
    ): Long? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val prefs = SharedPreferencesManager(appContext)
        val idUsuario = prefs.getUserId()
        if (idUsuario <= 0) {
            return@withContext null
        }

        val uuid = UUID.randomUUID().toString()
        val carpetaOffline = File(appContext.filesDir, "reportes_offline/$uuid")
        if (!carpetaOffline.exists() && !carpetaOffline.mkdirs()) {
            return@withContext null
        }

        val listaNuevasEvidencias = mutableListOf<EvidenciaReporteItem>()

        try {
            var index = 0
            for ((rutaOrigen, esVideo) in evidencias) {
                val archivoOrigen = File(rutaOrigen)
                if (archivoOrigen.exists()) {
                    val extension = if (esVideo) ".mp4" else ".jpg"
                    val prefijo = if (esVideo) "video_" else "foto_"
                    val nombreUnico = "${prefijo}${index}_${System.currentTimeMillis()}${extension}"
                    val archivoDestino = File(carpetaOffline, nombreUnico)

                    archivoOrigen.copyTo(archivoDestino, overwrite = true)
                    archivoOrigen.delete()

                    listaNuevasEvidencias.add(
                        EvidenciaReporteItem(
                            path = archivoDestino.absolutePath,
                            esVideo = esVideo,
                            urlRemota = null
                        )
                    )
                    index++
                }
            }

            val latStr = String.format(Locale.US, "%.7f", latitud)
            val lonStr = String.format(Locale.US, "%.7f", longitud)
            val evidenciasJson = ReporteEvidenciasParser.serializar(listaNuevasEvidencias)

            val entidad = ReporteLocalEntity(
                uuid = uuid,
                idUsuario = idUsuario,
                idTipo = idTipo,
                tipoNombre = tipoNombre,
                descripcion = descripcion,
                latitud = latStr,
                longitud = lonStr,
                direccion = direccion,
                evidenciasJson = evidenciasJson,
                estado = EstadoReporteLocal.PENDIENTE
            )

            val repo = ReporteLocalRepository.getInstance(appContext)
            val idLocal = repo.insertar(entidad)

            if (idLocal <= 0L) {
                carpetaOffline.deleteRecursively()
                return@withContext null
            }

            ReporteSyncScheduler.encolar(appContext, idLocal)
            return@withContext idLocal
        } catch (e: Exception) {
            carpetaOffline.deleteRecursively()
            return@withContext null
        }
    }
}

