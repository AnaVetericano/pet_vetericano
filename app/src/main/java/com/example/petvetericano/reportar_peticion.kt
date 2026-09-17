package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityReportarPeticionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class reportar_peticion : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionBinding

    // Lista para guardar los tipos que vienen de la base de datos
    private var listaTipos: List<TipoPeticionResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btndev.setOnClickListener {
            startActivity(Intent(this, bienvenida::class.java))
        }

        // 1. Descargar los tipos desde la API al abrir la pantalla
        cargarTiposDesdeApi()

        // 2. Configurar clics buscando el ID correspondiente de la lista obtenida
        binding.cardHerido.setOnClickListener {
            // Busca el ID cuyo nombre coincida o corresponda a "Animal Herido"
            obtenerIdYAvanzar("Animal Herido")
        }

        binding.cardMaltrato.setOnClickListener {
            obtenerIdYAvanzar("Maltrato animal")
        }

        binding.cardCalle.setOnClickListener {
            obtenerIdYAvanzar("Animal en condicion de calle")
        }
    }

    private fun cargarTiposDesdeApi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Aquí llamas al GET que ya creaste en tu ApiService
                val response = RetrofitClient.apiService.obtenerTiposPeticion()
                if (response.isSuccessful && response.body() != null) {
                    listaTipos = response.body()!!
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@reportar_peticion, "No se pudieron cargar los tipos", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@reportar_peticion, "Error de red al cargar tipos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun obtenerIdYAvanzar(nombreBuscado: String) {
        val tipoEncontrado = listaTipos.find { it.nombre.equals(nombreBuscado, ignoreCase = true) }
        val idReal = tipoEncontrado?.id_tipo ?: 1

        // GUARDAR EN PREFS LOCALES AL INSTANTE
        getSharedPreferences("MisReportesPrefs", MODE_PRIVATE)
            .edit()
            .putInt("ID_TIPO_SELECCIONADO", idReal)
            .apply()

        val intent = Intent(this, reportar_peticionn::class.java).apply {
            putExtra("ID_TIPO_SELECCIONADO", idReal)
            putExtra("TIPO_REPORTE", nombreBuscado)
        }
        startActivity(intent)
    }
}