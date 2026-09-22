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
    val id_tipo: Int,
    val datos: Any? = null
)

data class confirmarpeticion(
    val id_tipo : Int,
    val id_ubicacion: Int,
    val id_estado: Int,
    val responsable: Int,
    val descripcion: String,
    val prioridad: String
)
// Asegúrate de tener una Data Class para recibir el tipo (ajusta los nombres según tu JSON)
data class TipoPeticionModel(
    val id_tipo: Int,
    val nombre: String,
    val descripcion: String,
    val activo: Boolean
)

// En tu interfaz ApiService:


// Interfaz para la comunicación con Django
interface ApiService {

    @GET("peticiones/perfil/")
    suspend fun obtenerPerfil(): Response<UsuarioPerfilResponse>

    @PATCH("peticiones/perfil/")
    suspend fun actualizarPerfil(@Body usuario: Map<String, String>): Response<UsuarioPerfilResponse>

    @GET("peticiones/tipos/")
    suspend fun obtenerTiposPeticion(): Response<List<TipoPeticionResponse>>

    // CORREGIDO: Debe apuntar a "peticiones/iniciar/" como lo tienes en tu Django urls.py
    @POST("peticiones/iniciar/")
    suspend fun iniciarPeticion(@Body request: IniciarPeticionRequest): Response<IniciarPeticionResponse>

    // Este se queda en la raíz para enviar el formulario completo
    @POST("peticiones/")
    suspend fun enviarPeticion(@Body peticion: confirmarpeticion): Response<Any>


        // Tu POST que ya tienes para enviar el reporte:


        // NUEVO: El GET para traer los tipos de la base de datos



}