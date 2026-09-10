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

        // Obtener el token que viene desde el Login
        val token = intent.getStringExtra("TOKEN")

        if (token != null) {
            cargarDashboard(token)
        } else {
            Toast.makeText(
                this,
                "No se encontró el token",
                Toast.LENGTH_SHORT
            ).show()
        }

        configurarEventos()
    }

    private fun cargarDashboard(token: String) {

        lifecycleScope.launch {

            try {

                // Enviamos el token al backend
                val respuesta = RetrofitClient.apiService.obtenerDashboard(
                    "Bearer $token"
                )

                if (respuesta.isSuccessful) {

                    val datos = respuesta.body()

                    if (datos != null) {

                        // Mostrar nombre del usuario
                        binding.tvSaludo.text = "Hola, ${datos.nombre}"

                        // Mensaje de bienvenida
                        binding.tvMensajeBienvenida.text =
                            "¡Gracias por ayudar!"

                    }

                } else {

                    Toast.makeText(
                        this@bienvenida,
                        "No se pudo cargar el Dashboard",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                Toast.makeText(
                    this@bienvenida,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
            Toast.makeText(
                this,
                "Ya estás en Inicio",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.cardNavPrincipal.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.ivNavFavoritos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
        }

        binding.ivNavPerfil.setOnClickListener {
            val intent = Intent(this, editar_perfil::class.java)
            startActivity(intent)

            Toast.makeText(
                this,
                "Perfil",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}