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
import com.example.petvetericano.models.VoluntariadoEventos // 👈 Esta importación soluciona el problema de la línea 71
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
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

        // 👈 Se especifica <VoluntariadoEventos> para que Kotlin no tenga dudas
        adapter = EventoAdapter(emptyList<VoluntariadoEventos>()) { evento: VoluntariadoEventos ->
            // Paso 1 M3: dialogo informativo (no ejecuta la accion)
            mostrarDetalleEvento(evento)
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
                    val listaEventos: List<VoluntariadoEventos>? = respuesta.body()

                    if (!listaEventos.isNullOrEmpty()) {
                        adapter.actualizarLista(listaEventos)
                        binding.tvSinEventos.visibility = View.GONE
                    } else {
                        // 👈 Se especifica también aquí el tipo en la lista vacía
                        adapter.actualizarLista(emptyList<VoluntariadoEventos>())
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

    private fun mostrarDetalleEvento(evento: VoluntariadoEventos) {
        val titulo = evento.titulo.replace("\"", "").trim()
        val descripcion = evento.descripcion.replace("\"", "").trim()
        val fecha = evento.fecha.replace("\"", "").trim()

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage("$descripcion\n\n📅 Fecha: $fecha")
            .setNegativeButton("Cerrar") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Postularse") { dialog, _ ->
                dialog.dismiss()
                // Paso 2 M3: confirmacion antes de la accion con consecuencia
                mostrarConfirmacionPostulacion(evento, titulo)
            }
            .show()
    }

    private fun mostrarConfirmacionPostulacion(evento: VoluntariadoEventos, titulo: String) {
        AlertDialog.Builder(this)
            .setTitle("¿Confirmar postulación?")
            .setMessage(
                "Te postularás a \"$titulo\" con los datos de tu cuenta. " +
                    "El equipo te contactará."
            )
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Confirmar") { dialog, _ ->
                dialog.dismiss()
                postularse(evento)
            }
            .show()
    }

    private fun postularse(evento: VoluntariadoEventos) {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken()

        if (token.isEmpty()) {
            Toast.makeText(this, "Debes iniciar sesion para postularte.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.postularse("Bearer $token", evento.id)

                when (respuesta.code()) {
                    201 -> {
                        val msg = respuesta.body()?.mensaje ?: "¡Te has postulado exitosamente!"
                        Snackbar.make(
                            binding.root,
                            msg,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    400 -> {
                        val errorBody = respuesta.errorBody()?.string() ?: ""
                        val msg = if (errorBody.contains("postulado")) {
                            "Ya te encuentras postulado a esta jornada."
                        } else {
                            "No fue posible completar la postulacion."
                        }
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    }
                    401 -> {
                        Toast.makeText(this@Eventos, "Sesion expirada. Inicia sesion nuevamente.", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        val code = respuesta.code()
                        Toast.makeText(this@Eventos, "Error al postularse ($code).", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("POSTULARSE", "Excepcion: ${e.message}", e)
                Toast.makeText(this@Eventos, "Error de conexion. Revisa tu internet.", Toast.LENGTH_LONG).show()
            }
        }
    }
}