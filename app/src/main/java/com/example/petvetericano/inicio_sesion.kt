package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityInicioSesionBinding
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class inicio_sesion : AppCompatActivity() {

    private lateinit var binding: ActivityInicioSesionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }
        binding.txtRegistrarse.setOnClickListener {
            val intent = Intent(this, Registro::class.java)
            startActivity(intent)
        }
    }


    private fun iniciarSesion() {

        val email = binding.editTextText.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        // Validar correo
        if (email.isEmpty()) {
            binding.editTextText.error = "Ingrese su correo electrónico"
            binding.editTextText.requestFocus()
            return
        }

        // Validar contraseña
        if (password.isEmpty()) {
            binding.edtPassword.error = "Ingrese su contraseña"
            binding.edtPassword.requestFocus()
            return
        }

        // Crear objeto que se enviará al backend
        val loginRequest = LoginRequest(
            email = email,
            password = password
        )

        // Consumir API
        lifecycleScope.launch {

            try {

                val respuesta = RetrofitClient.apiService.login(loginRequest)

                if (respuesta.isSuccessful) {

                    val datos = respuesta.body()

                    if (datos != null) {

                        Toast.makeText(
                            this@inicio_sesion,
                            datos.mensaje,
                            Toast.LENGTH_SHORT
                        ).show()

                        // Obtener token
                        val accessToken = datos.tokens.access

                        // Ir a la pantalla de bienvenida
                        val intent = Intent(
                            this@inicio_sesion,
                            bienvenida::class.java
                        )

                        intent.putExtra("TOKEN", accessToken)
                        intent.putExtra("EMAIL", datos.email)
                        intent.putExtra("ID_ROL", datos.idRol)

                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                    }

                } else {

                    Toast.makeText(
                        this@inicio_sesion,
                        "Correo o contraseña incorrectos",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                Toast.makeText(
                    this@inicio_sesion,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
