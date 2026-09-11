package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding
import java.io.File

class reportar_peticionnn : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionnnBinding

    // Nombres para la libreta offline (SharedPreferences)
    private val PREFS_NAME = "ReporteOfflineMultimedia"
    private val KEY_BORRADOR = "borrador_descripcion"
    private val KEY_PATHS = "borrador_paths"
    private val KEY_IS_VIDEO = "borrador_is_video"

    // Datos de Cloudinary
    private val CLOUD_NAME = "aefeig5y"
    private val UPLOAD_PRESET = "preset_android"

    // Guardamos las fotos/videos seleccionados
    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false

    // URI de la foto que tomaremos con la cámara
    private var photoUri: Uri? = null

    // CÁMARA
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
                    actualizarVisualizacionMultimedia()
                    guardarMultimediaEnPrefs() // Guardar en caché al instante
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

        // =========================================================
        // RECUPERAR DESCRIPCIÓN
        // =========================================================
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val descripcionDelIntent = intent.getStringExtra("DESCRIPCION")
        val textoGuardado = prefs.getString(KEY_BORRADOR, "")

        if (!descripcionDelIntent.isNullOrEmpty()) {
            binding.etDescripcion.setText(descripcionDelIntent)
            prefs.edit().putString(KEY_BORRADOR, descripcionDelIntent).apply()
        } else if (!textoGuardado.isNullOrEmpty()) {
            binding.etDescripcion.setText(textoGuardado)
        }

        // Guardar texto al instante mientras el usuario escribe
        binding.etDescripcion.addTextChangedListener { text ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_BORRADOR, text.toString())
                .apply()
        }

        // Recuperar fotos o videos guardados previamente
        isVideoSelected = prefs.getBoolean(KEY_IS_VIDEO, false)
        val pathsSet = prefs.getStringSet(KEY_PATHS, emptySet())
        if (!pathsSet.isNullOrEmpty()) {
            selectedUris.clear()
            for (path in pathsSet) {
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                    selectedUris.add(uri)
                }
            }
            actualizarVisualizacionMultimedia()
        }
    }

    // GUARDAR RUTAS DE MULTIMEDIA EN PREFERENCES
    private fun guardarMultimediaEnPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_VIDEO, isVideoSelected)

        val pathsSet = mutableSetOf<String>()
        for (uri in selectedUris) {
            uri.path?.let {
                val file = File(filesDir, File(it).name)
                if (file.exists()) {
                    pathsSet.add(file.absolutePath)
                }
            }
        }
        editor.putStringSet(KEY_PATHS, pathsSet)
        editor.apply()
    }

    // INICIALIZAR CLOUDINARY
    private fun initCloudinary() {
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUD_NAME
        try {
            MediaManager.init(this, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // CONFIGURACIÓN DE BOTONES
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

        // Botón para eliminar la primera foto
        binding.btnEliminar1.setOnClickListener {
            if (selectedUris.isNotEmpty()) {
                selectedUris.removeAt(0)
                guardarMultimediaEnPrefs()
                actualizarVisualizacionMultimedia()
                Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show()
            }
        }

        // Botón para eliminar la segunda foto
        binding.btnEliminar2.setOnClickListener {
            if (selectedUris.size > 1) {
                selectedUris.removeAt(1)
                guardarMultimediaEnPrefs()
                actualizarVisualizacionMultimedia()
                Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnContinue.setOnClickListener {
            validarYContinuar()
        }
    }

    // ABRIR CÁMARA
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

    // CREAR ARCHIVO PERMANENTE Y TOMAR FOTO
    private fun tomarFoto() {
        try {
            val archivoFoto = File(filesDir, "foto_reporte_${System.currentTimeMillis()}.jpg")

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

    // ACTUALIZAR LA VISTA DE LAS MINIATURAS Y TEXTOS
    private fun actualizarVisualizacionMultimedia() {
        val cantidad = selectedUris.size

        if (cantidad == 0) {
            binding.layoutFotoCargada.visibility = View.GONE
            binding.cardFoto1.visibility = View.GONE
            binding.cardFoto2.visibility = View.GONE
            binding.btnSubirArchivo.text = "Subir archivo"
            binding.btnSubirArchivo.isEnabled = true
        } else if (cantidad == 1) {
            binding.layoutFotoCargada.visibility = View.VISIBLE
            binding.cardFoto1.visibility = View.VISIBLE
            binding.cardFoto2.visibility = View.GONE

            binding.ivFoto1.setImageURI(selectedUris[0])
            binding.btnSubirArchivo.text = "Tomar otra foto (1/2)"
            binding.btnSubirArchivo.isEnabled = true
        } else if (cantidad >= 2) {
            binding.layoutFotoCargada.visibility = View.VISIBLE
            binding.cardFoto1.visibility = View.VISIBLE
            binding.cardFoto2.visibility = View.VISIBLE

            binding.ivFoto1.setImageURI(selectedUris[0])
            binding.ivFoto2.setImageURI(selectedUris[1])
            binding.btnSubirArchivo.text = "2 fotos seleccionadas"
            binding.btnSubirArchivo.isEnabled = false
        }
    }

    // VALIDAR Y CONTINUAR (CON SUBIDA A CLOUDINARY)
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

    // SUBIR ARCHIVOS A CLOUDINARY Y CAMBIAR DE ACTIVIDAD
    private fun subirArchivosYContinuar(
        descripcion: String,
        latitud: Double,
        longitud: Double,
        tipoReporte: String?
    ) {
        val urlsSubidas = mutableListOf<String>()
        var subidasCompletadas = 0
        val totalArchivos = selectedUris.size

        binding.btnContinue.isEnabled = false
        Toast.makeText(this, "Subiendo...", Toast.LENGTH_SHORT).show()

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
        val referencia = intent.getStringExtra("PUNTO_REFERENCIA")

        val intent = Intent(this, confirmar_reporte::class.java).apply {
            putExtra("TIPO_REPORTE", tipoReporte)
            putExtra("DESCRIPCION", descripcion)
            putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
            putExtra("LATITUD", latitud)
            putExtra("LONGITUD", longitud)
            putExtra("PUNTO_REFERENCIA", referencia)
        }
        startActivity(intent)
    }
}