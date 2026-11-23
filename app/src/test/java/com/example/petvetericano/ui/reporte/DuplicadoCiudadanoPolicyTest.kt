package com.example.petvetericano.ui.reporte

import com.example.petvetericano.models.CandidatoDuplicadoCiudadano
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoRequest
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import com.example.petvetericano.models.confirmarpeticion
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
    fun detectarDuplicadosCiudadanoRequest_serializaTextoGpsEFotosCorrectamente() {
        val request = DetectarDuplicadosCiudadanoRequest(
            descripcion = "Perro herido cerca del parque central",
            latitud = 4.651234,
            longitud = -74.062345,
            foto = "https://res.cloudinary.com/demo/image/upload/foto1.jpg",
            fotos = listOf(
                "https://res.cloudinary.com/demo/image/upload/foto1.jpg",
                "https://res.cloudinary.com/demo/image/upload/foto2.jpg"
            )
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"descripcion\":\"Perro herido cerca del parque central\""))
        assertTrue(json.contains("\"latitud\":4.651234"))
        assertTrue(json.contains("\"longitud\":-74.062345"))
        assertTrue(json.contains("\"foto\":\"https://res.cloudinary.com/demo/image/upload/foto1.jpg\""))
        assertTrue(json.contains("\"fotos\":[\"https://res.cloudinary.com/demo/image/upload/foto1.jpg\",\"https://res.cloudinary.com/demo/image/upload/foto2.jpg\"]"))
    }

    @Test
    fun confirmarPeticion_serializaSinCampoFotosAutorizadasDuplicados() {
        val peticion = confirmarpeticion(
            id_tipo = 1,
            id_estado = 1,
            responsable = 7,
            descripcion = "Perro herido cerca del parque central",
            prioridad = null,
            direccion = "Calle 10 # 5-20",
            latitud = 4.651234,
            longitud = -74.062345,
            foto = "https://res.cloudinary.com/demo/image/upload/foto1.jpg",
            fotos = listOf("https://res.cloudinary.com/demo/image/upload/foto1.jpg")
        )
        val json = gson.toJson(peticion)
        assertFalse(json.contains("fotos_autorizadas_duplicados"))
        assertTrue(json.contains("\"foto\":\"https://res.cloudinary.com/demo/image/upload/foto1.jpg\""))
        assertTrue(json.contains("\"fotos\":[\"https://res.cloudinary.com/demo/image/upload/foto1.jpg\"]"))
    }

    @Test
    fun deserializarRespuestaClasicaConFotoPermitida_exponeUrlAutomaticamente() {
        val rawJson = """
            {
                "advertencia_duplicado": true,
                "posible_duplicado": true,
                "total_candidatos": 1,
                "mensaje": "Se encontraron reportes similares.",
                "posibles_duplicados": [
                    {
                        "coincidencia_texto": true,
                        "coincidencia_gps": true,
                        "cercania": "muy_cerca",
                        "similitud": "alta",
                        "foto": "https://res.cloudinary.com/demo/image/upload/e_blur_faces/c_fill,g_auto,w_480,h_360,q_auto,f_auto/v1/previo.jpg",
                        "tiene_fotografia": true
                    }
                ]
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, DetectarDuplicadosCiudadanoResponse::class.java)
        val alerta = DuplicadoCiudadanoPolicy.extraerAlertaVisual(response)

        assertNotNull(alerta)
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/e_blur_faces/c_fill,g_auto,w_480,h_360,q_auto,f_auto/v1/previo.jpg",
            alerta!!.fotoAutorizadaUrl
        )
        assertEquals("muy_cerca", alerta.cercania)
        assertEquals("alta", alerta.similitudTexto)
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
