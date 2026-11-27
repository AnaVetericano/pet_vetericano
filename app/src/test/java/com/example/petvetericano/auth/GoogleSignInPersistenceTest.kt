package com.example.petvetericano.auth

import com.example.petvetericano.GoogleAuthHelper
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GoogleSignInPersistenceTest {

    private lateinit var server: MockWebServer

    private class FakeAlmacenVinculoGoogle : AlmacenVinculoGoogle {
        private val store = mutableMapOf<String, CuentaGoogleVinculada>()

        override fun guardarCuentaVinculada(cuenta: CuentaGoogleVinculada): Boolean {
            val key = cuenta.email.trim().lowercase(Locale.ROOT)
            if (key.isEmpty() || cuenta.refreshToken.isBlank()) return false
            store[key] = cuenta.copy(email = key)
            return true
        }

        override fun obtenerCuentaVinculada(email: String): CuentaGoogleVinculada? =
            store[email.trim().lowercase(Locale.ROOT)]

        override fun actualizarRefreshTokenSiCoincide(
            userId: Int,
            nuevoRefreshToken: String,
            nombre: String?,
            apellido: String?,
            fotoPerfil: String?,
            telefono: String?,
            matriculaProfesional: String?
        ) {
            for ((k, v) in store.entries.toList()) {
                if (v.userId == userId) {
                    store[k] = v.copy(
                        refreshToken = nuevoRefreshToken.trim(),
                        nombre = nombre?.takeIf { it.isNotBlank() } ?: v.nombre,
                        apellido = apellido?.takeIf { it.isNotBlank() } ?: v.apellido,
                        fotoPerfil = fotoPerfil ?: v.fotoPerfil,
                        telefono = telefono?.takeIf { it.isNotBlank() } ?: v.telefono,
                        matriculaProfesional = matriculaProfesional?.takeIf { it.isNotBlank() }
                            ?: v.matriculaProfesional
                    )
                }
            }
        }

        override fun eliminarCuentaVinculada(email: String) {
            store.remove(email.trim().lowercase(Locale.ROOT))
        }

        override fun limpiarTodo() {
            store.clear()
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        RetrofitClient.configurarParaPruebas(
            baseUrl = server.url("/api/").toString(),
            deshabilitarRespaldoLocal = true
        )
        RetrofitClient.authToken = null
    }

    @After
    fun tearDown() {
        RetrofitClient.restaurarConfiguracion()
        RetrofitClient.authToken = null
        GoogleAuthHelper.restaurarConfiguracion()
        server.shutdown()
    }

    @Test
    fun googleSignInOAuth_primerInicio_logout_segundoInicio_sinPedirContrasena() = runTest {
        val jwtGoogle1 = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImpkcXVpbnRlcm9AdW5pY2F1Y2EuZWR1LmNvIn0.sig1"
        val jwtGoogle2 = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImpkcXVpbnRlcm9AdW5pY2F1Y2EuZWR1LmNvIn0.sig2"

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Juan Quintero",
                  "email": "jdquintero@unicauca.edu.co",
                  "id_usuario": 42,
                  "nombre": "Juan",
                  "apellido": "Quintero",
                  "telefono": "3123456789",
                  "matricula_profesional": "MV-9001",
                  "id_rol": 3,
                  "es_nuevo_usuario": true,
                  "tokens": {"refresh": "refresh-google-1", "access": "access-google-1"}
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Juan Quintero",
                  "email": "jdquintero@unicauca.edu.co",
                  "id_usuario": 42,
                  "nombre": "Juan",
                  "apellido": "Quintero",
                  "telefono": "3123456789",
                  "matricula_profesional": "MV-9001",
                  "id_rol": 3,
                  "es_nuevo_usuario": false,
                  "tokens": {"refresh": "refresh-google-2", "access": "access-google-2"}
                }
                """.trimIndent()
            )
        )

        val almacen = FakeAlmacenVinculoGoogle()
        var dialogosPasswordMostrados = 0
        GoogleAuthHelper.onDialogoPasswordMostradoPrueba = { dialogosPasswordMostrados++ }

        // 1er inicio con Google
        val req1 = GoogleAuthHelper.construirGoogleLoginRequest(
            GoogleAuthHelper.CredencialGoogleExtraida(
                idToken = jwtGoogle1,
                email = "jdquintero@unicauca.edu.co",
                nombre = "Juan",
                apellido = "Quintero"
            )
        )
        val resp1 = RetrofitClient.ejecutarConRespaldoLocalDebug { it.loginConGoogle(req1) }
        assertTrue(resp1.isSuccessful)
        val datos1 = resp1.body()!!
        assertEquals(inicio_sesion.ROL_VETERINARIO, datos1.idRol)
        assertTrue(
            almacen.guardarCuentaVinculada(
                CuentaGoogleVinculada(
                    userId = datos1.idUsuario,
                    email = datos1.email,
                    nombre = datos1.nombre.orEmpty(),
                    apellido = datos1.apellido.orEmpty(),
                    telefono = datos1.telefono.orEmpty(),
                    fotoPerfil = "",
                    matriculaProfesional = datos1.matriculaProfesional.orEmpty(),
                    idRol = datos1.idRol,
                    refreshToken = datos1.tokens.refresh
                )
            )
        )

        // Simula cierre de sesión (limpia token en memoria, pero conserva la bóveda Google cifrada)
        RetrofitClient.authToken = null
        assertNotNull(almacen.obtenerCuentaVinculada("jdquintero@unicauca.edu.co"))

        // 2do inicio con Google tras logout
        val req2 = GoogleAuthHelper.construirGoogleLoginRequest(
            GoogleAuthHelper.CredencialGoogleExtraida(
                idToken = jwtGoogle2,
                email = "jdquintero@unicauca.edu.co"
            )
        )
        val resp2 = RetrofitClient.ejecutarConRespaldoLocalDebug { it.loginConGoogle(req2) }
        assertTrue(resp2.isSuccessful)
        val datos2 = resp2.body()!!
        assertEquals(false, datos2.esNuevoUsuario)
        assertEquals("access-google-2", datos2.tokens.access)
        assertEquals(0, dialogosPasswordMostrados)
    }

    @Test
    fun respaldoGoogleVinculado_trasLogout_reanudaConRefreshSinPedirContrasena() = runTest {
        val almacen = FakeAlmacenVinculoGoogle()
        almacen.guardarCuentaVinculada(
            CuentaGoogleVinculada(
                userId = 42,
                email = "jdquintero@unicauca.edu.co",
                nombre = "Juan",
                apellido = "Quintero",
                telefono = "3123456789",
                fotoPerfil = "https://cdn.vetericano.test/vet.jpg",
                matriculaProfesional = "MV-9001",
                idRol = inicio_sesion.ROL_VETERINARIO,
                refreshToken = "refresh-vinculado-v1"
            )
        )

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access":"access-nuevo-v2","refresh":"refresh-rotado-v2"}"""
            )
        )

        val cuenta = almacen.obtenerCuentaVinculada("JDQUINTERO@UNICAUCA.EDU.CO")
        assertNotNull(cuenta)

        val resultado = GoogleAuthHelper.reanudarSesionGoogleVinculada(cuenta!!, almacen)
        assertTrue("Debe reanudar sesión sin pedir contraseña", resultado is GoogleAuthHelper.ResultadoReanudarVinculo.Exito)

        val exito = resultado as GoogleAuthHelper.ResultadoReanudarVinculo.Exito
        assertEquals("access-nuevo-v2", exito.loginResponse.tokens.access)
        assertEquals("refresh-rotado-v2", exito.loginResponse.tokens.refresh)
        assertEquals(inicio_sesion.ROL_VETERINARIO, exito.loginResponse.idRol)
        assertEquals(false, exito.loginResponse.esNuevoUsuario)

        // Verifica que el refresh rotado quedó persistido para el siguiente logout/login
        val actualizada = almacen.obtenerCuentaVinculada("jdquintero@unicauca.edu.co")
        assertEquals("refresh-rotado-v2", actualizada?.refreshToken)

        val recorded = server.takeRequest()
        assertEquals("/api/token/refresh/", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("refresh-vinculado-v1"))
    }

    @Test
    fun respaldoGoogleVinculado_cuentaDesactivada401_eliminaVinculoYRechazaSinPedirPassword() = runTest {
        val almacen = FakeAlmacenVinculoGoogle()
        val cuenta = CuentaGoogleVinculada(
            userId = 42,
            email = "inactivo@unicauca.edu.co",
            nombre = "Juan",
            apellido = "Quintero",
            telefono = "",
            fotoPerfil = "",
            matriculaProfesional = "",
            idRol = 4,
            refreshToken = "refresh-inactivo"
        )
        almacen.guardarCuentaVinculada(cuenta)

        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"detail":"El usuario está desactivado."}"""
            )
        )

        val resultado = GoogleAuthHelper.reanudarSesionGoogleVinculada(cuenta, almacen)
        assertTrue(resultado is GoogleAuthHelper.ResultadoReanudarVinculo.CuentaRechazada)
        assertNull("Debe purgar vínculo de cuenta desactivada", almacen.obtenerCuentaVinculada(cuenta.email))
    }

    @Test
    fun respaldoGoogleVinculado_tokenExpirado401_limpiaVinculoYSolicitaReverificacion() = runTest {
        val almacen = FakeAlmacenVinculoGoogle()
        val cuenta = CuentaGoogleVinculada(
            userId = 42,
            email = "expirado@unicauca.edu.co",
            nombre = "Juan",
            apellido = "Quintero",
            telefono = "",
            fotoPerfil = "",
            matriculaProfesional = "",
            idRol = 3,
            refreshToken = "refresh-vencido"
        )
        almacen.guardarCuentaVinculada(cuenta)

        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"detail":"Token is invalid or expired"}"""
            )
        )

        val resultado = GoogleAuthHelper.reanudarSesionGoogleVinculada(cuenta, almacen)
        assertTrue(resultado is GoogleAuthHelper.ResultadoReanudarVinculo.RequiereVerificacionPassword)
        assertNull(almacen.obtenerCuentaVinculada(cuenta.email))
    }

    @Test
    fun bovedaGoogle_serializacionNuncaIncluyeContrasena() {
        val mapa = mapOf(
            "jdquintero@unicauca.edu.co" to CuentaGoogleVinculada(
                userId = 42,
                email = "jdquintero@unicauca.edu.co",
                nombre = "Juan",
                apellido = "Quintero",
                telefono = "3123456789",
                fotoPerfil = "",
                matriculaProfesional = "MV-1",
                idRol = 3,
                refreshToken = "jwt-refresh-seguro"
            )
        )
        val json = GoogleLinkedSessionVault.serializarMapaAJson(mapa)
        assertFalse(json.contains("password", ignoreCase = true))
        val restaurado = GoogleLinkedSessionVault.deserializarMapaDesdeJson(json)
        assertEquals("jwt-refresh-seguro", restaurado["jdquintero@unicauca.edu.co"]?.refreshToken)
    }
}
