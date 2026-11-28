package com.example.petvetericano.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.petvetericano.BuildConfig
import com.example.petvetericano.SharedPreferencesManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // =================================================================
    // 🚦 ENTORNO DE PRODUCCIÓN (Railway) y DEV LOCAL (BuildConfig)
    // =================================================================
    internal const val PRODUCTION_BASE_URL = "https://backendvetericano-production.up.railway.app/api/"
    internal val BASE_URL: String = BuildConfig.API_BASE_URL

    // Variable estática global para almacenar el token de acceso
    @Volatile
    var authToken: String? = null

    @Volatile
    private var baseUrlPrueba: String? = null

    @Volatile
    private var deshabilitarRespaldoLocalEnPruebas: Boolean = false

    @Volatile
    private var apiServicePrueba: ApiService? = null

    @Volatile
    private var cleanClientPrueba: OkHttpClient? = null

    @SuppressLint("StaticFieldLeak")
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (authToken.isNullOrBlank()) {
            try {
                val persisted = SharedPreferencesManager(context.applicationContext).getAccessToken().trim()
                if (persisted.isNotEmpty()) {
                    authToken = persisted
                }
            } catch (_: Exception) {
            }
        }
    }

    internal fun baseUrlActiva(): String = baseUrlPrueba ?: BASE_URL

    private fun resolverTokenActual(): String? {
        val enMemoria = authToken?.trim()
        if (!enMemoria.isNullOrEmpty()) return enMemoria
        val ctx = appContext ?: return null
        return try {
            val persistido = SharedPreferencesManager(ctx).getAccessToken().trim()
            if (persistido.isNotEmpty()) {
                authToken = persistido
                persistido
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Logs HTTP: cuerpo completo solo en debug (con tokens/contraseñas enmascarados); nada en release.
    // El header Authorization se oculta siempre.
    private val loggingInterceptor = HttpLoggingInterceptor(LoggerHttpSeguro).apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    private val defaultCleanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    internal val cleanClient: OkHttpClient
        get() = cleanClientPrueba ?: defaultCleanClient

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            // Inyecta el token automáticamente con respaldo persistente ante process death
            .addInterceptor(AuthInterceptor { resolverTokenActual() })
            .authenticator(TokenAuthenticator { appContext })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .serializeNulls()
            .create()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val defaultApiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    val apiService: ApiService
        get() = apiServicePrueba ?: defaultApiService

    /** REST central de notificaciones (contrato v1 §6), mismo cliente autenticado. */
    val notificacionesApi: NotificacionesApi by lazy {
        retrofit.create(NotificacionesApi::class.java)
    }

    @VisibleForTesting
    internal fun configurarParaPruebas(
        baseUrl: String,
        deshabilitarRespaldoLocal: Boolean = true,
        cleanClientOverride: OkHttpClient? = null
    ) {
        val urlNormalizada = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        baseUrlPrueba = urlNormalizada
        deshabilitarRespaldoLocalEnPruebas = deshabilitarRespaldoLocal

        val testClean = cleanClientOverride ?: OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        cleanClientPrueba = testClean

        val testAuthClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { resolverTokenActual() })
            .authenticator(TokenAuthenticator { appContext })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        apiServicePrueba = Retrofit.Builder()
            .baseUrl(urlNormalizada)
            .client(testAuthClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    @VisibleForTesting
    internal fun restaurarConfiguracion() {
        baseUrlPrueba = null
        deshabilitarRespaldoLocalEnPruebas = false
        apiServicePrueba = null
        cleanClientPrueba = null
    }

    private fun crearApiServiceTemporal(baseUrl: String): ApiService {
        val clientRapido = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { resolverTokenActual() })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientRapido)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Ejecuta una llamada de autenticación (como solicitar-codigo, confirmar-codigo o google-login) contra BASE_URL.
     * Si en modo DEBUG el servidor de Railway aún no tiene desplegado el endpoint (HTTP 404/405)
     * o está caído (HTTP 502/503/504/5xx), reintenta automáticamente contra el backend Django local
     * (127.0.0.1 con adb reverse / 10.0.2.2 en emulador / IP LAN).
     */
    suspend fun <T> ejecutarConRespaldoLocalDebug(
        bloque: suspend (ApiService) -> retrofit2.Response<T>
    ): retrofit2.Response<T> {
        if (deshabilitarRespaldoLocalEnPruebas) {
            return bloque(apiService)
        }

        var respuestaPrimaria: retrofit2.Response<T>? = null
        var excepcionPrimaria: Exception? = null
        val debeProbarFallback = { code: Int -> code == 404 || code == 405 || code >= 500 }

        try {
            val resp = bloque(apiService)
            if (!BuildConfig.DEBUG || !debeProbarFallback(resp.code())) {
                return resp
            }
            respuestaPrimaria = resp
        } catch (e: Exception) {
            if (!BuildConfig.DEBUG) throw e
            excepcionPrimaria = e
        }

        val urlBaseActiva = baseUrlActiva()
        val urlsOrdenadas = listOf(
            "http://127.0.0.1:8000/api/",
            "http://192.168.101.88:8000/api/",
            "http://10.0.2.2:8000/api/"
        )
        for (urlLocal in urlsOrdenadas) {
            if (urlLocal.equals(urlBaseActiva, ignoreCase = true)) continue
            try {
                val apiLocal = crearApiServiceTemporal(urlLocal)
                val respLocal = bloque(apiLocal)
                if (!debeProbarFallback(respLocal.code())) {
                    return respLocal
                }
            } catch (_: Exception) {
                // Continuar probando el siguiente host local
            }
        }

        respuestaPrimaria?.let { return it }
        throw (excepcionPrimaria ?: IllegalStateException("No se pudo conectar con el servidor."))
    }
}

/** Logger de OkHttp que enmascara secretos del cuerpo antes de escribir en Logcat (solo se usa en debug). */
private object LoggerHttpSeguro : HttpLoggingInterceptor.Logger {
    private const val TAG = "OkHttp"
    private const val MAX_POR_LINEA = 4000

    override fun log(message: String) {
        val limpio = enmascararSecretosHttp(message)
        if (limpio.length <= MAX_POR_LINEA) {
            Log.d(TAG, limpio)
            return
        }
        var inicio = 0
        while (inicio < limpio.length) {
            val fin = minOf(limpio.length, inicio + MAX_POR_LINEA)
            Log.d(TAG, limpio.substring(inicio, fin))
            inicio = fin
        }
    }
}

private val REGEX_SECRETOS_JSON = Regex(
    "\"(token|access|refresh|access_token|refresh_token|id_token|password|nueva_password|codigo)\"\\s*:\\s*\"([^\"]*)\""
)
private val CLAVES_OCULTAS_COMPLETAS = setOf("password", "nueva_password", "codigo")

/**
 * Enmascara en un texto JSON los valores de tokens (deja solo los últimos 6 caracteres) y de
 * contraseñas/códigos (los oculta del todo). Pensado para logs de depuración.
 */
internal fun enmascararSecretosHttp(texto: String): String {
    if (texto.isEmpty()) return texto
    return REGEX_SECRETOS_JSON.replace(texto) { coincidencia ->
        val clave = coincidencia.groupValues[1]
        val valor = coincidencia.groupValues[2]
        val oculto = if (clave in CLAVES_OCULTAS_COMPLETAS || valor.length <= 12) {
            "***"
        } else {
            "***" + valor.takeLast(6)
        }
        "\"$clave\":\"$oculto\""
    }
}
