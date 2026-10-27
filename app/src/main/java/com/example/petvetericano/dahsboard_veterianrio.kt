package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityDahsboardVeterianrioBinding
import com.example.petvetericano.offline.ActaColaBottomSheet
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import com.example.petvetericano.models.PeticionPendiente
import com.example.petvetericano.models.TipoEstado
import com.example.petvetericano.network.RetrofitClient
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class dahsboard_veterianrio : AppCompatActivity() {
    private lateinit var binding: ActivityDahsboardVeterianrioBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDahsboardVeterianrioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.rvPendientes.layoutManager = LinearLayoutManager(this)

        // Configurar el gesto de recargar (Swipe to Refresh)
        binding.swipeRefreshLayout.setOnRefreshListener {
            actualizarDatosUsuario()
            fetchDashboardData()
            sincronizarPerfilUsuario()
            sincronizarKitDashboard()
            actualizarBadgeCola()
        }

        // Cargar nombre e iniciales del veterinario inmediatamente
        actualizarDatosUsuario()
        configurarBotonModoOscuro()

        // Avatar y nombre clicables para acceder a opciones de editar perfil
        binding.tvInicialesAvatar.setOnClickListener {
            startActivity(Intent(this, OpcionesEditarPerfilActivity::class.java))
        }
        binding.tvNombreUsuario.setOnClickListener {
            startActivity(Intent(this, OpcionesEditarPerfilActivity::class.java))
        }

        // Botones de navegación de acciones
        binding.btnPeticiones.setOnClickListener {
            startActivity(Intent(this, PetitionsListActivity::class.java))
        }

        // Tarjeta de Maletín de Medicamentos
        binding.cardMaletinMedicamentos.setOnClickListener {
            startActivity(Intent(this, MaletinMedicamentosActivity::class.java))
        }

        // Bottom Navigation
        binding.ivNavInicio.setOnClickListener {
            // Ya estamos en inicio, podemos hacer que suba el scroll al inicio
            binding.rvPendientes.smoothScrollToPosition(0)
        }

        binding.ivNavPeticiones.setOnClickListener {
            val intent = Intent(this, PetitionsListActivity::class.java)
            startActivity(intent)
        }

        binding.ivNavPerfil.setOnClickListener {
            startActivity(Intent(this, OpcionesEditarPerfilActivity::class.java))
        }

        // Cola de actas offline
        binding.btnActaCola.setOnClickListener {
            if (!supportFragmentManager.isStateSaved &&
                supportFragmentManager.findFragmentByTag(ActaColaBottomSheet.TAG) == null
            ) {
                val prefs = SharedPreferencesManager(this)
                val idVet = prefs.getUserId()
                ActaColaBottomSheet.newInstance(idVet).show(
                    supportFragmentManager,
                    ActaColaBottomSheet.TAG
                )
            }
        }

        supportFragmentManager.setFragmentResultListener(
            ActaColaBottomSheet.RESULT_KEY,
            this
        ) { _, _ ->
            actualizarBadgeCola()
        }

        if (savedInstanceState == null) {
            val prefs = SharedPreferencesManager(this)
            val idVet = prefs.getUserId()
            if (idVet > 0) {
                lifecycleScope.launch(Dispatchers.IO) {
                    ActaSyncScheduler.reactivarObsoletas(this@dahsboard_veterianrio, idVet)
                    withContext(Dispatchers.Main) {
                        actualizarBadgeCola()
                    }
                }
            }
        }

        // Mostrar animación de carga inicial
        binding.swipeRefreshLayout.isRefreshing = true
        fetchDashboardData()
        sincronizarPerfilUsuario()
        actualizarResumenMaletin()
    }

    override fun onResume() {
        super.onResume()
        // Cuando vuelve de editar perfil o de otra pantalla actualiza inmediatamente el nombre
        actualizarDatosUsuario()
        fetchDashboardData()
        sincronizarPerfilUsuario()
        actualizarResumenMaletin()
        sincronizarKitDashboard()
        actualizarBadgeCola()
    }

    private fun actualizarBadgeCola() {
        val prefs = SharedPreferencesManager(this)
        val idVet = prefs.getUserId()
        if (idVet <= 0) {
            binding.actaColaContainer.visibility = View.GONE
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val totalEnCola = ActaLocalRepository.getInstance(this@dahsboard_veterianrio).contarEnCola(idVet)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                if (totalEnCola > 0) {
                    binding.actaColaContainer.visibility = View.VISIBLE
                    binding.tvActaColaBadge.text = if (totalEnCola > 99) {
                        getString(R.string.acta_offline_cola_badge_max)
                    } else {
                        totalEnCola.toString()
                    }
                    binding.btnActaCola.contentDescription = getString(
                        R.string.acta_offline_cola_icono_desc,
                        totalEnCola
                    )
                } else {
                    binding.actaColaContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun actualizarResumenMaletin() {
        val maletin = MaletinManager.getInstance(this)
        binding.tvTotalMedicamentos.text = maletin.getTotalMedicamentosDisponibles().toString()
        binding.tvEstadoMaletin.text = maletin.getEstadoStock()
    }

    private fun sincronizarKitDashboard() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            MaletinManager.getInstance(this@dahsboard_veterianrio).sincronizarDesdeServidor(token)
            withContext(Dispatchers.Main) {
                actualizarResumenMaletin()
            }
        }
    }

    private fun actualizarDatosUsuario() {
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val saludo = when (hora) {
            in 5..11 -> "Buenos días,"
            in 12..18 -> "Buenas tardes,"
            else -> "Buenas noches,"
        }
        binding.tvSaludo.text = saludo

        val prefs = SharedPreferencesManager(this)
        val nombre = prefs.getUserName().trim()
        val apellido = prefs.getUserLastName().trim()
        val nombreCompleto = if (apellido.isNotEmpty() && !nombre.contains(apellido)) "$nombre $apellido" else nombre
        binding.tvNombreUsuario.text = nombreCompleto.ifEmpty { "Veterinario" }

        val iniciales = buildString {
            if (nombre.isNotEmpty()) append(nombre.first().uppercaseChar())
            if (apellido.isNotEmpty()) append(apellido.first().uppercaseChar())
            else if (nombre.length > 1) append(nombre[1].uppercaseChar())
        }
        binding.tvInicialesAvatar.text = iniciales.ifEmpty { "V" }
    }

    private fun sincronizarPerfilUsuario() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        RetrofitClient.authToken = token
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.obtenerPerfil()
                if (response.isSuccessful && response.body() != null) {
                    val perfil = response.body()!!
                    val nuevoNombre = perfil.nombre?.trim() ?: ""
                    val nuevoApellido = perfil.apellido?.trim() ?: ""
                    val nuevoEmail = perfil.email?.trim() ?: ""
                    val nuevoTelefono = perfil.telefono?.trim() ?: ""
                    val nuevaIdentificacion = perfil.identificacion?.trim() ?: ""
                    val nuevaMatricula = perfil.matriculaProfesional?.trim() ?: ""

                    if (nuevoNombre.isNotEmpty()) {
                        prefs.saveUserData(
                            nuevoNombre,
                            nuevoEmail.ifEmpty { prefs.getUserEmail() },
                            nuevoTelefono.ifEmpty { prefs.getUserPhone() }
                        )
                    }
                    if (nuevoApellido.isNotEmpty()) {
                        prefs.saveUserLastName(nuevoApellido)
                    }
                    if (nuevaIdentificacion.isNotEmpty()) {
                        prefs.saveUserIdentification(nuevaIdentificacion)
                    }
                    if (nuevaMatricula.isNotEmpty()) {
                        prefs.saveUserMatriculaProfesional(nuevaMatricula)
                    }
                    prefs.saveProfileImagePath(perfil.fotoPerfil ?: "")

                    withContext(Dispatchers.Main) {
                        actualizarDatosUsuario()
                    }
                }
            } catch (e: Exception) {
                // Silencioso: fallback a SharedPreferences
            }
        }
    }

    private fun fetchDashboardData() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

        if (token.isEmpty()) {
            Toast.makeText(this, "Debe iniciar sesión", Toast.LENGTH_SHORT).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                withContext(Dispatchers.Main) {
                    // Detener la animación de recarga
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (response.isSuccessful && response.body() != null) {
                        val petitions = response.body()!!

                        val asignadas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("asignad") || st.contains("evaluaci") || st.contains("urgente") || st.contains("pendiente") || st.contains("borrador")
                        }
                        val enTratamiento = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            (st.contains("proceso") || st.contains("tratamiento") || st.contains("observaci") || st.contains("seguimiento") || st.contains("reasignad")) && !st.contains("transferid")
                        }
                        val atendidas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("atendida") || st.contains("finaliz") || st.contains("alta") || st.contains("fallecid") || st.contains("resuelt")
                        }
                        val transferidas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("transferid")
                        }
                        val urgentes = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("urgente")
                        }
                        val total = petitions.size

                        binding.tvGraficoAsignadas.text = asignadas.toString()
                        binding.tvGraficoReasignadas.text = enTratamiento.toString()
                        binding.tvGraficoAtendidas.text = atendidas.toString()
                        binding.tvGraficoTransferidas.text = transferidas.toString()

                        // Tarjeta de Maletín de Medicamentos (Listo para conectar cuando esté la API)
                        // binding.tvTotalMedicamentos.text = totalMedicamentos.toString()

                        configurarGrafico(asignadas, enTratamiento, atendidas, transferidas, total)

                        // 1. Filtrar ÚNICAMENTE las peticiones PENDIENTES (excluir las ya trabajadas o con acta)
                        val pendientes = petitions.filter { pet ->
                            val st = pet.estado?.lowercase() ?: ""
                            val yaTrabajada = (pet.tiene_acta == true) ||
                                (pet.id_seguimiento != null && pet.id_seguimiento > 0) ||
                                st.contains("tratamient") ||
                                st.contains("observaci") ||
                                st.contains("atendid") ||
                                st.contains("finaliz") ||
                                st.contains("alta") ||
                                st.contains("fallecid") ||
                                st.contains("resuelt") ||
                                st.contains("cerrad") ||
                                st.contains("transferid") ||
                                st.contains("proceso") ||
                                st.contains("seguimiento")
                            !yaTrabajada
                        }

                        // Ordenar: urgentes primero
                        val pendientesOrdenadas = pendientes.sortedByDescending { pet ->
                            val st = pet.estado?.lowercase() ?: ""
                            val pr = pet.prioridad?.lowercase() ?: ""
                            st.contains("urgent") || pr.contains("urgent")
                        }

                        val urgentesPendientes = pendientes.count {
                            val st = it.estado?.lowercase() ?: ""
                            val pr = it.prioridad?.lowercase() ?: ""
                            st.contains("urgent") || pr.contains("urgent")
                        }

                        if (urgentesPendientes > 0) {
                            binding.tvCantidadUrgentes.text = "$urgentesPendientes urgentes"
                            binding.tvCantidadUrgentes.backgroundTintList = ContextCompat.getColorStateList(this@dahsboard_veterianrio, R.color.badge_urgente_bg)
                            binding.tvCantidadUrgentes.setTextColor(ContextCompat.getColor(this@dahsboard_veterianrio, R.color.badge_urgente_text))
                        } else {
                            binding.tvCantidadUrgentes.text = "${pendientes.size} pendientes"
                            binding.tvCantidadUrgentes.backgroundTintList = ContextCompat.getColorStateList(this@dahsboard_veterianrio, R.color.badge_asignada_bg)
                            binding.tvCantidadUrgentes.setTextColor(ContextCompat.getColor(this@dahsboard_veterianrio, R.color.badge_asignada_text))
                        }

                        val listaParaRvActivas = pendientesOrdenadas.map { pet ->
                            val estadoStr = pet.estado?.lowercase() ?: ""
                            val prioridadStr = pet.prioridad?.lowercase() ?: ""
                            val esUrgente = estadoStr.contains("urgent") || prioridadStr.contains("urgent")
                            val tipo = if (esUrgente) TipoEstado.URGENTE else TipoEstado.ASIGNADA

                            PeticionPendiente(
                                id_peticion = pet.id_peticion,
                                titulo = pet.tipo ?: "Desconocido",
                                paciente = "${pet.numero_radicado ?: ""} — ${pet.ubicacion_direccion ?: "Sin dirección"}",
                                estado = if (esUrgente && !estadoStr.contains("urgent")) "Urgente (${pet.estado ?: "Asignada"})" else (pet.estado ?: "Asignada"),
                                tipoEstado = tipo
                            )
                        }

                        if (listaParaRvActivas.isEmpty()) {
                            binding.rvPendientes.visibility = View.GONE
                            binding.layoutEmptyPendientes.visibility = View.VISIBLE
                        } else {
                            binding.rvPendientes.visibility = View.VISIBLE
                            binding.layoutEmptyPendientes.visibility = View.GONE
                        }

                        binding.rvPendientes.adapter = PendientesAdapter(listaParaRvActivas) { item ->
                            if (item.id_peticion > 0) {
                                val intent = Intent(this@dahsboard_veterianrio, DetallePeticion::class.java).apply {
                                    putExtra("PETICION_ID", item.id_peticion)
                                }
                                startActivity(intent)
                            }
                        }

                        // 2. Peticiones transferidas a otro centro (sección separada)
                        val listaTransferidasRaw = petitions.filter {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("transferid")
                        }

                        if (listaTransferidasRaw.isNotEmpty()) {
                            binding.cardTransferidas.visibility = android.view.View.VISIBLE
                            binding.tvCantidadTransferidas.text = "${listaTransferidasRaw.size} transferido(s)"

                            val listaTransferidasRv = listaTransferidasRaw.take(5).map { pet ->
                                PeticionPendiente(
                                    id_peticion = pet.id_peticion,
                                    titulo = pet.tipo ?: "Desconocido",
                                    paciente = "${pet.numero_radicado ?: ""} — ${pet.ubicacion_direccion ?: "Sin dirección"}",
                                    estado = pet.estado ?: "Transferido a otro centro",
                                    tipoEstado = TipoEstado.TRANSFERIDA
                                )
                            }

                            binding.rvTransferidas.adapter = PendientesAdapter(listaTransferidasRv) { item ->
                                if (item.id_peticion > 0) {
                                    val intent = Intent(this@dahsboard_veterianrio, DetallePeticion::class.java).apply {
                                        putExtra("PETICION_ID", item.id_peticion)
                                    }
                                    startActivity(intent)
                                }
                            }
                        } else {
                            binding.cardTransferidas.visibility = android.view.View.GONE
                        }
                    } else {
                        Toast.makeText(this@dahsboard_veterianrio, "Error al cargar dashboard", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@dahsboard_veterianrio, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun configurarGrafico(asignadas: Int, enTratamiento: Int, atendidas: Int, transferidas: Int, total: Int) {
        val pieChart = binding.pieChart

        val entries = ArrayList<PieEntry>()
        val colores = ArrayList<Int>()

        if (asignadas > 0) {
            entries.add(PieEntry(asignadas.toFloat(), ""))
            colores.add(Color.parseColor("#4141A5")) // primary (azul)
        }
        if (enTratamiento > 0) {
            entries.add(PieEntry(enTratamiento.toFloat(), ""))
            colores.add(Color.parseColor("#F1B800")) // secondary / en tratamiento (amarillo)
        }
        if (atendidas > 0) {
            entries.add(PieEntry(atendidas.toFloat(), ""))
            colores.add(Color.parseColor("#10B981")) // success / atendidas (verde)
        }
        if (transferidas > 0) {
            entries.add(PieEntry(transferidas.toFloat(), ""))
            colores.add(Color.parseColor("#6D28D9")) // morado transferido
        }

        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, ""))
            colores.add(ContextCompat.getColor(this, R.color.card_stroke))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colores
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        pieChart.data = data

        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(ContextCompat.getColor(this, R.color.card_background))
        pieChart.holeRadius = 75f
        pieChart.setHoleColor(ContextCompat.getColor(this, R.color.card_background))
        pieChart.setTransparentCircleAlpha(0)

        pieChart.setDrawCenterText(true)
        pieChart.centerText = "$total\ntotal"
        pieChart.setCenterTextSize(16f)
        pieChart.setCenterTextColor(ContextCompat.getColor(this, R.color.text_primary))

        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.invalidate()
    }

    /**
     * Configura el botón rápido en la cabecera para alternar entre Modo Claro y Oscuro con un solo toque.
     */
    private fun configurarBotonModoOscuro() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        binding.btnThemeToggle.setImageResource(
            if (isDarkMode) R.drawable.baseline_light_mode_24 else R.drawable.baseline_dark_mode_24
        )

        binding.btnThemeToggle.setOnClickListener {
            val nuevoModo = !sharedPreferences.getBoolean("isDarkMode", false)
            sharedPreferences.edit().putBoolean("isDarkMode", nuevoModo).apply()

            if (nuevoModo) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}