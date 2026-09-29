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
    @SerializedName("id_usuario") val idUsuario: Int,
    @SerializedName("id_rol") val idRol: Int,
    val nombre: String?,
    val tokens: TokenData
)

data class TokenData(
    val refresh: String,
    val access: String
)

// --- Registro (Actualizado con teléfono) ---
data class RegisterRequest(
    val email: String,
    val identificacion: String,
    val password: String,
    val nombre: String,
    val apellido: String,
    val telefono: String
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

data class PostularseResponse(
    val mensaje: String?,
    val id_postulacion: Int?,
    val evento: Int?,
    val estado: String?
)

// --- Perfil de Usuario ---
data class UsuarioPerfilResponse(
    @SerializedName("id_usuario") val idUsuario: Int?,
    val email: String,
    val nombre: String,
    val apellido: String?,
    val identificacion: String?
)

// --- Peticiones / Reportes ---
data class confirmarpeticion(
    val id_tipo: Int,
    val id_estado: Int,
    val responsable: Int,
    val descripcion: String,
    val prioridad: String?,
    val direccion: String,
    val latitud: Double,
    val longitud: Double,
    val foto : String?
)

data class ActualizarPerfilRequest(
    val nombre: String,
    val apellido: String,
    val email: String
)

data class TipoPeticionModel(
    val id_tipo: Int,
    val nombre: String,
    val descripcion: String,
    val activo: Boolean
)

data class TipoPeticionResponse(
    val id_tipo: Int,
    val nombre: String,
    val descripcion: String?
)

data class IniciarPeticionRequest(
    val id_tipo: Int
)

data class IniciarPeticionResponse(
    val mensaje: String,
    val id_peticion: Int,
    val id_tipo: Int,
    val datos: Any? = null
)

// --- Otros Módulos ---
data class JuridicoResponse(
    val email: String,
    val nombre: String?
)

// --- Adopciones ---
data class AdopcionAnimalResponse(
    val id: Int,
    val nombre: String,
    val raza: String?,
    val descripcion: String?,
    val imagen: String?,
    val disponible: Boolean,
    @SerializedName("fecha_creacion") val fechaCreacion: String? = null
)