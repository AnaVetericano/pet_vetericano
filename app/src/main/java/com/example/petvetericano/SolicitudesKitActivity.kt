package com.example.petvetericano

import android.graphics.Color
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
import com.example.petvetericano.models.SolicitudKitOffline
import com.example.petvetericano.models.SolicitudKitResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.offline.NetworkUtils
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

    // Control de solicitudes en trámite para evitar colisiones SOLICITUD_DUPLICADA
    private var solicitudesPendientes: List<SolicitudKitResponse> = emptyList()
    private var medicamentosEnTramiteIds: Set<Int> = emptySet()
    private var mapaMedTramiteInfo: Map<Int, Pair<Int, Int>> = emptyMap() // idMedicamento -> (idSolicitud, cantidad)

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
                        prepararFormularioNuevaSolicitud()
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

    private fun prepararFormularioNuevaSolicitud() {
        val medsRepo = maletinManager.getMedicamentosParaReposicion()
        val enTramite = medsRepo.filter { it.idMedicamentoInt in medicamentosEnTramiteIds }
        val nuevosFaltantes = medsRepo.filter { it.idMedicamentoInt !in medicamentosEnTramiteIds }

        when {
            nuevosFaltantes.isNotEmpty() -> {
                binding.cardSugeridosContainer.visibility = View.VISIBLE
                binding.cardSugeridosContainer.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
                binding.cardSugeridosContainer.strokeColor = Color.parseColor("#FDE68A")
                binding.tvSugeridosTitulo.text = "⚠️ ${nuevosFaltantes.size} medicamento(s) requieren radicar reposición:"
                binding.tvSugeridosTitulo.setTextColor(Color.parseColor("#92400E"))

                val sb = StringBuilder()
                sb.append("Fármacos a solicitar para completar cuota física:\n")
                nuevosFaltantes.forEach {
                    val cuota = it.cuotaAsignada ?: it.cantidadInicial.toInt()
                    sb.append("• ${it.nombre}: Stock actual ${it.saldoDisponibleInt} / Cuota $cuota (Faltan: ${it.unidadesFaltantesParaCuota} ${it.unidadMedida})\n")
                }

                if (enTramite.isNotEmpty()) {
                    sb.append("\n⏳ Ya en trámite ante Farmacia CBA (${enTramite.size} fármaco(s)):\n")
                    enTramite.forEach {
                        val tramite = mapaMedTramiteInfo[it.idMedicamentoInt]
                        val numSol = tramite?.first?.let { id -> "Solicitud #$id" } ?: "En trámite"
                        val cantSol = tramite?.second?.let { cant -> "$cant uds pedidas" } ?: ""
                        sb.append("• ${it.nombre}: $numSol ($cantSol, pendiente de entrega)\n")
                    }
                }

                binding.tvSugeridosDetalle.text = sb.toString().trimEnd()
                binding.tvSugeridosDetalle.setTextColor(Color.parseColor("#78350F"))
                binding.btnSolicitarReposicionTotal.text = "Radicar Reposición (${nuevosFaltantes.size} fármaco${if (nuevosFaltantes.size > 1) "s" else ""})"
                binding.btnSolicitarReposicionTotal.isEnabled = true
                binding.tilMotivoTotal.helperText = "${nuevosFaltantes.size} medicamento(s) listos para radicar reposición."
                if (binding.etMotivoTotal.text.isNullOrBlank()) {
                    binding.etMotivoTotal.setText("Reposición de existencias para completar cuota de maletín de campo CBA.")
                }
            }

            enTramite.isNotEmpty() -> {
                binding.cardSugeridosContainer.visibility = View.VISIBLE
                binding.cardSugeridosContainer.setCardBackgroundColor(Color.parseColor("#EFF6FF"))
                binding.cardSugeridosContainer.strokeColor = Color.parseColor("#BFDBFE")
                binding.tvSugeridosTitulo.text = "⏳ Reposición en trámite ante Farmacia CBA"
                binding.tvSugeridosTitulo.setTextColor(Color.parseColor("#1E40AF"))

                val sb = StringBuilder()
                sb.append("Los medicamentos con stock bajo ya cuentan con una solicitud radicada pendiente de entrega:\n\n")
                enTramite.forEach {
                    val tramite = mapaMedTramiteInfo[it.idMedicamentoInt]
                    val numSol = tramite?.first?.let { id -> "#$id" } ?: ""
                    val cantSol = tramite?.second?.let { cant -> "$cant uds" } ?: ""
                    sb.append("• ${it.nombre}: Solicitud $numSol ($cantSol) en estado PENDIENTE\n")
                }
                sb.append("\nTan pronto el gestor de farmacia despache la entrega, tu maletín se actualizará automáticamente.")

                binding.tvSugeridosDetalle.text = sb.toString().trimEnd()
                binding.tvSugeridosDetalle.setTextColor(Color.parseColor("#1E3A8A"))
                val primeraSol = enTramite.firstNotNullOfOrNull { mapaMedTramiteInfo[it.idMedicamentoInt]?.first }
                binding.btnSolicitarReposicionTotal.text = if (primeraSol != null) "Reposición en trámite (Solicitud #$primeraSol)" else "Reposición ya en trámite"
                binding.btnSolicitarReposicionTotal.isEnabled = false
                binding.tilMotivoTotal.helperText = "Ya tienes solicitudes en curso para cubrir las cuotas faltantes."
            }

            else -> {
                binding.cardSugeridosContainer.visibility = View.VISIBLE
                binding.cardSugeridosContainer.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
                binding.cardSugeridosContainer.strokeColor = Color.parseColor("#A7F3D0")
                binding.tvSugeridosTitulo.text = "✅ Cuotas al día"
                binding.tvSugeridosTitulo.setTextColor(Color.parseColor("#065F46"))
                binding.tvSugeridosDetalle.text = "Todas las referencias de tu maletín cumplen actualmente su cuota física asignada."
                binding.tvSugeridosDetalle.setTextColor(Color.parseColor("#047857"))
                binding.btnSolicitarReposicionTotal.text = "Maletín con cuota completa"
                binding.btnSolicitarReposicionTotal.isEnabled = false
                binding.tilMotivoTotal.helperText = "Tu maletín tiene actualmente todas sus cuotas completas."
                if (binding.etMotivoTotal.text.isNullOrBlank()) {
                    binding.etMotivoTotal.setText("Reposición preventiva de stock.")
                }
            }
        }

        if (binding.etMotivoPuntual.text.isNullOrBlank()) {
            binding.etMotivoPuntual.setText("Reposición de medicamento para atención de campo.")
        }
    }

    private fun configurarFormularioNuevaSolicitud() {
        // Validación reactiva al cambiar tipo de solicitud puntual
        binding.chipTipoReposicion.setOnCheckedChangeListener { _, _ -> validarMedicamentoSeleccionado() }
        binding.chipTipoDevolucion.setOnCheckedChangeListener { _, _ -> validarMedicamentoSeleccionado() }

        // Botón 1: Reposición Focalizada de Cuotas Faltantes (evita medicamentos ya en trámite)
        binding.btnSolicitarReposicionTotal.setOnClickListener {
            val motivo = binding.etMotivoTotal.text.toString().trim()
            if (motivo.isEmpty()) {
                binding.tilMotivoTotal.error = "Ingresa un motivo para la reposición"
                return@setOnClickListener
            }
            binding.tilMotivoTotal.error = null

            val medsFaltantes = maletinManager.getMedicamentosParaReposicion()
            val nuevosFaltantes = medsFaltantes.filter { it.idMedicamentoInt !in medicamentosEnTramiteIds }

            if (nuevosFaltantes.isEmpty()) {
                Toast.makeText(this, "Todos los medicamentos bajo cuota ya tienen una solicitud en trámite", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val resumenFaltantes = nuevosFaltantes.joinToString("\n") {
                val cuota = it.cuotaAsignada ?: it.cantidadInicial.toInt()
                "• ${it.nombre}: stock actual ${it.saldoDisponibleInt} / cuota $cuota (Faltan: ${it.unidadesFaltantesParaCuota})"
            }

            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_sync)
                .setTitle("¿Solicitar reposición de cuotas?")
                .setMessage("El gestor de Farmacia CBA recibirá tu petición para abastecer las referencias con stock faltante:\n\n$resumenFaltantes")
                .setPositiveButton("Confirmar y enviar") { dialog, _ ->
                    dialog.dismiss()
                    val itemsInput = nuevosFaltantes.map {
                        ItemSolicitudInput(
                            idMedicamento = it.idMedicamentoInt,
                            cantidad = it.unidadesFaltantesParaCuota.coerceAtLeast(1)
                        )
                    }
                    enviarSolicitud(
                        CrearSolicitudKitRequest(
                            tipoSolicitud = "REPOSICION",
                            motivo = motivo,
                            esReposicionTotal = false,
                            items = itemsInput
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

            val esReposicion = binding.chipTipoReposicion.isChecked
            val tipo = if (esReposicion) "REPOSICION" else "DEVOLUCION"

            if (esReposicion && medicamentosEnTramiteIds.contains(med.idMedicamento)) {
                val tramite = mapaMedTramiteInfo[med.idMedicamento]
                binding.tilMedSolicitud.error = "⏳ Ya existe la Solicitud #${tramite?.first} en trámite para este fármaco."
                return@setOnClickListener
            }

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

    private fun validarMedicamentoSeleccionado() {
        val med = medicamentoSeleccionado ?: return
        val tramite = mapaMedTramiteInfo[med.idMedicamento]
        val esReposicion = binding.chipTipoReposicion.isChecked

        if (esReposicion && tramite != null) {
            val (solId, cant) = tramite
            binding.tilMedSolicitud.error = "⏳ Ya existe la Solicitud #$solId en trámite ($cant uds) para este fármaco."
            binding.btnEnviarSolicitudPuntual.isEnabled = false
        } else {
            binding.tilMedSolicitud.error = null
            binding.btnEnviarSolicitudPuntual.isEnabled = true
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
                    val medMal = maletinMap[item.idMedicamento]
                    val cuota = medMal?.cuotaAsignada ?: medMal?.cantidadInicial?.toInt()
                    val tramite = mapaMedTramiteInfo[item.idMedicamento]
                    val tramiteTag = if (tramite != null) " [⏳ En trámite #${tramite.first}]" else ""
                    when {
                        cuota != null -> "${item.nombre}$tramiteTag (En maletín: ${item.saldoDisponible} / cuota $cuota)"
                        item.saldoDisponible > 0 -> "${item.nombre}$tramiteTag (En maletín: ${item.saldoDisponible})"
                        else -> "${item.nombre}$tramiteTag (Sin existencias en maletín)"
                    }
                }
                val arrayAdapter = ArrayAdapter(this@SolicitudesKitActivity, android.R.layout.simple_dropdown_item_1line, nombres)
                binding.autoCompleteMedSolicitud.setAdapter(arrayAdapter)

                binding.autoCompleteMedSolicitud.setOnItemClickListener { _, _, position, _ ->
                    val med = medicamentosDisponibles.getOrNull(position)
                    medicamentoSeleccionado = med
                    if (med != null) {
                        val medMal = maletinMap[med.idMedicamento]
                        val cuota = medMal?.cuotaAsignada ?: medMal?.cantidadInicial?.toInt()
                        if (medMal != null && cuota != null) {
                            val faltante = medMal.unidadesFaltantesParaCuota
                            if (faltante > 0) {
                                binding.etCantidadSolicitud.setText(faltante.toString())
                                binding.tilCantidadSolicitud.helperText = "Stock: ${medMal.saldoDisponibleInt} | Cuota: $cuota | Faltan para completar: $faltante ${medMal.unidadMedida}"
                            } else {
                                binding.etCantidadSolicitud.setText("1")
                                binding.tilCantidadSolicitud.helperText = "Stock: ${medMal.saldoDisponibleInt} (Cuota asignada completa)"
                            }
                        } else {
                            binding.tilCantidadSolicitud.helperText = null
                        }
                        validarMedicamentoSeleccionado()
                    }
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
            val hayRed = NetworkUtils.hayRedDisponible(this@SolicitudesKitActivity)

            // 1. Si hay red, sincronizar automáticamente solicitudes en cola offline
            if (hayRed) {
                try {
                    val enviadas = maletinManager.sincronizarSolicitudesOffline(token)
                    if (enviadas > 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@SolicitudesKitActivity,
                                "🚀 Se radicaron $enviadas solicitud(es) que estaban en cola offline",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Traer solicitudes del servidor si hay red
            var listaServidor: List<SolicitudKitResponse> = emptyList()
            if (hayRed) {
                try {
                    val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                    val response = RetrofitClient.apiService.listarSolicitudesKit(authHeader)
                    if (response.isSuccessful && response.body() != null) {
                        listaServidor = response.body()!!.results
                    }
                } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                binding.swipeRefreshSolicitudes.isRefreshing = false

                // 3. Mapear solicitudes que esperan red en cola offline local
                val colaOffline = maletinManager.getSolicitudesOffline()
                val offlineComoResponse = colaOffline.map { it.toSolicitudKitResponse() }

                // Lista unificada: en cola offline primero + servidor
                val listaTotal = offlineComoResponse + listaServidor
                adapter.updateList(listaTotal)

                // Identificar solicitudes PENDIENTE de REPOSICION para controlar medicamentos en trámite
                solicitudesPendientes = listaServidor.filter {
                    it.estado.equals("PENDIENTE", ignoreCase = true) &&
                    it.tipoSolicitud.equals("REPOSICION", ignoreCase = true)
                }

                val mapaTramite = mutableMapOf<Int, Pair<Int, Int>>()
                // Del servidor
                for (sol in solicitudesPendientes) {
                    for (det in sol.detalles) {
                        if (!mapaTramite.containsKey(det.idMedicamento)) {
                            mapaTramite[det.idMedicamento] = Pair(sol.idSolicitud, det.cantidadSolicitada)
                        }
                    }
                }
                // De la cola offline local
                for (off in colaOffline) {
                    for (item in off.items) {
                        if (!mapaTramite.containsKey(item.idMedicamento)) {
                            mapaTramite[item.idMedicamento] = Pair(-1, item.cantidad)
                        }
                    }
                }
                mapaMedTramiteInfo = mapaTramite
                medicamentosEnTramiteIds = mapaTramite.keys

                if (listaTotal.isEmpty()) {
                    binding.layoutEmptySolicitudes.visibility = View.VISIBLE
                    binding.rvSolicitudes.visibility = View.GONE
                } else {
                    binding.layoutEmptySolicitudes.visibility = View.GONE
                    binding.rvSolicitudes.visibility = View.VISIBLE
                }

                if (binding.tabLayoutSolicitudes.selectedTabPosition == 1) {
                    prepararFormularioNuevaSolicitud()
                    actualizarSelectorMedicamentos()
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

        // Si no hay red, guardar directamente en la cola offline del teléfono
        if (!NetworkUtils.hayRedDisponible(this)) {
            encolarSolicitudOfflineLocal(request)
            return
        }

        binding.swipeRefreshSolicitudes.isRefreshing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = RetrofitClient.apiService.crearSolicitudKit(authHeader, request)

                // Sincronizar maletín en segundo plano para que el StateFlow emita de inmediato
                maletinManager.sincronizarDesdeServidor(token)

                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        MaterialAlertDialogBuilder(this@SolicitudesKitActivity)
                            .setIcon(R.drawable.ic_check)
                            .setTitle("Solicitud registrada con éxito")
                            .setMessage("Tu solicitud #${body.idSolicitud} ha sido radicada ante el Gestor de Farmacia CBA con estado PENDIENTE.")
                            .setPositiveButton("Ver mis solicitudes") { _, _ ->
                                binding.tabLayoutSolicitudes.getTabAt(0)?.select()
                            }
                            .show()

                        cargarSolicitudes()
                    } else {
                        val errStr = response.errorBody()?.string() ?: ""
                        var titulo = "No se pudo registrar la solicitud"
                        var detalle = errStr
                        var esDuplicada = false

                        try {
                            val json = org.json.JSONObject(errStr)
                            if (json.optString("error") == "SOLICITUD_DUPLICADA") {
                                esDuplicada = true
                                titulo = "Solicitud ya en trámite"
                            }
                            detalle = json.optString("detalle", errStr)
                        } catch (_: Exception) {}

                        val builder = MaterialAlertDialogBuilder(this@SolicitudesKitActivity)
                            .setIcon(R.drawable.warning_icon4)
                            .setTitle(titulo)
                            .setMessage(detalle)

                        if (esDuplicada) {
                            builder.setPositiveButton("Ver mis solicitudes") { _, _ ->
                                binding.tabLayoutSolicitudes.getTabAt(0)?.select()
                            }
                            builder.setNegativeButton("Cerrar", null)
                            cargarSolicitudes()
                        } else {
                            builder.setPositiveButton("Entendido", null)
                        }
                        builder.show()
                    }
                }
            } catch (e: Exception) {
                // Si la red falló durante el envío, guardar en cola offline
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshSolicitudes.isRefreshing = false
                    encolarSolicitudOfflineLocal(request)
                }
            }
        }
    }

    private fun encolarSolicitudOfflineLocal(request: CrearSolicitudKitRequest) {
        val medsMap = maletinManager.getMedicamentos().associateBy { it.idMedicamentoInt }
        val nombres = request.items?.map { item ->
            val nombreMed = medsMap[item.idMedicamento]?.nombre ?: "Medicamento #${item.idMedicamento}"
            "• $nombreMed: ${item.cantidad} uds"
        } ?: emptyList()

        val offlineItem = SolicitudKitOffline(
            tipoSolicitud = request.tipoSolicitud,
            motivo = request.motivo,
            items = request.items ?: emptyList(),
            nombresItems = nombres,
            esReposicionTotal = request.esReposicionTotal
        )
        maletinManager.encolarSolicitudOffline(offlineItem)

        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_sync)
            .setTitle("📱 Solicitud guardada sin conexión")
            .setMessage("No hay conexión a internet en este momento.\n\nTu solicitud ha quedado guardada en la bandeja de salida de tu teléfono y se enviará automáticamente a Farmacia CBA apenas recuperes señal.")
            .setPositiveButton("Ver mis solicitudes") { _, _ ->
                binding.tabLayoutSolicitudes.getTabAt(0)?.select()
                cargarSolicitudes()
            }
            .setNegativeButton("Entendido") { _, _ ->
                cargarSolicitudes()
            }
            .show()
    }

    private fun confirmarCancelacionSolicitud(solicitud: SolicitudKitResponse) {
        if (solicitud.estado.equals("EN_COLA_OFFLINE", ignoreCase = true)) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.warning_icon4)
                .setTitle("¿Quitar de la cola offline?")
                .setMessage("Esta solicitud aún no se ha enviado al servidor. Si la quitas, no se radicará.")
                .setPositiveButton("Sí, quitar de la cola") { dialog, _ ->
                    dialog.dismiss()
                    val cola = maletinManager.getSolicitudesOffline()
                    val target = cola.firstOrNull { it.motivo == solicitud.motivoSolicitud } ?: cola.firstOrNull()
                    if (target != null) {
                        maletinManager.removerSolicitudOffline(target.clientUuid)
                    }
                    Toast.makeText(this, "Solicitud retirada de la cola offline", Toast.LENGTH_SHORT).show()
                    cargarSolicitudes()
                }
                .setNegativeButton("Conservar", null)
                .show()
            return
        }

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
