package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.network.RegistroRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class Registro : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        // Buscamos el botón de forma segura o por su ID si existe en tu XML
        val btnRegistrar = findViewById<Button>(resources.getIdentifier("btnRegistrar", "id", packageName))
        val etEmail = findViewById<EditText>(resources.getIdentifier("etEmail", "id", packageName))
        val etPassword = findViewById<EditText>(resources.getIdentifier("etPassword", "id", packageName))
        val etNombre = findViewById<EditText>(resources.getIdentifier("etNombre", "id", packageName))

        btnRegistrar?.setOnClickListener {
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val password = etPassword?.text?.toString()?.trim() ?: ""
            val nombre = etNombre?.text?.toString()?.trim() ?: ""

            if (email.isEmpty() || password.isEmpty() || nombre.isEmpty()) {
                Toast.makeText(this, "Por favor completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = RegistroRequest(
                email = email,
                password = password,
                nombre = nombre
            )

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.registrarUsuario(request)

                    if (response.isSuccessful) {
                        Toast.makeText(this@Registro, "¡Registro exitoso!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@Registro, inicio_sesion::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        Toast.makeText(this@Registro, "Error: $errorBody", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@Registro, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}