package com.example.petvetericano.auth

import com.example.petvetericano.models.ConfirmarPasswordRequest
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.RecuperarPasswordRequest
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

class PasswordRecoveryTest {

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
    fun validarNuevaPassword_exigeOchoCaracteresMayusculaYNumero() {
        assertFalse(AuthValidators.esPasswordFuerte("Abc1234"))
        assertFalse(AuthValidators.esPasswordFuerte("abcdefgh1"))
        assertFalse(AuthValidators.esPasswordFuerte("ABCDEFGH"))
        assertTrue(AuthValidators.esPasswordFuerte("NuevaClave2026"))
    }

    @Test
    fun flujoCompletoRecuperacion_solicitud_validacionCodigo_y_loginConNuevaPassword() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Si el correo está registrado, recibirás un código de recuperación."}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":"Código inválido o expirado."}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Contraseña actualizada exitosamente."}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Ana",
                  "email": "ana@vetericano.com",
                  "id_usuario": 15,
                  "nombre": "Ana",
                  "apellido": "Lopez",
                  "id_rol": 4,
                  "tokens": {"refresh": "ref-new", "access": "acc-new"}
                }
                """.trimIndent()
            )
        )

        // 1. Solicitar recuperación
        val respSol = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.solicitarRecuperacion(RecuperarPasswordRequest("ana@vetericano.com"))
        }
        assertTrue(respSol.isSuccessful)
        assertEquals("/api/usuarios/recuperar-password/", server.takeRequest().path)

        // 2. Código inválido
        val respBad = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.confirmarRecuperacion(ConfirmarPasswordRequest("ana@vetericano.com", "000000", "NuevaClave2026"))
        }
        assertFalse(respBad.isSuccessful)
        val msgErr = AuthValidators.parsearErrorGenericoHttp(respBad.code(), respBad.errorBody()?.string())
        assertEquals("Código inválido o expirado.", msgErr)
        server.takeRequest()

        // 3. Código válido
        val respConfirm = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.confirmarRecuperacion(ConfirmarPasswordRequest("ana@vetericano.com", "654321", "NuevaClave2026"))
        }
        assertTrue(respConfirm.isSuccessful)
        val confirmReq = server.takeRequest()
        assertEquals("/api/usuarios/confirmar-password/", confirmReq.path)
        assertTrue(confirmReq.body.readUtf8().contains("\"nueva_password\":\"NuevaClave2026\""))

        // 4. Login con nueva contraseña
        val respLogin = RetrofitClient.ejecutarConRespaldoLocalDebug {
            it.login(LoginRequest("ana@vetericano.com", "NuevaClave2026"))
        }
        assertTrue(respLogin.isSuccessful)
        assertEquals("acc-new", respLogin.body()?.tokens?.access)
    }
}
