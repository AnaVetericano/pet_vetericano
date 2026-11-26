package com.example.petvetericano.notificaciones

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.petvetericano.realtime.CategoriaEventoWs
import com.example.petvetericano.realtime.WebSocketRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Refresca el contador de la campana (que siempre se pide a la BD: `GET /api/notificaciones/contador/`)
 * cuando llega por WebSocket una NP nueva o una lectura hecha en otra pestaña o dispositivo,
 * mientras la pantalla está visible. Debounce de 1 s para agrupar ráfagas.
 */
object ContadorNotificacionesEnVivo {

    @OptIn(FlowPreview::class)
    fun observar(owner: LifecycleOwner, refrescar: () -> Unit) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketRepository.eventos
                    .filter {
                        it.categoria == CategoriaEventoWs.NP ||
                            it.eventType == "notificacion.leida" ||
                            it.eventType == "notificacion.todas_leidas"
                    }
                    .debounce(1_000L)
                    .collect { refrescar() }
            }
        }
    }
}
