package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityRegistroBinding
import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch
import org.json.JSONObject

class Registro : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private lateinit var prefs: SharedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        binding.btnSignUp.setOnClickListener {
            registrarUsuario()
        }

        binding.txtlogin.setOnClickListener {
            val intent = Intent(this, inicio_sesion::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun registrarUsuario() {
        val email = binding.editTextTextEmailAddress.text.toString().trim()
        val identificacion = binding.edtxtemailorphone.text.toString().trim()
        val nombre = binding.edtxtID.text.toString().trim()
        val apellido = binding.edtxtApellido.text.toString().trim()
        val telefono = binding.edtxtTelefono.text.toString().trim() // 👈 Capturamos el teléfono
        val password = binding.edtxtPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.editTextTextEmailAddress.error = "Ingrese su correo electrónico"
            binding.editTextTextEmailAddress.requestFocus()
            return
        }

        if (identificacion.isEmpty()) {
            binding.edtxtemailorphone.error = "Ingrese su documento o ID"
            binding.edtxtemailorphone.requestFocus()
            return
        }

        if (nombre.isEmpty()) {
            binding.edtxtID.error = "Ingrese su nombre"
            binding.edtxtID.requestFocus()
            return
        }

        if (apellido.isEmpty()) {
            binding.edtxtApellido.error = "Ingrese su apellido"
            binding.edtxtApellido.requestFocus()
            return
        }

        // 💡 Validar que el teléfono tenga al menos 10 cifras (estándar Colombia)
        if (telefono.isEmpty() || telefono.length < 10) {
            binding.edtxtTelefono.error = "Ingrese un número de teléfono válido (mínimo 10 dígitos)"
            binding.edtxtTelefono.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.edtxtPassword.error = "Ingrese una contraseña"
            binding.edtxtPassword.requestFocus()
            return
        }

        val contieneMayuscula = Regex("[A-Z]").containsMatchIn(password)
        if (password.length < 8 || !contieneMayuscula) {
            binding.edtxtPassword.error = "Mínimo 8 caracteres y al menos una letra mayúscula"
            binding.edtxtPassword.requestFocus()
            return
        }

        binding.btnSignUp.isEnabled = false

        lifecycleScope.launch {
            try {
                val request = RegisterRequest(
                    email = email,
                    identificacion = identificacion,
                    password = password,
                    nombre = nombre,
                    apellido = apellido,
                    telefono = telefono // 👈 Enviamos el teléfono en la petición
                )

                val response = RetrofitClient.apiService.registro(request)

                if (response.isSuccessful) {
                    // Guardamos el teléfono localmente también por si se necesita en eventos
                    prefs.saveUserData(name = nombre, email = email, phone = telefono)
                    prefs.saveUserLastName(apellido)

                    Toast.makeText(this@Registro, "¡Registro exitoso!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@Registro, inicio_sesion::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val mensajeError = parsearErrorDjango(errorBody)
                    Toast.makeText(this@Registro, mensajeError, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@Registro, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    private fun parsearErrorDjango(json: String?): String {
        if (json.isNullOrEmpty()) return "Error al registrar usuario"
        return try {
            val jsonObject = JSONObject(json)
            val primeraClave = jsonObject.keys().next()
            val primerError = jsonObject.getJSONArray(primeraClave).getString(0)
            "$primeraClave: $primerError"
        } catch (_: Exception) {
            "Datos inválidos o usuario ya registrado"
        }
    }
}