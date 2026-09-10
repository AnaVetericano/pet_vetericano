package com.example.petvetericano.models

import com.google.gson.annotations.SerializedName
// --- Login ---
data class LoginRequest(
    val email: String,
    val password: String
)
data class LoginResponse(
    val mensaje: String,
    val email: String,
    @SerializedName("id_rol") val idRol: Int,
    val tokens: TokenData
)
data class TokenData(
    val refresh: String,
    val access: String
)
// --- Registro ---
data class RegisterRequest(
    val email: String,
    val identificacion: String,
    val password: String,
    val nombre: String,
    val apellido: String
)
// --- Recuperar Contraseña ---
data class RecuperarPasswordRequest(
    val email: String
)
data class ConfirmarPasswordRequest(
    val email: String,
    val codigo: String,
    @SerializedName("nueva_password") val nuevaPassword: String
)
// --- Respuestas genéricas ---
data class GenericResponse(
    val mensaje: String?,
    val error: String?,
    val detail: String?
)

data class DashboardResponse(
    val id_usuario: Int,
    val nombre: String,
    val apellido: String,
    val email: String,
    val nombre_rol: String
)
