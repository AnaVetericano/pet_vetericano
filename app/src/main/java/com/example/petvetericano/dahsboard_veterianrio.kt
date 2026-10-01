package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityDahsboardVeterianrioBinding
import com.example.petvetericano.models.PeticionPendiente
import com.example.petvetericano.models.Petition
import com.example.petvetericano.models.TipoEstado
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

class dahsboard_veterianrio : AppCompatActivity() {
 //a la verga
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

        configurarGrafico()
        configurarRecyclerView()

        binding.btnPeticiones.setOnClickListener {
            val intent= Intent(this, PetitionsListActivity::class.java)
            startActivity(intent)
        }
    }

    private fun configurarGrafico() {
        val pieChart = binding.pieChart

        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(5f, "")) // Asignadas
        entries.add(PieEntry(10f, "")) // Atendidas
        entries.add(PieEntry(8f, ""))  // Reasignadas

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
        pieChart.centerText = "30\ntotal"
        pieChart.setCenterTextSize(16f)
        pieChart.setCenterTextColor(Color.parseColor("#0F172A"))

        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.invalidate()
    }

    private fun configurarRecyclerView() {
        val listaEstatica = listOf(
            PeticionPendiente("Vacuna contra el moquillo", "can. com. 14", "Urgente", TipoEstado.URGENTE),
            PeticionPendiente("Cirugía de pata izquierda", "fel. com. 9", "En proceso", TipoEstado.EN_PROCESO),
            PeticionPendiente("Revisión post-operatoria", "can. com. 5", "Asignada", TipoEstado.ASIGNADA)
        )

        val adapter = PendientesAdapter(listaEstatica)
        binding.rvPendientes.layoutManager = LinearLayoutManager(this)
        binding.rvPendientes.adapter = adapter
    }
}