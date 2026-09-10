package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import com.example.petvetericano.databinding.ActivityMainBinding
import com.example.petvetericano.databinding.ActivityReporteEnviadoBinding

class reporte_enviado : AppCompatActivity() {
    private lateinit var binding : ActivityReporteEnviadoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityReporteEnviadoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.contcba.setOnClickListener {
            realizarLlamada("3218905214")
        }
        binding.regremen.setOnClickListener {
           val intent= Intent (this, bienvenida :: class.java)
            startActivity(intent)
        }
    }

    private fun realizarLlamada(numeroTelefono: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$numeroTelefono")
        }
        startActivity(intent)
    }

}





