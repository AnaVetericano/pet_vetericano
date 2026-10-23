package com.example.petvetericano

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivitySolicitudesKitBinding
import com.example.petvetericano.models.CrearSolicitudKitRequest
import com.example.petvetericano.models.ItemSolicitudInput
import com.example.petvetericano.models.SolicitudKitResponse
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OpcionMedicamentoSolicitud(
    val idMedicamento: Int,
    val nombre: String,
    val presentacion: String = "",
    val saldoDisponible: Int = 0
)

class SolicitudesKitActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySolicitudesKitBinding
    private lateinit var maletinManager: MaletinManager
    private lateinit var adapter: SolicitudesKitAdapter

    private var medicamentosDisponibles: List<OpcionMedicamentoSolicitud> = emptyList()
    private var medicamentoSeleccionado: OpcionMedicamentoSolicitud? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySolicitudesKitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maletinManager = MaletinManager.getInstance(this)

        configurarToolbar()
        configurarTabs()
        configurarRecyclerView()
        configurarFormularioNuevaSolicitud()

        cargarSolicitudes()
    }

    private fun configurarToolbar() {
        binding.toolbarSolicitudes.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarTabs() {
        binding.tabLayoutSolicitudes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.swipeRefreshSolicitudes.visibility = View.VISIBLE
                        binding.scrollNuevaSolicitud.visibility = View.GONE
                        cargarSolicitudes()
                    }
                    1 -> {
                        binding.swipeRefreshSolicitudes.visibility = View.GONE
                        binding.scrollNuevaSolicitud.visibility = View.VISIBLE
                        actualizarSelectorMedicamentos()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.swipeRefreshSolicitudes.setOnRefreshListener {
            cargarSolicitudes()
        }
    }

    private fun configurarRecyclerView() {
        adapter = SolicitudesKitAdapter(emptyList()) { solicitud ->
            confirmarCancelacionSolicitud(solicitud)
        }
        binding.rvSolicitudes.layoutManager = LinearLayoutManager(this)
        binding.rvSolicitudes.adapter = adapter
    }

    private fun configurarFormularioNuevaSolicitud() {
        // Botón 1: Reposición Total Automática
        binding.btnSolicitarReposicionTotal.setOnClickListener {
            val motivo = binding.etMotivoTotal.text.toString().trim()
            if (motivo.isEmpty()) {
                binding.tilMotivoTotal.error = "Ingresa un motivo para la reposición"
                return@setOnClickListener
            }
            binding.tilMotivoTotal.error = null

            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_sync)
                .setTitle("¿Solicitar reposición total?")
                .setMessage("El gestor de inventario recibirá tu petición para dotar todas las referencias que tengas por debajo de su cantidad asignada.")
                .setPositiveButton("Confirmar y enviar") { dialog, _ ->
                    dialog.dismiss()
                    enviarSolicitud(
                        CrearSolicitudKitRequest(
                            tipoSolicitud = "REPOSICION",
                            motivo = motivo,
                            esReposicionTotal = true,
                            items = null
                        )
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Botón 2: Solicitud Específica / Devolución
        binding.btnEnviarSolicitudPuntual.setOnClickListener {
            val med = medicamentoSeleccionado
            if (med == null) {
                binding.tilMedSolicitud.error = "Selecciona un medicamento"
                return@setOnClickListener
            }
            binding.tilMedSolicitud.error = null

            val cantidad = binding.etCantidadSolicitud.text.toString().toIntOrNull()
            if (cantidad == null || cantidad <= 0) {
                binding.tilCantidadSolicitud.error = "Ingresa una cantidad entera mayor a 0"
                return@setOnClickListener
            }
            binding.tilCantidadSolicitud.error = null

            val motivo = binding.etMotivoPuntual.text.toString().trim()
            if (motivo.isEmpty()) {
                binding.tilMotivoPuntual.error = "Ingresa el motivo de la solicitud"
                return@setOnClickListener
            }
            binding.tilMotivoPuntual.error = null

            val esReposicion = binding.chipTipoReposicion.isChecked
            val tipo = if (esReposicion) "REPOSICION" else "DEVOLUCION"

            if (tipo == "DEVOLUCION" && cantidad > med.saldoDisponible) {
                binding.tilCantidadSolicitud.error = "No puedes devolver más de lo que tienes en el maletín (${med.saldoDisponible} uds)"
                return@setOnClickListener
            }

            enviarSolicitud(
                CrearSolicitudKitRequest(
                    tipoSolicitud = tipo,
                    motivo = motivo,
                    esReposicionTotal = false,
                    items = listOf(
                        ItemSolicitudInput(
                            idMedicamento = med.idMedicamento,
                            cantidad = cantidad
                        )
                    )
                )
            )
        }
    }

    private fun actualizarSelectorMedicamentos() {
        val medsMaletin = maletinManager.getMedicamentos()
        val maletinMap = medsMaletin.associateBy { it.idMedicamentoInt }

        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

        lifecycleScope.launch(Dispatchers.IO) {
            var listaOpciones: List<OpcionMedicamentoSolicitud> = emptyList()

            if (token.isNotEmpty()) {
                try {
                    val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                    val resp = RetrofitClient.apiService.obtenerCatalogoMedicamentos(authHeader)
                    if (resp.isSuccessful && resp.body() != null) {
                        listaOpciones = resp.body()!!.filter { it.activo }.map { cat ->
                            val enMaletin = maletinMap[cat.idMedicamento]
                            OpcionMedicamentoSolicitud(
                                idMedicamento = cat.idMedicamento,
                                nombre = cat.nombre,
                                presentacion = cat.presentacion ?: "",
                                saldoDisponible = enMaletin?.saldoDisponibleInt ?: 0
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fallback local ante fallo de red
                }
            }

            if (listaOpciones.isEmpty()) {
                listaOpciones = medsMaletin.map { med ->
                    OpcionMedicamentoSolicitud(
                        idMedicamento = med.idMedicamentoInt,
                        nombre = med.nombre,
                        presentacion = med.presentacion,
                        saldoDisponible = med.saldoDisponibleInt
                    )
                }
            }

            withContext(Dispatchers.Main) {
                medicamentosDisponibles = listaOpciones
                val nombres = listaOpciones.map { item ->
                    if (item.saldoDisponible > 0) {
                        "${item.nombre} (En maletín: ${item.saldoDisponible})"
                    } else {
                        "${item.nombre} (Sin existencias en maletín)"
                    }
                }
                val arrayAdapter = ArrayAdapter(this@SolicitudesKitActivity, android.R.layout.simple_dropdown_item_1line, nombres)
                binding.autoCompleteMedSolicitud.setAdapter(arrayAdapter)

                binding.autoCompleteMedSolicitud.setOnItemClickListener { _, _, position, _ ->
                    medicamentoSeleccionado = medicamentosDisponibles.getOrNull(position)
                }
            }
        }
    }

    private fun cargarSolicitudes() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) {
            binding.swipeRefreshSolicitudes.isRefreshing = false
            Toast.makeText(this, "Sesión no válida para consultar solicitudes", Toast.LENGTH_SHORT).show()
            return
        }

        binding.swipeRefreshSolicitudes.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = RetrofitClient.apiService.listarSolicitudesKit(authHeader)

                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    if (response.isSuccessful && response.body() != null) {
                        val lista = response.body()!!.results
                        adapter.updateList(lista)

                        if (lista.isEmpty()) {
                            binding.layoutEmptySolicitudes.visibility = View.VISIBLE
                            binding.rvSolicitudes.visibility = View.GONE
                        } else {
                            binding.layoutEmptySolicitudes.visibility = View.GONE
                            binding.rvSolicitudes.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(this@SolicitudesKitActivity, "No se pudieron obtener las solicitudes (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    Toast.makeText(this@SolicitudesKitActivity, "Error de red: ${e.localizedMessage ?: e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enviarSolicitud(request: CrearSolicitudKitRequest) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) {
            Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show()
            return
        }

        binding.swipeRefreshSolicitudes.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = RetrofitClient.apiService.crearSolicitudKit(authHeader, request)

                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        MaterialAlertDialogBuilder(this@SolicitudesKitActivity)
                            .setIcon(R.drawable.ic_check)
                            .setTitle("Solicitud registrada con éxito")
                            .setMessage("Tu solicitud #${body.idSolicitud} ha sido radicada ante el Gestor de Farmacia con estado PENDIENTE.")
                            .setPositiveButton("Ver mis solicitudes") { _, _ ->
                                binding.tabLayoutSolicitudes.getTabAt(0)?.select()
                            }
                            .show()
                    } else {
                        val errStr = response.errorBody()?.string() ?: ""
                        MaterialAlertDialogBuilder(this@SolicitudesKitActivity)
                            .setIcon(R.drawable.warning_icon4)
                            .setTitle("No se pudo registrar la solicitud")
                            .setMessage("El servidor respondió: $errStr")
                            .setPositiveButton("Entendido", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    Toast.makeText(this@SolicitudesKitActivity, "Error al enviar: ${e.localizedMessage ?: e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarCancelacionSolicitud(solicitud: SolicitudKitResponse) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.warning_icon4)
            .setTitle("¿Cancelar solicitud #${solicitud.idSolicitud}?")
            .setMessage("Esta acción retirará la solicitud de la bandeja del gestor.")
            .setPositiveButton("Sí, cancelar") { dialog, _ ->
                dialog.dismiss()
                ejecutarCancelacion(solicitud.idSolicitud)
            }
            .setNegativeButton("Conservar", null)
            .show()
    }

    private fun ejecutarCancelacion(idSolicitud: Int) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        binding.swipeRefreshSolicitudes.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = RetrofitClient.apiService.cancelarSolicitudKit(authHeader, idSolicitud)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SolicitudesKitActivity, "Solicitud cancelada con éxito", Toast.LENGTH_SHORT).show()
                        cargarSolicitudes()
                    } else {
                        binding.swipeRefreshSolicitudes.isRefreshing = false
                        Toast.makeText(this@SolicitudesKitActivity, "No se pudo cancelar la solicitud (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    Toast.makeText(this@SolicitudesKitActivity, "Error al cancelar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
