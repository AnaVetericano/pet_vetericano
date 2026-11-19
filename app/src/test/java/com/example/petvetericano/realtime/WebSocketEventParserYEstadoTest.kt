package com.example.petvetericano.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebSocketEventParserYEstadoTest {

    @Before
    fun setUp() {
        WebSocketRepository.limpiarDeduplicacion()
    }

    @Test
    fun `validarUrl exige wss en release y rechaza query string o placeholder`() {
        assertNull(WebSocketProtocolo.validarUrl("ws://10.0.2.2:8002/ws/notificaciones/", permitirCleartext = false))
        assertEquals(
            "ws://10.0.2.2:8002/ws/notificaciones/",
            WebSocketProtocolo.validarUrl("ws://10.0.2.2:8002/ws/notificaciones/", permitirCleartext = true)
        )
        assertEquals(
            "wss://ws.vetericano.app/ws/notificaciones/",
            WebSocketProtocolo.validarUrl("wss://ws.vetericano.app/ws/notificaciones/", permitirCleartext = false)
        )
        assertNull(WebSocketProtocolo.validarUrl("wss://ws.vetericano.app/ws/notificaciones/?token=abc", permitirCleartext = true))
        assertNull(WebSocketProtocolo.validarUrl("wss://REEMPLAZAR-DOMINIO-WS/ws/notificaciones/", permitirCleartext = false))
        assertNull(WebSocketProtocolo.validarUrl("", permitirCleartext = true))
    }

    @Test
    fun `frames auth y ping cumplen contrato v1`() {
        val auth = WebSocketEventParser.construirFrameAuth("jwt-123")
        assertTrue(auth.contains("\"type\":\"auth\""))
        assertTrue(auth.contains("\"token\":\"jwt-123\""))

        val ping = WebSocketEventParser.construirFramePing()
        assertEquals("{\"type\":\"ping\"}", ping)
    }

    @Test
    fun `parsea auth_ok, pong y eventos NP, AO y AS validos`() {
        val authOk = WebSocketEventParser.parsearMensaje(
            """{"type":"auth_ok","user_id":12,"id_rol":3,"token_exp":1763340000}"""
        ) as MensajeServidorWs.AuthOk
        assertEquals(12, authOk.userId)
        assertEquals(3, authOk.idRol)
        assertEquals(1763340000L, authOk.tokenExp)

        assertEquals(MensajeServidorWs.Pong, WebSocketEventParser.parsearMensaje("""{"type":"pong"}"""))

        val np = WebSocketEventParser.parsearMensaje(
            """{
                "type":"event","schema_version":"1","event_id":"ev-1","event_type":"peticion.asignada",
                "categoria":"NP","modulo":"peticiones","timestamp":"2026-11-19T10:00:00",
                "data":{"clave":"pet-893","titulo":"Petición asignada","mensaje":"RAD-2026-0215","referencia":{"id_peticion":215}}
            }"""
        ) as MensajeServidorWs.Evento
        assertEquals(CategoriaEventoWs.NP, np.evento.categoria)
        assertEquals("pet-893", np.evento.claveCanonica)
        assertEquals("Petición asignada", np.evento.titulo)
        assertEquals(215, np.evento.referencia?.get("id_peticion")?.asInt)

        val ao = WebSocketEventParser.parsearMensaje(
            """{
                "type":"event","schema_version":"1","event_id":"ev-2","event_type":"kit.alerta_stock_vencimiento",
                "categoria":"AO","modulo":"inventario","timestamp":"2026-11-19T10:00:00",
                "data":{"titulo":"Alerta","mensaje":"Stock bajo","nivel":"warning","referencia":{"id_veterinario":12}}
            }"""
        ) as MensajeServidorWs.Evento
        assertEquals(CategoriaEventoWs.AO, ao.evento.categoria)
        assertEquals("warning", ao.evento.nivel)

        val asEv = WebSocketEventParser.parsearMensaje(
            """{
                "type":"event","schema_version":"1","event_id":"ev-3","event_type":"kit.stock_actualizado",
                "categoria":"AS","modulo":"inventario","timestamp":"2026-11-19T10:00:00",
                "data":{"accion":"actualizado","referencia":{"id_veterinario":12}}
            }"""
        ) as MensajeServidorWs.Evento
        assertEquals(CategoriaEventoWs.AS, asEv.evento.categoria)
        assertEquals("actualizado", asEv.evento.accion)
    }

    @Test
    fun `rechaza schema_version incompatible y payloads invalidos sin excepcion`() {
        assertNull(WebSocketEventParser.parsearMensaje("no-es-json"))
        assertNull(WebSocketEventParser.parsearMensaje(""))
        assertNull(WebSocketEventParser.parsearMensaje("""{"type":"desconocido"}"""))
        assertNull(
            WebSocketEventParser.parsearMensaje(
                """{"type":"event","schema_version":"2","event_id":"e1","event_type":"peticion.asignada","categoria":"NP","data":{}}"""
            )
        )
        assertNull(
            WebSocketEventParser.parsearMensaje(
                """{"type":"event","schema_version":"1","event_id":"e1","event_type":"peticion.asignada","categoria":"DESCONOCIDA","data":{}}"""
            )
        )
    }

    @Test
    fun `deduplicacion compartida FCM y WebSocket evita doble notificacion local`() {
        val clavesMostradas = mutableSetOf("pet-893") // ya mostrada por FCM
        var mostradasPorWs = 0
        var avisosBandeja = 0

        val efectos = object : WebSocketRepository.EfectosNotificacion {
            override fun yaMostrada(clave: String): Boolean = clave in clavesMostradas
            override fun marcarMostrada(clave: String) { clavesMostradas.add(clave) }
            override fun mostrarNotificacionNp(evento: WebSocketEvent): Boolean { mostradasPorWs++; return true }
            override fun notificarBandeja() { avisosBandeja++ }
            override fun cancelarNotificacion(clave: String) {}
            override fun cancelarTodasNotificaciones() {}
        }

        val npYaEnFcm = (WebSocketEventParser.parsearMensaje(
            """{"type":"event","schema_version":"1","event_id":"ev-fcm-1","event_type":"peticion.asignada","categoria":"NP","modulo":"peticiones","timestamp":"2026-11-19T10:00:00","data":{"clave":"pet-893","titulo":"T","mensaje":"M"}}"""
        ) as MensajeServidorWs.Evento).evento

        assertTrue(WebSocketRepository.procesarEventoConEfectos(npYaEnFcm, efectos))
        assertEquals(0, mostradasPorWs) // no se duplica notificación local
        assertEquals(1, avisosBandeja)  // sí refresca bandeja

        // Mismo event_id repetido -> ignorado completamente
        assertFalse(WebSocketRepository.procesarEventoConEfectos(npYaEnFcm, efectos))
        assertEquals(1, avisosBandeja)

        // Nueva NP que no llegó por FCM -> se muestra 1 vez y se marca en clavesMostradas
        val npNueva = (WebSocketEventParser.parsearMensaje(
            """{"type":"event","schema_version":"1","event_id":"ev-ws-2","event_type":"kit.solicitud_resuelta","categoria":"NP","modulo":"inventario","timestamp":"2026-11-19T10:01:00","data":{"clave":"sis-12","titulo":"Aprobada","mensaje":"Ok"}}"""
        ) as MensajeServidorWs.Evento).evento
        assertTrue(WebSocketRepository.procesarEventoConEfectos(npNueva, efectos))
        assertEquals(1, mostradasPorWs)
        assertTrue("sis-12" in clavesMostradas)
    }

    @Test
    fun `eventos AO y AS no crean notificacion local y eventos no relevantes se ignoran`() {
        var mostradas = 0
        val efectos = object : WebSocketRepository.EfectosNotificacion {
            override fun yaMostrada(clave: String) = false
            override fun marcarMostrada(clave: String) {}
            override fun mostrarNotificacionNp(evento: WebSocketEvent): Boolean { mostradas++; return true }
            override fun notificarBandeja() {}
            override fun cancelarNotificacion(clave: String) {}
            override fun cancelarTodasNotificaciones() {}
        }

        val asRelevante = (WebSocketEventParser.parsearMensaje(
            """{"type":"event","schema_version":"1","event_id":"ev-as-1","event_type":"kit.stock_actualizado","categoria":"AS","modulo":"inventario","timestamp":"2026-11-19T10:00:00","data":{"accion":"actualizado"}}"""
        ) as MensajeServidorWs.Evento).evento
        assertTrue(WebSocketRepository.procesarEventoConEfectos(asRelevante, efectos))
        assertEquals(0, mostradas)

        val irrelevante = (WebSocketEventParser.parsearMensaje(
            """{"type":"event","schema_version":"1","event_id":"ev-irr-1","event_type":"admin.sede_actualizada","categoria":"AS","modulo":"sedes","timestamp":"2026-11-19T10:00:00","data":{"accion":"actualizado"}}"""
        ) as MensajeServidorWs.Evento).evento
        assertFalse(WebSocketRepository.procesarEventoConEfectos(irrelevante, efectos))
        assertEquals(0, mostradas)
    }

    @Test
    fun `clasificacion de cierres y backoff exponencial acotado`() {
        assertEquals(PoliticaCierre.NO_RECONECTAR_NORMAL, WebSocketProtocolo.clasificarCierre(1000, cierreIniciadoPorCliente = true))
        assertEquals(PoliticaCierre.RECONECTAR_BACKOFF, WebSocketProtocolo.clasificarCierre(1000, cierreIniciadoPorCliente = false))
        assertEquals(PoliticaCierre.REFRESCAR_Y_RECONECTAR, WebSocketProtocolo.clasificarCierre(4001))
        assertEquals(PoliticaCierre.REFRESCAR_Y_RECONECTAR, WebSocketProtocolo.clasificarCierre(4002))
        assertEquals(PoliticaCierre.REVOCAR_SESION, WebSocketProtocolo.clasificarCierre(4003))
        assertEquals(PoliticaCierre.ERROR_PERMANENTE, WebSocketProtocolo.clasificarCierre(4403))

        assertEquals(1000L, WebSocketProtocolo.calcularBackoffMs(1, factorJitter = 0.0))
        assertEquals(2000L, WebSocketProtocolo.calcularBackoffMs(2, factorJitter = 0.0))
        assertEquals(30_000L, WebSocketProtocolo.calcularBackoffMs(10, factorJitter = 0.0))
        assertNotNull(WebSocketProtocolo.calcularBackoffMs(3))
    }
}
