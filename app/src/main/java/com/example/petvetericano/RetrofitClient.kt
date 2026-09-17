package com.example.petvetericano

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // =================================================================
    // 🚦 CAMBIO DE ENTORNO: Descomenta la URL que vayas a usar
    // =================================================================

    // ☁️ ENTORNO DE PRODUCCIÓN (Railway - Celular físico)
    private const val BASE_URL = "https://backendvetericano-production.up.railway.app/api/"

    // 💻 ENTORNO DE DESARROLLO LOCAL (Compa / Emulador)
    // private const val BASE_URL = "http://10.0.2.2:8000/"

    // =================================================================

    var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { authToken })
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}