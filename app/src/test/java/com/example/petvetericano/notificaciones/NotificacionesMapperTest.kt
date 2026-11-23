package com.example.petvetericano.notificaciones

import com.example.petvetericano.models.NotificacionItem
import com.example.petvetericano.models.NotificacionesListaResponse
import com.example.petvetericano.models.NotificacionesMapper
import com.example.petvetericano.models.RegistrarDispositivoRequest
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de notificaciones v1: §5 (ítem REST), §6 (listado) y §7 (data FCM). */
class NotificacionesMapperTest {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    // ------------------------------------------------------------------ data FCM → modelo (§7)

    @Test
    fun dataFcmDelContrato_seMapeaConReferenciaComoObjeto() {
        val data = mapOf(
            "event_type" to "kit.solicitud_resuelta",
            "clave" to "sis-12",
            "modulo" to "inventario",
            "schema_version" to "1",
            "referencia" to "{\"id_solicitud\":5}"
        )

        val push = NotificacionesMapper.desdeDataFcm(data, "Solicitud aprobada", "Tu solicitud #5 fue aprobada")

        assertNotNull(push)
        push!!
        assertEquals("sis-12", push.clave)
        assertEquals("Solicitud aprobada", push.titulo)
        assertEquals("Tu solicitud #5 fue aprobada", push.mensaje)
        assertEquals("kit.solicitud_resuelta", push.eventType)
        assertEquals("inventario", push.modulo)
        assertEquals("1", push.schemaVersion)
        assertEquals(5, push.referencia!!.get("id_solicitud").asInt)
    }

    @Test
    fun dataFcmSinBloqueNotification_usaTituloYMensajeDeData() {
        val data = mapOf("clave" to "pet-893", "titulo" to "  Petición asignada  ", "mensaje" to "RAD-2026-0215")

        val push = NotificacionesMapper.desdeDataFcm(data, null, "   ")!!

        assertEquals("pet-893", push.clave)
        assertEquals("Petición asignada", push.titulo)
        assertEquals("RAD-2026-0215", push.mensaje)
        assertNull(push.referencia)
    }

    @Test
    fun dataFcmConReferenciaInvalida_noRompeYDejaReferenciaNula() {
        val invalida = NotificacionesMapper.desdeDataFcm(mapOf("clave" to "sis-1", "referencia" to "{no-json"), "T", "M")!!
        val noObjeto = NotificacionesMapper.desdeDataFcm(mapOf("clave" to "sis-2", "referencia" to "[1,2]"), "T", "M")!!

        assertNull(invalida.referencia)
        assertNull(noObjeto.referencia)
    }

    @Test
    fun dataFcmSinClaveNiTextos_devuelveNull_yConSoloTextoUsaTituloPorDefecto() {
        assertNull(NotificacionesMapper.desdeDataFcm(emptyMap(), null, null))
        assertNull(NotificacionesMapper.desdeDataFcm(mapOf("clave" to "  "), " ", ""))

        val soloCuerpo = NotificacionesMapper.desdeDataFcm(emptyMap(), null, "Mensaje")!!
        assertNull(soloCuerpo.clave)
        assertEquals(NotificacionesMapper.TITULO_POR_DEFECTO, soloCuerpo.titulo)
        assertEquals("Mensaje", soloCuerpo.mensaje)
    }

    // ------------------------------------------------------------------ parseo del ítem REST (§5/§6)

    @Test
    fun itemRestDelContrato_seParseaCompleto() {
        val json = """
            {"clave":"pet-893","origen":"peticiones","id_notificacion":893,
             "event_type":"peticion.asignada","modulo":"peticiones","tipo":"ASIGNACION",
             "titulo":"Petición asignada: RAD-2026-0215","mensaje":"...","prioridad":"Urgente",
             "leida":false,"fecha_creacion":"2026-11-17T10:00:00","fecha_lectura":null,
             "referencia":{"id_peticion":215,"numero_radicado":"RAD-2026-0215"}}
        """.trimIndent()

        val item = gson.fromJson(json, NotificacionItem::class.java)

        assertEquals("pet-893", item.clave)
        assertEquals("peticiones", item.origen)
        assertEquals(893L, item.idNotificacion)
        assertEquals("peticion.asignada", item.eventType)
        assertEquals("peticiones", item.modulo)
        assertEquals("ASIGNACION", item.tipo)
        assertEquals("Petición asignada: RAD-2026-0215", item.titulo)
        assertEquals("Urgente", item.prioridad)
        assertFalse(item.leida)
        assertEquals("2026-11-17T10:00:00", item.fechaCreacion)
        assertNull(item.fechaLectura)
        assertEquals(215, item.referenciaObjeto!!.get("id_peticion").asInt)
        assertEquals("RAD-2026-0215", item.referenciaObjeto!!.get("numero_radicado").asString)
        assertTrue(NotificacionesMapper.esUrgente(item))

        val push = NotificacionesMapper.desdeItem(item)
        assertEquals("pet-893", push.clave)
        assertEquals("Petición asignada: RAD-2026-0215", push.titulo)
        assertEquals(215, push.referencia!!.get("id_peticion").asInt)
    }

