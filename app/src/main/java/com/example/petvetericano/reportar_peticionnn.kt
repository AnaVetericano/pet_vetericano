package com.example.petvetericano

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding

class reportar_peticionnn : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionnnBinding

    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false

    // Corrección 1: Usar PickMultipleVisualMedia para permitir hasta 2 elementos
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
        if (uris.isNotEmpty()) {
            processSelectedMedia(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportarPeticionnnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }
    private fun setupUI() {
        // Botón regresar
        binding.btndev.setOnClickListener {
            finish()
        }

        // Botón subir archivo
        binding.btnSubirArchivo.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivity(intent)
        }

        // Botón continuar
        binding.btnContinue.setOnClickListener {
            validarYContinuar()
        }
    }

    private fun processSelectedMedia(uris: List<Uri>) {
        val resolver = contentResolver

        for (uri in uris) {
            val type = resolver.getType(uri) ?: continue

            if (type.startsWith("video/")) {
                if (selectedUris.isNotEmpty()) {
                    Toast.makeText(this, "Solo puedes subir 2 fotos O 1 video.", Toast.LENGTH_SHORT).show()
                    return
                }

                val duration = getVideoDuration(uri)
                if (duration > 15000) { // Max 15 segundos
                    Toast.makeText(this, "El video no puede durar más de 15 segundos.", Toast.LENGTH_LONG).show()
                    return
                }

                isVideoSelected = true
                selectedUris.clear()
                selectedUris.add(uri)
                binding.btnSubirArchivo.text = "Video seleccionado (${duration / 1000}s)"
                return

            } else if (type.startsWith("image/")) {
                if (isVideoSelected) {
                    Toast.makeText(this, "No puedes combinar fotos con un video.", Toast.LENGTH_SHORT).show()
                    return
                }

                if (selectedUris.size < 2) {
                    selectedUris.add(uri)
                }
            }
        }

        if (!isVideoSelected) {
            binding.btnSubirArchivo.text = "${selectedUris.size} foto(s) seleccionada(s)"
        }
    }

    private fun getVideoDuration(uri: Uri): Long {
        var duration = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = time?.toLong() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return duration
    }

    private fun validarYContinuar() {
        val descripcion = binding.etDescripcion.text.toString().trim()

        if (descripcion.isEmpty()) {
            binding.tilDescripcion.error = "Ingresa una descripción de la incidencia"
            return
        }

        binding.tilDescripcion.error = null

        val intent = Intent(this, confirmar_reporte::class.java).apply {
            putExtra("DESCRIPCION", descripcion)
            putParcelableArrayListExtra("ARCHIVOS", ArrayList(selectedUris))
        }
        startActivity(intent)
    }
}