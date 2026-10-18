package com.example.petvetericano

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityMaletinMedicamentosBinding
import com.example.petvetericano.models.MedicamentoMaletin
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaletinMedicamentosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMaletinMedicamentosBinding
    private lateinit var maletinManager: MaletinManager
    private lateinit var adapter: MedicamentoAdapter

    private var categoriaSeleccionada: String = "Todos"
    private var queryBusqueda: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMaletinMedicamentosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maletinManager = MaletinManager.getInstance(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarFiltros()
        configurarBuscador()
        actualizarDatosVista()
    }

    override fun onResume() {
        super.onResume()
        actualizarDatosVista()
    }

    private fun configurarToolbar() {
        binding.toolbarMaletin.setNavigationOnClickListener {
            finish()
        }

        binding.toolbarMaletin.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_recargar_maletin -> {
                    mostrarDialogoReiniciarMaletin()
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

    private fun configurarFiltros() {
        binding.chipGroupCategorias.setOnCheckedStateChangeListener { _, checkedIds ->
            categoriaSeleccionada = when (checkedIds.firstOrNull()) {
                R.id.chipCatAine -> "Analgésicos / AINE"
                R.id.chipCatAntibioticos -> "Antibióticos"
                R.id.chipCatAntiparasitarios -> "Antiparasitarios"
                R.id.chipCatFluidos -> "Fluidoterapia"
                R.id.chipCatCorticoides -> "Corticoides"
                else -> "Todos"
            }
            filtrarLista()
        }
    }

    private fun configurarBuscador() {
        binding.etBuscarMedicamento.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                queryBusqueda = s?.toString()?.trim() ?: ""
                filtrarLista()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun actualizarDatosVista() {
        val totalRefs = maletinManager.getTotalMedicamentosDisponibles()
        val totalTodas = maletinManager.getTotalReferencias()
        binding.tvTotalReferenciasHeader.text = "$totalRefs"
        binding.tvEstadoGeneralBadge.text = maletinManager.getEstadoStock()

        filtrarLista()
    }

    private fun filtrarLista() {
        val todos = maletinManager.getMedicamentos()
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
        } else {
            binding.layoutEmptyMedicamentos.visibility = View.GONE
            binding.rvMedicamentos.visibility = View.VISIBLE
        }
    }

    private fun mostrarDetalleMedicamento(med: MedicamentoMaletin) {
        val mensaje = buildString {
            append("• Concentración: ").append(med.concentracion).append("\n")
            append("• Presentación: ").append(med.presentacion).append("\n")
            append("• Vía sugerida: ").append(med.viaRecomendada).append("\n")
            append("• Stock actual: ").append(med.cantidadDisponible).append(" / ").append(med.cantidadInicial).append(" ").append(med.unidadMedida).append("\n")
            append("• Número de Lote: ").append(med.lote).append("\n")
            append("• Vencimiento: ").append(med.fechaVencimiento).append("\n\n")
            append("Indicaciones clínicas:\n").append(med.descripcionUso)
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_pill)
            .setTitle(med.nombre)
            .setMessage(mensaje)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun mostrarDialogoReiniciarMaletin() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_medical_bag)
            .setTitle("¿Restablecer inventario del maletín?")
            .setMessage("Esta opción recarga al 100% las existencias de todos los medicamentos asignados para continuar las pruebas en campo.")
            .setPositiveButton("Restablecer") { dialog, _ ->
                dialog.dismiss()
                maletinManager.reiniciarStock()
                actualizarDatosVista()
                Toast.makeText(this, "Stock del maletín restablecido con éxito", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