    @Test
    fun itemRestConReferenciaNulaOCamposAusentes_noRompe() {
        val item = gson.fromJson(
            """{"clave":"sis-7","leida":true,"referencia":null}""",
            NotificacionItem::class.java
        )

        assertEquals("sis-7", item.clave)
        assertTrue(item.leida)
        assertNull(item.referenciaObjeto)
        assertNull(item.titulo)
        assertEquals(NotificacionesMapper.TITULO_POR_DEFECTO, NotificacionesMapper.desdeItem(item).titulo)
        assertFalse(NotificacionesMapper.esUrgente(item))
    }

    @Test
    fun listadoRest_seParseaConTotalesYResultados() {
        val json = """
            {"total":2,"no_leidas":1,"resultados":[
              {"clave":"pet-2","leida":false,"titulo":"A","referencia":{"id_peticion":2}},
              {"clave":"sis-3","leida":true,"titulo":"B","referencia":{}}
            ]}
        """.trimIndent()

        val lista = gson.fromJson(json, NotificacionesListaResponse::class.java)

        assertEquals(2, lista.total)
        assertEquals(1, lista.noLeidas)
        assertEquals(listOf("pet-2", "sis-3"), lista.resultados!!.map { it.clave })
        assertEquals(2, lista.resultados!![0].referenciaObjeto!!.get("id_peticion").asInt)
    }

    @Test
    fun solicitudRegistroDispositivo_serializaContratoYNoExponeTokenEnToString() {
        val token = "fcm-token-de-prueba-ABCDEF123456"
        val request = RegistrarDispositivoRequest(token)

        val json = gson.toJson(request)

        assertEquals("""{"token":"$token","plataforma":"android"}""", json)
        assertFalse(request.toString().contains(token))
        assertTrue(request.toString().contains("123456"))
    }

    // ------------------------------------------------------------------ claves ya mostradas

    @Test
    fun filtrarPendientes_excluyeLeidasYaMostradasSinClaveYDuplicadas() {
        val items = listOf(
            NotificacionItem(clave = "pet-1", leida = false),
            NotificacionItem(clave = "pet-2", leida = true),
            NotificacionItem(clave = "sis-3", leida = false),
            NotificacionItem(clave = null, leida = false),
            NotificacionItem(clave = "  ", leida = false),
            NotificacionItem(clave = "pet-1", leida = false),
            NotificacionItem(clave = "sis-4", leida = false)
        )

        val pendientes = NotificacionesMapper.filtrarPendientesDeMostrar(items, setOf("sis-3"))

        assertEquals(listOf("pet-1", "sis-4"), pendientes.map { it.clave })
    }

    @Test
    fun conjuntoAcotado_mantieneOrdenSinDuplicadosYDescartaLasMasAntiguas() {
        val base = listOf("a", "b", "c")

        val conNuevas = NotificacionesMapper.agregarAcotado(base, listOf("d", "b", " ", "e"), max = 4)

        // "b" se vuelve a poner al final; "a" (la más antigua) sale por el límite.
        assertEquals(listOf("c", "d", "b", "e"), conNuevas)
        assertEquals(300, NotificacionesMapper.agregarAcotado(emptyList(), (1..450).map { "pet-$it" }, 300).size)
        assertEquals("pet-450", NotificacionesMapper.agregarAcotado(emptyList(), (1..450).map { "pet-$it" }, 300).last())
    }

    @Test
    fun serializacionDeClaves_idaYVuelta() {
        val claves = listOf("pet-1", "sis-22")

        val texto = NotificacionesMapper.serializarClaves(claves)

        assertEquals(claves, NotificacionesMapper.deserializarClaves(texto))
        assertTrue(NotificacionesMapper.deserializarClaves(null).isEmpty())
        assertTrue(NotificacionesMapper.deserializarClaves("").isEmpty())
    }

    // ------------------------------------------------------------------ presentación

    @Test
    fun formatearFecha_isoSinZona_conservaHoraLocal() {
        val texto = NotificacionesMapper.formatearFecha("2026-11-17T10:05:00")
        val conMicros = NotificacionesMapper.formatearFecha("2026-11-17T10:05:00.123456")

        assertTrue(texto, texto.startsWith("17 "))
        assertTrue(texto, texto.contains("2026"))
        assertTrue(texto, texto.endsWith("10:05"))
        assertEquals(texto, conMicros)
        assertEquals("", NotificacionesMapper.formatearFecha(null))
        assertEquals("no-es-fecha-valida", NotificacionesMapper.formatearFecha("no-es-fecha-valida"))
    }

    @Test
    fun etiquetaModulo_capitaliza() {
        assertEquals("Peticiones", NotificacionesMapper.etiquetaModulo("peticiones"))
        assertEquals("", NotificacionesMapper.etiquetaModulo(null))
    }

    @Test
    fun huellaRegistro_esEstableYNoContieneElToken() {
        val token = "token-fcm-ejemplo-XYZ987"

        val h1 = FcmTokenManager.huellaRegistro(token, 12)
        val h2 = FcmTokenManager.huellaRegistro(token, 12)
        val otroUsuario = FcmTokenManager.huellaRegistro(token, 13)

        assertEquals(h1, h2)
        assertFalse(h1 == otroUsuario)
        assertEquals(64, h1.length)
        assertFalse(h1.contains(token))
        assertEquals("XYZ987", FcmTokenManager.sufijo(token))
    }
}
