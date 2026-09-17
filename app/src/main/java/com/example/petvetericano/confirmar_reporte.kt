package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.Int

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding
    private var urlsArchivos: ArrayList<String>? = null

    // ==========================================
    // DATOS DE EMAILJS
    // ==========================================
    private val EMAILJS_SERVICE_ID = "service_9vahcuy"
    private val EMAILJS_TEMPLATE_ID = "template_0y9diuz"
    private val EMAILJS_PUBLIC_KEY = "JV0ON_2pnb7q0zXvH"
    private val EMAILJS_URL = "https://api.emailjs.com/api/v1.0/email/send"

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
        // OBTENER UBICACIÓN
        // ==========================================
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
            // 1. Limpiar cachés
            getSharedPreferences("ReporteOffline", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("MapaOffline", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("ReporteOfflineMultimedia", MODE_PRIVATE).edit().clear().apply()

            // 2. Obtener información actual de la interfaz
            val tipo = binding.tvTipoReporte.text.toString()
            val lugar = binding.ubicacionedit.text.toString()
            val descripcion = binding.descri.text.toString()

            // Desactivar botón para evitar dobles clics
            binding.btnenvR.isEnabled = false
            Toast.makeText(this, "Guardando y enviando reporte...", Toast.LENGTH_SHORT).show()

            // 3. Ejecutar la petición a la API y luego el correo
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Construir el objeto para la API
                    // IMPORTANTE: Asegúrate de que los nombres coincidan exactamente con tu data class
                    val nuevaPeticion = confirmarpeticion(
                        id_tipo = 1,
                        id_ubicacion = 1,
                        id_estado = 1,
                        responsable = 5,
                        descripcion = descripcionRecibida ?: "Sin descripción",
                        prioridad = "alta",
                        fecha_asignacion = "2026-09-16T23:23:48Z"
                    )

                    // Hacer la petición a la API (Retrofit)
                    val response = RetrofitClient.apiService.enviarPeticion(nuevaPeticion)

                    if (response.isSuccessful) {
                        android.util.Log.d("API_EXITO", "Petición guardada en BD")

                        // Si la BD guardó correctamente, enviamos el correo
                        withContext(Dispatchers.Main) {
                            enviarCorreoSilenciosoYContinuar(
                                tipo = tipo,
                                lugar = lugar,
                                descripcion = descripcion,
                                latitud = latitud,
                                longitud = longitud,
                                tipoReporte = tipoReporte,
                                descripcionRecibida = descripcionRecibida
                            )
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("API_ERROR", "Error: $errorBody")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@confirmar_reporte, "Error al guardar en base de datos", Toast.LENGTH_LONG).show()
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
    } // FIN DE ONCREATE

    // ==================================================
    // FUNCIÓN PARA ENVIAR CORREO MEDIANTE EMAILJS
    // ==================================================
    private fun enviarCorreoSilenciosoYContinuar(
        tipo: String?,
        lugar: String,
        descripcion: String,
        latitud: Double,
        longitud: Double,
        tipoReporte: String?,
        descripcionRecibida: String?
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            var correoEnviado = false
            try {
                val archivos = urlsArchivos?.joinToString("\n") ?: "No hay archivos"

                val templateParams = JSONObject()
                templateParams.put("tipo", tipo ?: "Sin tipo")
                templateParams.put("lugar", lugar)
                templateParams.put("descripcion", descripcion)
                templateParams.put("latitud", latitud.toString())
                templateParams.put("longitud", longitud.toString())
                templateParams.put("archivos", archivos)

                val json = JSONObject()
                json.put("service_id", EMAILJS_SERVICE_ID)
                json.put("template_id", EMAILJS_TEMPLATE_ID)
                json.put("user_id", EMAILJS_PUBLIC_KEY)
                json.put("template_params", templateParams)

                val url = URL(EMAILJS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                connection.outputStream.use { outputStream ->
                    outputStream.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    correoEnviado = true
                } else {
                    android.util.Log.e("EMAILJS", "Código HTTP: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("EMAILJS", "Error enviando correo", e)
                correoEnviado = false
            }

            withContext(Dispatchers.Main) {
                if (correoEnviado) {
                    Toast.makeText(this@confirmar_reporte, "Reporte enviado correctamente", Toast.LENGTH_SHORT).show()

                    // IR A PANTALLA FINAL
                    val nuevoIntent = Intent(this@confirmar_reporte, reporte_enviado::class.java).apply {
                        putExtra("TIPO_REPORTE", tipoReporte)
                        putExtra("LATITUD", latitud)
                        putExtra("LONGITUD", longitud)
                        putExtra("DESCRIPCION", descripcionRecibida)
                        putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
                    }
                    startActivity(nuevoIntent)
                    finish()
                } else {
                    Toast.makeText(this@confirmar_reporte, "No se pudo enviar el correo", Toast.LENGTH_LONG).show()
                    binding.btnenvR.isEnabled = true
                }
            }
        }
    }

    // ==================================================
    // OBTENER CANTIDAD DE FOTOS
    // ==================================================
    private fun obtenerCantidadFotos() {
        urlsArchivos = intent.getStringArrayListExtra("URLS_ARCHIVOS")
        val cantidad = urlsArchivos?.size ?: 0
        binding.numfoto.text = if (cantidad == 1) "1 foto" else "$cantidad fotos"
    }
}