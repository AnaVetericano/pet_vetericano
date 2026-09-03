package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityBienvenidaBinding

class bienvenida : AppCompatActivity() {

private lateinit var binding: ActivityBienvenidaBinding

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityBienvenidaBinding.inflate(layoutInflater)
    setContentView(binding.root)

    configurarEventos()
}

private fun configurarEventos() {


    binding.cardReportarPeticion.setOnClickListener {
        val intent = Intent(this, reportar_peticion::class.java)
        startActivity(intent)
    }


    binding.cardAdopcion.setOnClickListener {
        val intent = Intent(this, Adopcion::class.java)
        startActivity(intent)
    }

    binding.cardVoluntariado.setOnClickListener {
        val intent = Intent(this, voluntariado::class.java)
        startActivity(intent)
    }


    binding.ivNavInicio.setOnClickListener {
        Toast.makeText(this, "Ya estás en Inicio", Toast.LENGTH_SHORT).show()
    }




    binding.cardNavPrincipal.setOnClickListener {
        val intent = Intent(this, reportar_peticion::class.java)
        startActivity(intent)
    }


    binding.ivNavFavoritos.setOnClickListener {

        val intent = Intent(this, Eventos::class.java)
        startActivity(intent) }


    binding.ivNavPerfil.setOnClickListener {
        val intent = Intent(this, editar_perfil::class.java)
        startActivity(intent)
        Toast.makeText(this, "Perfil", Toast.LENGTH_SHORT).show()
    }
}
}