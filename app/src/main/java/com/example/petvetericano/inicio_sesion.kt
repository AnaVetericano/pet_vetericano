package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityInicioSesionBinding
import com.example.petvetericano.databinding.ActivityMainBinding

class inicio_sesion : AppCompatActivity() {
    private lateinit var binding: ActivityInicioSesionBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnIniciarSesion.setOnClickListener {
            val intent = Intent(this, bienvenida::class.java)
            startActivity(intent)

        }
    }

   /* private fun iniciarSesion() {

        val username = binding.edtUsername.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (username.isEmpty()) {
            binding.edtUsername.error = "Ingrese su email o teléfono"
            return
        }

        if (password.isEmpty()) {
            binding.edtPassword.error = "Ingrese su contraseña"
            return
        }
        validarCredenciales(username, password) -->
    }*/

    private fun validarCredenciales(
        emailTelefono: String,
        contrasena: String
    ) {


        val emailCorrecto = "usuario@gmail.com"
        val telefonoCorrecto = "3001234567"
        val contrasenaCorrecta = "123456"

        if (
            (emailTelefono == emailCorrecto || emailTelefono == telefonoCorrecto) &&
            contrasena == contrasenaCorrecta
        ) {

            Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()


        } else {
            Toast.makeText(this, "Su correo electronico y su contraseña NO coinciden.Intentelo de nuevo", Toast.LENGTH_SHORT
            ).show()
        }
    }
}