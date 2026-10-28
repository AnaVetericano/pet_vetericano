package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import com.example.petvetericano.offline.ReporteEncolador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding
    private var rutasArchivos: ArrayList<String> = arrayListOf()
    private var esVideoBanderas: BooleanArray = booleanArrayOf()
    private var direccionResuelta: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        rutasArchivos = intent.getStringArrayListExtra("RUTAS_ARCHIVOS") ?: arrayListOf()
        esVideoBanderas = intent.getBooleanArrayExtra("ES_VIDEO") ?: booleanArrayOf()

        obtenerCantidadFotos()

        val descripcionRecibida = intent.getStringExtra("DESCRIPCION")
        binding.descri.text = descripcionRecibida ?: getString(R.string.acta_offline_sin_observaciones)

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        binding.tvTipoReporte.text = tipoReporte ?: getString(R.string.reporte_offline_tipo_predeterminado)

        val latitudCruda = intent.getDoubleExtra("LATITUD", 0.0)
        val longitudCruda = intent.getDoubleExtra("LONGITUD", 0.0)

        val latitud = String.format(Locale.US, "%.7f", latitudCruda).toDouble()
        val longitud = String.format(Locale.US, "%.7f", longitudCruda).toDouble()

        if (latitud != 0.0 && longitud != 0.0) {
            binding.ubicacionedit.text = getString(R.string.reporte_offline_cargando_direccion)
            lifecycleScope.launch(Dispatchers.IO) {
                val dir = resolverDireccion(latitud, longitud)
                direccionResuelta = dir
                withContext(Dispatchers.Main) {
                    binding.ubicacionedit.text = dir
                }
            }
        } else {
            binding.ubicacionedit.text = getString(R.string.reporte_offline_ubicacion_no_seleccionada)
        }

        binding.btndev.setOnClickListener {
            finish()
        }

        binding.ubicacionedit.setOnClickListener {
            startActivity(Intent(this, reportar_peticionn::class.java))
        }

        binding.tipoPeticion.setOnClickListener {
            startActivity(Intent(this, reportar_peticion::class.java))
        }

        binding.descripedit.setOnClickListener {
            startActivity(Intent(this, reportar_peticionnn::class.java))
        }

        binding.archivosedi.setOnClickListener {
            startActivity(Intent(this, reportar_peticionnn::class.java))
        }

        binding.btnenvR.setOnClickListener {
            binding.btnenvR.isEnabled = false
            Toast.makeText(this, R.string.reporte_offline_guardando_reporte, Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                val dirFinal = direccionResuelta ?: resolverDireccion(latitud, longitud)
                direccionResuelta = dirFinal

                val prefsReportes = getSharedPreferences("MisReportesPrefs", MODE_PRIVATE)
                val idTipoFinal = intent.getIntExtra(
                    "ID_TIPO_SELECCIONADO",
                    prefsReportes.getInt("ID_TIPO_SELECCIONADO", 1)
                )

                val evidenciasList = mutableListOf<Pair<String, Boolean>>()
                for (i in rutasArchivos.indices) {
                    val esVideo = if (i < esVideoBanderas.size) esVideoBanderas[i] else false
                    evidenciasList.add(Pair(rutasArchivos[i], esVideo))
                }

                val idLocal = ReporteEncolador.encolar(
                    context = this@confirmar_reporte,
                    idTipo = idTipoFinal,
                    tipoNombre = tipoReporte ?: getString(R.string.reporte_offline_tipo_predeterminado),
                    descripcion = descripcionRecibida ?: "",
                    direccion = dirFinal,
                    latitud = latitud,
                    longitud = longitud,
                    evidencias = evidenciasList
                )

                if (idLocal != null && idLocal > 0L) {
                    getSharedPreferences("MisReportesPrefs", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("MapaOffline", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("ReporteOfflineMultimedia", MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("ReporteOffline", MODE_PRIVATE).edit().clear().apply()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@confirmar_reporte, R.string.reporte_offline_guardado_exitoso, Toast.LENGTH_SHORT).show()
                        val nuevoIntent = Intent(this@confirmar_reporte, reporte_enviado::class.java)
                        startActivity(nuevoIntent)
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.btnenvR.isEnabled = true
                        Toast.makeText(this@confirmar_reporte, R.string.reporte_offline_error_guardar_reporte, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun resolverDireccion(latitud: Double, longitud: Double): String {
        if (latitud == 0.0 && longitud == 0.0) {
            return getString(R.string.reporte_offline_ubicacion_no_seleccionada)
        }
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val direcciones = geocoder.getFromLocation(latitud, longitud, 1)
            if (!direcciones.isNullOrEmpty() && !direcciones[0].getAddressLine(0).isNullOrBlank()) {
                direcciones[0].getAddressLine(0)
            } else {
                getString(R.string.reporte_offline_direccion_coordenadas, latitud, longitud)
            }
        } catch (_: Exception) {
            getString(R.string.reporte_offline_direccion_coordenadas, latitud, longitud)
        }
    }

    private fun obtenerCantidadFotos() {
        val cantidad = rutasArchivos.size
        if (cantidad == 0) {
            binding.numfoto.text = getString(R.string.reporte_offline_sin_evidencias)
        } else {
            val hayVideo = esVideoBanderas.any { it } ||
                rutasArchivos.any { it.endsWith(".mp4", ignoreCase = true) }
            binding.numfoto.text = when {
                cantidad == 1 && hayVideo -> getString(R.string.reporte_offline_un_video)
                cantidad == 1 && !hayVideo -> getString(R.string.reporte_offline_una_foto)
                cantidad == 2 && hayVideo -> getString(R.string.reporte_offline_foto_y_video)
                else -> getString(R.string.reporte_offline_evidencias_formato, cantidad)
            }
        }
    }
}