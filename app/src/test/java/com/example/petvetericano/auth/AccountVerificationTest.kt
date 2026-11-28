package com.example.petvetericano.auth

import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.models.ConfirmarCodigoRegistroRequest
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountVerificationTest {

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
    fun validarCodigoOtp_exigeSeisDigitosNumericos() {
        assertTrue(AuthValidators.esCodigoOtpValido("123456"))
        assertTrue(AuthValidators.esCodigoOtpValido(" 009988 "))
        assertFalse(AuthValidators.esCodigoOtpValido("12345"))
        assertFalse(AuthValidators.esCodigoOtpValido("1234567"))
        assertFalse(AuthValidators.esCodigoOtpValido("12a456"))
        assertFalse(AuthValidators.esCodigoOtpValido(""))
    }

    @Test
    fun confirmarCodigo_exitoso_erroneo_y_agotado() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":"El código de verificación es incorrecto. Te quedan 4 intento(s).","intentos_restantes":4}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":"Has superado el límite de 5 intentos fallidos.","intentos_restantes":0}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"mensaje":"Correo verificado y cuenta creada exitosamente.","email":"nuevo@vetericano.com"}"""
            )
        )

        val respWrong = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.confirmarCodigoRegistro(ConfirmarCodigoRegistroRequest("nuevo@vetericano.com", "000000"))
        }
        assertFalse(respWrong.isSuccessful)
        val parsedWrong = AuthValidators.parsearErrorRegistroHttp(respWrong.code(), respWrong.errorBody()?.string())
        assertEquals(4, parsedWrong.intentosRestantes)
        assertTrue(parsedWrong.mensajeGeneral.contains("incorrecto", ignoreCase = true))

        val respExhausted = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.confirmarCodigoRegistro(ConfirmarCodigoRegistroRequest("nuevo@vetericano.com", "999999"))
        }
        val parsedExhausted = AuthValidators.parsearErrorRegistroHttp(
            respExhausted.code(),
            respExhausted.errorBody()?.string()
        )
        assertEquals(0, parsedExhausted.intentosRestantes)

        val respOk = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.confirmarCodigoRegistro(ConfirmarCodigoRegistroRequest("nuevo@vetericano.com", "654321"))
        }
        assertTrue(respOk.isSuccessful)
        assertEquals(201, respOk.code())
        assertEquals("nuevo@vetericano.com", respOk.body()?.email)
    }

    @Test
    fun loginUsuarioNoVerificadoOInactivo_devuelveMensajeControlado() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"non_field_errors":["Esta cuenta ha sido desactivada."]}"""
            )
        )

        val resp = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.login(LoginRequest("inactivo@vetericano.com", "ClaveSegura1"))
        }
        assertFalse(resp.isSuccessful)
        val mensaje = inicio_sesion.resolverMensajeErrorHttp(resp.code(), resp.errorBody()?.string())
        assertEquals("Esta cuenta ha sido desactivada.", mensaje)
    }
}
