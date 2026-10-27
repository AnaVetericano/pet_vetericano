package com.example.petvetericano.network

import android.content.Context
import android.util.Log
import com.example.petvetericano.SharedPreferencesManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.io.IOException

class TokenAuthenticator(
    private val contextProvider: () -> Context?
) : Authenticator {

    constructor(context: Context) : this({ context.applicationContext })

    companion object {
        private const val TAG = "TokenAuthenticator"
        private val lock = Any()

        private val EXCLUDED_PATH_SUBSTRINGS = listOf(
            "/login/",
            "/register/",
            "/google-login/",
            "/recuperar-password/",
            "/confirmar-password/",
            "/token/refresh/"
        )
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val urlString = response.request.url.toString()
        if (EXCLUDED_PATH_SUBSTRINGS.any { urlString.contains(it) }) {
            return null
        }

        if (response.priorResponse != null) {
            return null
        }

        val failedAuthHeader = response.request.header("Authorization")
        if (failedAuthHeader.isNullOrEmpty()) {
            return null
        }

        val context = contextProvider() ?: return null
        val prefs = SharedPreferencesManager(context)

        synchronized(lock) {
            val currentAccess = prefs.getAccessToken().trim()
            if (currentAccess.isNotEmpty() && "Bearer $currentAccess" != failedAuthHeader) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }

            val refreshToken = prefs.getRefreshToken().trim()
            if (refreshToken.isEmpty()) {
                return null
            }

            if (refreshToken == SesionExpiradaManager.refreshMuerto) {
                return null
            }

            val refreshUrl = "${RetrofitClient.BASE_URL}token/refresh/"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestJson = JSONObject().apply {
                put("refresh", refreshToken)
            }.toString()

            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(requestJson.toRequestBody(mediaType))
                .build()

            val refreshResponse = try {
                RetrofitClient.cleanClient.newCall(refreshRequest).execute()
            } catch (e: IOException) {
                Log.w(TAG, "IOException al contactar endpoint de refresh: ${e.message}")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado al contactar endpoint de refresh: ${e.message}")
                return null
            }

            refreshResponse.use { resp ->
                val code = resp.code
                if (code == 400 || code == 401) {
                    Log.w(TAG, "Refresh token inválido o vencido (HTTP $code)")
                    SesionExpiradaManager.marcarSesionMuerta(refreshToken)
                    return null
                }

                if (code in 500..599) {
                    Log.w(TAG, "Error de servidor en refresh (HTTP $code)")
                    return null
                }

                if (!resp.isSuccessful) {
                    Log.w(TAG, "Refresh no exitoso (HTTP $code)")
                    return null
                }

                val rawBody = resp.body?.string().orEmpty()
                if (rawBody.isBlank()) {
                    Log.w(TAG, "Cuerpo de respuesta vacío en refresh")
                    return null
                }

                val (newAccess, newRefresh) = try {
                    val json = JSONObject(rawBody)
                    val access = json.optString("access", "").trim()
                    val refresh = if (json.has("refresh") && !json.isNull("refresh")) {
                        json.optString("refresh", "").trim()
                    } else {
                        null
                    }
                    Pair(access, refresh)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al parsear JSON de refresh: ${e.message}")
                    return null
                }

                if (newAccess.isEmpty()) {
                    Log.w(TAG, "El token de acceso recibido está vacío")
                    return null
                }

                prefs.saveAccessToken(newAccess)
                if (!newRefresh.isNullOrEmpty()) {
                    prefs.saveRefreshToken(newRefresh)
                }
                RetrofitClient.authToken = newAccess

                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .build()
            }
        }
    }
}
