package com.example.petvetericano.network

import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.models.DashboardResponse
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.models.TipoPeticionResponse
import com.example.petvetericano.models.UsuarioPerfilResponse
import com.example.petvetericano.models.confirmarpeticion
import com.example.petvetericano.models.VoluntariadoEventos
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    // --- AUTENTICACION Y USUARIOS ---
    @POST("usuarios/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("usuarios/recuperar-password/")
    suspend fun solicitarRecuperacion(@Body request: Any): Response<GenericResponse>

    @POST("usuarios/confirmar-password/")
    suspend fun confirmarRecuperacion(@Body request: Any): Response<GenericResponse>

    // --- PERFIL DE USUARIO ---
    @GET("peticiones/perfil/")
    suspend fun obtenerPerfil(): Response<UsuarioPerfilResponse>

    @PATCH("peticiones/perfil/")
    suspend fun actualizarPerfil(@Body request: ActualizarPerfilRequest): Response<UsuarioPerfilResponse>

    // --- PETICIONES / REPORTES ---
    @GET("peticiones/tipos/")
    suspend fun obtenerTiposPeticion(): Response<List<TipoPeticionResponse>>

    // Método tradicional si la petición va sin foto (JSON plano)
    @POST("peticiones/iniciar/")
    suspend fun enviarPeticion(@Body peticion: confirmarpeticion): Response<Any>

    // 📌 MÉTODO CLAVE: Envío de petición con foto y datos obligatorios (Multipart)
    @Multipart
    @POST("peticiones/iniciar/")
    suspend fun enviarPeticionConFoto(
        @Part("tipo_peticion") tipoPeticion: RequestBody,
        @Part("descripcion") descripcion: RequestBody,
        @Part("latitud") latitud: RequestBody,
        @Part("longitud") longitud: RequestBody,
        @Part("direccion") direccion: RequestBody,
        @Part imagen: MultipartBody.Part?
    ): Response<GenericResponse>

    // --- OTROS MODULOS ---
    @GET("especies/especies/")
    suspend fun obtenerEspecies(): Response<Any>

    @GET("medicamentos/")
    suspend fun obtenerMedicamentos(): Response<Any>

    @GET("dashboard/")
    suspend fun obtenerDashboard(): Response<DashboardResponse>

    @GET("voluntariado/eventos/")
    suspend fun obtenerEventos(
        @Header("Authorization") token: String? = null
    ): Response<List<VoluntariadoEventos>>

    // --- MODULO JURIDICO ---
    @GET("usuarios/juridica/")
    suspend fun obtenerCorreoJuridico(): Response<JuridicoResponse>
}

// Modelos de soporte integrados
data class GenericResponse(
    val mensaje: String?,
    val error: String?
)

data class JuridicoResponse(
    val email: String?,
    val nombre: String?
)