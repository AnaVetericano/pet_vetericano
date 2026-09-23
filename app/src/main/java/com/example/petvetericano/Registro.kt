package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Registro : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        // Buscamos el botón de manera segura para evitar nulos si el ID es diferente
        val btnRegistrar = findViewById<Button>(resources.getIdentifier("btnRegistrar", "id", packageName))

        btnRegistrar?.setOnClickListener {
            // Acción temporal para que la app avance sin romper la compilación
            Toast.makeText(this, "Módulo de registro en desarrollo por el equipo", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, inicio_sesion::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}