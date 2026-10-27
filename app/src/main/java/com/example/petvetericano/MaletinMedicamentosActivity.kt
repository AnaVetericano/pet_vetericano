package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityMaletinMedicamentosBinding
import com.example.petvetericano.models.MedicamentoMaletin
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MaletinMedicamentosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMaletinMedicamentosBinding
    private lateinit var maletinManager: MaletinManager
    private lateinit var adapter: MedicamentoAdapter

    private var categoriaSeleccionada: String = "Todos"
    private var queryBusqueda: String = ""
    private var listaMedicamentosActual: List<MedicamentoMaletin> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMaletinMedicamentosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maletinManager = MaletinManager.getInstance(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarFiltros()
        configurarBuscador()
        configurarSwipeRefresh()
        configurarFab()

        // 1. Cargar inmediatamente datos locales reactivos desde StateFlow (0 ms, sin parpadeo)
        observarMedicamentosFlow()

        // 2. Sincronización silenciosa en segundo plano al abrir la pantalla
        sincronizarMaletin(forzar = false)
    }

    override fun onResume() {
        super.onResume()
        // Sincronización silenciosa en segundo plano al regresar (por si hubo cambios o consumos)
        sincronizarMaletin(forzar = false)
    }

    /**
     * Observa de forma reactiva el flujo de medicamentos del maletín.
     * Cualquier cambio (sincronización remota, consumo en visita, reposición)
     * se refleja visualmente de manera automática sin necesidad de recargar o deslizar.
     */
    private fun observarMedicamentosFlow() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                maletinManager.medicamentosFlow.collect { medicamentos ->
                    listaMedicamentosActual = medicamentos
                    actualizarDatosVista(medicamentos)
                }
            }
        }
    }

    private fun configurarToolbar() {
        binding.toolbarMaletin.setNavigationOnClickListener {
            finish()
        }

        binding.toolbarMaletin.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sincronizar_maletin -> {
                    sincronizarMaletin(forzar = true)
                    true
                }
                R.id.action_solicitudes_kit -> {
                    abrirPantallaSolicitudesKit()
                    true
                }
                else -> false
            }
        }
    }

    private fun configurarRecyclerView() {
        adapter = MedicamentoAdapter(emptyList()) { medicamento ->
            mostrarDetalleMedicamento(medicamento)
        }
        binding.rvMedicamentos.layoutManager = LinearLayoutManager(this)
        binding.rvMedicamentos.adapter = adapter
    }

    private fun configurarSwipeRefresh() {
        binding.swipeRefreshMaletin.setOnRefreshListener {
            sincronizarMaletin(forzar = true)
        }
    }

    private fun configurarFab() {
        binding.fabSolicitarReposicion.setOnClickListener {
            abrirPantallaSolicitudesKit()
        }

        binding.nestedScrollMaletin.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY > oldScrollY + 12 && binding.fabSolicitarReposicion.isExtended) {
                    binding.fabSolicitarReposicion.shrink()
                } else if (scrollY < oldScrollY - 12 && !binding.fabSolicitarReposicion.isExtended) {
                    binding.fabSolicitarReposicion.extend()
                }
            }
        )

        binding.btnEmptySolicitarDotacion.setOnClickListener {
            abrirPantallaSolicitudesKit()
        }
    }

    private fun configurarFiltros() {
        binding.chipGroupCategorias.setOnCheckedStateChangeListener { _, checkedIds ->
            categoriaSeleccionada = when (checkedIds.firstOrNull()) {
                R.id.chipCatAine -> "Analgésicos / AINE"
                R.id.chipCatAntibioticos -> "Antibióticos"
                R.id.chipCatAnestesicos -> "Anestésicos"
                R.id.chipCatAntiparasitarios -> "Antiparasitarios"
                R.id.chipCatFluidos -> "Fluidoterapia"
                R.id.chipCatCorticoides -> "Corticoides"
                else -> "Todos"
            }
            filtrarLista(listaMedicamentosActual)
        }
    }

    private fun configurarBuscador() {
        binding.etBuscarMedicamento.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                queryBusqueda = s?.toString()?.trim() ?: ""
                filtrarLista(listaMedicamentosActual)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun actualizarDatosVista(medicamentos: List<MedicamentoMaletin>) {
        val disponiblesCount = medicamentos.count { it.saldoDisponibleInt > 0 }
        binding.tvTotalReferenciasHeader.text = "$disponiblesCount"
        binding.tvEstadoGeneralBadge.text = maletinManager.getEstadoStock()

        val ultSync = maletinManager.getUltimoTimestampSync()
        if (ultSync > 0L) {
            val fechaFmt = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", ultSync)
            binding.tvEstadoSincronizacion.text = "Sincronizado: $fechaFmt • Disponible offline"
        } else {
            binding.tvEstadoSincronizacion.text = "Actualizando datos del maletín..."
        }

        filtrarLista(medicamentos)
    }

    private fun filtrarLista(todos: List<MedicamentoMaletin>) {
        val filtrados = todos.filter { med ->
            val coincideCategoria = if (categoriaSeleccionada == "Todos") {
                true
            } else {
                med.categoria.contains(categoriaSeleccionada, ignoreCase = true) ||
                        categoriaSeleccionada.contains(med.categoria, ignoreCase = true)
            }

            val coincideBusqueda = if (queryBusqueda.isEmpty()) {
                true
            } else {
                med.nombre.contains(queryBusqueda, ignoreCase = true) ||
                        med.categoria.contains(queryBusqueda, ignoreCase = true) ||
                        med.concentracion.contains(queryBusqueda, ignoreCase = true) ||
                        med.lote.contains(queryBusqueda, ignoreCase = true)
            }

            coincideCategoria && coincideBusqueda
        }

        adapter.updateList(filtrados)

        if (filtrados.isEmpty()) {
            binding.layoutEmptyMedicamentos.visibility = View.VISIBLE
            binding.rvMedicamentos.visibility = View.GONE
            if (todos.isEmpty()) {
                binding.tvEmptyTitulo.text = "Maletín sin asignación activa"
                binding.tvEmptySubtitulo.text = "Aún no tienes medicamentos asignados en tu kit personal por la administración."
                binding.btnEmptySolicitarDotacion.visibility = View.VISIBLE
            } else {
                binding.tvEmptyTitulo.text = getString(R.string.lbl_no_se_encontraron_medicamentos)
                binding.tvEmptySubtitulo.text = getString(R.string.lbl_intenta_con_otro_termino_de)
                binding.btnEmptySolicitarDotacion.visibility = View.GONE
            }
        } else {
            binding.layoutEmptyMedicamentos.visibility = View.GONE
            binding.rvMedicamentos.visibility = View.VISIBLE
        }
    }

    /**
     * Sincroniza el maletín con el servidor.
     * Si [forzar] es false, se ejecuta silenciosamente sin mostrar el spinner visual de recarga.
     */
    private fun sincronizarMaletin(forzar: Boolean) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) {
            binding.swipeRefreshMaletin.isRefreshing = false
            return
        }

        if (forzar) {
            binding.swipeRefreshMaletin.isRefreshing = true
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // Sincronizar consumos pendientes offline si existen
            maletinManager.sincronizarConsumosPendientes(token)

            val resultado = maletinManager.sincronizarDesdeServidor(token)

            withContext(Dispatchers.Main) {
                if (forzar) {
                    binding.swipeRefreshMaletin.isRefreshing = false
                }

                resultado.onSuccess {
                    if (forzar) {
                        Toast.makeText(this@MaletinMedicamentosActivity, "Maletín sincronizado con farmacia CBA", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    if (forzar) {
                        MaterialAlertDialogBuilder(this@MaletinMedicamentosActivity)
                            .setIcon(R.drawable.warning_icon4)
                            .setTitle("Error de sincronización")
                            .setMessage("No se pudo conectar con el servidor: ${err.localizedMessage ?: err.message}.\n\nSe mantienen los datos locales disponibles offline.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun mostrarDetalleMedicamento(med: MedicamentoMaletin) {
        val mensaje = buildString {
            append("• Stock vigente disponible: ").append(med.saldoDisponibleInt).append(" ").append(med.unidadMedida).append("\n")
            if (med.cuotaAsignada != null) {
                append("• Cuota asignada de referencia: ").append(med.cuotaAsignada).append(" ").append(med.unidadMedida).append("\n")
            }
            if (med.saldoVencido > 0) {
                append("⚠️ Stock vencido (no apto): ").append(med.saldoVencido).append(" ").append(med.unidadMedida).append("\n")
            }
            append("• Presentación: ").append(med.presentacion).append("\n")
            append("• Vía recomendada: ").append(med.viaRecomendada).append("\n")
            append("• Categoría médica: ").append(med.categoria).append("\n\n")

            if (med.lotes.isNotEmpty()) {
                append("Lotes asignados en kit:\n")
                med.lotes.forEach { lote ->
                    val estadoLote = if (lote.vencido) "❌ VENCIDO" else "✅ Vigente"
                    append("  - Lote #").append(lote.idInventario)
                        .append(": ").append(lote.cantidadActual).append(" uds")
                        .append(" (Vence: ").append(lote.fechaVencimiento ?: "N/D").append(" - ").append(estadoLote).append(")\n")
                }
                append("\n")
            }

            append("Indicaciones clínicas:\n").append(med.descripcionUso.ifEmpty { "Uso clínico veterinario institucional CBA." })
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_pill)
            .setTitle(med.nombre)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun abrirPantallaSolicitudesKit() {
        startActivity(Intent(this, SolicitudesKitActivity::class.java))
    }
}
