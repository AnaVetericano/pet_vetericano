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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa la vista usando ViewBinding
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Asigna eventos a los botones
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
        // Captura los datos de los EditText y elimina los espacios en blanco
        val email = binding.editTextTextEmailAddress.text.toString().trim()
        val identificacion = binding.edtxtemailorphone.text.toString().trim()
        val nombreCompleto = binding.edtxtID.text.toString().trim()
        val password = binding.edtxtPassword.text.toString().trim()

        // --- VALIDACIONES FRONTEND ---
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

        if (nombreCompleto.isEmpty()) {
            binding.edtxtID.error = "Ingrese su nombre"
            binding.edtxtID.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.edtxtPassword.error = "Ingrese una contraseña"
            binding.edtxtPassword.requestFocus()
            return
        }

        // Validación según las reglas de Django: mínimo 8 caracteres y una mayúscula
        val contieneMayuscula = Regex("[A-Z]").containsMatchIn(password)
        if (password.length < 8 || !contieneMayuscula) {
            binding.edtxtPassword.error = "Mínimo 8 caracteres y al menos una letra mayúscula"
            binding.edtxtPassword.requestFocus()
            return
        }

        // Separar nombre y apellido si los escribió juntos
        val partesNombre = nombreCompleto.split(" ", limit = 2)
        val nombre = partesNombre.getOrElse(0) { nombreCompleto }
        val apellido = partesNombre.getOrElse(1) { "." } // Si no puso apellido, envía un punto o espacio

        // Deshabilitar botón mientras procesa para evitar múltiples clics
        binding.btnSignUp.isEnabled = false

        // --- PETICIÓN A DJANGO CON RETROFIT ---
        lifecycleScope.launch {
            try {
                val request = RegisterRequest(
                    email = email,
                    identificacion = identificacion,
                    password = password,
                    nombre = nombre,
                    apellido = apellido
                )

                val response = RetrofitClient.apiService.registro(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@Registro, "¡Registro exitoso!", Toast.LENGTH_SHORT).show()

                    // Redirige a la pantalla de login o bienvenida
                    val intent = Intent(this@Registro, inicio_sesion::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    // Si Django devolvió un error (ej: email ya existe o cédula duplicada)
                    val errorBody = response.errorBody()?.string()
                    val mensajeError = parsearErrorDjango(errorBody)
                    Toast.makeText(this@Registro, mensajeError, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                // Error de red (sin internet o URL de Railway incorrecta)
                Toast.makeText(this@Registro, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                // Volver a habilitar el botón
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    // Función auxiliar para leer los errores en formato JSON que devuelve Django
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