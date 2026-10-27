package com.example.petvetericano.offline

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Resultado sellado del proceso de subida de evidencias a Cloudinary.
 */
sealed class ResultadoSubida {
    data class Exito(val secureUrl: String) : ResultadoSubida()
    object ArchivoNoExiste : ResultadoSubida()
    data class ErrorRed(val mensaje: String) : ResultadoSubida()
    data class ErrorPermanente(val mensaje: String) : ResultadoSubida()
}

/**
 * Gestor auxiliar para subidas de archivos multimedia a Cloudinary en segundo plano y en pantalla.
 * Reproduce exactamente la configuración de MediaManager y subida usada en ActaAtencionActivity:
 * - cloud_name = "aefeig5y" (ActaAtencionActivity.kt líneas 2942-2943)
 * - preset unsigned = "preset_android" (ActaAtencionActivity.kt línea 3303)
 */
object CloudinaryUploadHelper {

    private const val TAG = "CloudinaryUploadHelper"
    private const val CLOUD_NAME = "aefeig5y"
    private const val UPLOAD_PRESET = "preset_android"

    @Volatile
    private var initialized = false

    /**
     * Inicializa MediaManager con applicationContext de forma idempotente y thread-safe.
     * Atrapa el caso de inicialización previa sin lanzar excepción.
     */
    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return

