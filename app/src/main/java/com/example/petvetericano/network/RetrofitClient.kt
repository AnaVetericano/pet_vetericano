package com.example.petvetericano.network

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.petvetericano.BuildConfig
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
    var authToken: String? = null

    @SuppressLint("StaticFieldLeak")
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
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

    // TLS: validación estándar del sistema (cadena de confianza + hostname) en los dos clientes.
    // Las anclas de confianza están en res/xml/network_security_config.xml, que incluye
    // ISRG Root X1 (res/raw/isrg_root_x1.pem) para *.up.railway.app en Android 7.0 (API 24).
    // Detrás de un proxy que intercepte TLS se debe instalar su CA como CA de usuario (solo debug).
    internal val cleanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            // Inyecta el token automáticamente usando la variable estática
            .addInterceptor(AuthInterceptor { authToken })
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

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /** REST central de notificaciones (contrato v1 §6), mismo cliente autenticado. */
    val notificacionesApi: NotificacionesApi by lazy {
        retrofit.create(NotificacionesApi::class.java)
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
