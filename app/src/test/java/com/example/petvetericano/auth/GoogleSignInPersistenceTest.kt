package com.example.petvetericano.auth

import com.example.petvetericano.GoogleAuthHelper
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.models.CompletarDatosGoogleRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.gson.GsonBuilder
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

class GoogleSignInPersistenceTest {

    private lateinit var server: MockWebServer

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
    fun googleSignInEstandar_primerInicio_logout_segundoInicio_sinContrasena() = runTest {
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

        val req1 = GoogleAuthHelper.construirGoogleLoginRequest(
            GoogleAuthHelper.CredencialGoogleExtraida(
                idToken = jwtGoogle1,
                email = "jdquintero@unicauca.edu.co",
                nombre = "Juan",
                apellido = "Quintero"
            )
        )
        assertNull(req1.email)
        assertNull(req1.nombre)
        assertNull(req1.apellido)

        val resp1 = RetrofitClient.ejecutarConRespaldoLocalDebug { it.loginConGoogle(req1) }
        assertTrue(resp1.isSuccessful)
        val datos1 = resp1.body()!!
        assertEquals(inicio_sesion.ROL_VETERINARIO, datos1.idRol)
        assertEquals("access-google-1", datos1.tokens.access)

        val recorded1 = server.takeRequest()
        assertEquals("/api/usuarios/google-login/", recorded1.path)
        val body1 = recorded1.body.readUtf8()
        assertTrue(body1.contains(jwtGoogle1))

        // Simula cierre de sesión
        RetrofitClient.authToken = null

        // 2do inicio con Google tras logout: nuevo ID Token directo contra /api/usuarios/google-login/
        val req2 = GoogleAuthHelper.construirGoogleLoginRequest(
            GoogleAuthHelper.CredencialGoogleExtraida(idToken = jwtGoogle2)
        )
        val resp2 = RetrofitClient.ejecutarConRespaldoLocalDebug { it.loginConGoogle(req2) }
        assertTrue(resp2.isSuccessful)
        val datos2 = resp2.body()!!
        assertEquals(false, datos2.esNuevoUsuario)
        assertEquals("access-google-2", datos2.tokens.access)
    }

    @Test
    fun requiereCompletarDatosPerfil_detectaFaltantesDeIdentificacionOTelefono() {
        assertTrue(GoogleAuthHelper.requiereCompletarDatosPerfil(null, null))
        assertTrue(GoogleAuthHelper.requiereCompletarDatosPerfil("", ""))
        assertTrue(GoogleAuthHelper.requiereCompletarDatosPerfil("   ", "3123456789"))
        assertTrue(GoogleAuthHelper.requiereCompletarDatosPerfil("1061789123", null))
        assertTrue(GoogleAuthHelper.requiereCompletarDatosPerfil("1061789123", "   "))
        assertFalse(GoogleAuthHelper.requiereCompletarDatosPerfil("1061789123", "3123456789"))
    }

    @Test
    fun validarDatosComplementariosGoogle_validaFormatoDocumentoYTelefono() {
        val vacios = AuthValidators.validarDatosComplementariosGoogle("", "")
        assertFalse(vacios.esValido)
        assertNotNull(vacios.errorIdentificacion)
        assertNotNull(vacios.errorTelefono)

        val docCorto = AuthValidators.validarDatosComplementariosGoogle("123", "3123456789")
        assertFalse(docCorto.esValido)
        assertNotNull(docCorto.errorIdentificacion)
        assertNull(docCorto.errorTelefono)

        val docLetras = AuthValidators.validarDatosComplementariosGoogle("ABC12345", "3123456789")
        assertFalse(docLetras.esValido)
        assertNotNull(docLetras.errorIdentificacion)

        val telCorto = AuthValidators.validarDatosComplementariosGoogle("1061789123", "312345")
        assertFalse(telCorto.esValido)
        assertNull(telCorto.errorIdentificacion)
        assertNotNull(telCorto.errorTelefono)

        val valido10 = AuthValidators.validarDatosComplementariosGoogle("1061789123", "3123456789")
        assertTrue(valido10.esValido)
        assertNull(valido10.errorIdentificacion)
        assertNull(valido10.errorTelefono)

        val valido15 = AuthValidators.validarDatosComplementariosGoogle("10617891234567890123", "573123456789012")
        assertTrue(valido15.esValido)
    }

    @Test
    fun completarDatosGoogleRequest_serializaSoloIdentificacionYTelefonoSinNulosExtra() {
        val gsonConNulos = GsonBuilder().serializeNulls().create()
        val json = gsonConNulos.toJson(
            CompletarDatosGoogleRequest(
                identificacion = "1061789123",
                telefono = "3123456789"
            )
        )
        assertEquals("""{"identificacion":"1061789123","telefono":"3123456789"}""", json)
        assertFalse(json.contains("foto_perfil"))
        assertFalse(json.contains("email"))
    }

    @Test
    fun completarDatosPerfil_apiPatchExitosoYErrorIdentificacionDuplicada400() = runTest {
        RetrofitClient.authToken = "access-google-temp"

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id_usuario": 55,
                  "email": "ciudadano@gmail.com",
                  "nombre": "Laura",
                  "apellido": "Rios",
                  "identificacion": "1061789123",
                  "telefono": "3123456789",
                  "foto_perfil": "https://lh3.googleusercontent.com/a/avatar"
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"identificacion":["Ya existe un usuario registrado con este número de identificación."]}"""
            )
        )

        val respOk = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.completarDatosPerfil(CompletarDatosGoogleRequest("1061789123", "3123456789"))
        }
        assertTrue(respOk.isSuccessful)
        assertEquals("1061789123", respOk.body()?.identificacion)
        assertEquals("3123456789", respOk.body()?.telefono)

        val reqOk = server.takeRequest()
        assertEquals("PATCH", reqOk.method)
        assertEquals("/api/peticiones/perfil/", reqOk.path)

        val respDup = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.completarDatosPerfil(CompletarDatosGoogleRequest("1061789123", "3123456789"))
        }
        assertFalse(respDup.isSuccessful)
        assertEquals(400, respDup.code())
        val parsed = AuthValidators.parsearErrorRegistroHttp(respDup.code(), respDup.errorBody()?.string())
        assertTrue(parsed.errorDocumento?.contains("identificación", ignoreCase = true) == true)
    }
}
