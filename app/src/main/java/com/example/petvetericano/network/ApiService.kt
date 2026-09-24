package com.example.petvetericano.network

import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.models.DashboardResponse
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.models.TipoPeticionResponse
import com.example.petvetericano.models.UsuarioPerfilResponse
import com.example.petvetericano.models.confirmarpeticion
import com.example.petvetericano.models.PostularseResponse
import com.example.petvetericano.models.VoluntariadoEventos
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // --- AUTENTICACION Y USUARIOS ---
    @POST("usuarios/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>



    // MÉTODO RESTAURADO PARA EL REGISTRO
    @POST("usuarios/register/")
    suspend fun registro(@Body request: Any): Response<RegistroResponse>

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

    @POST("peticiones/iniciar/")
    suspend fun enviarPeticion(@Body peticion: confirmarpeticion): Response<Any>

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

    @POST("voluntariado/eventos/{id}/postularse/")
    suspend fun postularse(
        @Header("Authorization") token: String,
        @Path("id") idEvento: Int
    ): Response<PostularseResponse>

}

// 📌 MODELO RESTAURADO PARA EL REGISTRO
data class RegistroRequest(
    val email: String?,
    val password: String?,
    val nombre: String?
)

data class RegistroResponse(
    val mensaje: String?,
    val email: String?
)

data class GenericResponse(
    val mensaje: String?
)

data class JuridicoResponse(
    val email: String?,
    val nombre: String?
)