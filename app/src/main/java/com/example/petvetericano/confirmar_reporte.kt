package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import com.example.petvetericano.databinding.ActivityInicioSesionBinding


class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding= ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnenvR.setOnClickListener {
            val intent = Intent(this, reporte_enviado::class.java)
            startActivity(intent)
        }
        binding.ubicacioedit.setOnClickListener {
            val intent = Intent(this, reportar_peticionn :: class.java)
            startActivity(intent)
        }


        }

    }
