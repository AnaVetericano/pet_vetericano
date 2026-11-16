package com.example.petvetericano.ui.reporte

import com.example.petvetericano.models.CandidatoDuplicadoCiudadano
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import com.example.petvetericano.models.confirmarpeticion
import com.example.petvetericano.offline.EvidenciaReporteItem
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicadoCiudadanoPolicyTest {

    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun deserializarRespuestaConFotoAutorizadaYDinov2_extraeAlertaCompleta() {
        val rawJson = """
            {
                "advertencia_duplicado": true,
                "posible_duplicado": true,
                "total_candidatos": 1,
                "mensaje": "Encontramos un reporte previo cerca de este punto.",
                "posibles_duplicados": [
                    {
                        "coincidencia_texto": false,
                        "coincidencia_gps": true,
                        "cercania": "muy_cerca",
                        "similitud": "no_disponible",
                        "foto": "https://res.cloudinary.com/demo/image/upload/w_400/sample.jpg",
                        "tiene_fotografia": true,
                        "coincidencia_imagen": true,
                        "porcentaje_similitud_visual": 68.4,
                        "similitud_visual": "alta",
                        "metodo_imagen": "dinov2",
                        "es_certeza_identidad": false,
                        "tipo_metrica_visual": "orientativa",
                        "advertencia_metrica_visual": "Métrica visual orientativa, no certeza de identidad."
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, DetectarDuplicadosCiudadanoResponse::class.java)
        val alerta = DuplicadoCiudadanoPolicy.extraerAlertaVisual(response)

        assertNotNull(alerta)
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/w_400/sample.jpg",
            alerta!!.fotoAutorizadaUrl
        )
        assertEquals(68.4, alerta.porcentajeSimilitudVisual!!, 0.001)
        assertEquals("68.4 %", DuplicadoCiudadanoPolicy.formatearPorcentajeVisual(alerta.porcentajeSimilitudVisual))
        assertEquals("muy_cerca", alerta.cercania)
        assertEquals("Métrica visual orientativa, no certeza de identidad.", alerta.advertenciaMetricaVisual)
        assertFalse(alerta.esCertezaIdentidad)
    }

    @Test
    fun deserializarRespuestaSinAutorizacionDeFoto_nuncaExponeUrl() {
        val rawJson = """
            {
                "advertencia_duplicado": true,
                "posible_duplicado": true,
                "total_candidatos": 1,
                "mensaje": "Posible coincidencia visual cercana.",
                "posibles_duplicados": [
                    {
                        "coincidencia_texto": false,
                        "coincidencia_gps": true,
                        "cercania": "cerca",
                        "similitud": "no_disponible",
                        "foto": null,
                        "tiene_fotografia": false,
                        "coincidencia_imagen": true,
                        "porcentaje_similitud_visual": 52.0,
                        "similitud_visual": "media"
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, DetectarDuplicadosCiudadanoResponse::class.java)
        val alerta = DuplicadoCiudadanoPolicy.extraerAlertaVisual(response)

        assertNotNull(alerta)
        assertNull(alerta!!.fotoAutorizadaUrl)
        assertEquals("52 %", DuplicadoCiudadanoPolicy.formatearPorcentajeVisual(alerta.porcentajeSimilitudVisual))
        assertEquals(DuplicadoCiudadanoPolicy.ADVERTENCIA_METRICA_DEFECTO, alerta.advertenciaMetricaVisual)
    }

    @Test
    fun guardiaPrivacidad_rechazaUrlSiTieneFotografiaEsFalsoOEsEsquemaInseguro() {
        val noAutorizado = CandidatoDuplicadoCiudadano(
            tiene_fotografia = false,
            foto = "https://res.cloudinary.com/demo/image/upload/privada.jpg"
        )
        assertNull(DuplicadoCiudadanoPolicy.obtenerUrlFotoAutorizada(noAutorizado))

        val esquemaInvalido = CandidatoDuplicadoCiudadano(
            tiene_fotografia = true,
            foto = "file:///storage/emulated/0/secreto.jpg"
        )
        assertNull(DuplicadoCiudadanoPolicy.obtenerUrlFotoAutorizada(esquemaInvalido))
    }

    @Test
    fun sinPosibleDuplicado_retornaNullParaContinuarEnvioNormal() {
        val response = DetectarDuplicadosCiudadanoResponse(
            advertencia_duplicado = false,
            posible_duplicado = false,
            total_candidatos = 0,
            posibles_duplicados = emptyList()
        )
        assertNull(DuplicadoCiudadanoPolicy.extraerAlertaVisual(response))
        assertNull(DuplicadoCiudadanoPolicy.extraerAlertaVisual(null))
    }

    @Test
    fun construirFotosAutorizadas_respetaConsentimientoYExcluyeVideos() {
        val evidencias = listOf(
            EvidenciaReporteItem(
                path = "/data/foto1.jpg",
                esVideo = false,
                urlRemota = "https://res.cloudinary.com/demo/image/upload/foto1.jpg"
            ),
            EvidenciaReporteItem(
                path = "/data/video1.mp4",
                esVideo = true,
                urlRemota = "https://res.cloudinary.com/demo/video/upload/video1.mp4"
            ),
            EvidenciaReporteItem(
                path = "/data/foto_local_sin_subir.jpg",
                esVideo = false,
                urlRemota = null
            ),
            EvidenciaReporteItem(
                path = "/data/foto1_dup.jpg",
                esVideo = false,
                urlRemota = "https://res.cloudinary.com/demo/image/upload/foto1.jpg"
            ),
            EvidenciaReporteItem(
                path = "/data/foto2.jpg",
                esVideo = false,
                urlRemota = "https://res.cloudinary.com/demo/image/upload/foto2.jpg"
            )
        )

        // Sin consentimiento explicito: null siempre (fail-closed)
        assertNull(DuplicadoCiudadanoPolicy.construirFotosAutorizadasParaEnvio(false, evidencias))

        // Con consentimiento explicito: solo URLs remotas HTTP(S) unicas de imagenes
        val autorizadas = DuplicadoCiudadanoPolicy.construirFotosAutorizadasParaEnvio(true, evidencias)
        assertEquals(
            listOf(
                "https://res.cloudinary.com/demo/image/upload/foto1.jpg",
                "https://res.cloudinary.com/demo/image/upload/foto2.jpg"
            ),
            autorizadas
        )
    }

    @Test
    fun confirmarPeticion_serializaFotosAutorizadasDuplicadosCorrectamente() {
        val conAutorizacion = confirmarpeticion(
            id_tipo = 1,
            id_estado = 1,
            responsable = 7,
            descripcion = "Perro herido cerca del parque central",
            prioridad = null,
            direccion = "Calle 10 # 5-20",
            latitud = 4.651234,
            longitud = -74.062345,
            foto = "https://res.cloudinary.com/demo/image/upload/foto1.jpg",
            fotos = listOf("https://res.cloudinary.com/demo/image/upload/foto1.jpg"),
            fotos_autorizadas_duplicados = listOf("https://res.cloudinary.com/demo/image/upload/foto1.jpg")
        )
        val jsonCon = gson.toJson(conAutorizacion)
        assertTrue(jsonCon.contains("\"fotos_autorizadas_duplicados\":[\"https://res.cloudinary.com/demo/image/upload/foto1.jpg\"]"))

        val sinAutorizacion = conAutorizacion.copy(fotos_autorizadas_duplicados = null)
        val jsonSin = gson.toJson(sinAutorizacion)
        assertTrue(jsonSin.contains("\"fotos_autorizadas_duplicados\":null"))
    }

    @Test
    fun fallbackDinov2NoDisponiblePorProximidadGps_mantieneAlertaSinPorcentajeVisual() {
        val rawJson = """
            {
                "advertencia_duplicado": true,
                "posible_duplicado": true,
                "total_candidatos": 1,
                "mensaje": "Encontramos un reporte previo muy cerca de esta ubicación.",
                "posibles_duplicados": [
                    {
                        "coincidencia_texto": false,
                        "coincidencia_gps": true,
                        "cercania": "muy_cerca",
                        "similitud": "no_disponible",
                        "foto": null,
                        "tiene_fotografia": false,
                        "coincidencia_imagen": false,
                        "porcentaje_similitud_visual": null,
                        "similitud_visual": "no_disponible",
                        "metodo_imagen": "dinov2_no_disponible",
                        "es_certeza_identidad": false,
                        "advertencia_metrica_visual": "Similitud visual no disponible; advertencia generada por proximidad GPS (<= 200 m)."
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, DetectarDuplicadosCiudadanoResponse::class.java)
        val alerta = DuplicadoCiudadanoPolicy.extraerAlertaVisual(response)

        assertNotNull(alerta)
        assertNull(alerta!!.fotoAutorizadaUrl)
        assertNull(alerta.porcentajeSimilitudVisual)
        assertNull(DuplicadoCiudadanoPolicy.formatearPorcentajeVisual(alerta.porcentajeSimilitudVisual))
        assertEquals("no_disponible", alerta.similitudVisualLabel)
        assertEquals("muy_cerca", alerta.cercania)
        assertTrue(alerta.advertenciaMetricaVisual!!.contains("no disponible", ignoreCase = true))
    }
}
