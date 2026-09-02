package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding

class reportar_peticionnn : AppCompatActivity() {
    private lateinit var binding: ActivityReportarPeticionnnBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionnnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")

        val latitud = intent.getDoubleExtra("LATITUD", 0.0)
        val longitud = intent.getDoubleExtra("LONGITUD", 0.0)

        // 2. ENVIAMOS TODO A LA CONFIRMACIÓN FINAL
        binding.btnContinue.setOnClickListener {

            val intent = Intent(this, confirmar_reporte::class.java).apply {

                putExtra("TIPO_REPORTE", tipoReporte)

                putExtra("LATITUD", latitud)
                putExtra("LONGITUD", longitud)

            }

            startActivity(intent)
        }
    }
}