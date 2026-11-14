package com.example.petvetericano.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReporteEvidenciasAutorizacionTest {

    @Test
    fun parser_roundTrip_preservaAutorizaDuplicadosTrueYFalse() {
        val originales = listOf(
            EvidenciaReporteItem(
                path = "/data/user/0/app/files/foto_1.jpg",
                esVideo = false,
                urlRemota = "https://res.cloudinary.com/demo/image/upload/foto_1.jpg",
                autorizaDuplicados = true
            ),
            EvidenciaReporteItem(
                path = "/data/user/0/app/files/foto_2.jpg",
                esVideo = false,
                urlRemota = null,
                autorizaDuplicados = false
            ),
            EvidenciaReporteItem(
                path = "/data/user/0/app/files/video_1.mp4",
                esVideo = true,
                urlRemota = "https://res.cloudinary.com/demo/video/upload/video_1.mp4",
                autorizaDuplicados = false
            )
        )

        val json = ReporteEvidenciasParser.serializar(originales)
        val parseadas = ReporteEvidenciasParser.parsear(json)

        assertEquals(originales, parseadas)
        assertTrue(parseadas[0].autorizaDuplicados)
        assertFalse(parseadas[1].autorizaDuplicados)
        assertFalse(parseadas[2].autorizaDuplicados)
    }

    @Test
    fun parser_jsonLegadoSinCampoAutorizaDuplicados_retornaFalse() {
        val jsonLegado = """
            [
              {
                "path": "/tmp/foto_legada.jpg",
                "esVideo": false,
                "urlRemota": "https://res.cloudinary.com/demo/image/upload/legada.jpg"
              },
              {
                "path": "/tmp/video_legado.mp4",
                "esVideo": true,
                "urlRemota": null
              }
            ]
        """.trimIndent()

        val parseadas = ReporteEvidenciasParser.parsear(jsonLegado)

        assertEquals(2, parseadas.size)
        assertFalse(parseadas[0].autorizaDuplicados)
        assertFalse(parseadas[1].autorizaDuplicados)
        assertEquals("https://res.cloudinary.com/demo/image/upload/legada.jpg", parseadas[0].urlRemota)
    }

    @Test
    fun parser_jsonCorruptoONulo_noRompeYRetornaListaVacia() {
        assertTrue(ReporteEvidenciasParser.parsear(null).isEmpty())
        assertTrue(ReporteEvidenciasParser.parsear("").isEmpty())
        assertTrue(ReporteEvidenciasParser.parsear("{corrupto").isEmpty())
    }

    @Test
    fun fotosAutorizadasParaEnvio_filtraVideosHttpNoAutorizadosSinUrlDuplicadosYMaximo5() {
        val lista = listOf(
            // Válida #1
            EvidenciaReporteItem("/f1.jpg", esVideo = false, urlRemota = "https://cdn.example/1.jpg", autorizaDuplicados = true),
            // Duplicado de #1 -> debe ignorarse
            EvidenciaReporteItem("/f1_dup.jpg", esVideo = false, urlRemota = "https://cdn.example/1.jpg", autorizaDuplicados = true),
            // Video aunque tenga autorizaDuplicados=true -> debe ignorarse
            EvidenciaReporteItem("/v1.mp4", esVideo = true, urlRemota = "https://cdn.example/v1.mp4", autorizaDuplicados = true),
            // http:// inseguro -> debe ignorarse
            EvidenciaReporteItem("/f_http.jpg", esVideo = false, urlRemota = "http://cdn.example/insegura.jpg", autorizaDuplicados = true),
            // Sin urlRemota -> debe ignorarse
            EvidenciaReporteItem("/f_null.jpg", esVideo = false, urlRemota = null, autorizaDuplicados = true),
            EvidenciaReporteItem("/f_blank.jpg", esVideo = false, urlRemota = "   ", autorizaDuplicados = true),
            // No autorizada -> debe ignorarse
            EvidenciaReporteItem("/f_no_aut.jpg", esVideo = false, urlRemota = "https://cdn.example/no_aut.jpg", autorizaDuplicados = false),
            // Válidas #2..#6 (la #6 debe quedar fuera por límite máximo de 5)
            EvidenciaReporteItem("/f2.jpg", esVideo = false, urlRemota = "https://cdn.example/2.jpg", autorizaDuplicados = true),
            EvidenciaReporteItem("/f3.jpg", esVideo = false, urlRemota = "https://cdn.example/3.jpg", autorizaDuplicados = true),
            EvidenciaReporteItem("/f4.jpg", esVideo = false, urlRemota = "https://cdn.example/4.jpg", autorizaDuplicados = true),
            EvidenciaReporteItem("/f5.jpg", esVideo = false, urlRemota = "https://cdn.example/5.jpg", autorizaDuplicados = true),
            EvidenciaReporteItem("/f6.jpg", esVideo = false, urlRemota = "https://cdn.example/6.jpg", autorizaDuplicados = true)
        )

        val resultado = fotosAutorizadasParaEnvio(lista)

        assertEquals(
            listOf(
                "https://cdn.example/1.jpg",
                "https://cdn.example/2.jpg",
                "https://cdn.example/3.jpg",
                "https://cdn.example/4.jpg",
                "https://cdn.example/5.jpg"
            ),
            resultado
        )
    }

    @Test
    fun fotosAutorizadasParaEnvio_sinAutorizadasOListaVacia_retornaVacio() {
        assertTrue(fotosAutorizadasParaEnvio(emptyList()).isEmpty())

        val soloNoAutorizadas = listOf(
            EvidenciaReporteItem("/f1.jpg", esVideo = false, urlRemota = "https://cdn.example/1.jpg", autorizaDuplicados = false)
        )
        assertTrue(fotosAutorizadasParaEnvio(soloNoAutorizadas).isEmpty())
    }
}
