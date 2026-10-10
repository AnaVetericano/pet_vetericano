package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import com.example.petvetericano.models.confirmarpeticion
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.Int

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding
    private var urlsArchivos: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener cantidad de fotos
        obtenerCantidadFotos()

        // ==========================================
        // RECIBIR DATOS DEL REPORTE
        // ==========================================
        val descripcionRecibida = intent.getStringExtra("DESCRIPCION")
        binding.descri.text = descripcionRecibida ?: "Sin descripción"

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        binding.tvTipoReporte.text = tipoReporte ?: "Sin tipo"

        // ==========================================
        // OBTENER UBICACIÓN CON FORMATO DE 7 DECIMALES
        // ==========================================
        val latitudCruda = intent.getDoubleExtra("LATITUD", 0.0)
        val longitudCruda = intent.getDoubleExtra("LONGITUD", 0.0)

        val latitud = String.format(Locale.US, "%.7f", latitudCruda).toDouble()
        val longitud = String.format(Locale.US, "%.7f", longitudCruda).toDouble()

        if (latitud != 0.0 && longitud != 0.0) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val direcciones = geocoder.getFromLocation(latitud, longitud, 1)

                if (!direcciones.isNullOrEmpty()) {
                    val direccionReal = direcciones[0].getAddressLine(0)
                    binding.ubicacionedit.text = direccionReal
                } else {
                    // Texto de respaldo si el mapa no sabe qué calle es
                    binding.ubicacionedit.text = "Dirección no encontrada (Usar coordenadas)"
                }
            } catch (e: Exception) {
                // Texto de respaldo si falla el internet o el Geocoder crashea
                binding.ubicacionedit.text = "Dirección no encontrada (Usar coordenadas)"
            }
        } else {
            binding.ubicacionedit.text = "Ubicación no seleccionada"
        }

        // ==========================================
        // CAMBIAR PANTALLAS (Botones de edición)
        // ==========================================
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

        // ==========================================
        // BOTÓN CONFIRMAR REPORTE
        // ==========================================
        binding.btnenvR.setOnClickListener {
            getSharedPreferences("ReporteOffline", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("MapaOffline", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("ReporteOfflineMultimedia", MODE_PRIVATE).edit().clear().apply()

            val tipo = binding.tvTipoReporte.text.toString()
            val lugar = binding.ubicacionedit.text.toString()

            binding.btnenvR.isEnabled = false
            Toast.makeText(this, "Guardando reporte...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val sessionManager = SharedPreferencesManager(this@confirmar_reporte)
                    val tokenGuardado = sessionManager.getAccessToken()
                    val idUsuarioLogueado = sessionManager.getUserId()

                    if (!tokenGuardado.isNullOrEmpty()) {
                        RetrofitClient.authToken = tokenGuardado
                    }

                    val prefsReportes = getSharedPreferences("MisReportesPrefs", MODE_PRIVATE)
                    val idTipoFinal = prefsReportes.getInt("ID_TIPO_SELECCIONADO", 1)

                    val responsableFinal = if (idUsuarioLogueado != -1) idUsuarioLogueado else 1
                    val urlFotoCloudinary = if (!urlsArchivos.isNullOrEmpty()) urlsArchivos!![0] else null

                    val nuevaPeticion = confirmarpeticion(
                        id_tipo = idTipoFinal,
                        id_estado = 1,
                        responsable = responsableFinal,
                        descripcion = descripcionRecibida ?: "Sin descripción",
                        prioridad = null,
                        direccion = lugar,
                        latitud = latitud,
                        longitud = longitud,
                        foto = urlFotoCloudinary
                    )

                    val response = RetrofitClient.apiService.enviarPeticion(nuevaPeticion)

                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@confirmar_reporte, "Reporte enviado correctamente", Toast.LENGTH_SHORT).show()

                            val nuevoIntent = Intent(this@confirmar_reporte, reporte_enviado::class.java).apply {
                                putExtra("TIPO_REPORTES", tipo)
                                putExtra("LATITUD", latitud)
                                putExtra("LONGITUD", longitud)
                                putExtra("DESCRIPCION", descripcionRecibida)
                                putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
                            }
                            startActivity(nuevoIntent)
                            finish()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("API_ERROR", "Error: $errorBody")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@confirmar_reporte, "Error al guardar el reporte", Toast.LENGTH_LONG).show()
                            binding.btnenvR.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("API_FALLO", "Fallo la conexión", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@confirmar_reporte, "Fallo de conexión. Revisa tu internet.", Toast.LENGTH_LONG).show()
                        binding.btnenvR.isEnabled = true
                    }
                }
            }
        }
    }

    private fun obtenerCantidadFotos() {
        urlsArchivos = intent.getStringArrayListExtra("URLS_ARCHIVOS")
        val cantidad = urlsArchivos?.size ?: 0
        binding.numfoto.text = if (cantidad == 1) "1 foto" else "$cantidad fotos"
    }
}