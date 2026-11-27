package com.example.petvetericano.auth

import com.example.petvetericano.inicio_sesion

/**
 * Decisión pura de enrutamiento de sesión al abrir la app (SplashActivity / MainActivity / bienvenida).
 */
object SessionRouter {

    enum class DestinoSesion {
        WELCOME,
        CIUDADANO,
        VETERINARIO
    }

    fun resolverDestino(
        accessToken: String,
        refreshToken: String,
        userId: Int,
        userRole: Int
    ): DestinoSesion {
        val tieneToken = accessToken.trim().isNotEmpty() || refreshToken.trim().isNotEmpty()
        if (!tieneToken || userId <= 0 || userRole <= 0) {
            return DestinoSesion.WELCOME
        }
        return if (userRole == inicio_sesion.ROL_VETERINARIO) {
            DestinoSesion.VETERINARIO
        } else {
            DestinoSesion.CIUDADANO
        }
    }
}
