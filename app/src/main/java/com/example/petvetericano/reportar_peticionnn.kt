package com.example.petvetericano

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.petvetericano.databinding.ActivityReportarPeticionnnBinding
import com.example.petvetericano.databinding.BottomSheetAdjuntarEvidenciaBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream

data class EvidenciaItem(
    val uri: Uri,
    val esVideo: Boolean,
    val localFile: File? = null
)

class reportar_peticionnn : AppCompatActivity() {

    private lateinit var binding: ActivityReportarPeticionnnBinding

    // Nombres para la libreta offline (SharedPreferences)
    private val PREFS_NAME = "ReporteOfflineMultimedia"
    private val KEY_BORRADOR = "borrador_descripcion"
    private val KEY_PATHS = "borrador_paths_v2"
    private val KEY_IS_VIDEO = "borrador_is_video_v2"

    // Datos de Cloudinary
    private val CLOUD_NAME = "aefeig5y"
    private val UPLOAD_PRESET = "preset_android"

    // Evidencias multimedia seleccionadas (hasta 2)
    private val selectedMediaList = mutableListOf<EvidenciaItem>()

    // Archivo y URI temporal para captura de cámara
    private var tempCameraFile: File? = null
    private var tempCameraUri: Uri? = null
    private var pendingMediaEsVideo: Boolean = false

    // Launchers de actividad para foto y video
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            tempCameraFile?.let { optimizarImagen(it) }
            selectedMediaList.add(EvidenciaItem(tempCameraUri!!, esVideo = false, localFile = tempCameraFile))
            actualizarVisualizacionMultimedia()
            guardarMultimediaEnPrefs()
            Toast.makeText(this, "Foto capturada y optimizada 📷", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val localUri = copiarUriALocal(it, esVideo = false)
            if (localUri != null) {
                val file = File(localUri.path ?: "")
                selectedMediaList.add(EvidenciaItem(localUri, esVideo = false, localFile = file))
                actualizarVisualizacionMultimedia()
                guardarMultimediaEnPrefs()
                Toast.makeText(this, "Foto seleccionada 📷", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val recordVideoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && tempCameraUri != null) {
            if (tempCameraFile != null && tempCameraFile!!.length() > 10L * 1024 * 1024) {
                val sizeMb = tempCameraFile!!.length() / (1024 * 1024)
                tempCameraFile!!.delete()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Video excede el límite")
                    .setMessage("El video grabado pesa ${sizeMb} MB y supera el límite de 10 MB permitido.\n\nPor favor graba un video más breve (máx. 20-30 segundos).")
                    .setPositiveButton("Aceptar", null)
                    .show()
            } else {
                selectedMediaList.add(EvidenciaItem(tempCameraUri!!, esVideo = true, localFile = tempCameraFile))
                actualizarVisualizacionMultimedia()
                guardarMultimediaEnPrefs()
                Toast.makeText(this, "Video grabado correctamente 🎥", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val localUri = copiarUriALocal(it, esVideo = true)
            if (localUri != null) {
                val file = File(localUri.path ?: "")
                if (file.exists() && file.length() > 10L * 1024 * 1024) {
                    val sizeMb = file.length() / (1024 * 1024)
                    file.delete()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Video demasiado pesado")
                        .setMessage("El video seleccionado pesa ${sizeMb} MB y supera el límite de 10 MB permitido.\n\nPor favor selecciona un video más corto o graba un clip breve.")
                        .setPositiveButton("Aceptar", null)
                        .show()
                } else {
                    selectedMediaList.add(EvidenciaItem(localUri, esVideo = true, localFile = file))
                    actualizarVisualizacionMultimedia()
                    guardarMultimediaEnPrefs()
                    Toast.makeText(this, "Video seleccionado 🎥", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingMediaEsVideo) {
                lanzarGrabacionVideo()
            } else {
                lanzarCapturaFoto()
            }
        } else {
            Toast.makeText(this, "Permiso de cámara requerido para capturar evidencias", Toast.LENGTH_SHORT).show()
        }
    }

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

        // Recuperar estado en caso de recreación
        if (savedInstanceState != null) {
            tempCameraUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable("SAVED_CAMERA_URI", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("SAVED_CAMERA_URI")
            }
            val savedPath = savedInstanceState.getString("SAVED_CAMERA_FILE")
            if (!savedPath.isNullOrEmpty()) {
                tempCameraFile = File(savedPath)
            }
            pendingMediaEsVideo = savedInstanceState.getBoolean("PENDING_MEDIA_IS_VIDEO", false)

            val urisList = savedInstanceState.getStringArrayList("SAVED_MEDIA_URIS")
            val isVideoList = savedInstanceState.getBooleanArray("SAVED_MEDIA_IS_VIDEO")
            if (urisList != null && isVideoList != null && urisList.size == isVideoList.size) {
                selectedMediaList.clear()
                for (i in urisList.indices) {
                    selectedMediaList.add(EvidenciaItem(Uri.parse(urisList[i]), isVideoList[i]))
                }
            }
        }

        initCloudinary()
        setupUI()

        // Recuperar texto de descripción
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val descripcionDelIntent = intent.getStringExtra("DESCRIPCION")
        val textoGuardado = prefs.getString(KEY_BORRADOR, "")

        if (!descripcionDelIntent.isNullOrEmpty()) {
            binding.etDescripcion.setText(descripcionDelIntent)
            prefs.edit().putString(KEY_BORRADOR, descripcionDelIntent).apply()
        } else if (!textoGuardado.isNullOrEmpty()) {
            binding.etDescripcion.setText(textoGuardado)
        }

        // Guardar descripción en borrador en tiempo real
        binding.etDescripcion.addTextChangedListener { text ->
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_BORRADOR, text.toString())
                .apply()
        }

        // Si no venía de savedInstanceState, recuperar borrador local persistente de multimedia
        if (savedInstanceState == null) {
            val pathsStr = prefs.getString(KEY_PATHS, "") ?: ""
            val isVideoStr = prefs.getString(KEY_IS_VIDEO, "") ?: ""
            if (pathsStr.isNotEmpty()) {
                val paths = pathsStr.split(";")
                val isVideos = isVideoStr.split(";")
                selectedMediaList.clear()
                for (i in paths.indices) {
                    val file = File(paths[i])
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.fileprovider",
                            file
                        )
                        val esVideo = if (i < isVideos.size) isVideos[i] == "1" else false
                        selectedMediaList.add(EvidenciaItem(uri, esVideo, file))
                    }
                }
            }
        }

        actualizarVisualizacionMultimedia()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tempCameraUri?.let { outState.putParcelable("SAVED_CAMERA_URI", it) }
        tempCameraFile?.let { outState.putString("SAVED_CAMERA_FILE", it.absolutePath) }
        outState.putBoolean("PENDING_MEDIA_IS_VIDEO", pendingMediaEsVideo)

        val urisList = ArrayList(selectedMediaList.map { it.uri.toString() })
        val isVideoList = selectedMediaList.map { it.esVideo }.toBooleanArray()
        outState.putStringArrayList("SAVED_MEDIA_URIS", urisList)
        outState.putBooleanArray("SAVED_MEDIA_IS_VIDEO", isVideoList)
    }

    private fun guardarMultimediaEnPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        val paths = mutableListOf<String>()
        val isVideo = mutableListOf<String>()

        for (item in selectedMediaList) {
            val path = item.localFile?.absolutePath ?: (item.uri.path?.let { File(filesDir, File(it).name).absolutePath } ?: "")
            if (path.isNotEmpty() && File(path).exists()) {
                paths.add(path)
                isVideo.add(if (item.esVideo) "1" else "0")
            }
        }
        editor.putString(KEY_PATHS, paths.joinToString(";"))
        editor.putString(KEY_IS_VIDEO, isVideo.joinToString(";"))
        editor.apply()
    }

