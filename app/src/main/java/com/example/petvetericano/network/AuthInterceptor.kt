package com.example.petvetericano.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Si ya trae autorización, dejamos pasar la petición
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = tokenProvider()

        val finalRequest = if (!token.isNullOrEmpty()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(finalRequest)
    }
}