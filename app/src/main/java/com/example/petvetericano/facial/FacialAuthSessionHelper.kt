package com.example.petvetericano.facial

import android.content.Context
import android.util.Log
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import com.example.petvetericano.realtime.WebSocketConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Reanuda la sesión contra el endpoint existente `POST /api/token/refresh/` de Django
 * una vez que el rostro fue verificado localmente con ML Kit + MobileFaceNet.
 *
 * No requiere ningún cambio ni endpoint adicional en el backend.
 */
object FacialAuthSessionHelper {

    private const val TAG = "FacialAuthSessionHelper"

    sealed class ResultadoLoginFacial {
        data class Exito(
            val idRol: Int,
            val accessToken: String,
            val email: String,
            val nombre: String
        ) : ResultadoLoginFacial()

        data class RequiereLoginManual(val mensaje: String) : ResultadoLoginFacial()
        data class ErrorRed(val mensaje: String) : ResultadoLoginFacial()
    }

    suspend fun completarSesionConPerfilVerificado(
        context: Context,
        perfil: FacialStorageManager.PerfilFacialGuardado
    ): ResultadoLoginFacial = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val refreshToken = perfil.refreshToken.trim()
        if (refreshToken.isEmpty()) {
            return@withContext ResultadoLoginFacial.RequiereLoginManual(
                appContext.getString(R.string.facial_sesion_expirada)
            )
        }

        val refreshUrl = "${RetrofitClient.BASE_URL}token/refresh/"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val bodyJson = JSONObject().apply {
            put("refresh", refreshToken)
        }.toString()

        val request = Request.Builder()
            .url(refreshUrl)
            .post(bodyJson.toRequestBody(mediaType))
            .build()

        val response = try {
            RetrofitClient.cleanClient.newCall(request).execute()
        } catch (e: IOException) {
            Log.w(TAG, "Error de red refrescando sesión facial: ${e.javaClass.simpleName}")
            return@withContext ResultadoLoginFacial.ErrorRed(
                inicio_sesion.resolverMensajeErrorExcepcion(e)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado refrescando sesión facial: ${e.javaClass.simpleName}")
            return@withContext ResultadoLoginFacial.ErrorRed(
                inicio_sesion.resolverMensajeErrorExcepcion(e)
            )
        }

        response.use { resp ->
            val code = resp.code
            val rawBody = try {
                resp.body?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (code == 400 || code == 401 || code == 403) {
                val detalleServidor = extraerDetalleSeguro(rawBody)
                val esDesactivado = detalleServidor?.contains("desactivad", ignoreCase = true) == true
                val mensaje = if (esDesactivado && !detalleServidor.isNullOrBlank()) {
                    detalleServidor
                } else {
                    appContext.getString(R.string.facial_sesion_expirada)
                }
                return@withContext ResultadoLoginFacial.RequiereLoginManual(mensaje)
            }

            if (code in 500..599) {
                return@withContext ResultadoLoginFacial.ErrorRed(
                    "Error del servidor ($code). Intenta nuevamente más tarde."
                )
            }

            if (!resp.isSuccessful || rawBody.isBlank()) {
                return@withContext ResultadoLoginFacial.ErrorRed(
                    "No se pudo validar tu sesión con el servidor (código $code)."
                )
            }

            val parsed = parsearRespuestaRefresh(rawBody)
            if (parsed == null) {
                return@withContext ResultadoLoginFacial.ErrorRed(
                    "No se pudo procesar la respuesta del servidor. Intenta nuevamente."
                )
            }

            val (newAccess, newRefresh) = parsed
            val refreshFinal = newRefresh?.takeIf { it.isNotBlank() } ?: refreshToken

            RetrofitClient.authToken = newAccess
            SesionExpiradaManager.refreshMuerto = null

            val prefs = SharedPreferencesManager(appContext)
            prefs.saveUserData(
                name = perfil.nombre,
                email = perfil.email,
                phone = perfil.telefono
            )
            prefs.saveUserLastName(perfil.apellido)
            if (perfil.identificacion.isNotBlank()) {
                prefs.saveUserIdentification(perfil.identificacion)
            }
            if (perfil.matriculaProfesional.isNotBlank()) {
                prefs.saveUserMatriculaProfesional(perfil.matriculaProfesional)
            }
            prefs.saveProfileImagePath(perfil.fotoPerfil)
            prefs.saveAccessToken(newAccess)
            prefs.saveRefreshToken(refreshFinal)
            prefs.saveUserId(perfil.userId)
            prefs.saveUserRole(perfil.idRol)

            val storage = FacialStorageManager(appContext)
            storage.limpiarIntentosFallidos()
            storage.actualizarRefreshTokenSiCoincide(perfil.userId, refreshFinal)

            FcmTokenManager.onLoginExitoso(appContext)
            WebSocketConnectionManager.onLoginExitoso(appContext)
            encolarSincronizacionActasPendientes(appContext, perfil.userId)

            return@withContext ResultadoLoginFacial.Exito(
                idRol = perfil.idRol,
                accessToken = newAccess,
                email = perfil.email,
                nombre = perfil.nombre
            )
        }
    }

    internal fun parsearRespuestaRefresh(rawBody: String): Pair<String, String?>? {
        return try {
            val json = JSONObject(rawBody)
            val access = json.optString("access", "").trim()
            if (access.isEmpty()) return null
            val refresh = if (json.has("refresh") && !json.isNull("refresh")) {
                json.optString("refresh", "").trim().ifEmpty { null }
            } else {
                null
            }
            Pair(access, refresh)
        } catch (_: Exception) {
            null
        }
    }

    internal fun extraerDetalleSeguro(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return null
        return try {
            val json = JSONObject(trimmed)
            listOf("detail", "error", "mensaje")
                .mapNotNull { clave ->
                    if (json.has(clave) && !json.isNull(clave)) {
                        json.optString(clave, "").trim().ifEmpty { null }
                    } else {
                        null
                    }
                }
                .firstOrNull()
                ?.take(200)
        } catch (_: Exception) {
            null
        }
    }

    private fun encolarSincronizacionActasPendientes(appContext: Context, idVeterinario: Int) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ActaLocalRepository.getInstance(appContext).resetAuthErrorsToPending(idVeterinario)
                ActaSyncScheduler.enqueuePendingForCurrentUser(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error al sincronizar actas offline pendientes tras login facial: ${e.message}", e)
            }
        }
    }
}
