package com.example.petvetericano.network

import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.models.AdopcionAnimalResponse
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

    @PATCH("peticiones/{id}/estado/")
    suspend fun actualizarEstadoPeticion(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: com.example.petvetericano.models.ActualizarEstadoRequest
    ): Response<com.example.petvetericano.models.ActualizarEstadoResponse>

    @GET("peticiones/listar/")
    suspend fun listarPeticiones(
        @Header("Authorization") token: String
    ): Response<List<com.example.petvetericano.models.PeticionListResponse>>

    @POST("peticiones/seguimiento/crear/")
    suspend fun crearSeguimientoPeticionVisita(
        @Header("Authorization") token: String,
        @Body request: com.example.petvetericano.models.SeguimientoPeticionVisitaRequest
    ): Response<com.example.petvetericano.models.CrearActaResponse>

    @GET("peticiones/seguimiento/funcionarios/")
    suspend fun obtenerFuncionariosVisita(
        @Header("Authorization") token: String
    ): Response<List<com.example.petvetericano.models.FuncionarioItemResponse>>

    // --- PASO 2: PACIENTES Y CLÍNICA (POST) ---
    @POST("animales/animales/")
    suspend fun registrarAnimal(
        @Header("Authorization") token: String,
        @Body request: com.example.petvetericano.models.AnimalRequest
    ): Response<com.example.petvetericano.models.AnimalResponse>

    @POST("clinica/consultas/")
    suspend fun registrarConsultaClinica(
        @Header("Authorization") token: String,
        @Body request: com.example.petvetericano.models.ConsultaClinicaRequest
    ): Response<com.example.petvetericano.models.ConsultaClinicaResponse>

    @POST("clinica/tratamientos/")
    suspend fun registrarTratamientoClinico(
        @Header("Authorization") token: String,
        @Body request: com.example.petvetericano.models.TratamientoClinicoRequest
    ): Response<com.example.petvetericano.models.TratamientoClinicoResponse>

    // --- PASO 3: PRUEBAS Y CIERRE (POST) ---
    @POST("examenes-clinicos/examenes/")
    suspend fun registrarExamenClinico(
        @Header("Authorization") token: String,
        @Body request: com.example.petvetericano.models.ExamenClinicoRequest
    ): Response<com.example.petvetericano.models.ExamenClinicoResponse>



    // --- ADOPCIONES ---
    @GET("adopciones/")
    suspend fun obtenerAnimalesAdopcion(): Response<List<AdopcionAnimalResponse>>

    // --- OTROS MODULOS ---
    @GET("especies/especies/")
    suspend fun obtenerEspecies(
        @Header("Authorization") token: String? = null
    ): Response<List<com.example.petvetericano.models.EspecieItemResponse>>

    @GET("especies/razas/")
    suspend fun obtenerRazas(
        @Header("Authorization") token: String? = null
    ): Response<List<com.example.petvetericano.models.RazaItemResponse>>

    @GET("examenes-clinicos/catalogo/")
    suspend fun obtenerCatalogoExamenes(
        @Header("Authorization") token: String? = null
    ): Response<List<com.example.petvetericano.models.ExamenCatalogoItemResponse>>

    @GET("medicamentos/")
    suspend fun obtenerMedicamentos(): Response<Any>

    @GET("dashboard/")
    suspend fun obtenerDashboard(): Response<DashboardResponse>

    // --- VOLUNTARIADO ---
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