    private fun initCloudinary() {
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUD_NAME
        try {
            MediaManager.init(this, config)
        } catch (_: Exception) {
            // MediaManager ya inicializado
        }
    }

    private fun setupUI() {
        binding.btndev.setOnClickListener {
            finish()
        }

        binding.btnAdjuntarFoto.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = false)
        }

        binding.btnAdjuntarVideo.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = true)
        }

        binding.btnEliminar1.setOnClickListener {
            if (selectedMediaList.isNotEmpty()) {
                selectedMediaList.removeAt(0)
                guardarMultimediaEnPrefs()
                actualizarVisualizacionMultimedia()
                Toast.makeText(this, "Evidencia eliminada", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEliminar2.setOnClickListener {
            if (selectedMediaList.size > 1) {
                selectedMediaList.removeAt(1)
                guardarMultimediaEnPrefs()
                actualizarVisualizacionMultimedia()
                Toast.makeText(this, "Evidencia eliminada", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnContinue.setOnClickListener {
            validarYContinuar()
        }
    }

    private fun mostrarBottomSheetEvidencia(esVideo: Boolean) {
        if (selectedMediaList.size >= 2) {
            Toast.makeText(this, "Solo puedes adjuntar máximo 2 evidencias", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheet = BottomSheetDialog(
            this,
            R.style.ThemeOverlay_Vetericano_BottomSheetDialog
        )
        val sheetBinding = BottomSheetAdjuntarEvidenciaBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        if (esVideo) {
            sheetBinding.ivSheetHeaderIcon.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvSheetTitulo.text = "Adjuntar evidencia en video"
            sheetBinding.tvSheetSubtitulo.text = "Selecciona el origen para documentar en video"

            sheetBinding.ivIconCamara.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvTituloCamara.text = "Grabar video en campo"
            sheetBinding.tvSubtituloCamara.text = "Graba directamente con la cámara del dispositivo"

            sheetBinding.ivIconGaleria.setImageResource(R.drawable.ic_videocam)
            sheetBinding.tvTituloGaleria.text = "Elegir video de la galería"
            sheetBinding.tvSubtituloGaleria.text = "Seleccionar archivo de video guardado"

            sheetBinding.tvSheetInfoNota.text = "Almacenamiento en la nube: Graba clips breves de hasta 20-30 seg (máx. 10 MB)."
        } else {
            sheetBinding.ivSheetHeaderIcon.setImageResource(R.drawable.camera)
            sheetBinding.tvSheetTitulo.text = "Adjuntar evidencia fotográfica"
            sheetBinding.tvSheetSubtitulo.text = "Selecciona el origen para documentar el caso"

            sheetBinding.ivIconCamara.setImageResource(R.drawable.camera)
            sheetBinding.tvTituloCamara.text = "Tomar foto con la cámara"
            sheetBinding.tvSubtituloCamara.text = "Captura en tiempo real con la cámara"

            sheetBinding.ivIconGaleria.setImageResource(R.drawable.ic_photo_library)
            sheetBinding.tvTituloGaleria.text = "Elegir de la galería"
            sheetBinding.tvSubtituloGaleria.text = "Seleccionar imagen guardada en tu teléfono"

            sheetBinding.tvSheetInfoNota.text = "Optimización inteligente: Las fotos se procesan en Full HD para una subida ágil y segura."
        }

        sheetBinding.cardOpcionCamara.setOnClickListener {
            bottomSheet.dismiss()
            verificarPermisoYLanzar(esVideo = esVideo)
        }

        sheetBinding.cardOpcionGaleria.setOnClickListener {
            bottomSheet.dismiss()
            if (esVideo) {
                pickVideoLauncher.launch("video/*")
            } else {
                pickPhotoLauncher.launch("image/*")
            }
        }

        sheetBinding.btnCerrarSheet.setOnClickListener {
            bottomSheet.dismiss()
        }

        sheetBinding.btnCancelarSheet.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun verificarPermisoYLanzar(esVideo: Boolean) {
        pendingMediaEsVideo = esVideo
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (esVideo) lanzarGrabacionVideo() else lanzarCapturaFoto()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun lanzarCapturaFoto() {
        try {
            val file = File(filesDir, "reporte_foto_${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            file.createNewFile()
            tempCameraFile = file
            tempCameraUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            takePhotoLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar la cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lanzarGrabacionVideo() {
        try {
            val file = File(filesDir, "reporte_video_${System.currentTimeMillis()}.mp4")
            file.parentFile?.mkdirs()
            file.createNewFile()
            tempCameraFile = file
            tempCameraUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            recordVideoLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar la cámara para video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copiarUriALocal(uri: Uri, esVideo: Boolean): Uri? {
        return try {
            val extension = if (esVideo) ".mp4" else ".jpg"
            val prefix = if (esVideo) "reporte_video_" else "reporte_foto_"
            val localFile = File(filesDir, "${prefix}${System.currentTimeMillis()}${extension}")
            localFile.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!esVideo) {
                optimizarImagen(localFile)
            }
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                localFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun optimizarImagen(archivo: File): File {
        return try {
            if (!archivo.exists() || archivo.length() == 0L) return archivo

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(archivo.absolutePath, boundsOptions)

            val maxDimension = 1920
            var sampleSize = 1
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDimension || (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(archivo.absolutePath, decodeOptions) ?: return archivo

            val orientacionExif = try {
                val exif = ExifInterface(archivo.absolutePath)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientacionExif) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                val rotado = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotado != rawBitmap) rawBitmap.recycle()
                rotado
            } else {
                rawBitmap
            }

            val tempFile = File(filesDir, "opt_${System.currentTimeMillis()}_${archivo.name}")
            FileOutputStream(tempFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            finalBitmap.recycle()

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.copyTo(archivo, overwrite = true)
                tempFile.delete()
            }
            archivo
        } catch (e: Exception) {
            e.printStackTrace()
            archivo
        }
    }

    private fun actualizarVisualizacionMultimedia() {
        val cantidad = selectedMediaList.size

        when (cantidad) {
            0 -> {
                binding.layoutFotoCargada.visibility = View.GONE
                binding.cardFoto1.visibility = View.GONE
                binding.cardFoto2.visibility = View.GONE
                binding.tvContadorEvidencias.text = "Puedes adjuntar hasta 2 evidencias (fotos o video corto)"
                binding.btnAdjuntarFoto.isEnabled = true
                binding.btnAdjuntarVideo.isEnabled = true
                binding.btnAdjuntarFoto.alpha = 1.0f
                binding.btnAdjuntarVideo.alpha = 1.0f
            }
            1 -> {
                binding.layoutFotoCargada.visibility = View.VISIBLE
                binding.cardFoto1.visibility = View.VISIBLE
                binding.cardFoto2.visibility = View.GONE

                val item = selectedMediaList[0]
                val desc = if (item.esVideo) "1 video adjunto" else "1 foto adjunta"
                binding.tvContadorEvidencias.text = "$desc (puedes agregar 1 evidencia más)"
                binding.btnAdjuntarFoto.isEnabled = true
                binding.btnAdjuntarVideo.isEnabled = true
                binding.btnAdjuntarFoto.alpha = 1.0f
                binding.btnAdjuntarVideo.alpha = 1.0f

                mostrarMediaEnCard(item, binding.ivFoto1, binding.layoutVideoOverlay1, binding.cardFoto1)
            }
            else -> {
                binding.layoutFotoCargada.visibility = View.VISIBLE
                binding.cardFoto1.visibility = View.VISIBLE
                binding.cardFoto2.visibility = View.VISIBLE

                val fotos = selectedMediaList.count { !it.esVideo }
                val videos = selectedMediaList.count { it.esVideo }
                val desc = when {
                    fotos == 2 -> "2 fotos adjuntas"
                    videos == 2 -> "2 videos adjuntos"
                    else -> "1 foto y 1 video adjuntos"
                }
                binding.tvContadorEvidencias.text = "$desc (límite máximo alcanzado)"
                binding.btnAdjuntarFoto.isEnabled = false
                binding.btnAdjuntarVideo.isEnabled = false
                binding.btnAdjuntarFoto.alpha = 0.5f
                binding.btnAdjuntarVideo.alpha = 0.5f

                mostrarMediaEnCard(selectedMediaList[0], binding.ivFoto1, binding.layoutVideoOverlay1, binding.cardFoto1)
                mostrarMediaEnCard(selectedMediaList[1], binding.ivFoto2, binding.layoutVideoOverlay2, binding.cardFoto2)
            }
        }
    }

    private fun mostrarMediaEnCard(
        media: EvidenciaItem,
        imageView: ImageView,
        videoOverlay: View,
        cardView: View
    ) {
        if (media.esVideo) {
            Glide.with(this)
                .asBitmap()
                .load(media.uri)
                .centerCrop()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(imageView)
            videoOverlay.visibility = View.VISIBLE
            cardView.setOnClickListener {
                reproducirVideo(media.uri)
            }
        } else {
            Glide.with(this)
                .load(media.uri)
                .centerCrop()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(imageView)
            videoOverlay.visibility = View.GONE
            cardView.setOnClickListener(null)
        }
    }

    private fun reproducirVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se encontró un reproductor de video compatible", Toast.LENGTH_SHORT).show()
        }
    }

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

        if (selectedMediaList.isNotEmpty()) {
            subirArchivosYContinuar(descripcion, latitud, longitud, tipoReporte)
        } else {
            navegarAConfirmar(descripcion, latitud, longitud, tipoReporte, arrayListOf())
        }
    }

    private fun subirArchivosYContinuar(
        descripcion: String,
        latitud: Double,
        longitud: Double,
        tipoReporte: String?
    ) {
        val urlsSubidas = mutableListOf<String>()
        var subidasCompletadas = 0
        val totalArchivos = selectedMediaList.size

        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Subiendo evidencias..."
        Toast.makeText(this, "Subiendo evidencias a la nube...", Toast.LENGTH_SHORT).show()

        for (item in selectedMediaList) {
            MediaManager.get()
                .upload(item.uri)
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
                            binding.btnContinue.text = "Continuar"
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
                        binding.btnContinue.text = "Continuar"
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
        val idTipoRecibido = intent.getIntExtra("ID_TIPO_SELECCIONADO", 1)

        val intent = Intent(this, confirmar_reporte::class.java).apply {
            putExtra("TIPO_REPORTE", tipoReporte)
            putExtra("DESCRIPCION", descripcion)
            putStringArrayListExtra("URLS_ARCHIVOS", urlsArchivos)
            putExtra("LATITUD", latitud)
            putExtra("LONGITUD", longitud)
            putExtra("PUNTO_REFERENCIA", referencia)
            putExtra("ID_TIPO_SELECCIONADO", idTipoRecibido)
        }
        startActivity(intent)
    }
}