package com.example.petvetericano.models


import com.bumptech.glide.Priority
import com.google.gson.annotations.SerializedName

// --- Login ---
data class LoginRequest(
    val email: String,
    val password: String
)
//detalle peticion

data class DetallePeticionResponse(
    val codigo: String,
    val estado: String,
    val tipoEstado: String,
    val solicitanteNombre: String,
    val solicitanteTelefono: String,
    val solicitanteDireccion: String,
    val solicitanteComuna: String,
    val motivo: String,
    val fechaAsignada: String,
    val observaciones: String
)

//veterinario

data class PeticionPendiente(
    val id_peticion: Int = 0,
    val titulo: String,
    val paciente: String,
    val estado: String,
    val tipoEstado: TipoEstado
)

data class ActualizarEstadoRequest(
    val estado: String,
    val observacion: String? = null
)

data class ActualizarEstadoResponse(
    val mensaje: String,
    val id_peticion: Int? = null,
    val estado: String? = null
)

enum class TipoEstado {
    URGENTE, EN_PROCESO, ASIGNADA
}

data class LoginResponse(
    val mensaje: String,
    val email: String,
    @SerializedName("id_usuario") val idUsuario: Int,
    @SerializedName("id_rol") val idRol: Int,
    val nombre: String?,
    val apellido: String?,
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

// peticion
data class Petition(
    val code: String,
    val title: String,
    val location: String,
    val time: String,
    val status: String,
    val priorityType: Int
)

data class PeticionListResponse(
    val id_peticion: Int,
    val numero_radicado: String?,
    val tipo: String?,
    val estado: String?,
    val descripcion: String?,
    val prioridad: String?,
    val fecha: String?,
    val fecha_asignacion: String?,
    val asignado_a_nombre: String?,
    val asignado_a_apellido: String?,
    val ubicacion_direccion: String?,
    val ubicacion_latitud: String?,
    val ubicacion_longitud: String?,
    val foto: String?
)

data class AnimalActa(
    val nombre: String,
    val especie: String,
    val sexo: String,
    val color: String,
    val raza: String,
    val edad: String,
    val peso: Double,
    val esterilizado: Boolean,
    val descripcion: String
)

data class LugarAtencionActa(
    val direccion: String,
    val latitud: Double,
    val longitud: Double
)

data class FuncionarioActa(
    val id_usuario: Int?,
    val es_principal: Boolean,
    val nombre: String?,
    val cargo: String?
)

data class SeguimientoPeticionVisitaRequest(
    val id_peticion: Int,
    val numero_radicado: String,
    val fecha_atencion: String,
    val propietario_nombre: String,
    val propietario_cedula: String,
    val propietario_telefono: String,
    val propietario_barrio: String,
    val propietario_direccion: String,
    val quien_reporta: String,
    val solicitud_atencion_por: String,
    val lugar_atencion: LugarAtencionActa,
    val animales: List<AnimalActa>,
    val anamnesis_descripcion_queja: String,
    val tratamiento_realizado: String,
    val desparasitacion: Boolean,
    val pruebas_complementarias: String,
    val resultado_pruebas: String,
    val compromisos: String,
    val fundamento_legal: String,
    val plazo_dias_cumplimiento: Int,
    val funcionarios: List<FuncionarioActa>
)
