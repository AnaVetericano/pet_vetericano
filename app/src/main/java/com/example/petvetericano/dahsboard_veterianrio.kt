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

        binding.btnPeticiones.setOnClickListener {
            val intent= Intent(this, PetitionsListActivity::class.java)
            startActivity(intent)
        }
        
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        val sharedPreferences = getSharedPreferences("PetVetericanoPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("access_token", "") ?: ""

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
                        
                        // Counts
                        val asignadas = petitions.count { it.estado == "Asignada" }
                        val atendidas = petitions.count { it.estado == "Atendida" || it.estado == "Finalizada" }
                        val reasignadas = petitions.count { it.estado == "Reasignada" }
                        val total = asignadas + atendidas + reasignadas
                        
                        configurarGrafico(asignadas, atendidas, reasignadas, total)
                        
                        // Map for Recycler View (only pending or all latest)
                        val listaParaRv = petitions.take(5).map {
                            val estadoStr = it.estado?.lowercase() ?: ""
                            val tipo = when {
                                estadoStr.contains("urgente") -> TipoEstado.URGENTE
                                estadoStr.contains("proceso") -> TipoEstado.EN_PROCESO
                                else -> TipoEstado.ASIGNADA
                            }
                            PeticionPendiente(
                                titulo = it.tipo ?: "Desconocido",
                                paciente = "${it.numero_radicado ?: ""} - ${it.ubicacion_direccion ?: ""}",
                                estado = it.estado ?: "Asignada",
                                tipoEstado = tipo
                            )
                        }
                        
                        binding.rvPendientes.adapter = PendientesAdapter(listaParaRv)
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
            entries.add(PieEntry(1f, "")) // dummy para que no quede vacio
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