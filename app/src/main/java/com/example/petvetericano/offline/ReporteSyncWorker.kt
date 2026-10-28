package com.example.petvetericano.offline

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.models.confirmarpeticion
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Worker en segundo plano encargado de la sincronización y envío resiliente de reportes ciudadanos.
 * Administra el ciclo de vida de evidencias en Cloudinary, verificación anti-duplicados y envío seguro.
 */
class ReporteSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ID_LOCAL = "id_local"
        private const val TAG = "ReporteSyncWorker"
        private const val COORD_TOLERANCIA = 0.0000011
        private const val VENTANA_DUPLICADO_MS = 15L * 60L * 1000L
        private const val MAX_REINTENTOS = 10
    }

    private val repo by lazy { ReporteLocalRepository.getInstance(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val idLocal = inputData.getLong(KEY_ID_LOCAL, -1L)
        if (idLocal == -1L) {
            Log.w(TAG, "idLocal no provisto en inputData (-1)")
            return@withContext Result.success()
        }

        // 1. Recuperar leases vencidos e intentar adquirir lease
        repo.recuperarLeasesVencidos()

        val filaInicial = repo.obtenerPorId(idLocal)
        if (filaInicial == null || filaInicial.estado == EstadoReporteLocal.ENVIADO) {
            Log.i(TAG, "Reporte $idLocal inexistente o ya enviado.")
            return@withContext Result.success()
        }

        val leaseAdquirido = repo.intentarAdquirirLeaseEnvio(idLocal)
        if (!leaseAdquirido) {
            val filaActual = repo.obtenerPorId(idLocal)
            return@withContext if (filaActual?.estado == EstadoReporteLocal.ENVIANDO) {
                Log.i(TAG, "Lease vigente en otro worker para idLocal=$idLocal. Retrying.")
                Result.retry()
            } else {
                Log.i(TAG, "No se pudo adquirir lease para idLocal=$idLocal y no está en ENVIANDO.")
                Result.success()
            }
        }

        val reporte = repo.obtenerPorId(idLocal) ?: return@withContext Result.success()

        // 2. Verificar dueño vs usuario actualmente logueado
        val prefs = SharedPreferencesManager(applicationContext)
        val currentUserId = prefs.getUserId()
        if (reporte.idUsuario != currentUserId) {
            Log.w(TAG, "Reporte idLocal=$idLocal pertenece al usuario ${reporte.idUsuario}, actual=$currentUserId. Devolviendo a PENDIENTE.")
            repo.devolverAPendiente(idLocal)
            return@withContext Result.success()
        }

        try {
            ejecutarFlujoEnvio(idLocal, reporte, prefs)
        } catch (ce: CancellationException) {
            Log.w(TAG, "ReporteSyncWorker cancelado para idLocal=$idLocal: ${ce.message}")
            withContext(NonCancellable) {
                val actual = repo.obtenerPorId(idLocal)
                if (actual?.estado == EstadoReporteLocal.ENVIANDO) {
                    repo.marcarError(idLocal, EstadoReporteLocal.ERROR_REINTENTABLE, null)
                }
            }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada en ReporteSyncWorker para idLocal=$idLocal: ${e.message}", e)
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            if (actual.estado == EstadoReporteLocal.ENVIANDO) {
                manejarReintentoEnvio(idLocal, actual, null)
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun ejecutarFlujoEnvio(
        idLocal: Long,
        reporte: ReporteLocalEntity,
        prefs: SharedPreferencesManager
    ): Result {
        // d. Si post_iniciado_en != null, verificar duplicados en servidor ANTES de cualquier POST
        if (reporte.postIniciadoEn != null) {
            val resultadoAntiDuplicado = verificarAntiDuplicados(idLocal, reporte, prefs)
            if (resultadoAntiDuplicado != null) {
                return resultadoAntiDuplicado
            }
        }

        // e. Subir evidencias a Cloudinary una a una guardando urlRemota tras cada subida
        val listaEvidencias = ReporteEvidenciasParser.parsear(reporte.evidenciasJson).toMutableList()
        if (listaEvidencias.isNotEmpty()) {
            CloudinaryUploadHelper.ensureInitialized(applicationContext)
            for (i in listaEvidencias.indices) {
                val ev = listaEvidencias[i]
                if (ev.urlRemota.isNullOrBlank() || !ev.urlRemota.startsWith("http")) {
                    val archivo = File(ev.path)
                    if (!archivo.exists() || !archivo.isFile) {
                        Log.w(TAG, "Archivo de evidencia no encontrado en dispositivo: ${ev.path}")
                        val msg = applicationContext.getString(R.string.reporte_offline_error_evidencia_faltante)
                        repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, null)
                        ReporteNotificationHelper.notificarRequiereAtencion(applicationContext, idLocal, msg)
                        return Result.success()
                    }

                    val resultadoSubida = CloudinaryUploadHelper.subir(applicationContext, ev.path, ev.esVideo)
                    when (resultadoSubida) {
                        is ResultadoSubida.Exito -> {
                            listaEvidencias[i] = ev.copy(urlRemota = resultadoSubida.secureUrl)
                            val jsonActualizado = ReporteEvidenciasParser.serializar(listaEvidencias)
                            repo.actualizarEvidenciasJson(idLocal, jsonActualizado)
                        }
                        is ResultadoSubida.ArchivoNoExiste -> {
                            Log.w(TAG, "Cloudinary reportó archivo no existente: ${ev.path}")
                            val msg = applicationContext.getString(R.string.reporte_offline_error_evidencia_faltante)
                            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, null)
                            ReporteNotificationHelper.notificarRequiereAtencion(applicationContext, idLocal, msg)
                            return Result.success()
                        }
                        is ResultadoSubida.ErrorPermanente -> {
                            Log.w(TAG, "Cloudinary reportó error permanente al subir ${ev.path}: ${resultadoSubida.mensaje}")
                            val msg = applicationContext.getString(R.string.reporte_offline_error_evidencia_subida)
                            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, null)
                            ReporteNotificationHelper.notificarRequiereAtencion(applicationContext, idLocal, msg)
                            return Result.success()
                        }
                        is ResultadoSubida.ErrorRed -> {
                            Log.w(TAG, "Fallo de red en subida Cloudinary: ${resultadoSubida.mensaje}")
                            val actual = repo.obtenerPorId(idLocal) ?: reporte
                            return manejarReintentoEnvio(idLocal, actual, null)
                        }
                    }
                }
            }
        }

        // f. Marcar post_iniciado_en = ahora ANTES del POST
        val tokenPost = prefs.getAccessToken().trim()
        if (tokenPost.isEmpty()) {
            Log.w(TAG, "Token de acceso vacío antes del POST en idLocal=$idLocal")
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, 401)
            ReporteNotificationHelper.notificarSesionExpirada(applicationContext)
            return Result.success()
        }
        RetrofitClient.authToken = tokenPost

        val ahoraPost = System.currentTimeMillis()
        repo.marcarPostIniciado(idLocal, ahoraPost)

        val urlsRemotas = listaEvidencias.mapNotNull { it.urlRemota?.trim() }.filter { it.isNotEmpty() }
        val peticionRequest = confirmarpeticion(
            id_tipo = reporte.idTipo,
            id_estado = 1,
            responsable = reporte.idUsuario,
            descripcion = reporte.descripcion,
            prioridad = null,
            direccion = reporte.direccion ?: "",
            latitud = reporte.latitud.toDoubleOrNull() ?: 0.0,
            longitud = reporte.longitud.toDoubleOrNull() ?: 0.0,
            foto = urlsRemotas.firstOrNull(),
            fotos = urlsRemotas
        )

        // Ejecutar POST
        val response = try {
            RetrofitClient.apiService.enviarPeticion(peticionRequest)
        } catch (e: IOException) {
            Log.w(TAG, "IOException al enviar reporte idLocal=$idLocal: ${e.message}")
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al enviar reporte idLocal=$idLocal: ${e.message}", e)
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, null)
        }

        val code = response.code()
        return if (response.isSuccessful) {
            val body = response.body()
            val idRemoto = body?.id_peticion ?: -1
            val radicado = body?.numero_radicado?.takeIf { it.isNotBlank() }
            val enviadoEnMs = System.currentTimeMillis()

            repo.marcarEnviado(idLocal, idRemoto, radicado, enviadoEnMs)
            eliminarArchivosEvidencia(listaEvidencias)
            ReporteNotificationHelper.notificarExito(applicationContext, idLocal, radicado, idRemoto)
            Result.success()
        } else if (code == 401) {
            Log.w(TAG, "Respuesta HTTP 401 no autorizada al enviar reporte idLocal=$idLocal")
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, 401)
            ReporteNotificationHelper.notificarSesionExpirada(applicationContext)
            Result.success()
        } else if (code == 408 || code == 429 || code in 500..599) {
            Log.w(TAG, "Error de red/servidor transitorio HTTP $code en idLocal=$idLocal")
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            manejarReintentoEnvio(idLocal, actual, code)
        } else {
            // Otros 4xx
            Log.e(TAG, "Error de validación/cliente HTTP $code al enviar reporte idLocal=$idLocal")
            val msg = applicationContext.getString(R.string.reporte_offline_error_datos_servidor)
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, code)
            ReporteNotificationHelper.notificarRequiereAtencion(applicationContext, idLocal, msg)
            Result.success()
        }
    }

    /**
     * Consulta el listado de peticiones para detectar si un intento previo ya creó el reporte en el servidor.
     */
    private suspend fun verificarAntiDuplicados(
        idLocal: Long,
        reporte: ReporteLocalEntity,
        prefs: SharedPreferencesManager
    ): Result? {
        val token = prefs.getAccessToken().trim()
        if (token.isEmpty()) {
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, 401)
            ReporteNotificationHelper.notificarSesionExpirada(applicationContext)
            return Result.success()
        }
        RetrofitClient.authToken = token

        val listResponse = try {
            RetrofitClient.apiService.listarPeticiones("Bearer $token")
        } catch (e: IOException) {
            Log.w(TAG, "IOException al listar peticiones para anti-duplicados: ${e.message}")
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, null)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada al listar peticiones para anti-duplicados: ${e.message}", e)
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, null)
        }

        val code = listResponse.code()
        if (code == 401) {
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, 401)
            ReporteNotificationHelper.notificarSesionExpirada(applicationContext)
            return Result.success()
        }
        if (code == 408 || code == 429 || code in 500..599) {
            Log.w(TAG, "Fallo HTTP $code al listar peticiones; reintentando sin hacer POST")
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, code)
        }
        if (!listResponse.isSuccessful) {
            Log.w(TAG, "Respuesta no exitosa HTTP $code al listar peticiones; reintentando sin hacer POST")
            val actual = repo.obtenerPorId(idLocal) ?: reporte
            return manejarReintentoEnvio(idLocal, actual, code)
        }

        val items = listResponse.body().orEmpty()
        val idsEnlazados = repo.idsRemotosYaEnlazados()
        val evidenciasLocales = ReporteEvidenciasParser.parsear(reporte.evidenciasJson)
        val urlsRemotasLocales = evidenciasLocales.mapNotNull { it.urlRemota?.trim() }.filter { it.isNotEmpty() }

        val duplicado = items.firstOrNull { item ->
            if (idsEnlazados.contains(item.id_peticion)) return@firstOrNull false

            // Condición 1: coincidencia de URL remota subida
            val coincideUrl = urlsRemotasLocales.isNotEmpty() &&
                !item.foto.isNullOrBlank() &&
                urlsRemotasLocales.contains(item.foto.trim())

            if (coincideUrl) return@firstOrNull true

            // Condición 2: descripción + coordenadas (0.0000011) + fecha (>= post_iniciado_en - 15 min)
            val descIgual = !item.descripcion.isNullOrBlank() &&
                item.descripcion.trim() == reporte.descripcion.trim()

            val itemLat = item.ubicacion_latitud?.toDoubleOrNull()
            val itemLng = item.ubicacion_longitud?.toDoubleOrNull()
            val repLat = reporte.latitud.toDoubleOrNull()
            val repLng = reporte.longitud.toDoubleOrNull()
            val coordsCercanas = itemLat != null && itemLng != null && repLat != null && repLng != null &&
                Math.abs(itemLat - repLat) <= COORD_TOLERANCIA &&
                Math.abs(itemLng - repLng) <= COORD_TOLERANCIA

            val postIniciado = reporte.postIniciadoEn ?: 0L
            val itemFechaMs = parsearFechaBogota(item.fecha)
            val umbralFecha = postIniciado - VENTANA_DUPLICADO_MS
            val fechaValida = itemFechaMs != null && itemFechaMs >= umbralFecha

            descIgual && coordsCercanas && fechaValida
        }

        if (duplicado != null) {
            Log.i(TAG, "Duplicado detectado en backend: idPeticion=${duplicado.id_peticion}, radicado=${duplicado.numero_radicado}")
            repo.marcarEnviado(idLocal, duplicado.id_peticion, duplicado.numero_radicado)
            eliminarArchivosEvidencia(evidenciasLocales)
            ReporteNotificationHelper.notificarExito(
                applicationContext,
                idLocal,
                duplicado.numero_radicado,
                duplicado.id_peticion
            )
            return Result.success()
        }

        return null
    }

    /**
     * Parsea una fecha sin zona horaria (USE_TZ=False en backend) interpretándola en America/Bogota,
     * admitiendo formatos con o sin microsegundos.
     */
    private fun parsearFechaBogota(fechaStr: String?): Long? {
        if (fechaStr.isNullOrBlank()) return null
        return try {
            val limpia = fechaStr.trim().replace(' ', 'T')
            val dotIndex = limpia.indexOf('.')
            val (base, millisStr) = if (dotIndex != -1) {
                val sinPunto = limpia.substring(0, dotIndex)
                val resto = limpia.substring(dotIndex + 1).takeWhile { it.isDigit() }
                val ms = resto.take(3).padEnd(3, '0')
                Pair(sinPunto, ms)
            } else {
                val sinZona = limpia.takeWhile { it != 'Z' && it != '+' }
                Pair(sinZona, "000")
            }
            val normalizada = "$base.$millisStr"
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("America/Bogota")
                isLenient = false
            }
            sdf.parse(normalizada)?.time
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo parsear fecha sin zona '$fechaStr': ${e.message}")
            null
        }
    }

    /**
     * Maneja el flujo de reintentos con límite máximo de 10 intentos.
     */
    private fun manejarReintentoEnvio(idLocal: Long, entity: ReporteLocalEntity, httpCode: Int?): Result {
        val nuevosReintentos = entity.reintentos + 1
        return if (nuevosReintentos > MAX_REINTENTOS) {
            Log.w(TAG, "Superado el máximo de reintentos ($MAX_REINTENTOS) para idLocal=$idLocal")
            val msg = applicationContext.getString(R.string.reporte_offline_error_max_reintentos)
            repo.marcarError(idLocal, EstadoReporteLocal.REQUIERE_ATENCION, httpCode, nuevosReintentos)
            ReporteNotificationHelper.notificarRequiereAtencion(applicationContext, idLocal, msg)
            Result.success()
        } else {
            repo.marcarError(idLocal, EstadoReporteLocal.ERROR_REINTENTABLE, httpCode, nuevosReintentos)
            Result.retry()
        }
    }

    /**
     * Elimina físicamente los archivos locales de evidencias tras subida exitosa.
     */
    private fun eliminarArchivosEvidencia(evidencias: List<EvidenciaReporteItem>) {
        for (ev in evidencias) {
            try {
                val f = File(ev.path)
                if (f.exists()) {
                    f.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al eliminar archivo local de evidencia ${ev.path}: ${e.message}")
            }
        }
    }
}

