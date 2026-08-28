package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityInicioSesionBinding
import com.google.firebase.auth.FirebaseAuth

class inicio_sesion : AppCompatActivity() {

    private lateinit var binding: ActivityInicioSesionBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Conectar con Firebase Authentication
        auth = FirebaseAuth.getInstance()

        binding.btnIniciarSesion.setOnClickListener {
            iniciarSesion()
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

        // Iniciar sesión con Firebase Authentication
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Inicio de sesión exitoso",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this, bienvenida::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "No se pudo iniciar sesión: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}