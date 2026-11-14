package com.example.petvetericano.network

import com.example.petvetericano.models.confirmarpeticion
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ConfirmarPeticionAutorizacionContratoTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun gson_conFotosAutorizadasNoNulo_incluyeCampoYUrls() {
        val gsonRetrofit = GsonBuilder().serializeNulls().create()
        val payload = confirmarpeticion(
            id_tipo = 1,
            id_estado = 1,
            responsable = 7,
            descripcion = "Perro herido",
            prioridad = null,
            direccion = "Calle 10",
            latitud = 4.6,
            longitud = -74.08,
            foto = "https://res.cloudinary.com/demo/image/upload/f1.jpg",
            fotos = listOf("https://res.cloudinary.com/demo/image/upload/f1.jpg"),
            fotos_autorizadas_duplicados = listOf("https://res.cloudinary.com/demo/image/upload/f1.jpg")
        )

        val jsonObj = JsonParser.parseString(gsonRetrofit.toJson(payload)).asJsonObject
        assertTrue(jsonObj.has("fotos_autorizadas_duplicados"))
        val array = jsonObj.getAsJsonArray("fotos_autorizadas_duplicados")
        assertEquals(1, array.size())
        assertEquals("https://res.cloudinary.com/demo/image/upload/f1.jpg", array[0].asString)
    }

    @Test
    fun gson_conFotosAutorizadasNulo_respetaContratoTantoConSerializeNullsComoSinEl() {
        val payload = confirmarpeticion(
            id_tipo = 1,
            id_estado = 1,
            responsable = 7,
            descripcion = "Perro herido",
            prioridad = null,
            direccion = "Calle 10",
            latitud = 4.6,
            longitud = -74.08,
            foto = null,
            fotos = emptyList(),
            fotos_autorizadas_duplicados = null
        )

        // RetrofitClient usa GsonBuilder().serializeNulls(): emite la clave con valor null (válido en el backend).
        val gsonRetrofit = GsonBuilder().serializeNulls().create()
        val objConSerializeNulls = JsonParser.parseString(gsonRetrofit.toJson(payload)).asJsonObject
        assertTrue(objConSerializeNulls.has("fotos_autorizadas_duplicados"))
        assertTrue(objConSerializeNulls.get("fotos_autorizadas_duplicados").isJsonNull)

        // Gson por defecto omite claves nulas (también válido en el backend: campo opcional).
        val objPorDefecto = JsonParser.parseString(Gson().toJson(payload)).asJsonObject
        assertFalse(objPorDefecto.has("fotos_autorizadas_duplicados"))
    }

    @Test
    fun enviarPeticion_postPeticionesIniciar_llevaFotosAutorizadasDuplicadosEnCuerpo() = runTest {
        val gsonRetrofit = GsonBuilder().serializeNulls().create()
        val apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gsonRetrofit))
            .build()
            .create(ApiService::class.java)

        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"mensaje":"ok","id_peticion":123,"numero_radicado":"PET-2026-00123"}"""
            )
        )

        val urls = listOf(
            "https://res.cloudinary.com/demo/image/upload/f1.jpg",
            "https://res.cloudinary.com/demo/image/upload/f2.jpg"
        )
        val request = confirmarpeticion(
            id_tipo = 2,
            id_estado = 1,
            responsable = 15,
            descripcion = "Gato en peligro",
            prioridad = null,
            direccion = "Carrera 7",
            latitud = 4.61,
            longitud = -74.07,
            foto = urls.first(),
            fotos = urls,
            fotos_autorizadas_duplicados = urls
        )

        val response = apiService.enviarPeticion(request)
        assertTrue(response.isSuccessful)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/peticiones/iniciar/", recorded.path)

        val bodyJson = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertTrue(bodyJson.has("fotos_autorizadas_duplicados"))
        val enviadas = bodyJson.getAsJsonArray("fotos_autorizadas_duplicados").map { it.asString }
        assertEquals(urls, enviadas)
    }
}
