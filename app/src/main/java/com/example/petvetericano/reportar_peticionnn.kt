package com.example.petvetericano

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding
import java.io.File

class reportar_peticionnn : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionnnBinding

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

        binding =
            ActivityReportarPeticionnnBinding.inflate(layoutInflater)

        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        // -----------------------------------------------------
        // RESULTADO DE LA CÁMARA
        // -----------------------------------------------------

        cameraLauncher =
            registerForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->

                if (success && photoUri != null) {

                    // Guardamos la foto en la lista
                    selectedUris.add(photoUri!!)

                    // Actualizamos el TextView
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
    // CONFIGURACIÓN DE BOTONES
    // ---------------------------------------------------------

    private fun setupUI() {

        // Botón regresar
        binding.btndev.setOnClickListener {
            finish()
        }

        // Botón subir archivo
        binding.btnSubirArchivo.setOnClickListener {

            // Máximo 2 fotos
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

        // Botón continuar
        binding.btnContinue.setOnClickListener {
            validarYContinuar()
        }
    }

    // ---------------------------------------------------------
    // ABRIR CÁMARA
    // ---------------------------------------------------------

    private fun abrirCamara() {

        if (
            checkSelfPermission(Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {

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

            // Creamos el archivo donde se guardará la foto
            val archivoFoto = File.createTempFile(
                "foto_reporte_",
                ".jpg",
                cacheDir
            )

            // Convertimos el archivo en URI
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                archivoFoto
            )

            // Abrimos la cámara
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

            0 -> {
                binding.numimagenes.text = ""
            }

            1 -> {
                binding.numimagenes.text =
                    "📷 1 foto tomada correctamente"
            }

            2 -> {
                binding.numimagenes.text =
                    "📷 2 fotos tomadas correctamente"
            }
        }

        // Cambiamos también el texto del botón
        binding.btnSubirArchivo.text =
            if (cantidad < 2) {
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

            val type =
                resolver.getType(uri) ?: continue

            // -------------------------------------------------
            // VIDEO
            // -------------------------------------------------

            if (type.startsWith("video/")) {

                if (selectedUris.isNotEmpty()) {

                    Toast.makeText(
                        this,
                        "Solo puedes subir 2 fotos O 1 video.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return
                }

                val duration =
                    getVideoDuration(uri)

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

                binding.btnSubirArchivo.text =
                    "Video seleccionado (${duration / 1000}s)"

                binding.numimagenes.text =
                    "🎥 1 video seleccionado"

                return
            }

            // -------------------------------------------------
            // IMAGEN
            // -------------------------------------------------

            else if (type.startsWith("image/")) {

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

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(
                this,
                uri
            )

            val time =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )

            duration =
                time?.toLong() ?: 0L

        } catch (e: Exception) {

            e.printStackTrace()

        } finally {

            retriever.release()
        }

        return duration
    }

    // ---------------------------------------------------------
    // VALIDAR Y CONTINUAR
    // ---------------------------------------------------------

    private fun validarYContinuar() {

        val descripcion =
            binding.etDescripcion.text
                .toString()
                .trim()

        if (descripcion.isEmpty()) {

            binding.tilDescripcion.error =
                "Ingresa una descripción de la incidencia"

            return
        }

        binding.tilDescripcion.error = null

        // Coordenadas que vienen del mapa
        val latitud =
            intent.getDoubleExtra(
                "LATITUD",
                0.0
            )

        val longitud =
            intent.getDoubleExtra(
                "LONGITUD",
                0.0
            )

        val tipoReporte =
            intent.getStringExtra(
                "TIPO_REPORTE"
            )

        // -----------------------------------------------------
        // ENVIAMOS TODO A CONFIRMAR REPORTE
        // -----------------------------------------------------

        val intent =
            Intent(
                this,
                confirmar_reporte::class.java
            ).apply {

                putExtra(
                    "TIPO_REPORTE",
                    tipoReporte
                )

                putExtra(
                    "DESCRIPCION",
                    descripcion
                )

                // ENVIAMOS LAS FOTOS
                putParcelableArrayListExtra(
                    "ARCHIVOS",
                    ArrayList(selectedUris)
                )

                // ENVIAMOS LAS COORDENADAS
                putExtra(
                    "LATITUD",
                    latitud
                )

                putExtra(
                    "LONGITUD",
                    longitud
                )
            }

        startActivity(intent)
    }
}