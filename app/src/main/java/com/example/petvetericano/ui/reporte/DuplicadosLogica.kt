package com.example.petvetericano.ui.reporte

import com.example.petvetericano.models.CandidatoDuplicado
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoRequest
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

private const val MIN_DESCRIPCION_DUPLICADOS = 10
private const val MAX_DESCRIPCION_DUPLICADOS = 1000
private const val MAX_CANDIDATOS_DUPLICADOS = 5

sealed interface ResultadoVerificacion {
    data object SinCoincidencias : ResultadoVerificacion
    data class ConCoincidencias(val candidatos: List<CandidatoDuplicado>) : ResultadoVerificacion
    data class NoDisponible(val motivo: String) : ResultadoVerificacion
}

sealed interface EventoDuplicados {
    data object ContinuarConfirmacion : EventoDuplicados
    data object MostrarAdvertencia : EventoDuplicados
    data object MantenerBorrador : EventoDuplicados
}

class VerificadorDuplicados(
    private val llamada: suspend (DetectarDuplicadosCiudadanoRequest) -> Response<DetectarDuplicadosCiudadanoResponse>,
    private val timeoutMs: Long = 6_500L
) {
    suspend fun verificar(
        descripcion: String,
        latitud: Double,
        longitud: Double
    ): ResultadoVerificacion {
        val descripcionRecortada = descripcion.trim().take(MAX_DESCRIPCION_DUPLICADOS)
        if (descripcionRecortada.length < MIN_DESCRIPCION_DUPLICADOS ||
            !latitud.isFinite() ||
            !longitud.isFinite()
        ) {
            return ResultadoVerificacion.NoDisponible("entrada_no_valida")
        }

        val request = DetectarDuplicadosCiudadanoRequest(
            descripcion = descripcionRecortada,
            latitud = latitud,
            longitud = longitud
        )

        val response = try {
            withTimeoutOrNull(timeoutMs) {
                llamada(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return ResultadoVerificacion.NoDisponible("red")
        } catch (_: Exception) {
            return ResultadoVerificacion.NoDisponible("error_inesperado")
        } ?: return ResultadoVerificacion.NoDisponible("timeout")

        if (!response.isSuccessful) {
            return ResultadoVerificacion.NoDisponible("http_${response.code()}")
        }

        val body = response.body() ?: return ResultadoVerificacion.NoDisponible("cuerpo_vacio")
        val candidatos = (body.posibles_duplicados ?: emptyList()).take(MAX_CANDIDATOS_DUPLICADOS)

        return if (candidatos.isEmpty()) {
            ResultadoVerificacion.SinCoincidencias
        } else {
            ResultadoVerificacion.ConCoincidencias(candidatos)
        }
    }
}

fun calcularFirmaDuplicados(descripcion: String, latitud: Double, longitud: Double): String {
    val descripcionNormalizada = descripcion
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
    val latNormalizada = if (abs(latitud) < 0.000005) 0.0 else latitud
    val lonNormalizada = if (abs(longitud) < 0.000005) 0.0 else longitud
    return String.format(Locale.ROOT, "%s|%.5f|%.5f", descripcionNormalizada, latNormalizada, lonNormalizada)
}
