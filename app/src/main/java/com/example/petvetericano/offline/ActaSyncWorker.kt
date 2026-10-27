package com.example.petvetericano.offline

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.petvetericano.MaletinManager
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.models.ActualizarEstadoRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Worker en segundo plano encargado de la sincronización y envío resiliente de Actas de Atención.
 * Ejecuta el ciclo de vida de evidencias en Cloudinary, verificación anti-duplicados,
 * registro remoto (POST) y actualización del estado de la petición (PATCH).
 */
class ActaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ID_LOCAL = "id_local"
        private const val TAG = "ActaSyncWorker"
    }

    private val repo by lazy { ActaLocalRepository.getInstance(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val idLocal = inputData.getLong(KEY_ID_LOCAL, -1L)
        if (idLocal == -1L) {
            Log.w(TAG, "idLocal no provisto en inputData (-1)")
            return@withContext Result.success()
        }

        val acta = repo.obtenerPorIdLocal(idLocal)
        if (acta == null) {
            Log.w(TAG, "No se encontró el acta local con idLocal=$idLocal")
            return@withContext Result.success()
        }

        val prefs = SharedPreferencesManager(applicationContext)
        val currentUserId = prefs.getUserId()
        if (acta.idVeterinario != currentUserId) {
            Log.w(TAG, "El acta pertenece al veterinario ${acta.idVeterinario}, usuario actual=$currentUserId. Se omite.")
            return@withContext Result.success()
        }

        if (acta.estadoSincronizacion !in listOf(
                EstadoSincronizacionActa.PENDIENTE_ENVIO,
                EstadoSincronizacionActa.ERROR_REINTENTABLE,
                EstadoSincronizacionActa.ENVIANDO
            )
        ) {
            Log.i(TAG, "El acta $idLocal está en estado ${acta.estadoSincronizacion}, no requiere envío.")
            return@withContext Result.success()
        }

        // 1. Recuperar leases vencidos e intentar adquirir lease exclusivo de envío
        repo.recuperarLeasesVencidos()
        val leaseAdquirido = repo.intentarAdquirirLeaseEnvio(idLocal)
        if (!leaseAdquirido) {
            val filaActual = repo.obtenerPorIdLocal(idLocal)
            return@withContext if (filaActual?.estadoSincronizacion == EstadoSincronizacionActa.ENVIANDO) {
                Log.i(TAG, "Lease vigente de otro intento en idLocal=$idLocal. Retrying.")
                Result.retry()
            } else {
                Log.i(TAG, "No se pudo adquirir lease para idLocal=$idLocal y no está en ENVIANDO.")
                Result.success()
            }
        }

        try {
            ejecutarFlujoEnvio(idLocal, acta, prefs)
        } catch (ce: CancellationException) {
            Log.w(TAG, "ActaSyncWorker cancelado para idLocal=$idLocal: ${ce.message}")
            withContext(NonCancellable) {
                val fila = repo.obtenerPorIdLocal(idLocal)
                if (fila?.estadoSincronizacion == EstadoSincronizacionActa.ENVIANDO) {
                    repo.marcarEstado(idLocal, EstadoSincronizacionActa.ERROR_REINTENTABLE, null, "Worker cancelado")
                }
            }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada en ActaSyncWorker para idLocal=$idLocal: ${e.message}", e)
            val fila = repo.obtenerPorIdLocal(idLocal) ?: acta
            if (fila.estadoSincronizacion == EstadoSincronizacionActa.ENVIANDO) {
                ejecutarRutaReintento(idLocal, fila, null, "Excepción inesperada: ${e.message}")
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun ejecutarFlujoEnvio(
        idLocal: Long,
        actaInicial: ActaLocalEntity,
        prefs: SharedPreferencesManager
    ): Result {
        // 2. Token de sesión
        val token = prefs.getAccessToken().trim()
        if (token.isEmpty()) {
            Log.w(TAG, "Token vacío o nulo para idLocal=$idLocal, marcando como sesión expirada")
            return manejarSesionExpirada(idLocal)
        }

        var acta = repo.obtenerPorIdLocal(idLocal) ?: actaInicial

        // 3. Si idSeguimientoRemoto != null -> salta directo al paso 8 (solo PATCH)
        if (acta.idSeguimientoRemoto != null) {
            Log.i(TAG, "idSeguimientoRemoto ya existe (${acta.idSeguimientoRemoto}), saltando a PATCH paso 8")
            return ejecutarPaso8Patch(idLocal, acta, token)
        }

        // 4. Evidencias a Cloudinary
        CloudinaryUploadHelper.ensureInitialized(applicationContext)
        val listaEvidencias = ActaFormSnapshotMapper.deserializarEvidencias(acta.evidenciasJson).toMutableList()
        var huboCambioEvidencias = false

        for (i in listaEvidencias.indices) {
            val ev = listaEvidencias[i]
            val remoteUrl = ev.remoteUrl
            if (remoteUrl.isNullOrBlank() || !remoteUrl.startsWith("http")) {
                val resultado = CloudinaryUploadHelper.subir(applicationContext, ev.localPath, ev.esVideo)
                when (resultado) {
                    is ResultadoSubida.Exito -> {
                        listaEvidencias[i] = ev.copy(remoteUrl = resultado.secureUrl)
                        huboCambioEvidencias = true
                        val nuevoJson = ActaFormSnapshotMapper.serializarEvidencias(listaEvidencias)
                        repo.actualizarEvidenciasJson(idLocal, nuevoJson)
                    }
                    is ResultadoSubida.ArchivoNoExiste -> {
                        val msg = applicationContext.getString(R.string.acta_offline_error_evidencia_faltante)
                        repo.marcarEstado(idLocal, EstadoSincronizacionActa.REQUIERE_ATENCION, null, msg)
                        ActaNotificationHelper.notificarRequiereRevision(
                            applicationContext,
                            acta.nroRadicadoAtencion,
                            acta.idPeticion,
                            msg
                        )
                        return Result.success()
                    }
                    is ResultadoSubida.ErrorPermanente -> {
                        val msg = applicationContext.getString(R.string.acta_offline_error_evidencia_subida)
                        repo.marcarEstado(idLocal, EstadoSincronizacionActa.REQUIERE_ATENCION, null, msg)
                        ActaNotificationHelper.notificarRequiereRevision(
                            applicationContext,
                            acta.nroRadicadoAtencion,
                            acta.idPeticion,
                            msg
                        )
                        return Result.success()
                    }
                    is ResultadoSubida.ErrorRed -> {
                        return ejecutarRutaReintento(
                            idLocal,
                            acta,
                            null,
                            "Error de red Cloudinary: ${resultado.mensaje}"
                        )
                    }
                }
            }
        }

        if (huboCambioEvidencias) {
            acta = repo.obtenerPorIdLocal(idLocal) ?: acta.copy(
                evidenciasJson = ActaFormSnapshotMapper.serializarEvidencias(listaEvidencias)
            )
        }

        val fotosUrls = mutableListOf<String>()
        val videosUrls = mutableListOf<String>()
        for (item in listaEvidencias) {
            val u = item.remoteUrl ?: continue
            if (item.esVideo) {
                videosUrls.add(u)
            } else {
                fotosUrls.add(u)
            }
        }

        // 5. ANTI-DUPLICADOS (OBLIGATORIO y SIEMPRE, aunque reintentos == 0)
        val listResponse = try {
            RetrofitClient.apiService.listarSeguimientosVisita(
                token = "Bearer $token",
                idPeticion = acta.idPeticion,
                numeroRadicado = acta.nroRadicadoAtencion
            )
        } catch (e: IOException) {
            Log.e(TAG, "IOException al listarSeguimientosVisita: ${e.message}", e)
            return ejecutarRutaReintento(idLocal, acta, null, "IOException al listar seguimientos: ${e.message}")
        }

        if (listResponse.code() == 401) {
            return manejarSesionExpirada(idLocal)
        }

        if (!listResponse.isSuccessful) {
            val code = listResponse.code()
            Log.w(TAG, "Error HTTP $code al listarSeguimientosVisita")
            return ejecutarRutaReintento(idLocal, acta, code, "Error HTTP $code al listar seguimientos")
        }

        val seguimientos = listResponse.body().orEmpty()
        val coincidencia = seguimientos.firstOrNull {
            it.idPeticion == acta.idPeticion && it.numeroRadicado == acta.nroRadicadoAtencion
        }

        if (coincidencia != null && coincidencia.idSeguimiento != null) {
            val idRemoto = coincidencia.idSeguimiento
            Log.i(TAG, "Coincidencia exacta encontrada en servidor: idSeguimiento=$idRemoto")
            repo.guardarIdSeguimientoRemoto(idLocal, idRemoto)
            try {
                MaletinManager.getInstance(applicationContext).sincronizarDesdeServidor(token)
            } catch (e: Exception) {
                Log.w(TAG, "Error al sincronizar maletín tras coincidencia remota: ${e.message}", e)
            }
            ActaNotificationHelper.notificarExito(
                applicationContext,
                acta.nroRadicadoAtencion,
                acta.idPeticion
            )
            return ejecutarPaso8Patch(idLocal, acta.copy(idSeguimientoRemoto = idRemoto), token)
        }

        // 6. POST crearSeguimientoPeticionVisita
        val postRequest = ActaRequestBuilder.build(
            entity = acta,
            fotosUrls = fotosUrls,
            videosUrls = videosUrls,
            sincronizacionOffline = true
        )

        val postResponse = try {
            RetrofitClient.apiService.crearSeguimientoPeticionVisita("Bearer $token", postRequest)
        } catch (e: IOException) {
            Log.e(TAG, "IOException al crearSeguimientoPeticionVisita: ${e.message}", e)
            return ejecutarRutaReintento(idLocal, acta, null, "IOException POST crear: ${e.message}")
        }

        val postCode = postResponse.code()
        when {
            postCode == 201 || (postResponse.isSuccessful && postResponse.body() != null) -> {
                val body = postResponse.body()
                val idRemoto = body?.idSeguimiento ?: 0
                if (idRemoto > 0) {
                    repo.guardarIdSeguimientoRemoto(idLocal, idRemoto)
                }

                val conflictos = body?.alertasFarmacia?.conflictosStock.orEmpty()
                try {
                    MaletinManager.getInstance(applicationContext).sincronizarDesdeServidor(token)
                } catch (e: Exception) {
                    Log.w(TAG, "Error sincronizando maletín tras 201: ${e.message}", e)
                }

                if (conflictos.isNotEmpty()) {
                    val nombres = conflictos
                        .mapNotNull { it.nombreMedicamento?.takeIf { n -> n.isNotBlank() } }
                        .ifEmpty { listOf("Medicamento") }
                        .joinToString(", ")
                    ActaNotificationHelper.notificarExitoConAlertaStock(
                        applicationContext,
                        acta.nroRadicadoAtencion,
                        acta.idPeticion,
                        nombres
                    )
                } else {
                    ActaNotificationHelper.notificarExito(
                        applicationContext,
                        acta.nroRadicadoAtencion,
                        acta.idPeticion
                    )
                }

                return ejecutarPaso8Patch(idLocal, acta.copy(idSeguimientoRemoto = idRemoto), token)
            }

            postCode == 401 -> {
                return manejarSesionExpirada(idLocal)
            }

            postCode == 408 || postCode == 429 || postCode >= 500 -> {
                return ejecutarRutaReintento(idLocal, acta, postCode, "Error HTTP $postCode en POST crear")
            }

            else -> {
                // 400 y cualquier otro 4xx salvo 401, 408 y 429
                val rawBody = try {
                    postResponse.errorBody()?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                Log.e(TAG, "Error 4xx en crearSeguimiento: code=$postCode, body=$rawBody")
                val mensajeLegible = extraerMensajeLegible(rawBody, applicationContext)
                repo.marcarEstado(idLocal, EstadoSincronizacionActa.REQUIERE_ATENCION, postCode, mensajeLegible)
                ActaNotificationHelper.notificarRequiereRevision(
                    applicationContext,
                    acta.nroRadicadoAtencion,
                    acta.idPeticion,
                    mensajeLegible
                )
                return Result.success()
            }
        }
    }

    // 8. PATCH peticiones/{id_peticion}/estado/
    private suspend fun ejecutarPaso8Patch(
        idLocal: Long,
        acta: ActaLocalEntity,
        token: String
    ): Result {
        if (acta.idPeticion <= 0) {
            repo.marcarEstado(idLocal, EstadoSincronizacionActa.ENVIADA, null, null)
            limpiarArchivosLocales(applicationContext, acta.idPeticion)
            return Result.success()
        }

        val estadoCierre = acta.estadoCierre.trim().ifEmpty { "En tratamiento" }
        val patchRequest = ActualizarEstadoRequest(
            estado = estadoCierre,
            observacion = null
        )

        val patchResponse = try {
            RetrofitClient.apiService.actualizarEstadoPeticion(
                "Bearer $token",
                acta.idPeticion,
                patchRequest
            )
        } catch (e: IOException) {
            Log.e(TAG, "IOException en PATCH peticiones/${acta.idPeticion}/estado/: ${e.message}", e)
            return ejecutarRutaReintento(idLocal, acta, null, "IOException en PATCH estado: ${e.message}")
        }

        val code = patchResponse.code()
        if (code == 401) {
            return manejarSesionExpirada(idLocal)
        }

        if (code >= 500) {
            Log.w(TAG, "Error 5xx ($code) en PATCH estado de petición")
            return ejecutarRutaReintento(idLocal, acta, code, "Error 5xx ($code) en PATCH estado")
        }

        // Respuesta 2xx o 4xx (excepto 401)
        repo.marcarEstado(idLocal, EstadoSincronizacionActa.ENVIADA, code, null)
        limpiarArchivosLocales(applicationContext, acta.idPeticion)
        return Result.success()
    }

    // 7. Sesión expirada
    private fun manejarSesionExpirada(idLocal: Long): Result {
        val mensaje = applicationContext.getString(R.string.acta_offline_error_sesion)
        repo.marcarEstado(idLocal, EstadoSincronizacionActa.REQUIERE_ATENCION, 401, mensaje)
        ActaNotificationHelper.notificarSesionExpirada(applicationContext)
        return Result.success()
    }

    // Reintento con backoff y límite de seguridad
    private fun ejecutarRutaReintento(
        idLocal: Long,
        acta: ActaLocalEntity,
        codigoHttp: Int?,
        descripcionLog: String
    ): Result {
        val nuevosReintentos = repo.incrementarReintentos(idLocal)
        Log.w(TAG, "Reintento $nuevosReintentos para idLocal=$idLocal. Razón: $descripcionLog")

        val filaDb = repo.obtenerPorIdLocal(idLocal)
        val tieneIdRemoto = acta.idSeguimientoRemoto != null || filaDb?.idSeguimientoRemoto != null

        // EXCEPCIÓN DE SEGURIDAD: si idSeguimientoRemoto == null y reintentos supera 10
        if (!tieneIdRemoto && nuevosReintentos > 10) {
            val mensaje = applicationContext.getString(R.string.acta_offline_error_reintentos)
            repo.marcarEstado(idLocal, EstadoSincronizacionActa.REQUIERE_ATENCION, codigoHttp, mensaje)
            ActaNotificationHelper.notificarRequiereRevision(
                applicationContext,
                acta.nroRadicadoAtencion,
                acta.idPeticion,
                mensaje
            )
            return Result.success()
        }

        repo.marcarEstado(idLocal, EstadoSincronizacionActa.ERROR_REINTENTABLE, codigoHttp, descripcionLog)
        return Result.retry()
    }

    private fun extraerMensajeLegible(errorBody: String?, context: Context): String {
        val fallback = context.getString(R.string.acta_offline_error_datos_generico)
        if (errorBody.isNullOrBlank()) return fallback
        return try {
            val json = JSONObject(errorBody)
            var texto: String? = null
            for (key in listOf("detail", "mensaje", "detalle", "error")) {
                if (json.has(key)) {
                    val candidate = json.optString(key, "")
                    if (candidate.isNotBlank()) {
                        texto = candidate
                        break
                    }
                }
            }
            if (texto.isNullOrBlank()) {
                val primeraClave = json.keys().asSequence().firstOrNull()
                if (primeraClave != null) {
                    val array = json.optJSONArray(primeraClave)
                    val candidate = if (array != null && array.length() > 0) {
                        array.optString(0, "")
                    } else {
                        json.optString(primeraClave, "")
                    }
                    if (candidate.isNotBlank()) {
                        texto = candidate
                    }
                }
            }
            if (texto.isNullOrBlank()) return fallback

            val esCodigoMayusculas = texto.matches(Regex("^[A-Z0-9_]{3,}$"))
            val mencionaTecnico = texto.contains("HTTP", ignoreCase = true) ||
                    texto.contains("Exception", ignoreCase = true) ||
                    texto.contains("Error 4", ignoreCase = true) ||
                    texto.contains("Error 5", ignoreCase = true)

            if (esCodigoMayusculas || mencionaTecnico) {
                fallback
            } else {
                texto.take(200)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun limpiarArchivosLocales(context: Context, idPeticion: Int) {
        try {
            val filesDir = context.filesDir
            val baseOfflineDir = File(filesDir, "acta_offline")
            val peticionDir = File(baseOfflineDir, idPeticion.toString())
            val canonicalFilesDir = filesDir.canonicalPath
            val canonicalPeticionDir = peticionDir.canonicalPath
            if (canonicalPeticionDir.startsWith(canonicalFilesDir)) {
                if (peticionDir.exists()) {
                    peticionDir.deleteRecursively()
                }
            } else {
                Log.w(TAG, "Ruta fuera de filesDir: $canonicalPeticionDir")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar directorio local de acta ($idPeticion): ${e.message}", e)
        }
    }
}
