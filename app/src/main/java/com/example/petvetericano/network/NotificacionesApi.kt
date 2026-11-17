package com.example.petvetericano.network

import com.example.petvetericano.models.DesactivarDispositivoRequest
import com.example.petvetericano.models.DesactivarDispositivoResponse
import com.example.petvetericano.models.LeerTodasNotificacionesResponse
import com.example.petvetericano.models.NotificacionItem
import com.example.petvetericano.models.NotificacionesContadorResponse
import com.example.petvetericano.models.NotificacionesListaResponse
import com.example.petvetericano.models.RegistrarDispositivoRequest
import com.example.petvetericano.models.RegistrarDispositivoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST central de notificaciones (CONTRATO_NOTIFICACIONES_V1 §6). JWT Bearer como el resto de la API.
 * Se crea sobre el mismo Retrofit/OkHttp autenticado de [RetrofitClient] (AuthInterceptor + TokenAuthenticator).
 * El header `Authorization` se pasa explícito (leído de la sesión guardada) porque estas llamadas también
 * se hacen desde el servicio FCM y en primer plano, cuando `RetrofitClient.authToken` puede no estar cargado.
 */
interface NotificacionesApi {

    /** `GET /api/notificaciones/?solo_no_leidas=true&modulo=<m>&limit=50&offset=0` (limit máx. 200). */
    @GET("notificaciones/")
    suspend fun listar(
        @Header("Authorization") token: String,
        @Query("solo_no_leidas") soloNoLeidas: Boolean? = null,
        @Query("modulo") modulo: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): Response<NotificacionesListaResponse>

    /** `GET /api/notificaciones/contador/` */
    @GET("notificaciones/contador/")
    suspend fun contador(
        @Header("Authorization") token: String
    ): Response<NotificacionesContadorResponse>

    /** `PATCH /api/notificaciones/<clave>/leer/` → 200 ítem actualizado · 404 si no existe o no es del usuario. */
    @PATCH("notificaciones/{clave}/leer/")
    suspend fun leer(
        @Header("Authorization") token: String,
        @Path("clave") clave: String
    ): Response<NotificacionItem>

    /** `PATCH /api/notificaciones/leer-todas/?modulo=<m>` → `{"actualizadas":N}` */
    @PATCH("notificaciones/leer-todas/")
    suspend fun leerTodas(
        @Header("Authorization") token: String,
        @Query("modulo") modulo: String? = null
    ): Response<LeerTodasNotificacionesResponse>

    /** `POST /api/notificaciones/dispositivos/` `{"token":"...","plataforma":"android"}` → 201/200 `{"registrado":true}` */
    @POST("notificaciones/dispositivos/")
    suspend fun registrarDispositivo(
        @Header("Authorization") token: String,
        @Body request: RegistrarDispositivoRequest
    ): Response<RegistrarDispositivoResponse>

    /** `POST /api/notificaciones/dispositivos/desactivar/` `{"token":"..."}` → 200 `{"desactivado":true|false}` */
    @POST("notificaciones/dispositivos/desactivar/")
    suspend fun desactivarDispositivo(
        @Header("Authorization") token: String,
        @Body request: DesactivarDispositivoRequest
    ): Response<DesactivarDispositivoResponse>
}
