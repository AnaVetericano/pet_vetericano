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
import org.json.JSONArray
import org.json.JSONObject

import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.models.TipoPeticionResponse

class reportar_peticion : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionBinding

    // Lista para guardar los tipos que vienen de la base de datos o caché
    private var listaTipos: List<TipoPeticionResponse> = emptyList()

    private val PREFS_CACHE = "ReporteTiposCachePrefs"
    private val KEY_CACHE = "ReporteTiposCache"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btndev.setOnClickListener {
            startActivity(Intent(this, bienvenida::class.java))
        }

        // 1. Descargar los tipos desde la API al abrir la pantalla o usar caché
        cargarTiposDesdeApi()

        // 2. Configurar clics buscando el ID correspondiente de la lista obtenida
        binding.cardHerido.setOnClickListener {
            obtenerIdYAvanzar("Animal Herido")
        }

        binding.cardMaltrato.setOnClickListener {
            obtenerIdYAvanzar("Maltrato animal")
        }

        binding.cardCalle.setOnClickListener {
            obtenerIdYAvanzar("Animal en condicion de calle")
        }

        // 3. Configurar clic para la tarjeta "Otros"
        binding.cardOtros.setOnClickListener {
            obtenerIdYAvanzar("Otros")
        }
    }

    private fun cargarTiposDesdeApi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.obtenerTiposPeticion()
                if (response.isSuccessful && response.body() != null) {
                    val remotos = response.body()!!
                    listaTipos = remotos
                    guardarEnCache(remotos)
                } else {
                    cargarDeCacheOFallar()
                }
            } catch (e: Exception) {
                cargarDeCacheOFallar()
            }
        }
    }

    private fun guardarEnCache(tipos: List<TipoPeticionResponse>) {
        try {
            val jsonArray = JSONArray()
            for (item in tipos) {
                val obj = JSONObject().apply {
                    put("id_tipo", item.id_tipo)
                    put("nombre", item.nombre)
                }
                jsonArray.put(obj)
            }
            getSharedPreferences(PREFS_CACHE, MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHE, jsonArray.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private suspend fun cargarDeCacheOFallar() {
        val cached = obtenerTiposDeCache()
        if (cached.isNotEmpty()) {
            listaTipos = cached
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@reportar_peticion,
                    getString(R.string.reporte_offline_error_cargar_tipos),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun obtenerTiposDeCache(): List<TipoPeticionResponse> {
        val raw = getSharedPreferences(PREFS_CACHE, MODE_PRIVATE)
            .getString(KEY_CACHE, null) ?: return emptyList()
        val list = mutableListOf<TipoPeticionResponse>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TipoPeticionResponse(
                        id_tipo = obj.getInt("id_tipo"),
                        nombre = obj.getString("nombre"),
                        descripcion = null
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun obtenerIdYAvanzar(nombreBuscado: String) {
        if (listaTipos.isEmpty()) {
            Toast.makeText(this, R.string.reporte_offline_error_cargar_tipos, Toast.LENGTH_SHORT).show()
            return
        }
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