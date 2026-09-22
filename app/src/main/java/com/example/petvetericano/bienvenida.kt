package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityBienvenidaBinding
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class bienvenida : AppCompatActivity() {

    private lateinit var binding: ActivityBienvenidaBinding
    private lateinit var prefs: SharedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        // Muestra el saludo actualizado desde SharedPreferences
        actualizarSaludoLocal()

        val token = intent.getStringExtra("TOKEN")

        if (token != null) {
            cargarDashboard(token)
        } else {
            Toast.makeText(this, "No se encontró el token", Toast.LENGTH_SHORT).show()
        }

        configurarEventos()
    }

    // Se activa de nuevo al regresar desde la pantalla de editar perfil
    override fun onResume() {
        super.onResume()
        actualizarSaludoLocal()
    }

    // Actualiza el saludo "Hola, [Nombre]" en la vista
    private fun actualizarSaludoLocal() {
        val nombreGuardado = prefs.getUserName()
        if (nombreGuardado.isNotEmpty()) {
            val primerNombre = nombreGuardado.split(" ").firstOrNull() ?: nombreGuardado
            binding.tvSaludo.text = "Hola, $primerNombre"
        }
    }

    private fun cargarDashboard(token: String) {
        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.obtenerDashboard("Bearer $token")

                if (respuesta.isSuccessful) {
                    val datos = respuesta.body()

                    if (datos != null) {
                        binding.tvMensajeBienvenida.text = "¡Gracias por ayudar!"

                        val nombreCompleto = "${datos.nombre} ${datos.apellido}".trim()
                        val nombreFinal = if (nombreCompleto.isNotEmpty()) nombreCompleto else datos.nombre

                        prefs.saveUserData(
                            name  = nombreFinal,
                            email = datos.email,
                            phone = prefs.getUserPhone()
                        )

                        actualizarSaludoLocal()
                    }
                } else {
                    val codigoError = respuesta.code()
                    val mensajeError = respuesta.errorBody()?.string() ?: "Sin detalles"
                    android.util.Log.e("API_ERROR", "Error $codigoError: $mensajeError")

                    val mensajeUsuario = when (codigoError) {
                        401 -> "Tu sesión ha expirado. Por favor, inicia sesión de nuevo."
                        403 -> "No tienes permisos para ver esta información."
                        404 -> "No se encontró el perfil de usuario."
                        in 500..599 -> "Problemas en el servidor. Intenta más tarde."
                        else -> "No se pudo cargar tu información (Error $codigoError)."
                    }

                    Toast.makeText(this@bienvenida, mensajeUsuario, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("API_ERROR", "Excepción técnica: ${e.message}")
                Toast.makeText(this@bienvenida, "Error de conexión. Revisa tu internet.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configurarEventos() {
        binding.cardReportarPeticion.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }
        binding.cardAdopcion.setOnClickListener {
            val intent = Intent(this, Adopcion::class.java)
            startActivity(intent)
        }
        binding.cardVoluntariado.setOnClickListener {
            val intent = Intent(this, voluntariado::class.java)
            startActivity(intent)
        }

        binding.ivNavInicio.setOnClickListener {
            Toast.makeText(this, "Ya estás en Inicio", Toast.LENGTH_SHORT).show()
        }

        binding.cardNavPrincipal.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.ivNavFavoritos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
            Toast.makeText(this, "Jornadas y eventos", Toast.LENGTH_SHORT).show()
        }

        binding.ivNavPerfil.setOnClickListener {
            val intent = Intent(this, editar_perfil::class.java)
            startActivity(intent)
        }
    }
}