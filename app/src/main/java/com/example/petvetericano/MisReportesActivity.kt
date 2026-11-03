package com.example.petvetericano

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityMisReportesBinding
import com.example.petvetericano.offline.ReporteLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MisReportesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisReportesBinding
    private val adapter by lazy { MisReportesAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtras.setOnClickListener {
            finish()
        }

        binding.rvMisReportes.layoutManager = LinearLayoutManager(this)
        binding.rvMisReportes.adapter = adapter

        cargarReportes()
    }

    override fun onResume() {
        super.onResume()
        cargarReportes()
    }

    private fun cargarReportes() {
        val prefs = SharedPreferencesManager(this)
        val userId = prefs.getUserId()
        if (userId <= 0) {
            mostrarEstadoVacio(true)
            adapter.submitList(emptyList())
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val repo = ReporteLocalRepository.getInstance(this@MisReportesActivity)
            val lista = repo.listarPorUsuario(userId, 50)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (lista.isEmpty()) {
                    mostrarEstadoVacio(true)
                    adapter.submitList(emptyList())
                } else {
                    mostrarEstadoVacio(false)
                    adapter.submitList(lista)
                }
            }
        }
    }

    private fun mostrarEstadoVacio(esVacio: Boolean) {
        if (esVacio) {
            binding.tvMisReportesVacio.visibility = View.VISIBLE
            binding.rvMisReportes.visibility = View.GONE
        } else {
            binding.tvMisReportesVacio.visibility = View.GONE
            binding.rvMisReportes.visibility = View.VISIBLE
        }
    }
}
