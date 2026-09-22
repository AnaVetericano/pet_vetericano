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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ CORRECCIÓN: Usamos la misma clase que en el login para leer el token
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken()

        if (token.isNotEmpty()) {
            RetrofitClient.authToken = token
            cargarDashboard()
            configurarEventos()
        } else {
            // Si no hay token, redirigimos al login
            val intent = Intent(this, inicio_sesion::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun configurarEventos() {
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

    private fun cargarDashboard() {
        lifecycleScope.launch {
            try {
                // Llamada limpia sin parámetros, el AuthInterceptor inyecta el token automáticamente
                val response = RetrofitClient.apiService.obtenerDashboard()

                if (response.isSuccessful) {
                    val datos = response.body()

                    if (datos != null) {
                        binding.tvMensajeBienvenida.text = "¡Gracias por ayudar!"

                        val nombreCompleto = "${datos.nombre} ${datos.apellido}".trim()
                        val nombreFinal = if (nombreCompleto.isNotEmpty()) nombreCompleto else datos.nombre
                    }
                } else {
                    Toast.makeText(this@bienvenida, "Error al cargar el dashboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@bienvenida, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}