package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import java.util.Locale

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding

    // Lista para almacenar los enlaces de Cloudinary recibidos
    private var urlsArchivos: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. LEEMOS LAS URLS Y ACTUALIZAMOS LA CANTIDAD EN PANTALLA
        obtenerCantidadFotos()

        val descripcionRecibida = intent.getStringExtra("DESCRIPCION")
        binding.descri.text = descripcionRecibida ?: "Sin descripción"

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        binding.tvTipoReporte.text = tipoReporte

        val latitud = intent.getDoubleExtra("LATITUD", 0.0)
        val longitud = intent.getDoubleExtra("LONGITUD", 0.0)

        if (latitud != 0.0 && longitud != 0.0) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val direcciones = geocoder.getFromLocation(latitud, longitud, 1)

                if (!direcciones.isNullOrEmpty()) {
                    val direccionReal = direcciones[0].getAddressLine(0)
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

        // ENVIAR REPORTE FINAL
        binding.btnenvR.setOnClickListener {
            val nuevoIntent = Intent(this, reporte_enviado::class.java).apply {
                putExtra("TIPO_REPORTE", tipoReporte)
                putExtra("LATITUD", latitud)
                putExtra("LONGITUD", longitud)
                putExtra("DESCRIPCION", descripcionRecibida)

                // PASAMOS LAS URLS DE CLOUDINARY A LA PANTALLA FINAL O BASE DE DATOS
                putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
            }
            startActivity(nuevoIntent)
            finish()
        }

        binding.ubicacionedit.setOnClickListener {
            val intent = Intent(this, reportar_peticionn::class.java)
            startActivity(intent)
        }

        binding.tipoPeticion.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.descripedit.setOnClickListener {
            val intent = Intent(this, reportar_peticionnn::class.java)
            startActivity(intent)
        }

        binding.archivosedi.setOnClickListener {
            val intent = Intent(this, reportar_peticionnn::class.java)
            startActivity(intent)
        }
    }

    // Aqui recibimos a cloud dinary
    private fun obtenerCantidadFotos() {
        // Obtenemos los enlaces web que envió reportar_peticionnn juajuajua
        urlsArchivos = intent.getStringArrayListExtra("URLS_ARCHIVOS")

        val cantidad = urlsArchivos?.size ?: 0

        binding.numfoto.text = when (cantidad) {
            0 -> "Sin archivos adjuntos"
            1 -> "1 archivo subido a la nube"
            else -> "$cantidad archivos subidos a la nube"
        }
    }
}