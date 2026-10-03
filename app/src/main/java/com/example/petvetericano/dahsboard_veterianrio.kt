package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityDahsboardVeterianrioBinding
import com.example.petvetericano.models.PeticionPendiente
import com.example.petvetericano.models.TipoEstado
import com.example.petvetericano.network.RetrofitClient
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Cargar nombre e iniciales del veterinario desde SharedPreferences
        val prefs = SharedPreferencesManager(this)
        val nombre = prefs.getUserName()
        val apellido = prefs.getUserLastName()
        val nombreCompleto = if (apellido.isNotEmpty()) "$nombre $apellido" else nombre
        binding.tvNombreUsuario.text = nombreCompleto.ifEmpty { "Veterinario" }

        val iniciales = buildString {
            if (nombre.isNotEmpty()) append(nombre.first().uppercaseChar())
            if (apellido.isNotEmpty()) append(apellido.first().uppercaseChar())
            else if (nombre.length > 1) append(nombre[1].uppercaseChar())
        }
        binding.tvInicialesAvatar.text = iniciales.ifEmpty { "V" }

        // Botones de navegación de acciones
        binding.btnPeticiones.setOnClickListener {
            startActivity(Intent(this, PetitionsListActivity::class.java))
        }


        // Bottom Navigation: Inicio ya es esta pantalla, pero permitir re-scroll o no hacer nada
        binding.ivNavInicio.setOnClickListener {
            // Ya estamos en inicio
        }

        binding.ivNavPeticiones.setOnClickListener {
            val intent = Intent(this, PetitionsListActivity::class.java)
            startActivity(intent)
        }

        binding.ivNavPerfil.setOnClickListener {
            startActivity(Intent(this, OpcionesEditarPerfilActivity::class.java))
        }

        fetchDashboardData()
    }

    override fun onResume() {
        super.onResume()
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

        if (token.isEmpty()) {
            Toast.makeText(this, "Debe iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val petitions = response.body()!!

                        // Conteos
                        val asignadas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("asignad") || st.contains("evaluaci") || st.contains("urgente")
                        }
                        val atendidas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("atendida") || st.contains("finaliz") || st.contains("alta") || st.contains("fallecid")
                        }
                        val reasignadas = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("reasignad") || st.contains("proceso") || st.contains("tratamiento") || st.contains("observaci")
                        }
                        val urgentes = petitions.count {
                            val st = it.estado?.lowercase() ?: ""
                            st.contains("urgente")
                        }
                        val total = petitions.size

                        // Actualizar leyenda y contadores
                        binding.tvGraficoAsignadas.text = asignadas.toString()
                        binding.tvGraficoAtendidas.text = atendidas.toString()
                        binding.tvGraficoReasignadas.text = reasignadas.toString()
                        binding.tvPeticionesAsignadas.text = asignadas.toString()
                        binding.tvConteoAtencion.text = atendidas.toString()
                        binding.tvCantidadUrgentes.text = "$urgentes urgentes"

                        configurarGrafico(asignadas, atendidas, reasignadas, total)

                        // Lista para RecyclerView: las 5 más recientes
                        val listaParaRv = petitions.take(5).map { pet ->
                            val estadoStr = pet.estado?.lowercase() ?: ""
                            val tipo = when {
                                estadoStr.contains("urgente") -> TipoEstado.URGENTE
                                estadoStr.contains("proceso") || estadoStr.contains("tratamiento") || estadoStr.contains("observaci") -> TipoEstado.EN_PROCESO
                                else -> TipoEstado.ASIGNADA
                            }
                            PeticionPendiente(
                                id_peticion = pet.id_peticion,
                                titulo = pet.tipo ?: "Desconocido",
                                paciente = "${pet.numero_radicado ?: ""} — ${pet.ubicacion_direccion ?: "Sin dirección"}",
                                estado = pet.estado ?: "Asignada",
                                tipoEstado = tipo
                            )
                        }

                        binding.rvPendientes.adapter = PendientesAdapter(listaParaRv) { item ->
                            if (item.id_peticion > 0) {
                                val intent = Intent(this@dahsboard_veterianrio, DetallePeticion::class.java).apply {
                                    putExtra("PETICION_ID", item.id_peticion)
                                }
                                startActivity(intent)
                            }
                        }
                    } else {
                        Toast.makeText(this@dahsboard_veterianrio, "Error al cargar dashboard", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@dahsboard_veterianrio, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun configurarGrafico(asignadas: Int, atendidas: Int, reasignadas: Int, total: Int) {
        val pieChart = binding.pieChart

        val entries = ArrayList<PieEntry>()
        if (asignadas > 0) entries.add(PieEntry(asignadas.toFloat(), ""))
        if (atendidas > 0) entries.add(PieEntry(atendidas.toFloat(), ""))
        if (reasignadas > 0) entries.add(PieEntry(reasignadas.toFloat(), ""))

        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "")) // dummy para que no quede vacío
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#4141A5"),
            Color.parseColor("#F1B800"),
            Color.parseColor("#170B3D")
        )
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        pieChart.data = data

        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 75f
        pieChart.setTransparentCircleAlpha(0)

        pieChart.setDrawCenterText(true)
        pieChart.centerText = "$total\ntotal"
        pieChart.setCenterTextSize(16f)
        pieChart.setCenterTextColor(Color.parseColor("#0F172A"))

        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.invalidate()
    }
}