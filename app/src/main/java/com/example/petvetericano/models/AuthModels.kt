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

data class VoluntariadoEventos(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val imagen: String? = null,
    val fecha: String
)
// --- Perfil de Usuario ---
data class UsuarioPerfilResponse(
    @SerializedName("id_usuario") val idUsuario: Int?,
    val email: String,
    val nombre: String,
    val apellido: String?,
    val identificacion: String?
)


data class confirmarpeticion(
    val id_tipo : Int,
    val id_peticion : Int,
    val id_ubicacion : Int,
    val id_estado :Int,
    val responsable : Int,
    val descripcion : String,
    val prioridad: String)
data class ActualizarPerfilRequest(
    val nombre: String,
    val email: String)


data class TipoPeticionModel(
    val id_tipo: Int,
    val nombre: String,
    val descripcion: String,
    val activo: Boolean
)
