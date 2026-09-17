package com.example.petvetericano.network

import com.example.petvetericano.models.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

interface ApiService {

    // --- AUTENTICACIÓN Y USUARIOS ---
    @POST("usuarios/login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("usuarios/registro/")
    suspend fun registro(
        @Body request: RegisterRequest
    ): Response<GenericResponse>

    @POST("usuarios/recuperar-password/")
    suspend fun solicitarRecuperacion(
        @Body request: RecuperarPasswordRequest
    ): Response<GenericResponse>

    @POST("usuarios/confirmar-password/")
    suspend fun confirmarRecuperacion(
        @Body request: ConfirmarPasswordRequest
    ): Response<GenericResponse>

    // --- PERFIL DE USUARIO ---
    @GET("peticiones/perfil/")
    suspend fun obtenerPerfil(): Response<UsuarioPerfilResponse>

    @PATCH("peticiones/perfil/")
    suspend fun actualizarPerfil(
        @Body request: ActualizarPerfilRequest
    ): Response<UsuarioPerfilResponse>

    // --- OTROS MÓDULOS ---
    @GET("especies/especies/")
    suspend fun obtenerEspecies(
        @Header("Authorization") token: String
    ): Response<Any>

    @GET("medicamentos/")
    suspend fun obtenerMedicamentos(
        @Header("Authorization") token: String
    ): Response<Any>

    @GET("dashboard/")
    suspend fun obtenerDashboard(
        @Header("Authorization") token: String
    ): Response<DashboardResponse>
}