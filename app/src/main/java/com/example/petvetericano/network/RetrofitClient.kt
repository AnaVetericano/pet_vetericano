package com.example.petvetericano.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // =================================================================
    // 🚦 ENTORNO DE PRODUCCIÓN (Railway - Celular físico)
    // =================================================================
    private const val BASE_URL = "https://backendvetericano-production.up.railway.app/api/"

    // Variable estática global para almacenar el token de acceso
    var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            // Inyecta el token automáticamente usando la variable estática
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