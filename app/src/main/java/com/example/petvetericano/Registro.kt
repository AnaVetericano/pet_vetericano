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

        // Inicializa la vista usando ViewBinding
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializa Firebase Auth y la referencia al nodo "Usuarios" en Realtime Database
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Usuarios")

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
        val nombre = binding.edtxtID.text.toString().trim()
        val password = binding.edtxtPassword.text.toString().trim()

        // Bloque de validaciones para asegurar que ningún campo quede vacío
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

        // Crea el usuario en la sección de Authentication de Firebase
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->

                val userId = authResult.user?.uid

                // Crea el mapa de datos para la base de datos agregando el rol por defecto
                val usuarioMap = mapOf(
                    "nombre" to nombre,
                    "identificacion" to identificacion,
                    "email" to email,
                    "rol" to "peticionario"
                )

                // Guarda el mapa de datos en el nodo del usuario correspondiente
                if (userId != null) {
                    database.child(userId).setValue(usuarioMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()

                            // Redirige a la pantalla de bienvenida y limpia el historial de actividades
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