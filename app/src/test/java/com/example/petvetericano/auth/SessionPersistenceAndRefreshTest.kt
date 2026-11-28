package com.example.petvetericano.auth

import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.TokenAuthenticator
import com.example.petvetericano.network.TokenStore
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionPersistenceAndRefreshTest {

    private lateinit var server: MockWebServer

    private class FakeTokenStore(
        var access: String = "",
        var refresh: String = "",
        var uid: Int = 10
    ) : TokenStore {
        var rotacionesNotificadas: MutableList<Pair<Int, String>> = mutableListOf()

        override fun getAccessToken(): String = access
        override fun getRefreshToken(): String = refresh
        override fun getUserId(): Int = uid
        override fun saveAccessToken(token: String) {
            access = token
        }

        override fun saveRefreshToken(token: String) {
            refresh = token
        }

        override fun onRefreshTokenRotado(userId: Int, nuevoRefreshToken: String) {
            rotacionesNotificadas.add(userId to nuevoRefreshToken)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sessionRouter_conAccessYRefreshValidos_dirigeSegunRol() {
        assertEquals(
            SessionRouter.DestinoSesion.VETERINARIO,
            SessionRouter.resolverDestino("acc", "ref", 10, inicio_sesion.ROL_VETERINARIO)
        )
        assertEquals(
            SessionRouter.DestinoSesion.CIUDADANO,
            SessionRouter.resolverDestino("acc", "ref", 11, 4)
        )
    }

    @Test
    fun sessionRouter_soloConRefreshTokenTrasReinicio_mantieneSesionActiva() {
        assertEquals(
            SessionRouter.DestinoSesion.CIUDADANO,
            SessionRouter.resolverDestino("", "refresh-valido", 11, 4)
        )
        assertEquals(
            SessionRouter.DestinoSesion.VETERINARIO,
            SessionRouter.resolverDestino("", "refresh-valido", 12, inicio_sesion.ROL_VETERINARIO)
        )
    }

    @Test
    fun sessionRouter_sinTokensOTrasLogout_dirigeAWelcome() {
        assertEquals(SessionRouter.DestinoSesion.WELCOME, SessionRouter.resolverDestino("", "", 11, 4))
        assertEquals(SessionRouter.DestinoSesion.WELCOME, SessionRouter.resolverDestino("acc", "ref", -1, 4))
        assertEquals(SessionRouter.DestinoSesion.WELCOME, SessionRouter.resolverDestino("acc", "ref", 11, -1))
    }

    @Test
    fun tokenAuthenticator_401EnRutaProtegida_refrescaYRotaRefreshYReintenta() {
        val store = FakeTokenStore(access = "access-expirado", refresh = "refresh-inicial", uid = 42)
        var refreshMuerto: String? = null
        var authTokenEnMemoria: String? = null

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access":"access-renovado","refresh":"refresh-rotado-2"}"""
            )
        )

        val authenticator = TokenAuthenticator(
            tokenStoreProvider = { store },
            baseUrlProvider = { server.url("/api/").toString() },
            clientProvider = { OkHttpClient() },
            onSesionMuerta = { refreshMuerto = it },
            getRefreshMuerto = { refreshMuerto },
            onAuthTokenActualizado = { authTokenEnMemoria = it }
        )

        val failedRequest = Request.Builder()
            .url(server.url("/api/peticiones/perfil/"))
            .header("Authorization", "Bearer access-expirado")
            .build()
        val response401 = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        val retryRequest = authenticator.authenticate(null, response401)
        assertNotNull(retryRequest)
        assertEquals("Bearer access-renovado", retryRequest?.header("Authorization"))
        assertEquals("access-renovado", store.access)
        assertEquals("refresh-rotado-2", store.refresh)
        assertEquals("access-renovado", authTokenEnMemoria)
        assertEquals(listOf(42 to "refresh-rotado-2"), store.rotacionesNotificadas)
        assertNull(refreshMuerto)
    }

    @Test
    fun tokenAuthenticator_refresh401_marcaSesionMuertaYSinBucle() {
        val store = FakeTokenStore(access = "access-expirado", refresh = "refresh-revocado", uid = 42)
        var refreshMuerto: String? = null

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Token invalid"}"""))

        val authenticator = TokenAuthenticator(
            tokenStoreProvider = { store },
            baseUrlProvider = { server.url("/api/").toString() },
            clientProvider = { OkHttpClient() },
            onSesionMuerta = { refreshMuerto = it },
            getRefreshMuerto = { refreshMuerto },
            onAuthTokenActualizado = {}
        )

        val failedRequest = Request.Builder()
            .url(server.url("/api/peticiones/perfil/"))
            .header("Authorization", "Bearer access-expirado")
            .build()
        val response401 = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        assertNull(authenticator.authenticate(null, response401))
        assertEquals("refresh-revocado", refreshMuerto)
        assertEquals(1, server.requestCount)

        // Segundo 401 con el mismo refresh muerto no debe hacer ninguna petición HTTP adicional
        assertNull(authenticator.authenticate(null, response401))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun tokenAuthenticator_omiteRutasPublicasDeAuth() {
        val store = FakeTokenStore(access = "old", refresh = "ref")
        val authenticator = TokenAuthenticator(
            tokenStoreProvider = { store },
            baseUrlProvider = { server.url("/api/").toString() },
            clientProvider = { OkHttpClient() },
            onSesionMuerta = {},
            getRefreshMuerto = { null },
            onAuthTokenActualizado = {}
        )

        val rutas = listOf(
            "/api/usuarios/login/",
            "/api/usuarios/register/solicitar-codigo/",
            "/api/usuarios/google-login/",
            "/api/usuarios/recuperar-password/",
            "/api/usuarios/confirmar-password/",
            "/api/token/refresh/"
        )
        for (ruta in rutas) {
            val req = Request.Builder()
                .url(server.url(ruta))
                .header("Authorization", "Bearer old")
                .build()
            val resp = Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("".toResponseBody())
                .build()
            assertNull("Debe omitir $ruta", authenticator.authenticate(null, resp))
        }
        assertEquals(0, server.requestCount)
        assertTrue(store.rotacionesNotificadas.isEmpty())
    }
}
