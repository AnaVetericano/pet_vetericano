package com.example.petvetericano.ui.reporte

import com.example.petvetericano.models.CandidatoDuplicado
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoRequest
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

class VerificadorDuplicadosTest {

    private val descripcionValida = "Perro herido cerca del parque principal"
    private val latValida = 4.60971
    private val lonValida = -74.08175

    @Test
    fun verificar_conCoincidencias_retornaConCoincidencias() = runTest {
        val candidato = CandidatoDuplicado(
            coincidencia_texto = true,
            coincidencia_gps = true,
            cercania = "muy_cerca",
            similitud = "alta"
        )
        val verificador = VerificadorDuplicados(
            llamada = {
                Response.success(
                    DetectarDuplicadosCiudadanoResponse(
                        advertencia_duplicado = true,
                        posible_duplicado = true,
                        total_candidatos = 1,
                        posibles_duplicados = listOf(candidato)
                    )
                )
            }
        )

        val resultado = verificador.verificar(descripcionValida, latValida, lonValida)

        assertTrue(resultado is ResultadoVerificacion.ConCoincidencias)
        assertEquals(listOf(candidato), (resultado as ResultadoVerificacion.ConCoincidencias).candidatos)
    }

    @Test
    fun verificar_sinCoincidencias_yListaNull_retornaSinCoincidencias() = runTest {
        val verificadorVacio = VerificadorDuplicados(
            llamada = {
                Response.success(
                    DetectarDuplicadosCiudadanoResponse(
                        advertencia_duplicado = false,
                        posible_duplicado = false,
                        total_candidatos = 0,
                        posibles_duplicados = emptyList()
                    )
                )
            }
        )
        assertEquals(
            ResultadoVerificacion.SinCoincidencias,
            verificadorVacio.verificar(descripcionValida, latValida, lonValida)
        )

        val verificadorListaNull = VerificadorDuplicados(
            llamada = {
                Response.success(
                    DetectarDuplicadosCiudadanoResponse(
                        advertencia_duplicado = true,
                        posible_duplicado = true,
                        total_candidatos = 2,
                        posibles_duplicados = null
                    )
                )
            }
        )
        assertEquals(
            ResultadoVerificacion.SinCoincidencias,
            verificadorListaNull.verificar(descripcionValida, latValida, lonValida)
        )
    }

    @Test
    fun verificar_recortaMaximoCincoCandidatos() = runTest {
        val siete = (1..7).map { i -> CandidatoDuplicado(cercania = "cerca", similitud = "media", foto = "https://x/$i.jpg") }
        val verificador = VerificadorDuplicados(
            llamada = {
                Response.success(
                    DetectarDuplicadosCiudadanoResponse(
                        advertencia_duplicado = true,
                        posible_duplicado = true,
                        total_candidatos = 7,
                        posibles_duplicados = siete
                    )
                )
            }
        )

        val resultado = verificador.verificar(descripcionValida, latValida, lonValida)
        assertTrue(resultado is ResultadoVerificacion.ConCoincidencias)
        assertEquals(5, (resultado as ResultadoVerificacion.ConCoincidencias).candidatos.size)
    }

    @Test
    fun verificar_codigosHttpError_retornaNoDisponibleConCodigo() = runTest {
        val codigos = listOf(400, 401, 403, 429, 500)
        for (codigo in codigos) {
            val verificador = VerificadorDuplicados(
                llamada = {
                    Response.error(
                        codigo,
                        "{\"error\":\"fallo\"}".toResponseBody("application/json".toMediaType())
                    )
                }
            )
            val resultado = verificador.verificar(descripcionValida, latValida, lonValida)
            assertEquals(ResultadoVerificacion.NoDisponible("http_$codigo"), resultado)
        }
    }

    @Test
    fun verificar_ioException_retornaNoDisponible() = runTest {
        val verificador = VerificadorDuplicados(
            llamada = { throw UnknownHostException("sin red") }
        )
        val resultado = verificador.verificar(descripcionValida, latValida, lonValida)
        assertTrue(resultado is ResultadoVerificacion.NoDisponible)

        val verificadorIo = VerificadorDuplicados(
            llamada = { throw IOException("modo avion") }
        )
        assertTrue(
            verificadorIo.verificar(descripcionValida, latValida, lonValida) is ResultadoVerificacion.NoDisponible
        )
    }

    @Test
    fun verificar_timeoutVirtual_retornaNoDisponibleTimeout() = runTest {
        val verificador = VerificadorDuplicados(
            llamada = {
                delay(10_000L)
                Response.success(DetectarDuplicadosCiudadanoResponse())
            },
            timeoutMs = 6_500L
        )

        val resultado = verificador.verificar(descripcionValida, latValida, lonValida)

        assertEquals(ResultadoVerificacion.NoDisponible("timeout"), resultado)
    }

    @Test
    fun verificar_descripcionCortaOCoordenadasNoFinitas_retornaNoDisponibleSinLlamarRed() = runTest {
        var llamadas = 0
        var requestRecibido: DetectarDuplicadosCiudadanoRequest? = null
        val verificador = VerificadorDuplicados(
            llamada = { req ->
                llamadas++
                requestRecibido = req
                Response.success(DetectarDuplicadosCiudadanoResponse())
            }
        )

        val resCorta = verificador.verificar("   corto   ", latValida, lonValida)
        assertEquals(ResultadoVerificacion.NoDisponible("entrada_no_valida"), resCorta)
        assertEquals(0, llamadas)

        val resNaN = verificador.verificar(descripcionValida, Double.NaN, lonValida)
        assertEquals(ResultadoVerificacion.NoDisponible("entrada_no_valida"), resNaN)
        assertEquals(0, llamadas)

        val descLarga = "  " + "a".repeat(1200) + "  "
        verificador.verificar(descLarga, latValida, lonValida)
        assertEquals(1, llamadas)
        assertEquals(1000, requestRecibido!!.descripcion.length)
    }

    @Test(expected = CancellationException::class)
    fun verificar_cancellationException_sePropaga() = runTest {
        val verificador = VerificadorDuplicados(
            llamada = { throw CancellationException("cancelado") }
        )
        verificador.verificar(descripcionValida, latValida, lonValida)
    }
}
