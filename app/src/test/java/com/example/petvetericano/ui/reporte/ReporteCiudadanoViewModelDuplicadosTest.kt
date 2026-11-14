package com.example.petvetericano.ui.reporte

import androidx.lifecycle.SavedStateHandle
import com.example.petvetericano.models.CandidatoDuplicado
import com.example.petvetericano.models.DetectarDuplicadosCiudadanoResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteCiudadanoViewModelDuplicadosTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModelConBorrador(
        descripcion: String = "Perro herido cerca de la esquina norte",
        lat: Double = 4.60971,
        lng: Double = -74.08175
    ): ReporteCiudadanoViewModel {
        return ReporteCiudadanoViewModel(SavedStateHandle()).apply {
            establecerUbicacion(lat, lng, "Calle 10 # 5-20")
            actualizarDescripcion(descripcion)
        }
    }

    private fun TestScope.recolectarEventos(vm: ReporteCiudadanoViewModel): Pair<MutableList<EventoDuplicados>, kotlinx.coroutines.Job> {
        val eventos = mutableListOf<EventoDuplicados>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.eventosDuplicados.collect { eventos.add(it) }
        }
        return eventos to job
    }

    @Test
    fun verificar_conCoincidencias_emiteMostrarAdvertenciaYFijaEstado() = runTest(testDispatcher) {
        val candidato = CandidatoDuplicado(cercania = "cerca", similitud = "alta")
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(candidato)
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()

        assertEquals(1, llamadas)
        assertEquals(listOf(EventoDuplicados.MostrarAdvertencia), eventos)
        assertEquals(listOf(candidato), vm.uiState.value.candidatosDuplicados)
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)
        assertFalse(vm.uiState.value.verificandoDuplicados)
    }

    @Test
    fun verificar_ceroCoincidencias_emiteContinuarConfirmacion() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        vm.verificador = VerificadorDuplicados(llamada = {
            Response.success(DetectarDuplicadosCiudadanoResponse(posibles_duplicados = emptyList()))
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()

        assertEquals(listOf(EventoDuplicados.ContinuarConfirmacion), eventos)
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
    }

    @Test
    fun verificar_sinConexion_emiteContinuarConfirmacionSinLlamarVerificador() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(DetectarDuplicadosCiudadanoResponse())
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = false)
        advanceUntilIdle()

        assertEquals(0, llamadas)
        assertEquals(listOf(EventoDuplicados.ContinuarConfirmacion), eventos)
    }

    @Test
    fun verificar_fallo_emiteContinuarConfirmacionYPermiteReintento() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        val candidato = CandidatoDuplicado(cercania = "muy_cerca", similitud = "alta")
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            if (llamadas == 1) {
                Response.error(500, "err".toResponseBody("application/json".toMediaType()))
            } else {
                Response.success(
                    DetectarDuplicadosCiudadanoResponse(
                        advertencia_duplicado = true,
                        posible_duplicado = true,
                        total_candidatos = 1,
                        posibles_duplicados = listOf(candidato)
                    )
                )
            }
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(1, llamadas)
        assertEquals(listOf(EventoDuplicados.ContinuarConfirmacion), eventos)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(2, llamadas)
        assertEquals(
            listOf(EventoDuplicados.ContinuarConfirmacion, EventoDuplicados.MostrarAdvertencia),
            eventos
        )
    }

    @Test
    fun verificar_doblePulsacionMientrasVerifica_ejecutaUnaSolaLlamada() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        val puerta = CompletableDeferred<Unit>()
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            puerta.await()
            Response.success(DetectarDuplicadosCiudadanoResponse(posibles_duplicados = emptyList()))
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        assertTrue(vm.uiState.value.verificandoDuplicados)
        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)

        puerta.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, llamadas)
        assertEquals(listOf(EventoDuplicados.ContinuarConfirmacion), eventos)
        assertFalse(vm.uiState.value.verificandoDuplicados)
    }

    @Test
    fun verificar_mismoBorradorTrasExito_usaCacheConUnaSolaLlamada() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        val candidato = CandidatoDuplicado(cercania = "cerca", similitud = "media")
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(candidato)
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        vm.cancelarAdvertenciaDuplicados()

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()

        assertEquals(1, llamadas)
        assertEquals(
            listOf(EventoDuplicados.MostrarAdvertencia, EventoDuplicados.MostrarAdvertencia),
            eventos
        )
    }

    @Test
    fun responderCandidatoNo_avanzaIndiceYAlFinalActivaAntibucle() = runTest(testDispatcher) {
        val c1 = CandidatoDuplicado(cercania = "muy_cerca", similitud = "alta")
        val c2 = CandidatoDuplicado(cercania = "cerca", similitud = "media")
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 2,
                    posibles_duplicados = listOf(c1, c2)
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)

        vm.responderCandidatoNo()
        assertEquals(1, vm.uiState.value.indiceCandidatoDuplicado)
        assertEquals(listOf(EventoDuplicados.MostrarAdvertencia), eventos)

        vm.responderCandidatoNo()
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)
        assertEquals(
            listOf(EventoDuplicados.MostrarAdvertencia, EventoDuplicados.ContinuarConfirmacion),
            eventos
        )

        // Revisar de nuevo con el mismo borrador -> ContinuarConfirmacion sin llamada
        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(1, llamadas)
        assertEquals(
            listOf(
                EventoDuplicados.MostrarAdvertencia,
                EventoDuplicados.ContinuarConfirmacion,
                EventoDuplicados.ContinuarConfirmacion
            ),
            eventos
        )
    }

    @Test
    fun responderCandidatoSi_emiteMantenerBorradorYDejaBorradorIntacto() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador(descripcion = "Gato atrapado en techo de lamina")
        val evidencia = EvidenciaItemLocal(
            id = "foto_1.jpg",
            localPath = "/tmp/foto_1.jpg",
            uriString = "content://foto_1",
            esVideo = false
        )
        vm.agregarEvidencia(evidencia)

        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(CandidatoDuplicado(cercania = "cerca", similitud = "alta"))
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()

        vm.responderCandidatoSi()
        advanceUntilIdle()

        assertEquals(1, llamadas)
        assertEquals(
            listOf(EventoDuplicados.MostrarAdvertencia, EventoDuplicados.MantenerBorrador),
            eventos
        )
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)
        assertEquals("Gato atrapado en techo de lamina", vm.uiState.value.descripcion)
        assertEquals(listOf(evidencia), vm.uiState.value.evidencias)
    }

    @Test
    fun editarDescripcion_limpiaCandidatosYCambiaFirmaParaReconsultar() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador(descripcion = "Perro herido frente a la tienda")
        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(CandidatoDuplicado(cercania = "cerca", similitud = "alta"))
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.candidatosDuplicados.size)

        vm.actualizarDescripcion("Perro herido frente a la tienda de la esquina sur")
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(2, llamadas)
        assertEquals(
            listOf(EventoDuplicados.MostrarAdvertencia, EventoDuplicados.MostrarAdvertencia),
            eventos
        )
    }

    @Test
    fun cancelarAdvertencia_yLimpiarBorrador_reinicianEstadoYCache() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador()
        var llamadas = 0
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(CandidatoDuplicado(cercania = "cerca", similitud = "alta"))
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.candidatosDuplicados.size)

        vm.cancelarAdvertenciaDuplicados()
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
        assertEquals(0, vm.uiState.value.indiceCandidatoDuplicado)
        assertEquals(listOf(EventoDuplicados.MostrarAdvertencia), eventos)

        // Descartar para activar firmaDescartada y luego limpiarBorrador debe reiniciar todo
        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        vm.responderCandidatoNo()
        assertEquals(1, llamadas)

        vm.limpiarBorrador()
        vm.establecerUbicacion(4.60971, -74.08175, "Calle 10 # 5-20")
        vm.actualizarDescripcion("Perro herido cerca de la esquina norte")

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()
        assertEquals(2, llamadas)
    }

    @Test
    fun verificar_borradorEditadoEnVuelo_descartaResultadoSinCachearYEmiteContinuar() = runTest(testDispatcher) {
        val vm = crearViewModelConBorrador(descripcion = "Perro herido frente al parque")
        var llamadas = 0
        val candidato = CandidatoDuplicado(cercania = "muy_cerca", similitud = "alta")
        vm.verificador = VerificadorDuplicados(llamada = {
            llamadas++
            delay(500)
            Response.success(
                DetectarDuplicadosCiudadanoResponse(
                    advertencia_duplicado = true,
                    posible_duplicado = true,
                    total_candidatos = 1,
                    posibles_duplicados = listOf(candidato)
                )
            )
        })
        val (eventos, _) = recolectarEventos(vm)

        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceTimeBy(100)
        assertTrue(vm.uiState.value.verificandoDuplicados)

        vm.actualizarDescripcion("Gato herido en otro barrio muy distinto")
        advanceUntilIdle()

        assertEquals(1, llamadas)
        assertEquals(listOf(EventoDuplicados.ContinuarConfirmacion), eventos)
        assertTrue(vm.uiState.value.candidatosDuplicados.isEmpty())
        assertFalse(vm.uiState.value.verificandoDuplicados)

        // Al volver a la descripcion original debe reconsultar porque no se cacheo
        vm.actualizarDescripcion("Perro herido frente al parque")
        vm.verificarDuplicadosAntesDeRevisar(hayConexion = true)
        advanceUntilIdle()

        assertEquals(2, llamadas)
        assertEquals(
            listOf(EventoDuplicados.ContinuarConfirmacion, EventoDuplicados.MostrarAdvertencia),
            eventos
        )
    }
}
