package com.example.petvetericano.notificaciones

import com.example.petvetericano.realtime.MensajeServidorWs
import com.example.petvetericano.realtime.WebSocketEvent
import com.example.petvetericano.realtime.WebSocketEventParser
import com.example.petvetericano.realtime.WebSocketRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pruebas reales de concurrencia para la deduplicación atómica compartida
 * FCM + WebSocket (`NotificacionesRepository.mostrarUnaVezInterno`).
 * Miden invocaciones efectivas de presentación, no solamente respuestas booleanas.
 */
class NotificacionesDeduplicacionConcurrenciaTest {

    private class AlmacenEnMemoria(iniciales: List<String> = emptyList()) :
        NotificacionesRepository.AlmacenClavesMostradas {
        @Volatile
        private var lista: List<String> = iniciales.toList()

        override fun leer(): List<String> = lista

        override fun guardar(claves: List<String>) {
            lista = claves.toList()
        }
    }

    @Before
    fun setUp() {
        WebSocketRepository.limpiarDeduplicacion()
    }

    private fun crearEventoNp(eventId: String, clave: String): WebSocketEvent {
        val raw = """{
            "type":"event","schema_version":"1","event_id":"$eventId",
            "event_type":"peticion.asignada","categoria":"NP","modulo":"peticiones",
            "timestamp":"2026-11-19T14:21:58",
            "data":{"clave":"$clave","titulo":"Petición asignada","mensaje":"Prueba concurrencia"}
        }"""
        return (WebSocketEventParser.parsearMensaje(raw) as MensajeServidorWs.Evento).evento
    }

    private fun crearEfectosAtomicos(
        almacen: AlmacenEnMemoria,
        contadorPresentaciones: AtomicInteger,
        demoraMs: Long = 0L
    ): WebSocketRepository.EfectosNotificacion = object : WebSocketRepository.EfectosNotificacion {
        override fun yaMostrada(clave: String): Boolean = clave in almacen.leer()
        override fun marcarMostrada(clave: String) {}
        override fun mostrarNotificacionNp(evento: WebSocketEvent): Boolean {
            if (demoraMs > 0L) Thread.sleep(demoraMs)
            contadorPresentaciones.incrementAndGet()
            return true
        }

        override fun mostrarUnaVezNp(clave: String?, evento: WebSocketEvent): Boolean =
            NotificacionesRepository.mostrarUnaVezInterno(clave, almacen) {
                mostrarNotificacionNp(evento)
            }

        override fun notificarBandeja() {}
        override fun cancelarNotificacion(clave: String) {}
        override fun cancelarTodasNotificaciones() {}
    }

