package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding
import java.io.File

class reportar_peticionnn : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionnnBinding

    // Aqui van los datos de cloud
    private val CLOUD_NAME = "aefeig5y"
    private val UPLOAD_PRESET = "preset_android"

    // Guardamos las fotos/videos seleccionados
    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false

    // URI de la foto que tomaremos con la cámara
    private var photoUri: Uri? = null

    // ---------------------------------------------------------
    // GALERÍA
    // ---------------------------------------------------------
    private val pickMedia =
        registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(2)
        ) { uris ->
            if (uris.isNotEmpty()) {
                processSelectedMedia(uris)
            }
        }

    // ---------------------------------------------------------
    // CÁMARA
    // ---------------------------------------------------------
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityReportarPeticionnnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        // Inicializamos la conexión con Cloudinary
        initCloudinary()

        // RESULTADO DE LA CÁMARA
        cameraLauncher =
            registerForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                if (success && photoUri != null) {
                    selectedUris.add(photoUri!!)
                    actualizarTextoFotos()
                    Toast.makeText(
                        this,
                        "Foto tomada correctamente 📷",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "No se pudo tomar la foto",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        setupUI()
    }

    // ---------------------------------------------------------
    // INICIALIZAR CLOUDINARY
    // ---------------------------------------------------------
    private fun initCloudinary() {
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUD_NAME
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------
    // CONFIGURACIÓN DE BOTONES
    // ---------------------------------------------------------
    private fun setupUI() {
        binding.btndev.setOnClickListener {
            finish()
        }

        binding.btnSubirArchivo.setOnClickListener {
            if (selectedUris.size >= 2) {
                Toast.makeText(
                    this,
                    "Solo puedes tomar máximo 2 fotos",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                abrirCamara()
            }
        }

        binding.btnContinue.setOnClickListener {
            validarYContinuar()
        }
    }

    // ---------------------------------------------------------
    // ABRIR CÁMARA
    // ---------------------------------------------------------
    private fun abrirCamara() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            tomarFoto()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                101
            )
        }
    }

    // ---------------------------------------------------------
    // CREAR ARCHIVO Y TOMAR FOTO
    // ---------------------------------------------------------
    private fun tomarFoto() {
        try {
            val archivoFoto = File.createTempFile(
                "foto_reporte_",
                ".jpg",
                cacheDir
            )

            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                archivoFoto
            )

            cameraLauncher.launch(photoUri!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Error al abrir la cámara",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------------------------------------------------
    // MOSTRAR CANTIDAD DE FOTOS
    // ---------------------------------------------------------
    private fun actualizarTextoFotos() {
        val cantidad = selectedUris.size

        when (cantidad) {
            0 -> binding.numimagenes.text = ""
            1 -> binding.numimagenes.text = "📷 1 foto tomada correctamente"
            2 -> binding.numimagenes.text = "📷 2 fotos tomadas correctamente"
        }

        binding.btnSubirArchivo.text = if (cantidad < 2) {
            "Tomar otra foto"
        } else {
            "2 fotos seleccionadas"
        }
    }

    // ---------------------------------------------------------
    // PROCESAR FOTOS / VIDEOS DE GALERÍA
    // ---------------------------------------------------------
    private fun processSelectedMedia(uris: List<Uri>) {
        val resolver = contentResolver

        for (uri in uris) {
            val type = resolver.getType(uri) ?: continue

            if (type.startsWith("video/")) {
                if (selectedUris.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Solo puedes subir 2 fotos O 1 video.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val duration = getVideoDuration(uri)
                if (duration > 15000) {
                    Toast.makeText(
                        this,
                        "El video no puede durar más de 15 segundos.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                isVideoSelected = true
                selectedUris.clear()
                selectedUris.add(uri)

                binding.btnSubirArchivo.text = "Video seleccionado (${duration / 1000}s)"
                binding.numimagenes.text = "🎥 1 video seleccionado"
                return
            } else if (type.startsWith("image/")) {
                if (isVideoSelected) {
                    Toast.makeText(
                        this,
                        "No puedes combinar fotos con un video.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                if (selectedUris.size < 2) {
                    selectedUris.add(uri)
                }
            }
        }

        if (!isVideoSelected) {
            actualizarTextoFotos()
        }
    }

    // ---------------------------------------------------------
    // DURACIÓN DEL VIDEO
    // ---------------------------------------------------------
    private fun getVideoDuration(uri: Uri): Long {
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = time?.toLong() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
        return duration
    }

    // ---------------------------------------------------------
    // VALIDAR Y CONTINUAR (CON SUBIDA A CLOUDINARY)
    // ---------------------------------------------------------
    private fun validarYContinuar() {
        val descripcion = binding.etDescripcion.text.toString().trim()

        if (descripcion.isEmpty()) {
            binding.tilDescripcion.error = "Ingresa una descripción de la incidencia"
            return
        }

        binding.tilDescripcion.error = null

        val latitud = intent.getDoubleExtra("LATITUD", 0.0)
        val longitud = intent.getDoubleExtra("LONGITUD", 0.0)
        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")

        if (selectedUris.isNotEmpty()) {
            subirArchivosYContinuar(descripcion, latitud, longitud, tipoReporte)
        } else {
            navegarAConfirmar(descripcion, latitud, longitud, tipoReporte, arrayListOf())
        }
    }

    // ---------------------------------------------------------
    // SUBIR ARCHIVOS A CLOUDINARY Y CAMBIAR DE ACTIVIDAD
    // ---------------------------------------------------------
    private fun subirArchivosYContinuar(
        descripcion: String,
        latitud: Double,
        longitud: Double,
        tipoReporte: String?
    ) {
        val urlsSubidas = mutableListOf<String>()
        var subidasCompletadas = 0
        val totalArchivos = selectedUris.size

        // Desactivar el botón para evitar doble toque mientras sube
        binding.btnContinue.isEnabled = false
        Toast.makeText(this, "Subiendo multimedia a Cloudinary...", Toast.LENGTH_SHORT).show()

        for (uri in selectedUris) {
            MediaManager.get()
                .upload(uri)
                .unsigned(UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val url = resultData?.get("secure_url") as? String
                        if (url != null) {
                            urlsSubidas.add(url)
                        }

                        subidasCompletadas++

                        // Cuando se completen todas las subidas de la lista
                        if (subidasCompletadas == totalArchivos) {
                            binding.btnContinue.isEnabled = true
                            navegarAConfirmar(
                                descripcion,
                                latitud,
                                longitud,
                                tipoReporte,
                                ArrayList(urlsSubidas)
                            )
                        }
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        binding.btnContinue.isEnabled = true
                        Toast.makeText(
                            applicationContext,
                            "Error al subir: ${error?.description}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        }
    }

    private fun navegarAConfirmar(
        descripcion: String,
        latitud: Double,
        longitud: Double,
        tipoReporte: String?,
        urlsArchivos: ArrayList<String>
    ) {
        val intent = Intent(this, confirmar_reporte::class.java).apply {
            putExtra("TIPO_REPORTE", tipoReporte)
            putExtra("DESCRIPCION", descripcion)
            putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
            putExtra("LATITUD", latitud)
            putExtra("LONGITUD", longitud)
        }
        startActivity(intent)
    }
}