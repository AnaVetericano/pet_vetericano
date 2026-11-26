package com.example.petvetericano

import com.example.petvetericano.models.AdopcionAnimalResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdopcionMappingTest {

    private val gson = Gson()

    @Test
    fun adopcionResponse_interpretaContratoRealDeDjangoConEstadoDisponibleYFiltraNoDisponibles() {
        val jsonBackend = """
            [
                {
                    "id": 12,
                    "nombre": "Luna",
                    "raza": "Criollo",
                    "descripcion": "Paciente dada de alta desde Historia Clínica CBA.",
                    "imagen": "https://res.cloudinary.com/demo/image/upload/luna.jpg",
                    "estado": "Disponible",
                    "fecha": "2026-11-25"
                },
                {
                    "id": 9,
                    "nombre": "Rocky",
                    "raza": "Pitbull",
                    "descripcion": "En hospitalización CBA",
                    "imagen": "",
                    "estado": "No Disponible",
                    "fecha": "2026-11-20"
                },
                {
                    "id": 18,
                    "nombre": "Max",
                    "raza": "Desconocida",
                    "descripcion": "",
                    "imagen": "/media/animales/max.jpg",
                    "estado": "Disponible",
                    "fecha": "2026-11-25"
                }
            ]
        """.trimIndent()

        val listType = object : TypeToken<List<AdopcionAnimalResponse>>() {}.type
        val remotos: List<AdopcionAnimalResponse> = gson.fromJson(jsonBackend, listType)
        assertEquals(3, remotos.size)
        assertTrue(remotos[0].esDisponible)
        assertFalse(remotos[1].esDisponible)
        assertTrue(remotos[2].esDisponible)

        val mapeados = Adopcion.mapearAnimalesDisponibles(
            remotos,
            apiBaseUrl = "https://backendvetericano-production.up.railway.app/api/"
        )

        assertEquals(2, mapeados.size)
        // Ordenados del más reciente (id mayor) al más antiguo
        assertEquals("#ADOP-0018", mapeados[0].idFicha)
        assertEquals("Max", mapeados[0].nombre)
        assertEquals("Mestizo", mapeados[0].raza)
        assertEquals(Adopcion.DESCRIPCION_DEFAULT_CBA, mapeados[0].descripcion)
        assertEquals(
            "https://backendvetericano-production.up.railway.app/media/animales/max.jpg",
            mapeados[0].urlImagen
        )

        assertEquals("#ADOP-0012", mapeados[1].idFicha)
        assertEquals("Luna", mapeados[1].nombre)
        assertEquals("Criollo", mapeados[1].raza)
        assertEquals("https://res.cloudinary.com/demo/image/upload/luna.jpg", mapeados[1].urlImagen)
    }

    @Test
    fun normalizarUrlImagenAnimal_soportaCloudinaryRutasRelativasDataUriYVacios() {
        val base = "https://backendvetericano-production.up.railway.app/api/"
        assertNull(Adopcion.normalizarUrlImagenAnimal(null, base))
        assertNull(Adopcion.normalizarUrlImagenAnimal("   ", base))
        assertNull(Adopcion.normalizarUrlImagenAnimal("null", base))
        assertNull(Adopcion.normalizarUrlImagenAnimal("undefined", base))

        assertEquals(
            "https://res.cloudinary.com/vet/foto.jpg",
            Adopcion.normalizarUrlImagenAnimal("https://res.cloudinary.com/vet/foto.jpg", base)
        )
        assertEquals(
            "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
            Adopcion.normalizarUrlImagenAnimal("data:image/jpeg;base64,/9j/4AAQSkZJRg==", base)
        )
        assertEquals(
            "https://backendvetericano-production.up.railway.app/media/Coco.jpeg",
            Adopcion.normalizarUrlImagenAnimal("/media/Coco.jpeg", base)
        )
        assertEquals(
            "https://backendvetericano-production.up.railway.app/media/Coco.jpeg",
            Adopcion.normalizarUrlImagenAnimal("Coco.jpeg", base)
        )
    }

    @Test
    fun adopcionResponse_mantieneRetrocompatibilidadConBooleanos() {
        val conBool = AdopcionAnimalResponse(id = 4, nombre = "Michi", disponible = true)
        assertTrue(conBool.esDisponible)

        val conDisponibleAdopcion = AdopcionAnimalResponse(
            id = 5,
            nombre = "Toby",
            disponibleAdopcion = true
        )
        assertTrue(conDisponibleAdopcion.esDisponible)
    }
}
