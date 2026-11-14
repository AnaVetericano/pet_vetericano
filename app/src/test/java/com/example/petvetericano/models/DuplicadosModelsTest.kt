package com.example.petvetericano.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicadosModelsTest {

    private val gson = Gson()

    @Test
    fun detectarDuplicadosResponse_deserializaJsonEjemploYFotoNull() {
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
                  "cercania": "cerca",
                  "similitud": "alta",
                  "foto": null,
                  "tiene_fotografia": false
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, DetectarDuplicadosCiudadanoResponse::class.java)

        assertTrue(response.advertencia_duplicado)
        assertTrue(response.posible_duplicado)
        assertEquals(1, response.total_candidatos)
        assertEquals("Se encontraron reportes similares.", response.mensaje)
        val candidatos = response.posibles_duplicados!!
        assertEquals(1, candidatos.size)

        val candidato = candidatos.first()
        assertTrue(candidato.coincidencia_texto)
        assertTrue(candidato.coincidencia_gps)
        assertEquals(NivelCercania.CERCA, candidato.nivelCercania)
        assertEquals(NivelSimilitud.ALTA, candidato.nivelSimilitud)
        assertNull(candidato.foto)
        assertNull(candidato.fotoSegura)
        assertFalse(candidato.tiene_fotografia)
    }

    @Test
    fun candidatoDuplicado_mapeaValoresConocidosYDesconocidosANoDisponible() {
        val muyCercaMedia = CandidatoDuplicado(cercania = "muy_cerca", similitud = "media")
        assertEquals(NivelCercania.MUY_CERCA, muyCercaMedia.nivelCercania)
        assertEquals(NivelSimilitud.MEDIA, muyCercaMedia.nivelSimilitud)

        val desconocido = CandidatoDuplicado(cercania = "lejos", similitud = "baja")
        assertEquals(NivelCercania.NO_DISPONIBLE, desconocido.nivelCercania)
        assertEquals(NivelSimilitud.NO_DISPONIBLE, desconocido.nivelSimilitud)

        val nulos = CandidatoDuplicado(cercania = null, similitud = null)
        assertEquals(NivelCercania.NO_DISPONIBLE, nulos.nivelCercania)
        assertEquals(NivelSimilitud.NO_DISPONIBLE, nulos.nivelSimilitud)
    }

    @Test
    fun fotoSegura_aceptaSoloHttpsYRechazaHttpONull() {
        val httpsFoto = CandidatoDuplicado(foto = "https://res.cloudinary.com/demo/image/upload/perro.jpg")
        assertEquals("https://res.cloudinary.com/demo/image/upload/perro.jpg", httpsFoto.fotoSegura)

        val httpFoto = CandidatoDuplicado(foto = "http://res.cloudinary.com/demo/image/upload/perro.jpg")
        assertNull(httpFoto.fotoSegura)

        val nullFoto = CandidatoDuplicado(foto = null)
        assertNull(nullFoto.fotoSegura)

        val vaciaFoto = CandidatoDuplicado(foto = "   ")
        assertNull(vaciaFoto.fotoSegura)
    }
}