    @Test
    fun `1 FCM y WebSocket simultaneos con misma clave producen exactamente una presentacion`() {
        val almacen = AlmacenEnMemoria()
        val presentaciones = AtomicInteger(0)
        val barrera = CyclicBarrier(2)
        val fin = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        val eventoWs = crearEventoNp("ev-sim-1", "sis-9")
        val efectosWs = crearEfectosAtomicos(almacen, presentaciones, demoraMs = 40L)

        try {
            // Hilo WebSocket
            pool.execute {
                try {
                    barrera.await(5, TimeUnit.SECONDS)
                    WebSocketRepository.procesarEventoConEfectos(eventoWs, efectosWs)
                } finally {
                    fin.countDown()
                }
            }
            // Hilo FCM (VetericanoMessagingService.onMessageReceived usa exactamente mostrarUnaVez)
            pool.execute {
                try {
                    barrera.await(5, TimeUnit.SECONDS)
                    NotificacionesRepository.mostrarUnaVezInterno("sis-9", almacen) {
                        Thread.sleep(40L)
                        presentaciones.incrementAndGet()
                        true
                    }
                } finally {
                    fin.countDown()
                }
            }

            assertTrue("Timeout esperando hilos FCM+WS", fin.await(10, TimeUnit.SECONDS))
            assertEquals(1, presentaciones.get())
            assertEquals(listOf("sis-9"), almacen.leer())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `2 orden inverso de llegada (FCM luego WS y WS luego FCM) produce una sola presentacion`() {
        // Orden A: FCM primero, luego WS
        val almacenA = AlmacenEnMemoria()
        val presA = AtomicInteger(0)
        assertTrue(NotificacionesRepository.mostrarUnaVezInterno("sis-101", almacenA) {
            presA.incrementAndGet()
            true
        })
        val efectosA = crearEfectosAtomicos(almacenA, presA)
        assertTrue(WebSocketRepository.procesarEventoConEfectos(crearEventoNp("ev-ord-a", "sis-101"), efectosA))
        assertEquals(1, presA.get())

        // Orden B: WS primero, luego FCM
        val almacenB = AlmacenEnMemoria()
        val presB = AtomicInteger(0)
        val efectosB = crearEfectosAtomicos(almacenB, presB)
        assertTrue(WebSocketRepository.procesarEventoConEfectos(crearEventoNp("ev-ord-b", "pet-202"), efectosB))
        assertFalse(NotificacionesRepository.mostrarUnaVezInterno("pet-202", almacenB) {
            presB.incrementAndGet()
            true
        })
        assertEquals(1, presB.get())
    }

    @Test
    fun `3 dieciseis hilos concurrentes con la misma clave producen una sola presentacion`() {
        val hilos = 16
        val almacen = AlmacenEnMemoria()
        val presentaciones = AtomicInteger(0)
        val exitsTrue = AtomicInteger(0)
        val barrera = CyclicBarrier(hilos)
        val fin = CountDownLatch(hilos)
        val pool = Executors.newFixedThreadPool(hilos)

        try {
            repeat(hilos) {
                pool.execute {
                    try {
                        barrera.await(5, TimeUnit.SECONDS)
                        val ok = NotificacionesRepository.mostrarUnaVezInterno("pet-500", almacen) {
                            Thread.sleep(25L)
                            presentaciones.incrementAndGet()
                            true
                        }
                        if (ok) exitsTrue.incrementAndGet()
                    } finally {
                        fin.countDown()
                    }
                }
            }

            assertTrue("Timeout esperando 16 hilos", fin.await(10, TimeUnit.SECONDS))
            assertEquals(1, presentaciones.get())
            assertEquals(1, exitsTrue.get())
            assertEquals(listOf("pet-500"), almacen.leer())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `4 dos claves diferentes concurrentes producen exactamente dos presentaciones`() {
        val almacen = AlmacenEnMemoria()
        val presentaciones = AtomicInteger(0)
        val barrera = CyclicBarrier(2)
        val fin = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        try {
            listOf("sis-10", "pet-20").forEach { clave ->
                pool.execute {
                    try {
                        barrera.await(5, TimeUnit.SECONDS)
                        assertTrue(NotificacionesRepository.mostrarUnaVezInterno(clave, almacen) {
                            Thread.sleep(20L)
                            presentaciones.incrementAndGet()
                            true
                        })
                    } finally {
                        fin.countDown()
                    }
                }
            }

            assertTrue(fin.await(10, TimeUnit.SECONDS))
            assertEquals(2, presentaciones.get())
            assertEquals(setOf("sis-10", "pet-20"), almacen.leer().toSet())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `5 fallo inicial de presentacion libera reserva y permite reintento exitoso`() {
        val almacen = AlmacenEnMemoria()
        val invocaciones = AtomicInteger(0)

        // Intento 1: retorna false (p. ej. sin permiso temporal)
        val primerIntento = NotificacionesRepository.mostrarUnaVezInterno("sis-retry-1", almacen) {
            invocaciones.incrementAndGet()
            false
        }
        assertFalse(primerIntento)
        assertTrue(almacen.leer().isEmpty())

        // Intento 2: lanza excepción -> tampoco debe quedar bloqueada ni persistida
        var excepcionCapturada = false
        try {
            NotificacionesRepository.mostrarUnaVezInterno("sis-retry-1", almacen) {
                invocaciones.incrementAndGet()
                throw IllegalStateException("Fallo simulado de NotificationManager")
            }
        } catch (_: IllegalStateException) {
            excepcionCapturada = true
        }
        assertTrue(excepcionCapturada)
        assertTrue(almacen.leer().isEmpty())

        // Intento 3: éxito -> se muestra y se persiste
        val tercerIntento = NotificacionesRepository.mostrarUnaVezInterno("sis-retry-1", almacen) {
            invocaciones.incrementAndGet()
            true
        }
        assertTrue(tercerIntento)
        assertEquals(3, invocaciones.get())
        assertEquals(listOf("sis-retry-1"), almacen.leer())

        // Intento 4: ya persistida -> se omite
        assertFalse(NotificacionesRepository.mostrarUnaVezInterno("sis-retry-1", almacen) {
            invocaciones.incrementAndGet()
            true
        })
        assertEquals(3, invocaciones.get())
    }

    @Test
    fun `6 clave previamente presentada omite invocacion de presentacion`() {
        val almacen = AlmacenEnMemoria(listOf("sis-previa-1", "pet-previa-2"))
        val presentaciones = AtomicInteger(0)

        assertFalse(NotificacionesRepository.mostrarUnaVezInterno("sis-previa-1", almacen) {
            presentaciones.incrementAndGet()
            true
        })
        assertFalse(NotificacionesRepository.mostrarUnaVezInterno("  pet-previa-2  ", almacen) {
            presentaciones.incrementAndGet()
            true
        })
        assertEquals(0, presentaciones.get())
    }

    @Test
    fun `7 conserva el limite acotado de almacenamiento de claves`() {
        val maxPrueba = 5
        val almacen = AlmacenEnMemoria()
        val presentaciones = AtomicInteger(0)

        for (i in 1..8) {
            assertTrue(NotificacionesRepository.mostrarUnaVezInterno("sis-$i", almacen, maxClaves = maxPrueba) {
                presentaciones.incrementAndGet()
                true
            })
        }

        assertEquals(8, presentaciones.get())
        assertEquals(maxPrueba, almacen.leer().size)
        assertEquals(listOf("sis-4", "sis-5", "sis-6", "sis-7", "sis-8"), almacen.leer())
        assertEquals(300, NotificacionesRepository.MAX_CLAVES_MOSTRADAS)
    }
}
