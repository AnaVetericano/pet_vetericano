package com.example.petvetericano.auth

import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterValidationAndErrorParsingTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        RetrofitClient.configurarParaPruebas(
            baseUrl = server.url("/api/").toString(),
            deshabilitarRespaldoLocal = true
        )
    }

    @After
    fun tearDown() {
        RetrofitClient.restaurarConfiguracion()
        server.shutdown()
    }

    @Test
    fun validarFormularioRegistro_detectaErroresLocalesAntesDeLlamarAlBackend() {
        val sinNombre = AuthValidators.validarFormularioRegistro("", "Perez", "123", "3001234567", "a@b.com", "Clave123")
        assertFalse(sinNombre.esValido)
        assertNotNull(sinNombre.errorNombre)

        val telNoNumerico = AuthValidators.validarFormularioRegistro("Ana", "Perez", "123", "----------", "a@b.com", "Clave123")
        assertFalse(telNoNumerico.esValido)
        assertNotNull(telNoNumerico.errorTelefono)

        val emailInvalido = AuthValidators.validarFormularioRegistro("Ana", "Perez", "123", "3001234567", "correo-malo", "Clave123")
        assertFalse(emailInvalido.esValido)
        assertNotNull(emailInvalido.errorEmail)

        val passDebil = AuthValidators.validarFormularioRegistro("Ana", "Perez", "123", "3001234567", "ana@correo.com", "sinnumero")
        assertFalse(passDebil.esValido)
        assertNotNull(passDebil.errorPassword)

        val valido = AuthValidators.validarFormularioRegistro("Ana", "Perez", "1061123456", "3001234567", "ana@correo.com", "ClaveSegura1")
        assertTrue(valido.esValido)
    }

    @Test
    fun parsearErrorRegistroHttp_detectaCorreoDuplicadoCooldownYErrores5xx() {
        val dup = AuthValidators.parsearErrorRegistroHttp(
            400,
            """{"email":["Ya existe un usuario registrado con este correo electrónico."]}"""
        )
        assertTrue(dup.esCorreoDuplicado)
        assertEquals("Ya existe un usuario registrado con este correo electrónico.", dup.errorEmail)

        // Error de formato de correo NO debe clasificarse falsamente como correo duplicado
        val formato = AuthValidators.parsearErrorRegistroHttp(
            400,
            """{"email":["Introduzca una dirección de correo electrónico válida."]}"""
        )
        assertFalse(formato.esCorreoDuplicado)

        // Error de identificación duplicada NO debe clasificarse como correo duplicado
        val docDup = AuthValidators.parsearErrorRegistroHttp(
            400,
            """{"identificacion":["Ya existe un usuario registrado con este número de identificación."]}"""
        )
        assertFalse(docDup.esCorreoDuplicado)
        assertNotNull(docDup.errorDocumento)

        val cooldown = AuthValidators.parsearErrorRegistroHttp(
            429,
            """{"error":"Espera 45 segundos.","retry_after":45}"""
        )
        assertEquals(45, cooldown.retryAfterSegundos)

        val err500Html = AuthValidators.parsearErrorRegistroHttp(500, "<html><body>Internal Server Error</body></html>")
        assertTrue(err500Html.mensajeGeneral.contains("500"))
        assertFalse(err500Html.mensajeGeneral.contains("<html>"))
    }

    @Test
    fun solicitarCodigoRegistro_correoDuplicado400_y_registroExitoso200() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"email":["Ya existe un usuario registrado con este correo electrónico."]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Código enviado","email":"nuevo@vetericano.com","cooldown_segundos":60,"expira_en_segundos":600}"""
            )
        )

        val reqDup = RegisterRequest("dup@vetericano.com", "1061000111", "ClaveSegura1", "Ana", "Lopez", "3101234567")
        val respDup = RetrofitClient.ejecutarConRespaldoLocalDebug { it.solicitarCodigoRegistro(reqDup) }
        assertEquals(400, respDup.code())
        val parsedDup = AuthValidators.parsearErrorRegistroHttp(respDup.code(), respDup.errorBody()?.string())
        assertTrue(parsedDup.esCorreoDuplicado)

        val reqOk = reqDup.copy(email = "nuevo@vetericano.com")
        val respOk = RetrofitClient.ejecutarConRespaldoLocalDebug { it.solicitarCodigoRegistro(reqOk) }
        assertTrue(respOk.isSuccessful)
        assertEquals(60, respOk.body()?.cooldownSegundos)
        assertEquals(600, respOk.body()?.expiraEnSegundos)
    }
}
