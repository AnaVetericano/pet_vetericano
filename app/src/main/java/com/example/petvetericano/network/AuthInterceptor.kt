package com.example.petvetericano.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {

    companion object {
        private val PUBLIC_AUTH_PATHS = listOf(
            "/usuarios/login/",
            "/usuarios/register/",
            "/usuarios/google-login/",
            "/usuarios/recuperar-password/",
            "/usuarios/confirmar-password/",
            "/token/refresh/"
        )

        internal fun esRutaAuthPublica(encodedPath: String): Boolean {
            return PUBLIC_AUTH_PATHS.any { encodedPath.endsWith(it) }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Las rutas públicas de autenticación nunca deben recibir un token previo en Authorization
        if (esRutaAuthPublica(originalRequest.url.encodedPath)) {
            return chain.proceed(originalRequest)
        }

        // Si ya trae autorización, dejamos pasar la petición
        if (originalRequest.header("Authorization") != null) {
            return chain.proceed(originalRequest)
        }

        val token = tokenProvider()?.trim()

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