        try {
            MediaManager.get()
            initialized = true
            return
        } catch (_: IllegalStateException) {
            // Aún no inicializado en el singleton de MediaManager
        }

        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = CLOUD_NAME
            MediaManager.init(context.applicationContext, config)
            initialized = true
        } catch (_: IllegalStateException) {
            // Atrapa el caso "MediaManager ya fue inicializado previamente"
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada al inicializar Cloudinary MediaManager: ${e.message}", e)
        }
    }

    /**
     * Sube un archivo local a Cloudinary en una corrutina suspendible y cancelable.
     * Retorna [ResultadoSubida] indicando el resultado o la categoría del fallo.
     */
    suspend fun subir(context: Context, localPath: String, esVideo: Boolean): ResultadoSubida {
        if (localPath.isBlank()) {
            return ResultadoSubida.ArchivoNoExiste
        }

        val file = File(localPath)
        if (!file.exists() || !file.isFile) {
            return ResultadoSubida.ArchivoNoExiste
        }

        ensureInitialized(context)

        return suspendCancellableCoroutine { cont ->
            val resumido = AtomicBoolean(false)

            fun safeResume(resultado: ResultadoSubida) {
                if (resumido.compareAndSet(false, true)) {
                    if (cont.isActive) {
                        cont.resume(resultado)
                    }
                }
            }

            var requestId: String? = null
            try {
                val uploadRequest = MediaManager.get()
                    .upload(file.absolutePath)
                    .unsigned(UPLOAD_PRESET)
                    .callback(object : UploadCallback {
                        override fun onStart(reqId: String?) {
                            // Sin acción en inicio
                        }

                        override fun onProgress(reqId: String?, bytes: Long, totalBytes: Long) {
                            // Sin acción en progreso
                        }

                        override fun onSuccess(reqId: String?, resultData: Map<*, *>?) {
                            val secureUrl = resultData?.get("secure_url") as? String
                            if (!secureUrl.isNullOrBlank()) {
                                safeResume(ResultadoSubida.Exito(secureUrl))
                            } else {
                                safeResume(ResultadoSubida.ErrorRed("Respuesta de Cloudinary sin secure_url"))
                            }
                        }

                        override fun onError(reqId: String?, error: ErrorInfo?) {
                            val resultado = clasificarErrorInfo(error)
                            safeResume(resultado)
                        }

                        override fun onReschedule(reqId: String?, error: ErrorInfo?) {
                            try {
                                reqId?.let { MediaManager.get().cancelRequest(it) }
                            } catch (e: Exception) {
                                Log.w(TAG, "Excepción al cancelar petición reprogramada Cloudinary: ${e.message}")
                            }
                            val mensaje = error?.description ?: "Petición reprogramada por conectividad"
                            safeResume(ResultadoSubida.ErrorRed(mensaje))
                        }
                    })

                requestId = uploadRequest.dispatch()

                cont.invokeOnCancellation {
                    try {
                        requestId?.let { id ->
                            MediaManager.get().cancelRequest(id)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Excepción al cancelar petición de subida Cloudinary: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al iniciar despacho de subida Cloudinary: ${e.message}", e)
                safeResume(ResultadoSubida.ErrorRed(e.message ?: "Excepción al iniciar subida"))
            }
        }
    }

    /**
     * Clasifica el error reportado por Cloudinary según las constantes de [ErrorInfo].
     * Si no se puede distinguir con certeza, todo error que no sea archivo inexistente
     * se clasifica como [ResultadoSubida.ErrorRed] (reintentable).
     */
    private fun clasificarErrorInfo(error: ErrorInfo?): ResultadoSubida {
        if (error == null) {
            return ResultadoSubida.ErrorRed("Error desconocido de Cloudinary (ErrorInfo null)")
        }

        val code = error.code
        val descripcion = error.description?.takeIf { it.isNotBlank() } ?: "Error Cloudinary código $code"

        return when (code) {
            ErrorInfo.FILE_DOES_NOT_EXIST,
            ErrorInfo.URI_DOES_NOT_EXIST,
            ErrorInfo.RESOURCE_DOES_NOT_EXIST -> {
                ResultadoSubida.ArchivoNoExiste
            }
            ErrorInfo.OPTIONS_FAILURE,
            ErrorInfo.SIGNATURE_FAILURE,
            ErrorInfo.PAYLOAD_EMPTY,
            ErrorInfo.BYTE_ARRAY_PAYLOAD_EMPTY,
            ErrorInfo.PREPROCESS_ERROR -> {
                ResultadoSubida.ErrorPermanente(descripcion)
            }
            ErrorInfo.NETWORK_ERROR,
            ErrorInfo.REQUEST_CANCELLED,
            ErrorInfo.TOO_MANY_ERRORS -> {
                ResultadoSubida.ErrorRed(descripcion)
            }
            else -> {
                // Cualquier error no distinguible con certeza se trata como reintentable de red
                ResultadoSubida.ErrorRed(descripcion)
            }
        }
    }

    /**
     * Función auxiliar opcional para que la Activity (o componentes futuros) puedan
     * obtener directamente la URL segura como String o null si falló, sin forzar cambios ahora.
     */
    suspend fun subirArchivo(context: Context, localPath: String, esVideo: Boolean): String? {
        return when (val resultado = subir(context, localPath, esVideo)) {
            is ResultadoSubida.Exito -> resultado.secureUrl
            else -> null
        }
    }

    /**
     * Función auxiliar opcional para subir directamente a partir de un [Uri],
     * manteniendo compatibilidad con las fuentes actuales de la Activity.
     */
    suspend fun subirUri(context: Context, uri: Uri, esVideo: Boolean = false): ResultadoSubida {
        ensureInitialized(context)

        return suspendCancellableCoroutine { cont ->
            val resumido = AtomicBoolean(false)

            fun safeResume(resultado: ResultadoSubida) {
                if (resumido.compareAndSet(false, true)) {
                    if (cont.isActive) {
                        cont.resume(resultado)
                    }
                }
            }

            var requestId: String? = null
            try {
                val uploadRequest = MediaManager.get()
                    .upload(uri)
                    .unsigned(UPLOAD_PRESET)
                    .callback(object : UploadCallback {
                        override fun onStart(reqId: String?) {}
                        override fun onProgress(reqId: String?, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(reqId: String?, resultData: Map<*, *>?) {
                            val secureUrl = resultData?.get("secure_url") as? String
                            if (!secureUrl.isNullOrBlank()) {
                                safeResume(ResultadoSubida.Exito(secureUrl))
                            } else {
                                safeResume(ResultadoSubida.ErrorRed("Respuesta de Cloudinary sin secure_url"))
                            }
                        }
                        override fun onError(reqId: String?, error: ErrorInfo?) {
                            safeResume(clasificarErrorInfo(error))
                        }
                        override fun onReschedule(reqId: String?, error: ErrorInfo?) {
                            try {
                                reqId?.let { MediaManager.get().cancelRequest(it) }
                            } catch (e: Exception) {
                                Log.w(TAG, "Excepción al cancelar petición reprogramada Uri Cloudinary: ${e.message}")
                            }
                            val mensaje = error?.description ?: "Petición reprogramada por conectividad"
                            safeResume(ResultadoSubida.ErrorRed(mensaje))
                        }
                    })

                requestId = uploadRequest.dispatch()

                cont.invokeOnCancellation {
                    try {
                        requestId?.let { id ->
                            MediaManager.get().cancelRequest(id)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Excepción al cancelar petición de subida Uri Cloudinary: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al iniciar despacho de subida Uri Cloudinary: ${e.message}", e)
                safeResume(ResultadoSubida.ErrorRed(e.message ?: "Excepción al iniciar subida Uri"))
            }
        }
    }
}
