package com.example.petvetericano.notificaciones

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.MainActivity
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ActivityNotificacionesBinding
import com.example.petvetericano.models.NotificacionItem
import com.example.petvetericano.realtime.CategoriaEventoWs
import com.example.petvetericano.realtime.WebSocketRepository
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Bandeja de notificaciones del usuario (REST central §6).
 * Tocar una no leída la marca como leída (`PATCH /api/notificaciones/<clave>/leer/`);
 * "Marcar todas" llama a `PATCH /api/notificaciones/leer-todas/`.
 * Se abre desde la notificación del sistema.
 */
class NotificacionesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificacionesBinding
    private val adapter = NotificacionesAdapter { item -> alTocarNotificacion(item) }

    private var items: List<NotificacionItem> = emptyList()
    private var cargando = false
    /** Se pidió recargar mientras otra carga estaba en vuelo (p. ej. tras marcar al abrir): se repite al terminar. */
    private var recargaPendiente = false
    private var marcandoTodas = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNotificacionesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (!NotificacionesRepository.haySesion(this)) {
            irAInicio()
            return
        }

        binding.toolbarNotificaciones.setNavigationOnClickListener { volver() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = volver()
        })

        binding.rvNotificaciones.layoutManager = LinearLayoutManager(this)
        binding.rvNotificaciones.adapter = adapter
        binding.swipeRefreshNotificaciones.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshNotificaciones.setOnRefreshListener { cargar(mostrarProgreso = false) }
        binding.btnMarcarTodas.setOnClickListener { marcarTodas() }
        binding.btnReintentarNotificaciones.setOnClickListener { cargar(mostrarProgreso = true) }

        observarEventosRealtime()
        // Al recrearse (rotación) no se repite el marcado: solo se recarga.
        if (savedInstanceState == null) abrirDesdeIntent(intent) else cargar(mostrarProgreso = true)
    }

    /**
     * Si la bandeja se abrió tocando una notificación del sistema (EXTRA_CLAVE), abrirla = leerla:
     * primero se marca en el servidor (la BD es la fuente de verdad) y después se carga la lista,
     * para no mostrar un estado anterior al PATCH. Sin clave, solo carga.
     */
    private fun abrirDesdeIntent(intent: Intent?) {
        val clave = intent?.getStringExtra(VetericanoMessagingService.EXTRA_CLAVE)?.trim().orEmpty()
        intent?.removeExtra(VetericanoMessagingService.EXTRA_CLAVE) // no repetir al recrear la Activity
        if (clave.isEmpty()) {
            cargar(mostrarProgreso = true)
            return
        }
        if (items.isEmpty()) binding.progressNotificaciones.visibility = View.VISIBLE
        lifecycleScope.launch {
            NotificacionesRepository.marcarLeida(this@NotificacionesActivity, clave)
                .onSuccess { VetericanoMessagingService.cancelar(this@NotificacionesActivity, clave) }
                .onFailure { error ->
                    if (error !is SinSesionException) {
                        Snackbar.make(binding.root, R.string.notif_error_marcar, Snackbar.LENGTH_SHORT).show()
                    }
                }
            cargar(mostrarProgreso = true)
        }
    }

    @OptIn(FlowPreview::class)
    private fun observarEventosRealtime() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WebSocketRepository.eventos
                    .filter {
                        it.categoria == CategoriaEventoWs.NP ||
                            it.eventType == "notificacion.leida" ||
                            it.eventType == "notificacion.todas_leidas"
                    }
                    .debounce(1_000L)
                    .collect { cargar(mostrarProgreso = false) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirDesdeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        NotificacionesRepository.alLlegarNotificacion = { cargar(mostrarProgreso = false) }
    }

    override fun onPause() {
        NotificacionesRepository.alLlegarNotificacion = null
        super.onPause()
    }

    private fun cargar(mostrarProgreso: Boolean) {
        if (cargando) {
            recargaPendiente = true
            binding.swipeRefreshNotificaciones.isRefreshing = false
            return
        }
        cargando = true
        recargaPendiente = false
        if (mostrarProgreso && items.isEmpty()) binding.progressNotificaciones.visibility = View.VISIBLE
        lifecycleScope.launch {
            val resultado = NotificacionesRepository.listar(this@NotificacionesActivity, soloNoLeidas = false)
            cargando = false
            if (recargaPendiente) {
                // Llegó un cambio (PATCH o evento) mientras esta carga viajaba: su foto puede ser anterior.
                cargar(mostrarProgreso = false)
                return@launch
            }
            binding.progressNotificaciones.visibility = View.GONE
            binding.swipeRefreshNotificaciones.isRefreshing = false
            resultado
                .onSuccess { respuesta ->
                    val lista = respuesta.resultados.orEmpty()
                    // Ya están a la vista en la bandeja: no repetirlas como notificación del sistema.
                    NotificacionesRepository.marcarMostradas(
                        this@NotificacionesActivity,
                        lista.filter { !it.leida }.mapNotNull { it.clave }
                    )
                    mostrarLista(lista, respuesta.noLeidas)
                }
                .onFailure { error ->
                    if (error is SinSesionException) {
                        irAInicio()
                    } else {
                        NotificacionesRepository.logDebug("Bandeja: error ${error.javaClass.simpleName}")
                        mostrarError()
                    }
                }
        }
    }

    private fun mostrarLista(lista: List<NotificacionItem>, noLeidasServidor: Int) {
        items = lista
        adapter.submitList(lista)
        val noLeidas = maxOf(noLeidasServidor, lista.count { !it.leida })
        actualizarCabecera(noLeidas)
        if (lista.isEmpty()) {
            mostrarEstado(
                getString(R.string.notif_vacio_titulo),
                getString(R.string.notif_vacio_subtitulo),
                conReintento = false
            )
        } else {
            binding.layoutEstadoNotificaciones.visibility = View.GONE
        }
    }

    private fun mostrarError() {
        if (items.isNotEmpty()) {
            Snackbar.make(binding.root, R.string.notif_error_cargar, Snackbar.LENGTH_LONG).show()
            return
        }
        actualizarCabecera(0)
        mostrarEstado(
            getString(R.string.notif_error_titulo),
            getString(R.string.notif_error_cargar),
            conReintento = true
        )
    }

    private fun mostrarEstado(titulo: String, subtitulo: String, conReintento: Boolean) {
        binding.tvEstadoTitulo.text = titulo
        binding.tvEstadoSubtitulo.text = subtitulo
        binding.btnReintentarNotificaciones.visibility = if (conReintento) View.VISIBLE else View.GONE
        binding.layoutEstadoNotificaciones.visibility = View.VISIBLE
    }

    private fun actualizarCabecera(noLeidas: Int) {
        binding.toolbarNotificaciones.subtitle = if (noLeidas > 0) {
            getString(R.string.notif_subtitulo_no_leidas, noLeidas)
        } else {
            getString(R.string.notif_subtitulo_al_dia)
        }
        binding.btnMarcarTodas.isEnabled = noLeidas > 0 && !marcandoTodas
    }

    private fun alTocarNotificacion(item: NotificacionItem) {
        val clave = item.clave?.trim().orEmpty()
        if (item.leida || clave.isEmpty()) return
        // Respuesta optimista; si falla se revierte.
        reemplazar(item.copy(leida = true))
        lifecycleScope.launch {
            NotificacionesRepository.marcarLeida(this@NotificacionesActivity, clave)
                .onSuccess { actualizado ->
                    VetericanoMessagingService.cancelar(this@NotificacionesActivity, clave)
                    if (actualizado.clave == clave) reemplazar(actualizado)
                }
                .onFailure {
                    reemplazar(item)
                    Snackbar.make(binding.root, R.string.notif_error_marcar, Snackbar.LENGTH_SHORT).show()
                }
        }
    }

    private fun reemplazar(nuevo: NotificacionItem) {
        items = items.map { if (it.clave == nuevo.clave) nuevo else it }
        adapter.submitList(items)
        actualizarCabecera(items.count { !it.leida })
    }

    private fun marcarTodas() {
        if (marcandoTodas) return
        marcandoTodas = true
        binding.btnMarcarTodas.isEnabled = false
        lifecycleScope.launch {
            val resultado = NotificacionesRepository.marcarTodasLeidas(this@NotificacionesActivity)
            marcandoTodas = false
            resultado
                .onSuccess {
                    VetericanoMessagingService.cancelarTodas(this@NotificacionesActivity)
                    cargar(mostrarProgreso = false)
                }
                .onFailure {
                    actualizarCabecera(items.count { !it.leida })
                    Snackbar.make(binding.root, R.string.notif_error_marcar, Snackbar.LENGTH_SHORT).show()
                }
        }
    }

    /** Si se abrió desde la notificación con la app cerrada, volver lleva al inicio de la app. */
    private fun volver() {
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun irAInicio() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
