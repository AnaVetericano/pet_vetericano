package com.example.petvetericano

import retrofit2.Response
import retrofit2.http.*

// Clases de modelo para mapear las respuestas del servidor
data class UsuarioPerfilResponse(
    val id_usuario: Int,
    val email: String,
    val nombre: String,
    val apellido: String,
    val identificacion: String?
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
    val id_tipo: Int
)

// Interfaz para la comunicación con Django
interface ApiService {

    @GET("api/peticiones/perfil/")
    suspend fun obtenerPerfil(): Response<UsuarioPerfilResponse>

    @PATCH("api/peticiones/perfil/")
    suspend fun actualizarPerfil(@Body usuario: Map<String, String>): Response<UsuarioPerfilResponse>

    @GET("api/peticiones/tipos/")
    suspend fun obtenerTiposPeticion(): Response<List<TipoPeticionResponse>>

    @POST("api/peticiones/iniciar/")
    suspend fun iniciarPeticion(@Body request: IniciarPeticionRequest): Response<IniciarPeticionResponse>
}