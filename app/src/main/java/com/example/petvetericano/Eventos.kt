package com.example.petvetericano

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityEventosBinding
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class Eventos : AppCompatActivity() {

    private lateinit var binding: ActivityEventosBinding
    private lateinit var adapter: EventoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Inicializar Binding
        binding = ActivityEventosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarVistas()
        cargarEventos()
    }

    private fun configurarVistas() {
        // Botón regresar
        binding.btnAtras.setOnClickListener {
            finish()
        }

        // Configurar RecyclerView y Adaptador
        adapter = EventoAdapter(emptyList()) { evento ->
            // Al tocar un evento, mostrar detalles completos
            val titulo = evento.titulo.replace("\"", "").trim()
            val descripcion = evento.descripcion.replace("\"", "").trim()
            val fecha = evento.fecha.replace("\"", "").trim()

            AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage("$descripcion\n\n📅 Fecha: $fecha")
                .setPositiveButton("Aceptar") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.recyclerEventos.layoutManager = LinearLayoutManager(this)
        binding.recyclerEventos.adapter = adapter
    }

    private fun cargarEventos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvSinEventos.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Obtener token si existe sesión guardada
                val prefs = SharedPreferencesManager(this@Eventos)
                val token = prefs.getAccessToken()
                val authHeader = if (token.isNotEmpty()) "Bearer $token" else null

                val respuesta = RetrofitClient.apiService.obtenerEventos(authHeader)

                if (respuesta.isSuccessful) {
                    val listaEventos = respuesta.body()

                    if (!listaEventos.isNullOrEmpty()) {
                        adapter.actualizarLista(listaEventos)
                        binding.tvSinEventos.visibility = View.GONE
                    } else {
                        adapter.actualizarLista(emptyList())
                        binding.tvSinEventos.visibility = View.VISIBLE
                    }
                } else {
                    val codigoError = respuesta.code()
                    val mensajeError = respuesta.errorBody()?.string() ?: "Sin detalles"
                    Log.e("API_EVENTOS", "Error $codigoError: $mensajeError")

                    val mensajeUsuario = when (codigoError) {
                        401 -> "Sesión expirada o no autorizada."
                        404 -> "No se encontraron eventos."
                        in 500..599 -> "Problemas en el servidor. Intenta más tarde."
                        else -> "No se pudieron cargar los eventos (Error $codigoError)."
                    }

                    Toast.makeText(this@Eventos, mensajeUsuario, Toast.LENGTH_LONG).show()
                    binding.tvSinEventos.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("API_EVENTOS", "Excepción al consultar eventos: ${e.message}", e)
                Toast.makeText(
                    this@Eventos,
                    "Error de conexión. Revisa tu internet.",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvSinEventos.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
