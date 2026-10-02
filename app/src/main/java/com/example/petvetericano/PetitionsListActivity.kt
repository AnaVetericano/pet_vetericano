package com.example.petvetericano

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.adapters.PetitionAdapter
import com.example.petvetericano.databinding.ActivityPetitionsListBinding
import com.example.petvetericano.models.Petition
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PetitionsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPetitionsListBinding
    private var todasLasPeticiones: List<Petition> = emptyList()
    private var estadoFiltroActual: String = "pendientes" // pendientes, proceso, atendidas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPetitionsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerPeticiones.layoutManager = LinearLayoutManager(this)

        configurarFecha()
        setupBottomNavigation()
        setupTabs()
        setupSearch()
        fetchPeticiones()
    }

    override fun onResume() {
        super.onResume()
        fetchPeticiones()
    }

    private fun configurarFecha() {
        val sdf = SimpleDateFormat("'Hoy' d MMM yyyy", Locale("es", "ES"))
        binding.tvFechaHoy.text = sdf.format(Date())
    }

    private fun fetchPeticiones() {
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
                        val listaOriginal = response.body()!!
                        todasLasPeticiones = listaOriginal.map {
                            Petition(
                                code = it.numero_radicado ?: "#INC-2026-${it.id_peticion}",
                                title = it.tipo ?: "Desconocido",
                                location = it.ubicacion_direccion ?: "No especificada",
                                time = it.fecha ?: "",
                                status = it.estado ?: "Pendiente",
                                priorityType = it.id_peticion
                            )
                        }

                        binding.tvAsignadasHoy.text = "Asignadas hoy: ${todasLasPeticiones.size}"
                        aplicarFiltros()
                    } else {
                        Toast.makeText(this@PetitionsListActivity, "Error al cargar peticiones", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PetitionsListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etBuscarPeticion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTabs() {
        binding.tabPendientes.setOnClickListener {
            estadoFiltroActual = "pendientes"
            actualizarEstiloTabs(binding.tabPendientes)
            aplicarFiltros()
        }

        binding.tabEnProceso.setOnClickListener {
            estadoFiltroActual = "proceso"
            actualizarEstiloTabs(binding.tabEnProceso)
            aplicarFiltros()
        }

        binding.tabAtendidas.setOnClickListener {
            estadoFiltroActual = "atendidas"
            actualizarEstiloTabs(binding.tabAtendidas)
            aplicarFiltros()
        }
    }

    private fun actualizarEstiloTabs(tabActiva: TextView) {
        val tabs = listOf(binding.tabPendientes, binding.tabEnProceso, binding.tabAtendidas)
        val colorActivo = ContextCompat.getColor(this, R.color.blue)
        val colorInactivo = ContextCompat.getColor(this, R.color.text_muted)

        for (tab in tabs) {
            if (tab == tabActiva) {
                tab.setTextColor(colorActivo)
                tab.typeface = Typeface.DEFAULT_BOLD
            } else {
                tab.setTextColor(colorInactivo)
                tab.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun aplicarFiltros() {
        val query = binding.etBuscarPeticion.text.toString().trim().lowercase()

        val filtradas = todasLasPeticiones.filter { peticion ->
            val coincideEstado = when (estadoFiltroActual) {
                "pendientes" -> {
                    val st = peticion.status.lowercase()
                    st.contains("urgente") || st.contains("pendiente") || st.contains("asignada") || st.contains("evaluación")
                }
                "proceso" -> {
                    val st = peticion.status.lowercase()
                    st.contains("proceso") || st.contains("tratamiento") || st.contains("observación")
                }
                "atendidas" -> {
                    val st = peticion.status.lowercase()
                    st.contains("atendida") || st.contains("alta") || st.contains("finalizada") || st.contains("resuelta") || st.contains("fallecido")
                }
                else -> true
            }

            val coincideQuery = if (query.isEmpty()) {
                true
            } else {
                peticion.code.lowercase().contains(query) ||
                peticion.title.lowercase().contains(query) ||
                peticion.location.lowercase().contains(query) ||
                peticion.status.lowercase().contains(query)
            }

            coincideEstado && coincideQuery
        }

        binding.recyclerPeticiones.adapter = PetitionAdapter(filtradas) { petition ->
            val intent = Intent(this@PetitionsListActivity, DetallePeticion::class.java).apply {
                putExtra("PETICION_ID", petition.priorityType)
            }
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        // Peticiones: ya estamos aquí
        binding.ivNavPeticiones.setOnClickListener {
            binding.recyclerPeticiones.smoothScrollToPosition(0)
        }

        // Inicio: vuelve al dashboard del veterinario
        binding.ivNavInicio.setOnClickListener {
            val intent = Intent(this, dahsboard_veterianrio::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        // Perfil: abre el perfil del veterinario
        binding.ivNavPerfil.setOnClickListener {
            val intent = Intent(this, OpcionesEditarPerfilActivity::class.java)
            startActivity(intent)
        }
    }
}