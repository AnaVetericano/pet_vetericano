package com.example.petvetericano

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Si estás usando el emulador de Android Studio, la IP para apuntar al localhost de tu PC es 10.0.2.2.
    // Si estás probando con un teléfono físico vía USB/WiFi, pon la IP local de tu PC (ej. http://192.168.1.50:8000/).
    private const val BASE_URL = "http://10.0.2.2:8000/"

    // Variable para almacenar el token de sesión (SimpleJWT access token)
    var authToken: String? = null

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { authToken })
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