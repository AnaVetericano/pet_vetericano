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
import org.json.JSONObject
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
        // OBTENER UBICACIÓN CON FORMATO DE 7 DECIMALES
        // ==========================================
        val latitudCruda = intent.getDoubleExtra("LATITUD", 0.0)
        val longitudCruda = intent.getDoubleExtra("LONGITUD", 0.0)

        // Limitar a 7 decimales exactos para evitar errores en el backend
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
                    // ✅ Cargar el token desde SharedPreferences antes de enviar para evitar errores 401
                    val prefsSesionToken = getSharedPreferences("SesionUsuario", MODE_PRIVATE)
                    val tokenGuardado = prefsSesionToken.getString("TOKEN", null)
                    if (!tokenGuardado.isNullOrEmpty()) {
                        RetrofitClient.authToken = tokenGuardado
                    }

                    val prefs = getSharedPreferences("MisReportesPrefs", MODE_PRIVATE)
                    val idTipoFinal = prefs.getInt("ID_TIPO_SELECCIONADO", 1)

                    // 1. Leemos el ID del usuario logueado
                    val idUsuarioLogueado = prefsSesionToken.getInt("ID_USUARIO", 1)

                    val urlFotoCloudinary = if (!urlsArchivos.isNullOrEmpty()) urlsArchivos!![0] else null

                    // 2. Construir la petición con los datos unificados
                    val nuevaPeticion = confirmarpeticion(
                        id_tipo = idTipoFinal,
                        id_estado = 1, // Se envía 1 ("Pendiente") para cumplir con la regla del backend
                        responsable = idUsuarioLogueado,
                        descripcion = descripcionRecibida ?: "Sin descripción",
                        prioridad = null,
                        // Nuevos campos unificados enviados al mismo endpoint
                        direccion = lugar,
                        latitud = latitud,
                        longitud = longitud,
                        foto = urlFotoCloudinary
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

                val templateParams = JSONObject().apply {
                    put("tipo", tipo ?: "Sin tipo")
                    put("lugar", lugar)
                    put("descripcion", descripcion)
                    put("latitud", latitud.toString())
                    put("longitud", longitud.toString())
                    put("archivos", archivos)
                }

                val json = JSONObject().apply {
                    put("service_id", EMAILJS_SERVICE_ID)
                    put("template_id", EMAILJS_TEMPLATE_ID)
                    put("user_id", EMAILJS_PUBLIC_KEY)
                    put("template_params", templateParams)
                }

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

    private fun obtenerCantidadFotos() {
        urlsArchivos = intent.getStringArrayListExtra("URLS_ARCHIVOS")
        val cantidad = urlsArchivos?.size ?: 0
        binding.numfoto.text = if (cantidad == 1) "1 foto" else "$cantidad fotos"
    }
}