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
import com.example.petvetericano.models.PeticionListResponse
import com.example.petvetericano.offline.NetworkUtils
import com.example.petvetericano.offline.PeticionesCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PetitionsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPetitionsListBinding
    private var todasLasPeticiones: List<Petition> = emptyList()
    private var estadoFiltroActual: String = "todas" // todas, pendientes, proceso, atendidas
    private var avisoOfflineMostrado = false

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
            val idVet = prefs.getUserId()

            if (!NetworkUtils.hayRedDisponible(this@PetitionsListActivity)) {
                cargarDesdeCacheOFallar(idVet) {
                    Toast.makeText(this@PetitionsListActivity, R.string.acta_offline_error_cargar_peticiones, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (idVet > 0 && body.isNotEmpty()) {
                        PeticionesCacheManager.guardar(this@PetitionsListActivity, idVet, body)
                    }
                    withContext(Dispatchers.Main) {
                        procesarListaPeticiones(body)
                    }
                } else if (response.code() == 401) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PetitionsListActivity, R.string.acta_offline_error_cargar_peticiones, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    cargarDesdeCacheOFallar(idVet) {
                        Toast.makeText(this@PetitionsListActivity, R.string.acta_offline_error_cargar_peticiones, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                cargarDesdeCacheOFallar(idVet) {
                    val mensaje = e.message
                    val texto = if (!mensaje.isNullOrBlank()) {
                        getString(R.string.acta_offline_error_generico_con_detalle, mensaje)
                    } else {
                        getString(R.string.acta_offline_error_cargar_peticiones)
                    }
                    Toast.makeText(this@PetitionsListActivity, texto, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun cargarDesdeCacheOFallar(idVet: Int, mensajeError: () -> Unit) {
        val cached = if (idVet > 0) {
            PeticionesCacheManager.obtenerTodas(this@PetitionsListActivity, idVet)
        } else {
            emptyList()
        }
        withContext(Dispatchers.Main) {
            if (cached.isNotEmpty()) {
                procesarListaPeticiones(cached)
                mostrarAvisoModoSinConexion()
            } else {
                mensajeError()
            }
        }
    }

    private fun mostrarAvisoModoSinConexion() {
        if (!avisoOfflineMostrado) {
            avisoOfflineMostrado = true
            Toast.makeText(this, R.string.acta_offline_detalle_modo_sin_conexion, Toast.LENGTH_SHORT).show()
        }
    }

    private fun procesarListaPeticiones(listaOriginal: List<PeticionListResponse>) {
        todasLasPeticiones = listaOriginal.map {
            Petition(
                code = it.numero_radicado?.takeIf { rad -> rad.isNotBlank() }
                    ?: String.format(Locale.US, "PET-2026-%05d", it.id_peticion),
                title = it.tipo ?: "Desconocido",
                location = it.ubicacion_direccion ?: "No especificada",
                time = it.fecha ?: "",
                status = it.estado ?: "Pendiente",
                priorityType = it.id_peticion
            )
        }

        binding.tvAsignadasHoy.text = "Asignadas hoy: ${todasLasPeticiones.size}"
        aplicarFiltros()
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
        binding.tabTodas.setOnClickListener {
            estadoFiltroActual = "todas"
            actualizarEstiloTabs(binding.tabTodas)
            aplicarFiltros()
        }

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
        val tabs = listOf(binding.tabTodas, binding.tabPendientes, binding.tabEnProceso, binding.tabAtendidas)
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

    private fun normalizar(texto: String?): String {
        if (texto == null) return ""
        return java.text.Normalizer.normalize(texto.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
    }

    private fun aplicarFiltros() {
        val query = normalizar(binding.etBuscarPeticion.text.toString())

        val filtradas = todasLasPeticiones.filter { peticion ->
            val st = normalizar(peticion.status)
            val coincideEstado = when (estadoFiltroActual) {
                "todas" -> true
                "pendientes" -> {
                    st.contains("urgent") || st.contains("pendient") || st.contains("asignad") || st.contains("evaluac")
                }
                "proceso" -> {
                    st.contains("proces") || st.contains("tratamient") || st.contains("observac")
                }
                "atendidas" -> {
                    st.contains("atendid") || st.contains("alta") || st.contains("finaliz") || st.contains("resuelt") || st.contains("fallecid")
                }
                else -> true
            }

            val coincideQuery = if (query.isEmpty()) {
                true
            } else {
                normalizar(peticion.code).contains(query) ||
                normalizar(peticion.title).contains(query) ||
                normalizar(peticion.location).contains(query) ||
                st.contains(query)
            }

            coincideEstado && coincideQuery
        }

        binding.recyclerPeticiones.adapter = PetitionAdapter(filtradas) { petition ->
            val intent = Intent(this@PetitionsListActivity, DetallePeticion::class.java).apply {
                putExtra("PETICION_ID", petition.priorityType)
                putExtra("DIRECCION_ACTUAL", petition.location)
                putExtra("PETICION_RADICADO", petition.code)
                putExtra("PETICION_ESTADO", petition.status)
                putExtra("PETICION_TIPO", petition.title)
                putExtra("PETICION_FECHA", petition.time)
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
