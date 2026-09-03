package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityRegistroBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class Registro : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Usuarios")

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

        if (password.isEmpty()) {
            binding.edtxtPassword.error = "Ingrese una contraseña"
            binding.edtxtPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.edtxtPassword.error = "La contraseña debe tener al menos 6 caracteres"
            binding.edtxtPassword.requestFocus()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val userId = authResult.user?.uid

                val usuarioMap = mapOf(
                    "nombre" to nombre,
                    "identificacion" to identificacion,
                    "email" to email
                )

                if (userId != null) {
                    database.child(userId).setValue(usuarioMap)
                        .addOnSuccessListener {

                            Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, bienvenida::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al guardar perfil: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "No se pudo registrar: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }
}