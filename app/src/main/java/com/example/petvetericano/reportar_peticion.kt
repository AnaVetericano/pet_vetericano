package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityReportarPeticionBinding

class reportar_peticion : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()


        binding = ActivityReportarPeticionBinding.inflate(layoutInflater)

        setContentView(binding.root)


        binding.btndev.setOnClickListener {
            val intent= Intent(this, bienvenida::class.java)
            startActivity(intent)
        }
        binding.cardHerido.setOnClickListener {
            abrirSiguientePantalla("Animal herido o enfermo")
        }

        binding.cardMaltrato.setOnClickListener {
            abrirSiguientePantalla("Maltrato animal")
        }

        binding.cardCalle.setOnClickListener {
            abrirSiguientePantalla("Animal en condicion de calle")
        }
    }

    private fun abrirSiguientePantalla(tipoReporte: String) {

        val intent = Intent(this, reportar_peticionn::class.java).apply {

            putExtra("TIPO_REPORTE", tipoReporte)
        }

        startActivity(intent)
    }
}