package com.example.petvetericano.ui.reporte

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteCiudadanoViewModelAutorizacionFotoTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun estadoInicial_autorizaFotoDuplicadosEsFalsePorDefecto() {
        val vm = ReporteCiudadanoViewModel(SavedStateHandle())
        assertFalse(vm.uiState.value.autorizaFotoDuplicados)
    }

    @Test
    fun actualizarAutorizacionFotoDuplicados_persisteYRestauraConSavedStateHandle() {
        val handle = SavedStateHandle()
        val vm1 = ReporteCiudadanoViewModel(handle)

        vm1.actualizarAutorizacionFotoDuplicados(true)

        assertTrue(vm1.uiState.value.autorizaFotoDuplicados)
        assertEquals(true, handle.get<Boolean>("key_reporte_autoriza_foto_dup"))

        // Recrear ViewModel con el mismo SavedStateHandle simula recreación tras muerte de proceso
        val vm2 = ReporteCiudadanoViewModel(handle)
        assertTrue(vm2.uiState.value.autorizaFotoDuplicados)
    }

    @Test
    fun limpiarBorrador_reiniciaAutorizacionFotoDuplicadosAFalse() {
        val handle = SavedStateHandle()
        val vm = ReporteCiudadanoViewModel(handle)
        vm.actualizarAutorizacionFotoDuplicados(true)
        assertTrue(vm.uiState.value.autorizaFotoDuplicados)

        vm.limpiarBorrador()

        assertFalse(vm.uiState.value.autorizaFotoDuplicados)
        assertFalse(handle.contains("key_reporte_autoriza_foto_dup"))

        val vmRecreado = ReporteCiudadanoViewModel(handle)
        assertFalse(vmRecreado.uiState.value.autorizaFotoDuplicados)
    }

    @Test
    fun eliminarEvidencia_alQuedarCeroFotos_reiniciaAutorizacionAFalse_inclusoSiQuedaVideo() {
        val handle = SavedStateHandle()
        val vm = ReporteCiudadanoViewModel(handle)
        val foto1 = EvidenciaItemLocal("f1.jpg", "/tmp/f1.jpg", "", esVideo = false)
        val foto2 = EvidenciaItemLocal("f2.jpg", "/tmp/f2.jpg", "", esVideo = false)
        val video = EvidenciaItemLocal("v1.mp4", "/tmp/v1.mp4", "", esVideo = true)

        vm.agregarEvidencia(foto1)
        vm.agregarEvidencia(foto2)
        vm.agregarEvidencia(video)
        vm.actualizarAutorizacionFotoDuplicados(true)

        // Al eliminar una foto pero quedar otra, conserva la autorización
        vm.eliminarEvidencia("f1.jpg")
        assertEquals(1, vm.uiState.value.fotosCount)
        assertTrue(vm.uiState.value.autorizaFotoDuplicados)

        // Al eliminar la última foto (aunque quede un video), la autorización vuelve a false
        vm.eliminarEvidencia("f2.jpg")
        assertEquals(0, vm.uiState.value.fotosCount)
        assertEquals(1, vm.uiState.value.videosCount)
        assertFalse(vm.uiState.value.autorizaFotoDuplicados)
        assertEquals(false, handle.get<Boolean>("key_reporte_autoriza_foto_dup"))
    }

    @Test
    fun autorizacionEfectiva_reglaPura_soloTrueSiAutorizaYHayFotos() {
        assertFalse(autorizacionEfectiva(autoriza = false, fotosCount = 0))
        assertFalse(autorizacionEfectiva(autoriza = false, fotosCount = 2))
        assertFalse(autorizacionEfectiva(autoriza = true, fotosCount = 0))
        assertTrue(autorizacionEfectiva(autoriza = true, fotosCount = 1))
        assertTrue(autorizacionEfectiva(autoriza = true, fotosCount = 5))
    }
}
