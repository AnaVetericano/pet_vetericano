package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        binding.tvTipoReporte.text = tipoReporte

        binding.tipoPeticion.setOnClickListener {
            val intent=Intent(this, reportar_peticion :: class.java)
            startActivity(intent)
        }

        binding.btnenvR.setOnClickListener {

            val nuevoIntent = Intent(this, reporte_enviado::class.java).apply {

                putExtra("TIPO_REPORTE", tipoReporte)
            }

            startActivity(nuevoIntent)

            // Opcional pero recomendado: si esta es la confirmación y ya se envió,
            // usar finish() destruye esta actividad para que el usuario no pueda volver atrás
            // pulsando el botón de retroceso del celular y reenviar el reporte por error.
            finish()
        }
    }
}