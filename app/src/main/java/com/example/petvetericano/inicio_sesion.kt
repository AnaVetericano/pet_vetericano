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

    companion object {
        const val ROL_VETERINARIO = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val emailRegistrado = intent.getStringExtra("EMAIL_REGISTRADO")
        if (!emailRegistrado.isNullOrEmpty()) {
            binding.editTextText.setText(emailRegistrado)
        }

        val mensajeRegistro = intent.getStringExtra("REGISTRO_GOOGLE_MSG")
        if (!mensajeRegistro.isNullOrEmpty()) {
            binding.root.post {
                GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, mensajeRegistro, esError = false)
            }
        }

        binding.btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }

        binding.btnGoogleLogin.setOnClickListener {
            iniciarSesionConGoogle()
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

    private fun iniciarSesionConGoogle() {
        lifecycleScope.launch {
            GoogleAuthHelper.autenticarConGoogle(
                activity = this@inicio_sesion,
                esFlujoRegistro = false,
                onEstadoCarga = { cargando ->
                    binding.btnGoogleLogin.isEnabled = !cargando
                    binding.btnIniciarSesion.isEnabled = !cargando
                    binding.btnGoogleLogin.text = if (cargando) {
                        "Conectando con Google..."
                    } else {
                        getString(R.string.continuar_con_google)
                    }
                }
            )
        }
    }

    private fun iniciarSesion() {
        val email = binding.editTextText.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        // Limpiar errores previos
        binding.editTextText.error = null
        binding.tilPassword.error = null

        if (email.isEmpty()) {
            binding.editTextText.error = "Ingrese su correo electrónico"
            binding.editTextText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese su contraseña"
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
                        prefs.saveUserRole(datos.idRol)

                        val intent = if (datos.idRol == ROL_VETERINARIO) {
                            Intent(this@inicio_sesion, dahsboard_veterianrio::class.java)
                        } else {
                            Intent(this@inicio_sesion, bienvenida::class.java)
                        }

                        intent.putExtra("TOKEN", datos.tokens.access)
                        intent.putExtra("EMAIL", datos.email)
                        intent.putExtra("ID_ROL", datos.idRol)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                    }
                } else {
                    val errorRaw = respuesta.errorBody()?.string()
                    val msgError = try {
                        val json = org.json.JSONObject(errorRaw ?: "")
                        json.optJSONArray("non_field_errors")?.optString(0)
                            ?: json.optString("detail").takeIf { it.isNotEmpty() }
                            ?: "Correo o contraseña incorrectos"
                    } catch (_: Exception) {
                        "Correo o contraseña incorrectos"
                    }
                    GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, msgError, esError = true)
                }
            } catch (e: Exception) {
                GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, "Error de conexión con el servidor", esError = true)
            }
        }
    }
}