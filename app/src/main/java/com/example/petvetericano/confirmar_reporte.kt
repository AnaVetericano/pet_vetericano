package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import java.util.Locale

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        val latitud = intent.getDoubleExtra("LATITUD", 0.0)
        val longitud = intent.getDoubleExtra("LONGITUD", 0.0)

        binding.tvTipoReporte.text = tipoReporte

        if (latitud != 0.0 && longitud != 0.0) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val direcciones = geocoder.getFromLocation(latitud, longitud, 1)

                if (!direcciones.isNullOrEmpty()) {
                    val direccionReal = direcciones[0].getAddressLine(0)
                    // Aquí llamamos exactamente a tu ID: ubicacionedi
                    binding.ubicacionedit.text = direccionReal
                } else {
                    binding.ubicacionedit.text = "$latitud, $longitud"
                }
            } catch (e: Exception) {
                binding.ubicacionedit.text = "$latitud, $longitud"
            }
        } else {
            binding.ubicacionedit.text = "Ubicación no seleccionada"
        }

        binding.btnenvR.setOnClickListener {
            val nuevoIntent = Intent(this, reporte_enviado::class.java).apply {
                putExtra("TIPO_REPORTE", tipoReporte)
                putExtra("LATITUD", latitud)
                putExtra("LONGITUD", longitud)
            }
            startActivity(nuevoIntent)
            finish()


        }
        binding.ubicacioedit.setOnClickListener {
            val intent= Intent(this, reportar_peticionn :: class.java)
            startActivity(intent)
        }
        binding.tipoPeticion.setOnClickListener {
            val intent= Intent(this, reportar_peticion :: class.java)
            startActivity(intent)
        }
        binding.descripedit.setOnClickListener {
            val intent = Intent  (this, reportar_peticionnn :: class.java)
            startActivity(intent)
        }
        binding.archivosedi.setOnClickListener {
            val intent = Intent  (this, reportar_peticionnn :: class.java)
            startActivity(intent)
        }
    }
}