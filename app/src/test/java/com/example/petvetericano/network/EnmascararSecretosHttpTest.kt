package com.example.petvetericano.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los logs HTTP de debug no deben contener JWT, tokens FCM ni contraseñas completos. */
class EnmascararSecretosHttpTest {

    @Test
    fun respuestaDeLogin_enmascaraAccessYRefresh() {
        val access = "eyJhbGciOiJIUzI1NiJ9.payload.firmaACCESS123456"
        val refresh = "eyJhbGciOiJIUzI1NiJ9.payload.firmaREFRESH654321"
        val cuerpo = """{"mensaje":"ok","tokens":{"refresh":"$refresh","access": "$access"},"id_usuario":12}"""

        val limpio = enmascararSecretosHttp(cuerpo)

        assertFalse(limpio.contains(access))
        assertFalse(limpio.contains(refresh))
        assertTrue(limpio.contains("\"access\":\"***123456\""))
        assertTrue(limpio.contains("\"refresh\":\"***654321\""))
        assertTrue(limpio.contains("\"id_usuario\":12"))
        assertTrue(limpio.contains("\"mensaje\":\"ok\""))
    }

    @Test
    fun registroDeDispositivo_enmascaraTokenFcm() {
        val tokenFcm = "dGVzdC10b2tlbi1mY20tbXV5LWxhcmdvLXBhcmEtcHJ1ZWJhcw:APA91bQwErTy"
        val cuerpo = """{"token":"$tokenFcm","plataforma":"android"}"""

        val limpio = enmascararSecretosHttp(cuerpo)

        assertEquals("""{"token":"***QwErTy","plataforma":"android"}""", limpio)
    }

    @Test
    fun contrasenasYCodigos_seOcultanCompletos() {
        val cuerpo = """{"email":"a@b.co","password":"SuperSecreta2026!","codigo":"123456","nueva_password":"OtraClave99"}"""

        val limpio = enmascararSecretosHttp(cuerpo)

        assertEquals(
            """{"email":"a@b.co","password":"***","codigo":"***","nueva_password":"***"}""",
            limpio
        )
    }

    @Test
    fun textoSinSecretos_quedaIgual() {
        val linea = "--> GET https://backendvetericano-production.up.railway.app/api/notificaciones/?solo_no_leidas=true"

        assertEquals(linea, enmascararSecretosHttp(linea))
        assertEquals("", enmascararSecretosHttp(""))
    }
}
