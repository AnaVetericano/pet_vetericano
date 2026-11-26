package com.example.petvetericano.realtime

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketConnectionManagerTest {

    private class FakeWsClient : VetericanoWebSocketClient() {
        var conexionesIniciadas = 0
        var ultimoToken: String? = null
        var ultimoClientId: String? = null
        var ultimoCierreCodigo: Int? = null
        var ultimoCierreMotivo: String? = null
        var activo = false
        var listenerActual: Listener? = null

        override fun conectar(
            wsUrl: String,
            tokenJwt: String,
            clientId: String,
            permitirCleartext: Boolean,
            listener: Listener
        ): Boolean {
            if (WebSocketProtocolo.validarUrl(wsUrl, permitirCleartext) == null) return false
            if (tokenJwt.isBlank()) return false
            conexionesIniciadas++
            ultimoToken = tokenJwt
            ultimoClientId = clientId
            activo = true
            listenerActual = listener
            return true
        }

        override fun cerrar(codigo: Int, motivo: String) {
            ultimoCierreCodigo = codigo
            ultimoCierreMotivo = motivo
            activo = false
        }

        override fun tieneSocketActivo(): Boolean = activo
    }

    private class FakeEntorno(
        override var habilitado: Boolean = true,
        override var wsUrl: String = "ws://10.0.2.2:8002/ws/notificaciones/",
        override var clientId: String = "android-petvetericano",
        override var permitirCleartext: Boolean = true,
        var sesionActiva: Boolean = true,
        var userId: Int = 12,
        var accessToken: String = "jwt-inicial",
        var refreshToken: String = "ref-inicial",
        var redDisponible: Boolean = true,
        var tokenRefrescado: String? = "jwt-renovado"
    ) : WebSocketConnectionManager.EntornoWs {
        var refreshLlamados = 0
        var revocaciones = 0
        var recuperacionesRest = 0
        val eventosRecibidos = mutableListOf<WebSocketEvent>()

        override fun haySesion(): Boolean = sesionActiva
        override fun obtenerUserId(): Int = userId
        override fun obtenerAccessToken(): String = accessToken
        override fun obtenerRefreshToken(): String = refreshToken
        override fun hayRedDisponible(): Boolean = redDisponible
        override fun refrescarAccessToken(): String? {
            refreshLlamados++
            val nuevo = tokenRefrescado
            if (!nuevo.isNullOrBlank()) accessToken = nuevo
            return nuevo
        }
        override fun marcarSesionRevocada(refreshToken: String) { revocaciones++ }
        override fun recuperarNotificacionesRest() { recuperacionesRest++ }
        override fun procesarEvento(evento: WebSocketEvent): Boolean {
            eventosRecibidos.add(evento)
            return true
        }
        override fun logDebug(mensaje: String) {}
    }

    @Test
    fun `login y multiples foreground no duplican socket y background cierra con 1000`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onLoginExitoso()
        assertEquals(1, fakeClient.conexionesIniciadas)
        assertEquals("android-petvetericano", fakeClient.ultimoClientId)

        // Simula handshake + auth_ok
        fakeClient.listenerActual?.alAbrir()
        assertTrue(WebSocketRepository.estado.value is WebSocketState.Autenticando)
        fakeClient.listenerActual?.alRecibirMensaje(MensajeServidorWs.AuthOk(12, 3, 1900000000L))
        assertTrue(WebSocketRepository.estado.value is WebSocketState.Autenticado)

        // Llamadas repetidas a onForeground (cambio entre Activities) no abren un segundo socket
        WebSocketConnectionManager.onForeground()
        WebSocketConnectionManager.onForeground()
        assertEquals(1, fakeClient.conexionesIniciadas)

        // Al ir a segundo plano cierra limpiamente con 1000
        WebSocketConnectionManager.onBackground()
        assertEquals(1000, fakeClient.ultimoCierreCodigo)
        assertEquals("background", fakeClient.ultimoCierreMotivo)
        assertEquals(WebSocketState.Desconectado, WebSocketRepository.estado.value)

        // Al volver a primer plano reconecta y recupera REST tras auth_ok
        WebSocketConnectionManager.onForeground()
        assertEquals(2, fakeClient.conexionesIniciadas)
        fakeClient.listenerActual?.alAbrir()
        fakeClient.listenerActual?.alRecibirMensaje(MensajeServidorWs.AuthOk(12, 3, 1900000000L))
        assertEquals(1, env.recuperacionesRest)
    }

    @Test
    fun `flag apagado o token ausente no abre conexion`() {
        val envOff = FakeEntorno(habilitado = false)
        val fakeOff = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(envOff, fakeOff, Dispatchers.Unconfined)
        WebSocketConnectionManager.onForeground()
        assertEquals(0, fakeOff.conexionesIniciadas)

        val envSinToken = FakeEntorno(accessToken = "")
        val fakeSinToken = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(envSinToken, fakeSinToken, Dispatchers.Unconfined)
        WebSocketConnectionManager.onForeground()
        assertEquals(0, fakeSinToken.conexionesIniciadas)
    }

    @Test
    fun `cierre 4002 refresca JWT una vez y evita bucle si vuelve a fallar`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        assertEquals(1, fakeClient.conexionesIniciadas)
        assertEquals("jwt-inicial", fakeClient.ultimoToken)

        // Primer 4002 -> refresca JWT y reconecta con jwt-renovado
        fakeClient.activo = false
        fakeClient.listenerActual?.alCerrar(4002, "expired")
        assertEquals(1, env.refreshLlamados)
        assertEquals(2, fakeClient.conexionesIniciadas)
        assertEquals("jwt-renovado", fakeClient.ultimoToken)

        // Segundo 4002 consecutivo sin autenticar -> ErrorPermanente, no bucle infinito
        fakeClient.activo = false
        fakeClient.listenerActual?.alCerrar(4002, "expired_again")
        assertEquals(1, env.refreshLlamados)
        assertTrue(WebSocketRepository.estado.value is WebSocketState.ErrorPermanente)
    }

    @Test
    fun `cierre 4003 o evento sesion revocada fuerza revocacion y no reconecta`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        fakeClient.activo = false
        fakeClient.listenerActual?.alCerrar(4003, "revoked")
        assertEquals(1, env.revocaciones)
        assertTrue(WebSocketRepository.estado.value is WebSocketState.ErrorPermanente)
        assertEquals(1, fakeClient.conexionesIniciadas)
    }

    @Test
    fun `tras 4003 volver a primer plano no reconecta hasta un nuevo login`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        fakeClient.activo = false
        fakeClient.listenerActual?.alCerrar(4003, "revoked")
        WebSocketConnectionManager.onBackground()
        WebSocketConnectionManager.onForeground()
        assertEquals(1, fakeClient.conexionesIniciadas)

        WebSocketConnectionManager.onLoginExitoso()
        assertEquals(2, fakeClient.conexionesIniciadas)
    }

    @Test
    fun `evento sesion revocada tampoco permite reconectar al volver a primer plano`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        fakeClient.listenerActual?.alAbrir()
        fakeClient.listenerActual?.alRecibirMensaje(MensajeServidorWs.AuthOk(12, 3, 1900000000L))
        val revocada = (WebSocketEventParser.parsearMensaje(
            """{"type":"event","schema_version":"1","event_id":"ev-rev-1","event_type":"sesion.revocada","categoria":"AO","modulo":"seguridad","timestamp":"2026-11-26T10:00:00","data":{"titulo":"T","mensaje":"M","nivel":"danger","referencia":{},"motivo":"inactivado"}}"""
        ) as MensajeServidorWs.Evento)
        fakeClient.listenerActual?.alRecibirMensaje(revocada)
        assertEquals(1, env.revocaciones)

        WebSocketConnectionManager.onBackground()
        WebSocketConnectionManager.onForeground()
        assertEquals(1, fakeClient.conexionesIniciadas)
    }

    @Test
    fun `cierre 4403 por origen o cliente invalido pasa a ErrorPermanente sin reconectar`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        fakeClient.activo = false
        fakeClient.listenerActual?.alCerrar(4403, "forbidden")
        assertTrue(WebSocketRepository.estado.value is WebSocketState.ErrorPermanente)
        assertEquals(1, fakeClient.conexionesIniciadas)
    }

    @Test
    fun `desconexion de red programa reconexion y logout cierra y cancela`() {
        val env = FakeEntorno()
        val fakeClient = FakeWsClient()
        WebSocketConnectionManager.configurarParaPruebas(env, fakeClient, Dispatchers.Unconfined)

        WebSocketConnectionManager.onForeground()
        fakeClient.activo = false
        fakeClient.listenerActual?.alFallar(null, RuntimeException("network down"))
        assertTrue(WebSocketRepository.estado.value is WebSocketState.Reconectando)

        WebSocketConnectionManager.onLogout()
        assertEquals(1000, fakeClient.ultimoCierreCodigo)
        assertEquals("logout", fakeClient.ultimoCierreMotivo)
        assertEquals(WebSocketState.Desconectado, WebSocketRepository.estado.value)
        assertFalse(fakeClient.tieneSocketActivo())
    }
}
