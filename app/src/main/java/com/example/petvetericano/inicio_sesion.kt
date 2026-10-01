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

    // 1. AQUÍ DECLARAS LOS ROLES (Adiós a los datos quemados)
    companion object {
        const val ROL_VETERINARIO = 3
    }

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
        binding.txtRecoverPassword.setOnClickListener {
            val dialogRecuperar = RecuperarContrasenia(this@inicio_sesion)
            dialogRecuperar.show()
        }
    }

    private fun iniciarSesion() {
        val email = binding.editTextText.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.editTextText.error = "Ingrese su correo electrónico"
            binding.editTextText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.edtPassword.error = "Ingrese su contraseña"
            binding.edtPassword.requestFocus()
            return
        }

        val loginRequest = LoginRequest(email = email, password = password)

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.login(loginRequest)

                if (respuesta.isSuccessful) {
                    val datos = respuesta.body()

                    if (datos != null) {
                        Toast.makeText(this@inicio_sesion, datos.mensaje, Toast.LENGTH_SHORT).show()

                        RetrofitClient.authToken = datos.tokens.access

                        val prefs = SharedPreferencesManager(this@inicio_sesion)
                        prefs.saveUserData(
                            name  = datos.nombre ?: "",
                            email = datos.email,
                            phone = ""
                        )
                        prefs.saveUserLastName(datos.apellido ?: "")
                        prefs.saveAccessToken(datos.tokens.access)
                        prefs.saveUserId(datos.idUsuario)

                        // 2. AQUÍ SE SEPARA EL CAMINO SEGÚN EL ROL
                        val intent = if (datos.idRol == ROL_VETERINARIO) {
                            // Si es 2, va al dashboard del veterinario
                            Intent(this@inicio_sesion, dahsboard_veterianrio::class.java)
                        } else {
                            // Si es cualquier otra cosa, va a la bienvenida normal
                            Intent(this@inicio_sesion, bienvenida::class.java)
                        }

                        intent.putExtra("TOKEN", datos.tokens.access)
                        intent.putExtra("EMAIL", datos.email)
                        intent.putExtra("ID_ROL", datos.idRol)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this@inicio_sesion, "Correo o contraseña incorrectos", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@inicio_sesion, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}