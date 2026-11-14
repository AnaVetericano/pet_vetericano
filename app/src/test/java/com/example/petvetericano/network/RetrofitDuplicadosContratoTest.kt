package com.example.petvetericano.network

import com.example.petvetericano.models.DetectarDuplicadosCiudadanoRequest
import com.example.petvetericano.models.NivelCercania
import com.example.petvetericano.models.NivelSimilitud
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitDuplicadosContratoTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        apiService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun detectarDuplicadosCiudadano_enviaPostConRutaYJsonEsperado_yParsea200() = runTest {
        val responseJson = """
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
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val request = DetectarDuplicadosCiudadanoRequest(
            descripcion = "Perro herido en la avenida central",
            latitud = 4.60971,
            longitud = -74.08175
        )
        val response = apiService.detectarDuplicadosCiudadano(request)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/peticiones/detectar-duplicados-ciudadano/", recorded.path)

        val bodyEnviado = Gson().fromJson(recorded.body.readUtf8(), DetectarDuplicadosCiudadanoRequest::class.java)
        assertEquals(request, bodyEnviado)

        assertTrue(response.isSuccessful)
        val body = response.body()!!
        assertTrue(body.advertencia_duplicado)
        assertEquals(1, body.posibles_duplicados?.size)
        val candidato = body.posibles_duplicados!!.first()
        assertEquals(NivelCercania.CERCA, candidato.nivelCercania)
        assertEquals(NivelSimilitud.ALTA, candidato.nivelSimilitud)
        assertNull(candidato.fotoSegura)
    }

    @Test
    fun detectarDuplicadosCiudadano_403y429_retornanIsSuccessfulFalse() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"detail\":\"Forbidden\"}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"detail\":\"Too many requests\"}"))

        val req = DetectarDuplicadosCiudadanoRequest("Descripcion valida de prueba", 4.6, -74.0)

        val res403 = apiService.detectarDuplicadosCiudadano(req)
        assertFalse(res403.isSuccessful)
        assertEquals(403, res403.code())

        val res429 = apiService.detectarDuplicadosCiudadano(req)
        assertFalse(res429.isSuccessful)
        assertEquals(429, res429.code())
    }
}
