package com.example.petvetericano.network

import android.content.Context
import android.util.Log
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.facial.FacialStorageManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

interface TokenStore {
    fun getAccessToken(): String
    fun getRefreshToken(): String
    fun getUserId(): Int
    fun saveAccessToken(token: String)
    fun saveRefreshToken(token: String)
    fun onRefreshTokenRotado(userId: Int, nuevoRefreshToken: String)
}

internal class SharedPreferencesTokenStore(private val context: Context) : TokenStore {
    private val prefs = SharedPreferencesManager(context)

    override fun getAccessToken(): String = prefs.getAccessToken()
    override fun getRefreshToken(): String = prefs.getRefreshToken()
    override fun getUserId(): Int = prefs.getUserId()
    override fun saveAccessToken(token: String) = prefs.saveAccessToken(token)
    override fun saveRefreshToken(token: String) = prefs.saveRefreshToken(token)

    override fun onRefreshTokenRotado(userId: Int, nuevoRefreshToken: String) {
        try {
            FacialStorageManager(context).actualizarRefreshTokenSiCoincide(
                userId = userId,
                nuevoRefreshToken = nuevoRefreshToken
            )
        } catch (_: Exception) {
        }
    }
}

class TokenAuthenticator internal constructor(
    private val tokenStoreProvider: () -> TokenStore?,
    private val baseUrlProvider: () -> String,
    private val clientProvider: () -> OkHttpClient,
    private val onSesionMuerta: (String) -> Unit,
    private val getRefreshMuerto: () -> String?,
    private val onAuthTokenActualizado: (String) -> Unit
) : Authenticator {

    constructor(contextProvider: () -> Context?) : this(
        tokenStoreProvider = { contextProvider()?.let { SharedPreferencesTokenStore(it) } },
        baseUrlProvider = { RetrofitClient.baseUrlActiva() },
        clientProvider = { RetrofitClient.cleanClient },
        onSesionMuerta = { SesionExpiradaManager.marcarSesionMuerta(it) },
        getRefreshMuerto = { SesionExpiradaManager.refreshMuerto },
        onAuthTokenActualizado = { RetrofitClient.authToken = it }
    )

    constructor(context: Context) : this({ context.applicationContext })

    companion object {
        private const val TAG = "TokenAuthenticator"
        private val lock = Any()
        private val gson = Gson()

        private val EXCLUDED_PATH_SUBSTRINGS = listOf(
            "/login/",
            "/register/",
            "/google-login/",
            "/recuperar-password/",
            "/confirmar-password/",
            "/token/refresh/"
        )

        internal fun parsearTokensRefresh(rawBody: String): Pair<String, String?>? {
            if (rawBody.isBlank()) return null
            return try {
                val json = JsonParser.parseString(rawBody).asJsonObject
                val accessEl = json.get("access")
                val access = if (accessEl != null && accessEl.isJsonPrimitive) {
                    accessEl.asString.trim()
                } else {
                    ""
                }
                if (access.isEmpty()) return null

                val refreshEl = json.get("refresh")
                val refresh = if (refreshEl != null && refreshEl.isJsonPrimitive) {
                    refreshEl.asString.trim().ifEmpty { null }
                } else {
                    null
                }
                Pair(access, refresh)
            } catch (_: Exception) {
                null
            }
        }
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

        val tokenStore = tokenStoreProvider() ?: return null

        synchronized(lock) {
            val currentAccess = tokenStore.getAccessToken().trim()
            if (currentAccess.isNotEmpty() && "Bearer $currentAccess" != failedAuthHeader) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }

            val refreshToken = tokenStore.getRefreshToken().trim()
            if (refreshToken.isEmpty()) {
                return null
            }

            if (refreshToken == getRefreshMuerto()) {
                return null
            }

            val baseUrl = baseUrlProvider().let { if (it.endsWith("/")) it else "$it/" }
            val refreshUrl = "${baseUrl}token/refresh/"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestJson = gson.toJson(mapOf("refresh" to refreshToken))

            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(requestJson.toRequestBody(mediaType))
                .build()

            val refreshResponse = try {
                clientProvider().newCall(refreshRequest).execute()
            } catch (e: IOException) {
                logWarn("IOException al contactar endpoint de refresh: ${e.message}")
                return null
            } catch (e: Exception) {
                logError("Error inesperado al contactar endpoint de refresh: ${e.message}")
                return null
            }

            refreshResponse.use { resp ->
                val code = resp.code
                if (code == 400 || code == 401) {
                    logWarn("Refresh token inválido o vencido (HTTP $code)")
                    onSesionMuerta(refreshToken)
                    return null
                }

                if (code in 500..599) {
                    logWarn("Error de servidor en refresh (HTTP $code)")
                    return null
                }

                if (!resp.isSuccessful) {
                    logWarn("Refresh no exitoso (HTTP $code)")
                    return null
                }

                val rawBody = resp.body?.string().orEmpty()
                val parsed = parsearTokensRefresh(rawBody)
                if (parsed == null) {
                    logError("Respuesta de refresh inválida o sin access token")
                    return null
                }

                val (newAccess, newRefresh) = parsed
                tokenStore.saveAccessToken(newAccess)
                if (!newRefresh.isNullOrEmpty()) {
                    tokenStore.saveRefreshToken(newRefresh)
                    tokenStore.onRefreshTokenRotado(tokenStore.getUserId(), newRefresh)
                }
                onAuthTokenActualizado(newAccess)

                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .build()
            }
        }
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }

    private fun logError(msg: String) {
        try {
            Log.e(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }
}
