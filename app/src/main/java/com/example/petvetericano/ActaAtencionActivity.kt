package com.example.petvetericano

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityActaAtencionBinding
import com.example.petvetericano.databinding.BottomSheetAplicarMedicamentoBinding
import com.example.petvetericano.databinding.ItemAnimalFormularioBinding
import com.example.petvetericano.databinding.ItemExamenAgregadoBinding
import com.example.petvetericano.databinding.ItemMedicamentoAplicadoActaBinding
import com.example.petvetericano.models.AnimalCardState
import com.example.petvetericano.models.AnimalRapidoRequest
import com.example.petvetericano.models.ExamenSeleccionadoActa
import com.example.petvetericano.models.MedicamentoAplicadoActa
import com.example.petvetericano.models.MedicamentoMaletin
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.petvetericano.models.FuncionarioActaRequest
import com.example.petvetericano.models.LugarAtencionActa
import com.example.petvetericano.models.SeguimientoPeticionVisitaRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import java.io.FileOutputStream
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.core.content.FileProvider
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ItemActaMediaAdjuntoBinding
import com.example.petvetericano.offline.ActaFormSnapshotMapper
import com.example.petvetericano.offline.ActaLocalEntity
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaRequestBuilder
import com.example.petvetericano.offline.ActaSyncScheduler
import com.example.petvetericano.offline.CloudinaryUploadHelper
import com.example.petvetericano.offline.EstadoSincronizacionActa
import com.example.petvetericano.offline.EvidenciaLocalItem
import com.example.petvetericano.offline.NetworkUtils
import com.example.petvetericano.offline.PeticionesCacheManager
import com.example.petvetericano.offline.ResultadoSubida
import kotlinx.coroutines.withTimeoutOrNull
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.WeakHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine

data class MediaAdjunto(
    val uri: Uri,
    val esVideo: Boolean,
    val nombre: String = "",
    val localPath: String = "",
    var remoteUrl: String? = null
)

class ActaAtencionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActaAtencionBinding
    private var currentStep = 1
    private var peticionId: Int = -1

    // Localización en campo
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latitudVisita: Double = 0.0
    private var longitudVisita: Double = 0.0

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            obtenerUbicacionActual()
        } else {
            Toast.makeText(this, "Permiso de ubicación denegado. Se requiere para fijar el punto en campo.", Toast.LENGTH_SHORT).show()
            binding.tvLugarAtencionStatus.text = "Sin permiso"
            binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#B91C1C"))
        }
    }

    // Evidencia Multimedia (Fotos y Videos para Cloudinary)
    private val selectedMediaList = mutableListOf<MediaAdjunto>()
    private var tempCameraUri: Uri? = null
    private var tempCameraFile: File? = null
    private var pendingMediaEsVideo: Boolean = false

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val optFile = tempCameraFile?.let { optimizarImagen(it) }
            val rutaLocal = optFile?.absolutePath ?: tempCameraFile?.absolutePath.orEmpty()
            selectedMediaList.add(MediaAdjunto(tempCameraUri!!, esVideo = false, "Foto de campo", localPath = rutaLocal))
            actualizarVistaMultimedia()
            programarGuardado()
            Toast.makeText(this, "Foto capturada y optimizada 📷", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val localUri = copiarUriALocal(it, esVideo = false)
            if (localUri != null) {
                selectedMediaList.add(MediaAdjunto(localUri, esVideo = false, "Foto seleccionada", localPath = ultimaRutaLocalCopiada))
                actualizarVistaMultimedia()
                programarGuardado()
                Toast.makeText(this, "Foto adjunta al acta 📷", Toast.LENGTH_SHORT).show()
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
                    .setIcon(R.drawable.warning_icon4)
                    .setTitle("Video excede el límite")
                    .setMessage("El video grabado pesa ${sizeMb} MB y supera el límite de 10 MB permitido.\n\nPor favor graba un video más breve (máx. 20-30 segundos).")
                    .setPositiveButton("Aceptar", null)
                    .show()
            } else {
                val rutaLocal = tempCameraFile?.absolutePath.orEmpty()
                selectedMediaList.add(MediaAdjunto(tempCameraUri!!, esVideo = true, "Video de campo", localPath = rutaLocal))
                actualizarVistaMultimedia()
                programarGuardado()
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
                selectedMediaList.add(MediaAdjunto(localUri, esVideo = true, "Video seleccionado", localPath = ultimaRutaLocalCopiada))
                actualizarVistaMultimedia()
                programarGuardado()
                Toast.makeText(this, "Video adjunto al acta 🎥", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Se requiere permiso de cámara para capturar fotos o videos", Toast.LENGTH_SHORT).show()
        }
    }

    // Lista de ViewBindings dinámicos para cada tarjeta de animal
    private val animalBindingsList = mutableListOf<ItemAnimalFormularioBinding>()

    // Lista de medicamentos suministrados y descontados del maletín en campo
    private val medicamentosAplicadosList = mutableListOf<MedicamentoAplicadoActa>()

    // Catálogos sincronizados del backend
    private val listaEspeciesBackend = mutableListOf<com.example.petvetericano.models.EspecieItemResponse>()
    private val listaRazasBackend = mutableListOf<com.example.petvetericano.models.RazaItemResponse>()
    private val listaExamenesBackend = mutableListOf<com.example.petvetericano.models.ExamenCatalogoItemResponse>()
    private val examenesAgregadosList = mutableListOf<com.example.petvetericano.models.ExamenSeleccionadoActa>()

    private val catalogoRazasOffline = mapOf(
        "Canino" to listOf("Mestizo / Criollo", "Labrador Retriever", "Pastor Alemán", "Golden Retriever", "Bulldog Francés", "Poodle / Caniche", "Beagle", "Rottweiler", "Chihuahua", "Pitbull", "Pinscher", "Husky Siberiano", "Boxer", "Schnauzer", "Shih Tzu", "Pug", "Dálmata", "Criollo de Manejo Especial", "Otro Canino"),
        "Felino" to listOf("Mestizo / Criollo Común Europeo", "Siamés", "Persa", "Bengalí", "Maine Coon", "Angora", "Ragdoll", "Azul Ruso", "Esfinge / Sphynx", "Otro Felino"),
        "Equino" to listOf("Criollo Colombiano", "Paso Fino", "Trochador", "Cuarto de Milla", "Pura Sangre Inglés", "Árabe", "Percherón", "Mular / Asnal", "Otro Equino"),
        "Bovino" to listOf("Cebú / Brahman", "Holstein", "Normando", "Girolando", "Jersey", "Pardo Suizo", "Angus", "Criollo Hartón del Valle", "Otro Bovino"),
        "Porcino" to listOf("Criollo / Zungo", "Landrace", "Yorkshire / Large White", "Duroc", "Pietrain", "Hampshire", "Otro Porcino"),
        "Ave" to listOf("Criolla de Campo", "Gallina Ponedora Hy-Line", "Pollo de Engorde Cobb", "Loro / Perico común", "Paloma bravía", "Pato criollo", "Otro Ave"),
        "Otro" to listOf("Mestizo / Indeterminado", "Silvestre / Fauna Urbana", "Otro")
    )
    private val coloresFrecuentes = arrayOf(
        "Negro", "Blanco", "Café / Marrón", "Caramelo / Dorado", "Gris", "Atigrado / Barcino",
        "Bicolor Negro/Blanco", "Bicolor Café/Blanco", "Tricolor", "Manchado", "Crema", "Canela"
    )
    private val mucosasOpciones = arrayOf(
        "Rosadas / Normales", "Pálidas / Anémicas", "Cianóticas / Azulosas", "Ictéricas / Amarillentas", "Congestivas / Enrojecidas"
    )
    private val unidadesEdad = arrayOf("Años", "Meses", "Semanas")

    // Gestión de borrador offline en SQLite
    private lateinit var actaLocalRepository: ActaLocalRepository
    private var entidadBorradorActual: ActaLocalEntity? = null
    private val dbWriteLock = Any()
    private val guardadoHandler = Handler(Looper.getMainLooper())
    private val guardadoDebounceRunnable = Runnable {
        ejecutarGuardadoDebounce()
    }
    private var isInitialSetup = true
    private var isRestaurandoBorrador = false
    private var actualizandoCatalogos = false
    private var mostrandoDialogoBorrador = false
    private var usuarioHaEditado = false
    private var huboInteraccionUsuario = false
    private var actaEnviadaExitosamente = false
    private var edicionBloqueadaPorEstado = false
    private var enviandoActa = false
    private var ultimaRutaLocalCopiada: String = ""
    private val estadoRestauradoPorTarjeta = WeakHashMap<ItemAnimalFormularioBinding, AnimalCardState>()
    private val medicamentosPersistidosPrevios = mutableListOf<MedicamentoAplicadoActa>()

    private inner class DebugBuildConfig {
        val DEBUG: Boolean
            get() = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    private val BuildConfig = DebugBuildConfig()

    private val resolverIdRaza: (String, String) -> Int? = { especieRaw, razaRaw ->
        val especie = if (especieRaw.trim().equals("Seleccionar", ignoreCase = true)) "" else especieRaw.trim()
        val raza = if (razaRaw.trim().equals("Seleccionar", ignoreCase = true)) "" else razaRaw.trim().ifEmpty { "Mestizo" }
        val razaObj = listaRazasBackend.firstOrNull {
            it.nombre.equals(raza, ignoreCase = true) &&
                (it.nombreEspecie.isNullOrEmpty() || it.nombreEspecie.equals(especie, ignoreCase = true))
        } ?: listaRazasBackend.firstOrNull {
            it.nombre.equals(raza, ignoreCase = true)
        } ?: listaRazasBackend.firstOrNull {
            it.nombreEspecie?.equals(especie, ignoreCase = true) == true
        }
        razaObj?.idRaza
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isInitialSetup = true
        actaLocalRepository = ActaLocalRepository.getInstance(this)

        binding = ActivityActaAtencionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Manejo adecuado de Insets (Barras del sistema + Teclado virtual IME)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomPadding = if (isImeVisible) ime.bottom else systemBars.bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
            insets
        }

        peticionId = intent.getIntExtra("PETICION_ID", -1)
        if (peticionId == -1 && savedInstanceState != null) {
            peticionId = savedInstanceState.getInt("KEY_PETICION_ID", -1)
        }

        val idVetAlAbrir = obtenerIdVeterinarioActual()
        if (peticionId > 0 && idVetAlAbrir > 0) {
            val existenteAlAbrir = synchronized(dbWriteLock) {
                actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, idVetAlAbrir)
            }
            if (existenteAlAbrir != null && verificarFilaBloqueadaAlAbrir(existenteAlAbrir)) {
                return
            }
        }

        setupToolbar()
        setupUI(savedInstanceState)
        setupDropdowns(savedInstanceState)
        setupLugarAtencion()
        setupDesparasitacionSwitch(savedInstanceState)
        setupModuloRescate(savedInstanceState)
        setupChecklistSwitches(savedInstanceState)
        setupAutoScrollOnFocus()
        setupAceleradoresCampo()
        setupSeccionExamenesMultiples()
        setupModuloMaletinTratamiento()
        initCloudinary()
        setupEvidenciaMultimedia()
        cargarCatalogosBackend()

        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt("KEY_CURRENT_STEP", 1)
            peticionId = savedInstanceState.getInt("KEY_PETICION_ID", peticionId)
            latitudVisita = savedInstanceState.getDouble("KEY_LATITUD_VISITA", latitudVisita)
            longitudVisita = savedInstanceState.getDouble("KEY_LONGITUD_VISITA", longitudVisita)
            pendingMediaEsVideo = savedInstanceState.getBoolean("KEY_PENDING_MEDIA_ES_VIDEO", false)
            usuarioHaEditado = savedInstanceState.getBoolean("KEY_USUARIO_HA_EDITADO", false)
            huboInteraccionUsuario = savedInstanceState.getBoolean("KEY_HUBO_INTERACCION_USUARIO", false)

            tempCameraUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable("KEY_TEMP_CAMERA_URI", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("KEY_TEMP_CAMERA_URI")
            }
            val savedFilePath = savedInstanceState.getString("KEY_TEMP_CAMERA_FILE")
            if (!savedFilePath.isNullOrEmpty()) {
                tempCameraFile = File(savedFilePath)
            }
            val urisList = savedInstanceState.getStringArrayList("KEY_MEDIA_URIS")
            val isVideoList = savedInstanceState.getBooleanArray("KEY_MEDIA_IS_VIDEO")
            val localPathsList = savedInstanceState.getStringArrayList("KEY_MEDIA_LOCAL_PATHS")
            val nombresList = savedInstanceState.getStringArrayList("KEY_MEDIA_NOMBRES")
            val remoteUrlsList = savedInstanceState.getStringArrayList("KEY_MEDIA_REMOTE_URLS")
            if (urisList != null && isVideoList != null && urisList.size == isVideoList.size) {
                selectedMediaList.clear()
                for (i in urisList.indices) {
                    val path = localPathsList?.getOrNull(i).orEmpty()
                    val nom = nombresList?.getOrNull(i).orEmpty()
                    val rem = remoteUrlsList?.getOrNull(i)?.takeIf { it.isNotEmpty() }
                    selectedMediaList.add(MediaAdjunto(Uri.parse(urisList[i]), isVideoList[i], nom, path, rem))
                }
            }
            actualizarVistaMultimedia()

            // Restaurar lista de exámenes clínicos complementarios
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val examenesGuardados = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("KEY_EXAMENES_AGREGADOS", ArrayList::class.java) as? ArrayList<ExamenSeleccionadoActa>
            } else {
                savedInstanceState.getSerializable("KEY_EXAMENES_AGREGADOS") as? ArrayList<ExamenSeleccionadoActa>
            }
            if (!examenesGuardados.isNullOrEmpty()) {
                examenesAgregadosList.clear()
                examenesAgregadosList.addAll(examenesGuardados)
                renderizarExamenesAgregados()
            }

            // Restaurar medicamentos suministrados desde el maletín en campo
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val medsGuardados = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("KEY_MEDICAMENTOS_APLICADOS", ArrayList::class.java) as? ArrayList<MedicamentoAplicadoActa>
            } else {
                savedInstanceState.getSerializable("KEY_MEDICAMENTOS_APLICADOS") as? ArrayList<MedicamentoAplicadoActa>
            }
            if (!medsGuardados.isNullOrEmpty()) {
                medicamentosAplicadosList.clear()
                medicamentosAplicadosList.addAll(medsGuardados)
                medicamentosPersistidosPrevios.clear()
                medicamentosPersistidosPrevios.addAll(medsGuardados)
                actualizarVistaMedicamentosAplicados()
            }

            // Restaurar todos los formularios de pacientes diligenciados en el Paso 2
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val savedAnimalStates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("KEY_ANIMAL_STATES", ArrayList::class.java) as? ArrayList<AnimalCardState>
            } else {
                savedInstanceState.getSerializable("KEY_ANIMAL_STATES") as? ArrayList<AnimalCardState>
            }

            binding.containerAnimales.removeAllViews()
            animalBindingsList.clear()
            if (!savedAnimalStates.isNullOrEmpty()) {
                for (state in savedAnimalStates) {
                    agregarFormularioAnimal(state)
                }
            } else {
                agregarFormularioAnimal()
            }

            val idVet = obtenerIdVeterinarioActual()
            if (peticionId != -1 && idVet != -1) {
                val existente = synchronized(dbWriteLock) {
                    actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, idVet)
                }
                if (existente != null && (existente.estadoSincronizacion == EstadoSincronizacionActa.BORRADOR || existente.estadoSincronizacion == EstadoSincronizacionActa.REQUIERE_ATENCION)) {
                    entidadBorradorActual = if (existente.estadoSincronizacion == EstadoSincronizacionActa.REQUIERE_ATENCION) {
                        existente.copy(
                            estadoSincronizacion = EstadoSincronizacionActa.BORRADOR,
                            ultimoError = null,
                            ultimoCodigoHttp = null,
                            reintentos = 0
                        )
                    } else {
                        existente
                    }
                }
            }
        } else {
            // Agregar por defecto al menos un formulario de paciente al iniciar por primera vez
            agregarFormularioAnimal()
        }

        updateStepper()
        verificarSiPeticionYaAtendida()

        binding.btnAddAnimal.setOnClickListener {
            agregarFormularioAnimal()
            programarGuardado()
        }

        binding.btnSiguiente.setOnClickListener {
            when (currentStep) {
                1 -> {
                    if (validarPaso1()) {
                        currentStep = 2
                        updateStepper()
                        guardarBorradorEnCambioDePaso()
                    }
                }
                2 -> {
                    if (validarPaso2()) {
                        currentStep = 3
                        // Autocompletar datos del notificado con los del propietario si están vacíos
                        if (binding.etNotificadoNombre.text.isNullOrEmpty()) {
                            binding.etNotificadoNombre.setText(binding.etPropietarioNombre.text.toString().trim())
                        }
                        if (binding.etNotificadoIdentificacion.text.isNullOrEmpty()) {
                            binding.etNotificadoIdentificacion.setText(binding.etPropietarioCedula.text.toString().trim())
                        }
                        // Si el rescate ya fue activado, sincronizar campos clave si están vacíos
                        if (binding.swRequiereRescate.isChecked) {
                            autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura = false)
                        }
                        updateStepper()
                        guardarBorradorEnCambioDePaso()
                    }
                }
                3 -> {
                    if (validarPaso3()) {
                        submitActa()
                    }
                }
            }
        }

        binding.btnAnterior.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepper()
                guardarBorradorEnCambioDePaso()
            }
        }

        setupListenersGuardadoBorrador()
        isInitialSetup = false
        if (savedInstanceState == null) {
            verificarBorradorExistenteAlAbrir()
        }
    }

    override fun onPause() {
        if (edicionBloqueadaPorEstado) {
            super.onPause()
            return
        }
        if (!actaEnviadaExitosamente) {
            guardarBorradorSincrono(incluirFirmas = true)
        }
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (edicionBloqueadaPorEstado) return
        huboInteraccionUsuario = true
    }

    override fun onDestroy() {
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (edicionBloqueadaPorEstado) return
        outState.putInt("KEY_CURRENT_STEP", currentStep)
        outState.putInt("KEY_PETICION_ID", peticionId)
        outState.putDouble("KEY_LATITUD_VISITA", latitudVisita)
        outState.putDouble("KEY_LONGITUD_VISITA", longitudVisita)
        outState.putBoolean("KEY_PENDING_MEDIA_ES_VIDEO", pendingMediaEsVideo)
        outState.putBoolean("KEY_USUARIO_HA_EDITADO", usuarioHaEditado)
        outState.putBoolean("KEY_HUBO_INTERACCION_USUARIO", huboInteraccionUsuario)

        tempCameraUri?.let { outState.putParcelable("KEY_TEMP_CAMERA_URI", it) }
        tempCameraFile?.let { outState.putString("KEY_TEMP_CAMERA_FILE", it.absolutePath) }
        val urisList = ArrayList(selectedMediaList.map { it.uri.toString() })
        val isVideoList = ArrayList(selectedMediaList.map { it.esVideo })
        val localPathsList = ArrayList(selectedMediaList.map { it.localPath })
        val nombresList = ArrayList(selectedMediaList.map { it.nombre })
        val remoteUrlsList = ArrayList(selectedMediaList.map { it.remoteUrl.orEmpty() })
        outState.putStringArrayList("KEY_MEDIA_URIS", urisList)
        outState.putBooleanArray("KEY_MEDIA_IS_VIDEO", isVideoList.toBooleanArray())
        outState.putStringArrayList("KEY_MEDIA_LOCAL_PATHS", localPathsList)
        outState.putStringArrayList("KEY_MEDIA_NOMBRES", nombresList)
        outState.putStringArrayList("KEY_MEDIA_REMOTE_URLS", remoteUrlsList)

        // 1. Guardar estado completo de todas las tarjetas dinámicas de pacientes
        val animalStates = ArrayList(extraerEstadosAnimales())
        outState.putSerializable("KEY_ANIMAL_STATES", animalStates)

        // 2. Guardar lista de exámenes clínicos complementarios agregados (Paso 3)
        outState.putSerializable("KEY_EXAMENES_AGREGADOS", ArrayList(examenesAgregadosList))

        // 3. Guardar lista de medicamentos aplicados en campo
        outState.putSerializable("KEY_MEDICAMENTOS_APLICADOS", ArrayList(medicamentosAplicadosList))
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (edicionBloqueadaPorEstado) return
        sincronizarLabelsSwitches()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            confirmarSalidaActa()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmarSalidaActa()
            }
        })
    }

    private fun confirmarSalidaActa() {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.warning_icon4)
            .setTitle("¿Salir del acta de atención?")
            .setMessage("Si sales ahora sin guardar, la información diligenciada de la atención en campo se perderá.")
            .setPositiveButton("Salir") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("Continuar editando") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupUI(savedInstanceState: Bundle? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        if (binding.etFechaAtencion.text.isNullOrEmpty()) {
            binding.etFechaAtencion.setText(currentDate)
        }

        if (savedInstanceState == null || binding.etRadicado.text.isNullOrEmpty()) {
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val randomSuffix = (100..999).random()
            binding.etRadicado.setText("RAD-VIS-$year-$randomSuffix")
        }

        val prefs = SharedPreferencesManager(this)
        val nombreGuardado = prefs.getUserName().trim()
        val apellidoGuardado = prefs.getUserLastName().trim()
        val nombreCompleto = if (nombreGuardado.isNotEmpty() || apellidoGuardado.isNotEmpty()) {
            "$nombreGuardado $apellidoGuardado".trim()
        } else {
            "Médico Veterinario"
        }
        binding.etFuncionarioNombre.setText(nombreCompleto)
        val tituloGuardado = prefs.getUserTituloProfesional().trim()
        binding.etFuncionarioCargo.setText(tituloGuardado.ifEmpty { "Médico Veterinario Zootecnista" })

        val identificacion = prefs.getUserIdentification().trim()
        if (identificacion.isNotEmpty()) {
            binding.etFuncionarioIdentificacion.setText(identificacion)
        }

        val matriculaGuardada = prefs.getUserMatriculaProfesional().trim()
        if (matriculaGuardada.isNotEmpty()) {
            binding.etFuncionarioMatricula.setText(matriculaGuardada)
        }

        // Detalle oficial dinámico del veterinario al pie de su firma
        val actualizarPieFirma = {
            val nom = binding.etFuncionarioNombre.text.toString().trim().ifEmpty { "Médico Veterinario" }
            val car = binding.etFuncionarioCargo.text.toString().trim().ifEmpty { "Médico Veterinario Zootecnista" }
            val mat = binding.etFuncionarioMatricula.text.toString().trim()
            val cc = binding.etFuncionarioIdentificacion.text.toString().trim()

            val pie = buildString {
                append(nom)
                if (cc.isNotEmpty()) append(" • C.C. $cc")
                append("\n$car")
                if (mat.isNotEmpty()) append(" • M.P. $mat")
            }
            binding.tvFirma1DetalleProfesional.text = pie
        }
        actualizarPieFirma()

        binding.etFuncionarioNombre.addTextChangedListener { actualizarPieFirma() }
        binding.etFuncionarioCargo.addTextChangedListener { actualizarPieFirma() }
        binding.etFuncionarioIdentificacion.addTextChangedListener { actualizarPieFirma() }
        binding.etFuncionarioMatricula.addTextChangedListener {
            actualizarPieFirma()
            if (!it.isNullOrBlank()) {
                binding.tilFuncionarioMatricula.error = null
            }
        }

        // Datos por defecto para recepción en rescate
        binding.etVeterinarioReceptor.setText(nombreCompleto)
        binding.etFechaRecepcion.setText(currentDate)
        val hf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.etHoraRecepcion.setText(hf.format(Date()))

        binding.btnClearSignatureNotificador.setOnClickListener {
            binding.signatureNotificador.clear()
            programarGuardado()
        }

        binding.btnClearSignatureNotificado.setOnClickListener {
            binding.signatureNotificado.clear()
            programarGuardado()
        }
    }

    private fun setupModuloRescate(savedInstanceState: Bundle? = null) {
        val motivosRescate = arrayOf(
            "Por salud crítica / Urgencia médica",
            "Por maltrato animal / Aprehensión preventiva",
            "Abandono / Vía pública sin tenedor",
            "Entrega voluntaria por incapacidad de tenencia",
            "Riesgo inminente para la comunidad o el animal"
        )
        val adapterMotivo = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, motivosRescate)
        binding.autoCompleteMotivoRescate.setAdapter(adapterMotivo)
        if (savedInstanceState == null || binding.autoCompleteMotivoRescate.text.isNullOrEmpty()) {
            binding.autoCompleteMotivoRescate.setText(motivosRescate[0], false)
        }

        binding.autoCompleteMotivoRescate.setOnItemClickListener { _, _, position, _ ->
            val causalSeleccionada = motivosRescate[position]
            actualizarObservacionRescatePorCausal(causalSeleccionada)
        }

        // Estado inicial: Rescate inactivo por defecto solo si no hay estado restaurado
        if (savedInstanceState == null) {
            binding.swRequiereRescate.isChecked = false
            binding.cardBadgeRescateActivo.visibility = View.GONE
            binding.layoutModuloRescateIntegrado.visibility = View.GONE
            binding.tvEstadoRequiereRescate.text = "No requerido (atención normal)"
            binding.tvEstadoRequiereRescate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        binding.swRequiereRescate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cardBadgeRescateActivo.visibility = View.VISIBLE
                binding.layoutModuloRescateIntegrado.visibility = View.VISIBLE
                binding.tvEstadoRequiereRescate.text = "Rescate y traslado activo"
                binding.tvEstadoRequiereRescate.setTextColor(ContextCompat.getColor(this, R.color.primary))
                binding.tvFirma1Titulo.text = "Firma del médico veterinario / rescatista"
                binding.tvFirma2Titulo.text = "Firma del ciudadano notificado / testigo"

                // Cargar automáticamente los datos comunes si están vacíos
                autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura = false)

                Toast.makeText(this, "Módulo de rescate CBA activado", Toast.LENGTH_SHORT).show()
            } else {
                binding.cardBadgeRescateActivo.visibility = View.GONE
                binding.layoutModuloRescateIntegrado.visibility = View.GONE
                binding.tvEstadoRequiereRescate.text = "No requerido (atención normal)"
                binding.tvEstadoRequiereRescate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.tvFirma1Titulo.text = "Firma del funcionario / notificador"
                binding.tvFirma2Titulo.text = "Firma del ciudadano notificado"
            }
            programarGuardado()
        }

        // Botón para sincronizar manualmente en cualquier momento
        binding.btnCargarDatosRescate.setOnClickListener {
            autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura = true)
            Toast.makeText(this, "Datos de la atención sincronizados en el acta de rescate", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura: Boolean) {
        if (isRestaurandoBorrador && !forzarSobrescritura) return
        // 1. Persona que entrega / atiende
        val nombrePropietario = binding.etPropietarioNombre.text.toString().trim()
        if (forzarSobrescritura || binding.etPersonaAtiendeRescatista.text.isNullOrEmpty()) {
            if (nombrePropietario.isNotEmpty()) {
                binding.etPersonaAtiendeRescatista.setText(nombrePropietario)
            }
        }

        val cedulaPropietario = binding.etPropietarioCedula.text.toString().trim()
        if (forzarSobrescritura || binding.etPersonaAtiendeCedula.text.isNullOrEmpty()) {
            if (cedulaPropietario.isNotEmpty()) {
                binding.etPersonaAtiendeCedula.setText(cedulaPropietario)
            }
        }

        val telPropietario = binding.etPropietarioTelefono.text.toString().trim()
        if (forzarSobrescritura || binding.etPersonaAtiendeTelefono.text.isNullOrEmpty()) {
            if (telPropietario.isNotEmpty()) {
                binding.etPersonaAtiendeTelefono.setText(telPropietario)
            }
        }

        // 2. Barrio de rescate
        val barrio = binding.etPropietarioBarrio.text.toString().trim()
        val lugarAtencion = binding.etLugarAtencion.text.toString().trim()
        if (forzarSobrescritura || binding.etBarrioRescate.text.isNullOrEmpty()) {
            if (barrio.isNotEmpty()) {
                binding.etBarrioRescate.setText(barrio)
            } else if (lugarAtencion.isNotEmpty()) {
                binding.etBarrioRescate.setText(lugarAtencion)
            }
        }

        // 3. Médico receptor en CBA
        val funcionario = binding.etFuncionarioNombre.text.toString().trim()
        if (forzarSobrescritura || binding.etVeterinarioReceptor.text.isNullOrEmpty()) {
            if (funcionario.isNotEmpty()) {
                binding.etVeterinarioReceptor.setText(funcionario)
            }
        }

        // 4. Fecha y hora de recepción
        val sdfFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfHora = SimpleDateFormat("HH:mm", Locale.getDefault())
        val ahora = Date()

        if (forzarSobrescritura || binding.etFechaRecepcion.text.isNullOrEmpty()) {
            val fechaAtencion = binding.etFechaAtencion.text.toString().trim()
            binding.etFechaRecepcion.setText(if (fechaAtencion.isNotEmpty()) fechaAtencion else sdfFecha.format(ahora))
        }
        if (forzarSobrescritura || binding.etHoraRecepcion.text.isNullOrEmpty()) {
            binding.etHoraRecepcion.setText(sdfHora.format(ahora))
        }

        // 5. Observaciones iniciales del rescate sintetizadas
        if (forzarSobrescritura || binding.etObservacionesRescate.text.isNullOrEmpty()) {
            val causal = binding.autoCompleteMotivoRescate.text.toString().trim()
            actualizarObservacionRescatePorCausal(causal)
        }
    }

    private fun actualizarObservacionRescatePorCausal(causal: String) {
        val primerAnimal = animalBindingsList.firstOrNull()
        val nombrePaciente = primerAnimal?.etPacienteNombreCard?.text?.toString()?.trim()?.ifEmpty { "Paciente" } ?: "Paciente"
        val especieRaw = primerAnimal?.autoCompleteEspecieCard?.text?.toString()?.trim()
        val especie = if (especieRaw.isNullOrEmpty() || especieRaw.equals("Seleccionar", ignoreCase = true)) null else especieRaw
        val solicitud = binding.etSolicitudAtencion.text.toString().trim()
        val anamnesis = binding.etAnamnesis.text.toString().trim()

        val textoGenerado = buildString {
            if (causal.isNotEmpty()) appendLine("Causal: $causal.")
            val descAnimal = if (especie != null) "$nombrePaciente ($especie)" else nombrePaciente
            appendLine("Se efectúa rescate y retiro preventivo de $descAnimal para su traslado inmediato al Centro de Bienestar Animal (CBA).")
            if (solicitud.isNotEmpty()) appendLine("Motivo de reporte: $solicitud.")
            if (anamnesis.isNotEmpty()) appendLine("Estado clínico inicial: $anamnesis.")
        }.trim()

        binding.etObservacionesRescate.setText(textoGenerado)
    }

    private fun setupChecklistSwitches(savedInstanceState: Bundle? = null) {
        val switchLabelPairs = listOf(
            binding.swRespiratorioMoco to binding.tvEstadoRespiratorioMoco,
            binding.swRespiratorioDificultad to binding.tvEstadoRespiratorioDificultad,
            binding.swRespiratorioTos to binding.tvEstadoRespiratorioTos,
            binding.swRespiratorioLaganeo to binding.tvEstadoRespiratorioLaganeo,
            binding.swDigestivoVomito to binding.tvEstadoDigestivoVomito,
            binding.swDigestivoDiarrea to binding.tvEstadoDigestivoDiarrea,
            binding.swDigestivoDesnutrido to binding.tvEstadoDigestivoDesnutrido,
            binding.swDigestivoDeshidratado to binding.tvEstadoDigestivoDeshidratado,
            binding.swMusculoCaidaPatas to binding.tvEstadoMusculoCaidaPatas,
            binding.swMusculoCojo to binding.tvEstadoMusculoCojo,
            binding.swMusculoHeridaAbierta to binding.tvEstadoMusculoHeridaAbierta,
            binding.swMusculoGusanos to binding.tvEstadoMusculoGusanos,
            binding.swMusculoPostrado to binding.tvEstadoMusculoPostrado,
            binding.swDermatoPeladuras to binding.tvEstadoDermatoPeladuras,
            binding.swDermatoHeridasSangre to binding.tvEstadoDermatoHeridasSangre,
            binding.swDermatoPulgas to binding.tvEstadoDermatoPulgas,
            binding.swGenitoMasaSecrecion to binding.tvEstadoGenitoMasaSecrecion,
            binding.swGenitoGestante to binding.tvEstadoGenitoGestante,
            binding.swGenitoCelo to binding.tvEstadoGenitoCelo
        )

        val colorPrimario = ContextCompat.getColor(this, R.color.primary)
        val colorSecundario = ContextCompat.getColor(this, R.color.text_secondary)

        if (savedInstanceState == null) {
            for ((switch, label) in switchLabelPairs) {
                switch.isChecked = false
                label.text = "NO"
                label.setTextColor(colorSecundario)
            }
        }

        for ((switch, label) in switchLabelPairs) {
            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    label.text = "SÍ"
                    label.setTextColor(colorPrimario)
                } else {
                    label.text = "NO"
                    label.setTextColor(colorSecundario)
                }
                programarGuardado()
            }
        }
    }

    private fun sincronizarLabelsSwitches() {
        val colorPrimario = ContextCompat.getColor(this, R.color.primary)
        val colorSecundario = ContextCompat.getColor(this, R.color.text_secondary)

        val switchLabelPairs = listOf(
            binding.swRespiratorioMoco to binding.tvEstadoRespiratorioMoco,
            binding.swRespiratorioDificultad to binding.tvEstadoRespiratorioDificultad,
            binding.swRespiratorioTos to binding.tvEstadoRespiratorioTos,
            binding.swRespiratorioLaganeo to binding.tvEstadoRespiratorioLaganeo,
            binding.swDigestivoVomito to binding.tvEstadoDigestivoVomito,
            binding.swDigestivoDiarrea to binding.tvEstadoDigestivoDiarrea,
            binding.swDigestivoDesnutrido to binding.tvEstadoDigestivoDesnutrido,
            binding.swDigestivoDeshidratado to binding.tvEstadoDigestivoDeshidratado,
            binding.swMusculoCaidaPatas to binding.tvEstadoMusculoCaidaPatas,
            binding.swMusculoCojo to binding.tvEstadoMusculoCojo,
            binding.swMusculoHeridaAbierta to binding.tvEstadoMusculoHeridaAbierta,
            binding.swMusculoGusanos to binding.tvEstadoMusculoGusanos,
            binding.swMusculoPostrado to binding.tvEstadoMusculoPostrado,
            binding.swDermatoPeladuras to binding.tvEstadoDermatoPeladuras,
            binding.swDermatoHeridasSangre to binding.tvEstadoDermatoHeridasSangre,
            binding.swDermatoPulgas to binding.tvEstadoDermatoPulgas,
            binding.swGenitoMasaSecrecion to binding.tvEstadoGenitoMasaSecrecion,
            binding.swGenitoGestante to binding.tvEstadoGenitoGestante,
            binding.swGenitoCelo to binding.tvEstadoGenitoCelo
        )

        for ((switch, label) in switchLabelPairs) {
            if (switch.isChecked) {
                label.text = "SÍ"
                label.setTextColor(colorPrimario)
            } else {
                label.text = "NO"
                label.setTextColor(colorSecundario)
            }
        }

        if (binding.swDesparasitacion.isChecked) {
            binding.tvEstadoDesparasitacion.text = "Sí aplicada"
            binding.tvEstadoDesparasitacion.setTextColor(colorPrimario)
        } else {
            binding.tvEstadoDesparasitacion.text = "No aplicada"
            binding.tvEstadoDesparasitacion.setTextColor(colorSecundario)
        }

        if (binding.swRequiereRescate.isChecked) {
            binding.cardBadgeRescateActivo.visibility = View.VISIBLE
            binding.layoutModuloRescateIntegrado.visibility = View.VISIBLE
            binding.tvEstadoRequiereRescate.text = "Rescate y traslado activo"
            binding.tvEstadoRequiereRescate.setTextColor(colorPrimario)
        } else {
            binding.cardBadgeRescateActivo.visibility = View.GONE
            binding.layoutModuloRescateIntegrado.visibility = View.GONE
            binding.tvEstadoRequiereRescate.text = "No requerido (atención normal)"
            binding.tvEstadoRequiereRescate.setTextColor(colorSecundario)
        }
    }

    private fun setupLugarAtencion() {
        val dirPrevia = intent.getStringExtra("DIRECCION_ACTUAL")
        if (!dirPrevia.isNullOrBlank()) {
            binding.etLugarAtencion.setText(dirPrevia)
            binding.tvLugarAtencionInfo.text = "$dirPrevia (Reportada en petición)"
            binding.tvLugarAtencionStatus.text = "Asignada"
            binding.tvLugarAtencionStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
        }

        binding.btnCapturarUbicacion.setOnClickListener {
            verificarPermisosUbicacion()
        }
    }

    private fun verificarPermisosUbicacion() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                obtenerUbicacionActual()
            }
            else -> {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun obtenerUbicacionActual() {
        binding.tvLugarAtencionStatus.text = "Detectando..."
        binding.tvLugarAtencionStatus.setTextColor(ContextCompat.getColor(this, R.color.primary))
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        latitudVisita = location.latitude
                        longitudVisita = location.longitude
                        obtenerDireccionDesdeCoordenadas(location.latitude, location.longitude)
                    } else {
                        fusedLocationClient.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                            null
                        ).addOnSuccessListener { loc ->
                            if (loc != null) {
                                latitudVisita = loc.latitude
                                longitudVisita = loc.longitude
                                obtenerDireccionDesdeCoordenadas(loc.latitude, loc.longitude)
                            } else {
                                binding.tvLugarAtencionStatus.text = "Error GPS"
                                binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#B91C1C"))
                                Toast.makeText(this, "No se pudo obtener la señal GPS actual.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    binding.tvLugarAtencionStatus.text = "Error GPS"
                    binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#B91C1C"))
                    Toast.makeText(this, "Error al capturar ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: SecurityException) {
            binding.tvLugarAtencionStatus.text = "Sin permiso"
            binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#B91C1C"))
        }
    }

    private fun obtenerDireccionDesdeCoordenadas(lat: Double, lng: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        val fallbackTexto = String.format(Locale.US, "Coordenadas: %.5f, %.5f", lat, lng)

        val aplicarDireccion = { dir: String ->
            runOnUiThread {
                binding.etLugarAtencion.setText(dir)
                binding.tvLugarAtencionInfo.text = dir
                binding.tvLugarAtencionStatus.text = "Guardada"
                binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#047857"))
                Toast.makeText(this, "Ubicación fijada correctamente", Toast.LENGTH_SHORT).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                val dir = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: fallbackTexto
                } else {
                    fallbackTexto
                }
                aplicarDireccion(dir)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val dir = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: fallbackTexto
                } else {
                    fallbackTexto
                }
                aplicarDireccion(dir)
            } catch (_: Exception) {
                aplicarDireccion(fallbackTexto)
            }
        }
    }

    private fun setupDesparasitacionSwitch(savedInstanceState: Bundle? = null) {
        if (savedInstanceState == null) {
            binding.swDesparasitacion.isChecked = false
            binding.tvEstadoDesparasitacion.text = "No aplicada"
            binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        binding.swDesparasitacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoDesparasitacion.text = "Sí aplicada"
                binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                binding.tvEstadoDesparasitacion.text = "No aplicada"
                binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            programarGuardado()
        }
    }

    private fun setupAutoScrollOnFocus() {
        val camposCriticos = listOf(
            binding.etFrecuenciaCardiaca,
            binding.etFrecuenciaRespiratoria,
            binding.etMucosas,
            binding.etAnamnesis,
            binding.etTratamiento,
            binding.etCompromisos,
            binding.etObservaciones,
            binding.etFuncionarioMatricula,
            binding.etNotificadoNombre,
            binding.etNotificadoIdentificacion,
            binding.etObservacionesRescate,
            binding.etObservacionesRecepcion
        )

        for (campo in camposCriticos) {
            campo.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.postDelayed({
                        if (view.isFocused) {
                            val rect = Rect()
                            view.getDrawingRect(rect)
                            // Expandir ligeramente el rectángulo para incluir la etiqueta del TextInputLayout
                            rect.top = (rect.top - 24).coerceAtLeast(0)
                            rect.bottom += 24
                            // Llamar requestRectangleOnScreen sobre la propia vista para que la jerarquía
                            // acumule correctamente las coordenadas relativas al NestedScrollView
                            view.requestRectangleOnScreen(rect, false)
                        }
                    }, 280)
                }
            }
        }
    }

    private fun setupDropdowns(savedInstanceState: Bundle? = null) {
        val quienReportaOpciones = arrayOf("Seleccionar", "Propietario", "Comunidad", "Policia", "Fundacion", "Otro")
        val adapterReporta = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, quienReportaOpciones)
        binding.autoCompleteQuienReporta.setAdapter(adapterReporta)
        if (savedInstanceState == null || binding.autoCompleteQuienReporta.text.isNullOrEmpty()) {
            binding.autoCompleteQuienReporta.setText(quienReportaOpciones[0], false)
        }

        val actualizarVisibilidadQuienReporta = { seleccion: String ->
            if (seleccion.equals("Otro", ignoreCase = true)) {
                binding.tilQuienReportaOtro.visibility = View.VISIBLE
            } else {
                binding.tilQuienReportaOtro.visibility = View.GONE
            }
        }

        binding.autoCompleteQuienReporta.setOnItemClickListener { _, _, position, _ ->
            val seleccion = quienReportaOpciones[position]
            if (!seleccion.equals("Seleccionar", ignoreCase = true)) {
                binding.tilQuienReporta.error = null
            }
            actualizarVisibilidadQuienReporta(seleccion)
        }

        binding.autoCompleteQuienReporta.addTextChangedListener {
            val actual = binding.autoCompleteQuienReporta.text.toString().trim()
            if (actual.isNotEmpty() && !actual.equals("Seleccionar", ignoreCase = true)) {
                binding.tilQuienReporta.error = null
            }
            actualizarVisibilidadQuienReporta(actual)
        }

        // Dropdown Paso 3: Selector de pruebas rápidas / complementarias (Sincronizado con backend)
        actualizarDropdownExamenes()

        // Dropdown Paso 3: Estado de Cierre de la Petición
        val estadosCierre = arrayOf("En tratamiento", "En observación", "Alta médica")
        val adapterCierre = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosCierre)
        binding.autoCompleteEstadoCierre.setAdapter(adapterCierre)
        if (savedInstanceState == null || binding.autoCompleteEstadoCierre.text.isNullOrEmpty()) {
            binding.autoCompleteEstadoCierre.setText(estadosCierre[0], false)
        }
    }

    /**
     * Configura la lógica para agregar múltiples exámenes clínicos al acta de atención en campo
     */
    private fun setupSeccionExamenesMultiples() {
        binding.autoCompleteTipoPrueba.setOnItemClickListener { _, _, position, _ ->
            val seleccion = binding.autoCompleteTipoPrueba.adapter?.getItem(position)?.toString() ?: ""
            if (seleccion.isNotEmpty() && !seleccion.contains("Ninguna", ignoreCase = true) && !seleccion.contains("Otro", ignoreCase = true)) {
                if (binding.etPruebasComplementarias.text.isNullOrEmpty()) {
                    binding.etPruebasComplementarias.setText(seleccion)
                }
                if (binding.etResultadoPruebas.text.isNullOrEmpty()) {
                    binding.etResultadoPruebas.setText("Negativo")
                }
            } else if (seleccion.contains("Ninguna", ignoreCase = true)) {
                binding.etPruebasComplementarias.text?.clear()
                binding.etResultadoPruebas.text?.clear()
            }
        }

        binding.btnAgregarExamenLista.setOnClickListener {
            val tipoSeleccionado = binding.autoCompleteTipoPrueba.text.toString().trim()
            val detallePrueba = binding.etPruebasComplementarias.text.toString().trim()
            val resultado = binding.etResultadoPruebas.text.toString().trim().ifEmpty { "Pendiente" }

            val nombreFinal = when {
                detallePrueba.isNotEmpty() -> detallePrueba
                tipoSeleccionado.isNotEmpty() && !tipoSeleccionado.contains("Ninguna", ignoreCase = true) -> tipoSeleccionado
                else -> ""
            }

            if (nombreFinal.isEmpty() || nombreFinal.contains("Ninguna", ignoreCase = true)) {
                Toast.makeText(this, "Seleccione o escriba un examen para añadir al acta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (examenesAgregadosList.any { it.nombreExamen.equals(nombreFinal, ignoreCase = true) }) {
                Toast.makeText(this, "Este examen ya está en la lista del acta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val idExamenEncontrado = listaExamenesBackend.firstOrNull {
                it.obtenerNombre().equals(nombreFinal, ignoreCase = true)
            }?.idExamen

            examenesAgregadosList.add(
                ExamenSeleccionadoActa(
                    idExamenCatalogo = idExamenEncontrado,
                    nombreExamen = nombreFinal,
                    resultado = resultado
                )
            )

            renderizarExamenesAgregados()
            programarGuardado()

            // Limpiar campos para permitir ingresar un nuevo examen rápidamente
            binding.autoCompleteTipoPrueba.setText("", false)
            binding.etPruebasComplementarias.text?.clear()
            binding.etResultadoPruebas.text?.clear()
            binding.tilResultadoPruebas.error = null

            Toast.makeText(this, "Examen añadido: $nombreFinal ($resultado)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderizarExamenesAgregados() {
        binding.containerExamenesAgregados.removeAllViews()

        if (examenesAgregadosList.isEmpty()) {
            binding.layoutExamenesAgregados.visibility = View.GONE
            return
        }

        binding.layoutExamenesAgregados.visibility = View.VISIBLE
        binding.tvExamenesAgregadosTitulo.text = "Exámenes clínicos incluidos (${examenesAgregadosList.size}):"

        for (item in examenesAgregadosList) {
            val itemBinding = ItemExamenAgregadoBinding.inflate(layoutInflater, binding.containerExamenesAgregados, false)
            itemBinding.tvNombreExamenItem.text = item.nombreExamen
            itemBinding.tvResultadoExamenItem.text = "Resultado: ${item.resultado}"

            itemBinding.btnEliminarExamenItem.setOnClickListener {
                examenesAgregadosList.remove(item)
                renderizarExamenesAgregados()
                programarGuardado()
            }

            binding.containerExamenesAgregados.addView(itemBinding.root)
        }
    }

    private fun actualizarDropdownExamenes() {
        val nombresExamenes = mutableListOf<String>()
        nombresExamenes.add("Ninguna / No requerida")

        if (listaExamenesBackend.isNotEmpty()) {
            val examenesActivos = listaExamenesBackend
                .filter { it.esActivo() }
                .map { it.obtenerNombre() }
                .filter { it.isNotBlank() }
                .distinct()
            nombresExamenes.addAll(examenesActivos)
        } else {
            nombresExamenes.addAll(
                listOf(
                    "Test Rápido Parvovirus Canino",
                    "Test Rápido Distemper / Moquillo",
                    "Test Rápido Hemoparásitos (Ehrlichia / Anaplasma)",
                    "Test Rápido Triple Felina (VIF / FeLV)",
                    "Raspado Cutáneo / Ectoparásitos",
                    "Coprológico directo"
                )
            )
        }

        if (!nombresExamenes.any { it.contains("Otro examen específico", ignoreCase = true) }) {
            nombresExamenes.add("Otro examen específico")
        }

        val seleccionPrevia = binding.autoCompleteTipoPrueba.text.toString().trim()
        val adapterPruebas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresExamenes)
        binding.autoCompleteTipoPrueba.setAdapter(adapterPruebas)
        if (seleccionPrevia.isEmpty()) {
            binding.autoCompleteTipoPrueba.setText(nombresExamenes[0], false)
        } else {
            binding.autoCompleteTipoPrueba.setText(seleccionPrevia, false)
        }
    }

    /**
     * Carga todos los catálogos oficiales desde el backend (Especies, Razas y Exámenes)
     * para que la app móvil consuma exactamente los insumos creados desde la web.
     */
    private fun cargarCatalogosBackend() {
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Sincronizar Especies
            try {
                val respEspecies = RetrofitClient.apiService.obtenerEspecies("Bearer $token")
                if (respEspecies.isSuccessful && respEspecies.body() != null) {
                    val activas = respEspecies.body()!!.filter { it.activo != false }
                    if (activas.isNotEmpty()) {
                        listaEspeciesBackend.clear()
                        listaEspeciesBackend.addAll(activas)
                    }
                }
            } catch (_: Exception) {}

            // 2. Sincronizar Razas
            try {
                val respRazas = RetrofitClient.apiService.obtenerRazas("Bearer $token")
                if (respRazas.isSuccessful && respRazas.body() != null) {
                    val activas = respRazas.body()!!.filter { it.activo != false }
                    if (activas.isNotEmpty()) {
                        listaRazasBackend.clear()
                        listaRazasBackend.addAll(activas)
                    }
                }
            } catch (_: Exception) {}

            // 3. Sincronizar Catálogo de Exámenes
            try {
                val respExamenes = RetrofitClient.apiService.obtenerCatalogoExamenes("Bearer $token")
                if (respExamenes.isSuccessful && respExamenes.body() != null) {
                    val activas = respExamenes.body()!!.filter { it.esActivo() }
                    if (activas.isNotEmpty()) {
                        listaExamenesBackend.clear()
                        listaExamenesBackend.addAll(activas)
                    }
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                actualizandoCatalogos = true
                try {
                    // Actualizar dropdown de exámenes clínicos en el Paso 3
                    actualizarDropdownExamenes()

                    // Actualizar especies y razas de cada formulario de animal instanciado
                    for (itemBinding in animalBindingsList) {
                        val estadoPendiente = estadoRestauradoPorTarjeta[itemBinding]
                        val especieActual = itemBinding.autoCompleteEspecieCard.text.toString().trim().let {
                            if (it.isEmpty() || it.equals("Seleccionar", ignoreCase = true)) {
                                estadoPendiente?.especie.orEmpty()
                            } else {
                                it
                            }
                        }
                        val razaActual = itemBinding.autoCompleteRazaCard.text.toString().trim().let {
                            if (it.isEmpty() || it.equals("Seleccionar", ignoreCase = true)) {
                                estadoPendiente?.raza.orEmpty()
                            } else {
                                it
                            }
                        }
                        actualizarOpcionesEspecies(itemBinding)
                        if (especieActual.isNotEmpty() && !especieActual.equals("Seleccionar", ignoreCase = true)) {
                            itemBinding.autoCompleteEspecieCard.setText(especieActual, false)
                            itemBinding.tilPacienteRaza.isEnabled = true
                            itemBinding.autoCompleteRazaCard.isEnabled = true
                            itemBinding.tilPacienteRaza.helperText = null
                            actualizarOpcionesRazas(itemBinding, especieActual, razaActual)
                        }
                    }
                } finally {
                    actualizandoCatalogos = false
                }
            }
        }
    }

    /**
     * Actualiza el listado de especies disponibles en la tarjeta del paciente
     */
    private fun actualizarOpcionesEspecies(itemBinding: ItemAnimalFormularioBinding) {
        val listaNombres = if (listaEspeciesBackend.isNotEmpty()) {
            listaEspeciesBackend.map { it.nombre }
        } else {
            listOf("Canino", "Felino", "Ave", "Equino", "Bovino", "Porcino", "Otro")
        }

        val adapterEspecie = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listaNombres)
        itemBinding.autoCompleteEspecieCard.setAdapter(adapterEspecie)

        val actual = itemBinding.autoCompleteEspecieCard.text.toString().trim()
        if (actual.isEmpty() || actual.equals("Seleccionar", ignoreCase = true)) {
            itemBinding.autoCompleteEspecieCard.setText("Seleccionar", false)
            itemBinding.tilPacienteRaza.isEnabled = false
            itemBinding.autoCompleteRazaCard.isEnabled = false
            itemBinding.autoCompleteRazaCard.setText("", false)
            itemBinding.tilPacienteRaza.helperText = "Seleccione primero la especie"
        } else {
            itemBinding.tilPacienteRaza.isEnabled = true
            itemBinding.autoCompleteRazaCard.isEnabled = true
            itemBinding.tilPacienteRaza.helperText = null
            actualizarOpcionesRazas(itemBinding, actual)
        }
    }

    /**
     * Actualiza el listado de razas seleccionables para un paciente según su especie,
     * consumiendo estrictamente las razas registradas en el backend/web.
     */
    private fun actualizarOpcionesRazas(
        itemBinding: ItemAnimalFormularioBinding,
        especie: String,
        razaSugerida: String? = null
    ) {
        if (especie.isEmpty() || especie.equals("Seleccionar", ignoreCase = true)) {
            itemBinding.autoCompleteRazaCard.setAdapter(null)
            itemBinding.autoCompleteRazaCard.setText("", false)
            itemBinding.tilPacienteRaza.isEnabled = false
            itemBinding.autoCompleteRazaCard.isEnabled = false
            itemBinding.tilPacienteRaza.helperText = "Seleccione primero la especie"
            return
        }

        val razasEspecie = mutableListOf<String>()

        // 1. Filtrar razas que correspondan a la especie seleccionada
        if (listaRazasBackend.isNotEmpty()) {
            val especieObj = listaEspeciesBackend.firstOrNull { it.nombre.equals(especie, ignoreCase = true) }

            val razasFiltradas = listaRazasBackend.filter { razaItem ->
                (especieObj != null && razaItem.idEspecie == especieObj.idEspecie) ||
                razaItem.nombreEspecie?.equals(especie, ignoreCase = true) == true ||
                razaItem.nombre.contains(especie, ignoreCase = true)
            }.map { it.nombre }

            razasEspecie.addAll(razasFiltradas)
        }

        // 2. Si no hay razas en la BD para esta especie, proveer opción de contingencia respetuosa
        if (razasEspecie.isEmpty()) {
            val offlineList = catalogoRazasOffline[especie] ?: listOf("Mestizo / Criollo", "Otra raza")
            razasEspecie.addAll(offlineList)
        }

        // Garantizar que exista una opción de mestizo o criollo al inicio
        if (!razasEspecie.any { it.contains("Mestizo", ignoreCase = true) || it.contains("Criollo", ignoreCase = true) }) {
            razasEspecie.add(0, "Mestizo / Criollo")
        }

        val valorActual = (razaSugerida ?: itemBinding.autoCompleteRazaCard.text.toString()).trim()
        if (valorActual.isNotEmpty() && !valorActual.equals("Seleccionar", ignoreCase = true) && !razasEspecie.contains(valorActual)) {
            razasEspecie.add(valorActual)
        }

        val adapterRazas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, razasEspecie)
        itemBinding.autoCompleteRazaCard.setAdapter(adapterRazas)

        if (valorActual.isEmpty() || valorActual.equals("Seleccionar", ignoreCase = true)) {
            itemBinding.autoCompleteRazaCard.setText("Seleccionar", false)
        } else {
            itemBinding.autoCompleteRazaCard.setText(valorActual, false)
        }
    }

    /**
     * Agrega dinámicamente un formulario con View Binding para un paciente/animal con aceleradores M3.
     * Permite recibir un [initialState] para restaurar fielmente todos los datos tras captura multimedia o recreación.
     */
    private fun agregarFormularioAnimal(initialState: AnimalCardState? = null) {
        val itemBinding = ItemAnimalFormularioBinding.inflate(layoutInflater, binding.containerAnimales, false)
        val numeroAnimal = animalBindingsList.size + 1
        itemBinding.tvAnimalNumero.text = "Paciente #$numeroAnimal"

        // 1. Selector de Especies asistido por catálogo web (por defecto "Seleccionar")
        actualizarOpcionesEspecies(itemBinding)

        // 2. Selector de Sexo (por defecto "Seleccionar", obligatorio)
        val sexos = arrayOf("Seleccionar", "Macho", "Hembra", "Desconocido")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sexos)
        itemBinding.autoCompleteSexoCard.setAdapter(adapterSexo)
        itemBinding.autoCompleteSexoCard.setText(sexos[0], false)

        itemBinding.autoCompleteSexoCard.setOnItemClickListener { _, _, position, _ ->
            val sel = sexos.getOrNull(position).orEmpty()
            if (sel.isNotEmpty() && !sel.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteSexo.error = null
            }
        }
        itemBinding.autoCompleteSexoCard.addTextChangedListener {
            val sel = it?.toString().orEmpty().trim()
            if (sel.isNotEmpty() && !sel.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteSexo.error = null
            }
        }

        // 3. Selector de Estado Reproductivo (Esterilizado)
        val esterilizadoOpciones = arrayOf("Si", "No", "No se sabe")
        val adapterEsterilizado = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, esterilizadoOpciones)
        itemBinding.autoCompleteEsterilizadoCard.setAdapter(adapterEsterilizado)
        itemBinding.autoCompleteEsterilizadoCard.setText(esterilizadoOpciones[1], false)

        // 4. Selector de Color con catálogo frecuente (obligatorio)
        val adapterColor = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, coloresFrecuentes)
        itemBinding.autoCompleteColorCard.setAdapter(adapterColor)

        itemBinding.autoCompleteColorCard.setOnItemClickListener { _, _, _, _ ->
            itemBinding.tilPacienteColor.error = null
        }
        itemBinding.autoCompleteColorCard.addTextChangedListener {
            val col = it?.toString().orEmpty().trim()
            if (col.isNotEmpty() && !col.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteColor.error = null
            }
        }

        // 5. Catálogo de Razas dinámicas reactivas a la especie del backend (inicia deshabilitado hasta elegir especie)
        itemBinding.tilPacienteRaza.isEnabled = false
        itemBinding.autoCompleteRazaCard.isEnabled = false
        itemBinding.tilPacienteRaza.helperText = "Seleccione primero la especie"
        itemBinding.autoCompleteRazaCard.setText("", false)

        itemBinding.autoCompleteEspecieCard.setOnItemClickListener { _, _, position, _ ->
            val especieSeleccionada = itemBinding.autoCompleteEspecieCard.adapter?.getItem(position)?.toString() ?: ""
            if (!isRestaurandoBorrador && !actualizandoCatalogos) {
                estadoRestauradoPorTarjeta.remove(itemBinding)
            }
            if (especieSeleccionada.isNotEmpty() && !especieSeleccionada.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteEspecie.error = null
                itemBinding.tilPacienteRaza.isEnabled = true
                itemBinding.autoCompleteRazaCard.isEnabled = true
                itemBinding.tilPacienteRaza.helperText = null
                actualizarOpcionesRazas(itemBinding, especieSeleccionada)
                itemBinding.autoCompleteRazaCard.setText("Seleccionar", false)
            } else {
                itemBinding.tilPacienteRaza.isEnabled = false
                itemBinding.autoCompleteRazaCard.isEnabled = false
                itemBinding.autoCompleteRazaCard.setText("", false)
                itemBinding.tilPacienteRaza.helperText = "Seleccione primero la especie"
            }
        }
        itemBinding.autoCompleteEspecieCard.addTextChangedListener {
            val especieActual = it?.toString().orEmpty().trim()
            val razaPendiente = if (isRestaurandoBorrador || actualizandoCatalogos) {
                estadoRestauradoPorTarjeta[itemBinding]?.raza
            } else {
                null
            }
            if (especieActual.isNotEmpty() && !especieActual.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteEspecie.error = null
                itemBinding.tilPacienteRaza.isEnabled = true
                itemBinding.autoCompleteRazaCard.isEnabled = true
                itemBinding.tilPacienteRaza.helperText = null
                actualizarOpcionesRazas(itemBinding, especieActual, razaPendiente)
            } else if (!actualizandoCatalogos) {
                itemBinding.tilPacienteRaza.isEnabled = false
                itemBinding.autoCompleteRazaCard.isEnabled = false
                itemBinding.autoCompleteRazaCard.setText("", false)
                itemBinding.tilPacienteRaza.helperText = "Seleccione primero la especie"
            }
        }

        itemBinding.autoCompleteRazaCard.setOnItemClickListener { _, _, position, _ ->
            val razaSeleccionada = itemBinding.autoCompleteRazaCard.adapter?.getItem(position)?.toString() ?: ""
            if (!isRestaurandoBorrador && !actualizandoCatalogos) {
                estadoRestauradoPorTarjeta[itemBinding]?.let { previo ->
                    estadoRestauradoPorTarjeta[itemBinding] = previo.copy(raza = razaSeleccionada)
                }
            }
            if (razaSeleccionada.isNotEmpty() && !razaSeleccionada.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteRaza.error = null
            }
        }
        itemBinding.autoCompleteRazaCard.addTextChangedListener {
            val razaActual = it?.toString().orEmpty().trim()
            if (razaActual.isNotEmpty() && !razaActual.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteRaza.error = null
            }
        }

        // 6. Contador interactivo de Edad (Stepper con flechitas [-] [+] y Chips)
        val adapterUnidadEdad = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, unidadesEdad)
        itemBinding.autoCompleteEdadUnidad.setAdapter(adapterUnidadEdad)
        itemBinding.autoCompleteEdadUnidad.setText("Años", false)

        val sincronizarEdadTexto = {
            val num = itemBinding.etEdadNumero.text.toString().trim().toIntOrNull() ?: 1
            val unidad = itemBinding.autoCompleteEdadUnidad.text.toString().trim().ifEmpty { "Años" }
            val textoEdad = "$num $unidad"
            itemBinding.etPacienteEdadCard.setText(textoEdad)
        }

        itemBinding.etEdadNumero.setText("1")
        sincronizarEdadTexto()

        itemBinding.btnEdadMenos.setOnClickListener {
            val actual = itemBinding.etEdadNumero.text.toString().trim().toIntOrNull() ?: 1
            if (actual > 1) {
                itemBinding.etEdadNumero.setText((actual - 1).toString())
                sincronizarEdadTexto()
            }
        }

        itemBinding.btnEdadMas.setOnClickListener {
            val actual = itemBinding.etEdadNumero.text.toString().trim().toIntOrNull() ?: 0
            if (actual < 50) {
                itemBinding.etEdadNumero.setText((actual + 1).toString())
                sincronizarEdadTexto()
            }
        }

        itemBinding.etEdadNumero.addTextChangedListener {
            sincronizarEdadTexto()
        }

        itemBinding.autoCompleteEdadUnidad.setOnItemClickListener { _, _, _, _ ->
            sincronizarEdadTexto()
        }

        // Chips rápidos de edad
        itemBinding.chipCachorro.setOnClickListener {
            itemBinding.etEdadNumero.setText("4")
            itemBinding.autoCompleteEdadUnidad.setText("Meses", false)
            sincronizarEdadTexto()
        }

        itemBinding.chipAdulto.setOnClickListener {
            itemBinding.etEdadNumero.setText("2")
            itemBinding.autoCompleteEdadUnidad.setText("Años", false)
            sincronizarEdadTexto()
        }

        itemBinding.chipSenior.setOnClickListener {
            itemBinding.etEdadNumero.setText("8")
            itemBinding.autoCompleteEdadUnidad.setText("Años", false)
            sincronizarEdadTexto()
        }

        // 7. Ajuste rápido de peso con botones [-] y [+]
        itemBinding.btnPesoMenos.setOnClickListener {
            val pesoActual = itemBinding.etPacientePesoCard.text.toString().trim().toDoubleOrNull() ?: 0.0
            if (pesoActual >= 0.5) {
                val nuevoPeso = Math.max(0.0, pesoActual - 0.5)
                itemBinding.etPacientePesoCard.setText(String.format(Locale.US, "%.1f", nuevoPeso))
            }
        }

        itemBinding.btnPesoMas.setOnClickListener {
            val pesoActual = itemBinding.etPacientePesoCard.text.toString().trim().toDoubleOrNull() ?: 0.0
            val nuevoPeso = pesoActual + 0.5
            itemBinding.etPacientePesoCard.setText(String.format(Locale.US, "%.1f", nuevoPeso))
        }

        // 8. Chip rápido para animal en condición de calle / comunitario
        itemBinding.chipSinNombre.setOnClickListener {
            val especieRaw = itemBinding.autoCompleteEspecieCard.text.toString().trim()
            val especie = if (especieRaw.isEmpty() || especieRaw.equals("Seleccionar", ignoreCase = true)) "Paciente" else especieRaw
            itemBinding.etPacienteNombreCard.setText("Comunitario ($especie)")
        }

        // 9. Eliminación de paciente de la lista con confirmación Material 3
        itemBinding.btnEliminarAnimal.setOnClickListener {
            val nombre = itemBinding.etPacienteNombreCard.text?.toString()?.trim().orEmpty()
            val titulo = if (nombre.isNotEmpty()) "¿Eliminar paciente $nombre?" else "¿Eliminar este paciente?"
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_delete)
                .setTitle(titulo)
                .setMessage("Se eliminarán los datos registrados de este paciente en el acta actual.")
                .setPositiveButton("Eliminar") { dialog, _ ->
                    dialog.dismiss()
                    estadoRestauradoPorTarjeta.remove(itemBinding)
                    binding.containerAnimales.removeView(itemBinding.root)
                    animalBindingsList.remove(itemBinding)
                    actualizarNumeracionYBotonesEliminar()
                    programarGuardado()
                }
                .setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        // Si tenemos un estado previo a restaurar (ej. tras tomar foto, grabar video o recreación)
        if (initialState != null) {
            estadoRestauradoPorTarjeta[itemBinding] = initialState
            if (initialState.nombre.isNotEmpty()) {
                itemBinding.etPacienteNombreCard.setText(initialState.nombre)
            }
            if (initialState.especie.isNotEmpty() && !initialState.especie.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.autoCompleteEspecieCard.setText(initialState.especie, false)
                itemBinding.tilPacienteRaza.isEnabled = true
                itemBinding.autoCompleteRazaCard.isEnabled = true
                itemBinding.tilPacienteRaza.helperText = null
                actualizarOpcionesRazas(itemBinding, initialState.especie, initialState.raza)
            }
            if (initialState.sexo.isNotEmpty()) {
                itemBinding.autoCompleteSexoCard.setText(initialState.sexo, false)
            }
            if (initialState.color.isNotEmpty()) {
                itemBinding.autoCompleteColorCard.setText(initialState.color, false)
            }
            if (initialState.edadNumero.isNotEmpty()) {
                itemBinding.etEdadNumero.setText(initialState.edadNumero)
            }
            if (initialState.edadUnidad.isNotEmpty()) {
                itemBinding.autoCompleteEdadUnidad.setText(initialState.edadUnidad, false)
                sincronizarEdadTexto()
            }
            if (initialState.edadTexto.isNotEmpty()) {
                itemBinding.etPacienteEdadCard.setText(initialState.edadTexto)
            }
            if (initialState.peso.isNotEmpty()) {
                itemBinding.etPacientePesoCard.setText(initialState.peso)
            }
            if (initialState.esterilizado.isNotEmpty()) {
                itemBinding.autoCompleteEsterilizadoCard.setText(initialState.esterilizado, false)
            }
            if (initialState.descripcion.isNotEmpty()) {
                itemBinding.etPacienteDescripcionCard.setText(initialState.descripcion)
            }
        }

        val camposAnimal = listOf(
            itemBinding.etPacienteNombreCard,
            itemBinding.autoCompleteEspecieCard,
            itemBinding.autoCompleteRazaCard,
            itemBinding.autoCompleteSexoCard,
            itemBinding.autoCompleteColorCard,
            itemBinding.etEdadNumero,
            itemBinding.autoCompleteEdadUnidad,
            itemBinding.etPacienteEdadCard,
            itemBinding.etPacientePesoCard,
            itemBinding.autoCompleteEsterilizadoCard,
            itemBinding.etPacienteDescripcionCard
        )
        for (campo in camposAnimal) {
            campo.addTextChangedListener { programarGuardado() }
        }

        binding.containerAnimales.addView(itemBinding.root)
        animalBindingsList.add(itemBinding)
        actualizarNumeracionYBotonesEliminar()
    }

    private fun setupAceleradoresCampo() {
        // --- PASO 1: Aceleradores del Acta y Datos del Notificado ---
        binding.chipSinPropietario.setOnClickListener {
            binding.etPropietarioNombre.setText("Comunidad / Sin tenedor conocido")
            binding.etPropietarioCedula.setText("222222222")
            binding.etPropietarioTelefono.setText("3000000000")
            val lugar = binding.etLugarAtencion.text.toString().trim()
            if (lugar.isNotEmpty() && binding.etPropietarioDireccion.text.isNullOrEmpty()) {
                binding.etPropietarioDireccion.setText(lugar)
            }
            binding.autoCompleteQuienReporta.setText("Comunidad", false)
            binding.tilQuienReporta.error = null
            binding.tilQuienReportaOtro.visibility = View.GONE
            Toast.makeText(this, "Datos de reporte comunitario cargados", Toast.LENGTH_SHORT).show()
        }

        val solicitudesFrecuentes = arrayOf(
            "Verificación presunta situación de maltrato animal",
            "Atención de urgencia por atropellamiento en vía pública",
            "Atención médica por animal enfermo / herido en calle",
            "Inspección por presunto abandono y desatención",
            "Revisión y valoración médico veterinaria en campo"
        )
        val adapterSolicitudes = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, solicitudesFrecuentes)
        binding.etSolicitudAtencion.setAdapter(adapterSolicitudes)

        // --- PASO 2: Aceleradores Clínicos (Mucosas, Anamnesis, Tratamiento) ---
        val adapterMucosas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mucosasOpciones)
        binding.etMucosas.setAdapter(adapterMucosas)

        binding.chipAnamnesisCalle.setOnClickListener {
            concatenarTextoEnCampo(binding.etAnamnesis, "Paciente encontrado en vía pública / condición de calle")
        }
        binding.chipAnamnesisHerido.setOnClickListener {
            concatenarTextoEnCampo(binding.etAnamnesis, "Presenta lesiones evidentes por presunto trauma o agresión")
        }
        binding.chipAnamnesisDesnutrido.setOnClickListener {
            concatenarTextoEnCampo(binding.etAnamnesis, "Baja condición corporal, signos de desnutrición")
        }

        binding.chipTratamientoCuracion.setOnClickListener {
            concatenarTextoEnCampo(binding.etTratamiento, "Curación tópica de heridas y desinfección antiséptica")
        }
        binding.chipTratamientoMedicacion.setOnClickListener {
            concatenarTextoEnCampo(binding.etTratamiento, "Administración de antibiótico, analgésico y desparasitante según peso")
        }
        binding.chipTratamientoTraslado.setOnClickListener {
            concatenarTextoEnCampo(binding.etTratamiento, "Estabilización y traslado al Centro de Bienestar Animal (CBA)")
        }

        // --- PASO 3: Aceleradores de Cierre (Resultados, Compromisos, Plazo) ---
        binding.chipResultadoNegativo.setOnClickListener {
            binding.etResultadoPruebas.setText("Negativo")
        }
        binding.chipResultadoPositivo.setOnClickListener {
            binding.etResultadoPruebas.setText("Positivo (+)")
        }
        binding.chipResultadoPendiente.setOnClickListener {
            binding.etResultadoPruebas.setText("Pendiente resultado de laboratorio")
        }

        binding.chipCompromisoAgua.setOnClickListener {
            concatenarTextoEnCampo(binding.etCompromisos, "Garantizar suministro constante de agua potable y alimento balanceado")
        }
        binding.chipCompromisoTecho.setOnClickListener {
            concatenarTextoEnCampo(binding.etCompromisos, "Proveer espacio techado, seco y protegido de condiciones climáticas")
        }
        binding.chipCompromisoVacunas.setOnClickListener {
            concatenarTextoEnCampo(binding.etCompromisos, "Garantizar plan de vacunación y desparasitación al día")
        }
        binding.chipCompromisoVeterinario.setOnClickListener {
            concatenarTextoEnCampo(binding.etCompromisos, "Cumplir con valoración médico veterinaria periódica")
        }
        binding.chipCompromisoEsterilizacion.setOnClickListener {
            concatenarTextoEnCampo(binding.etCompromisos, "Agendar y cumplir con esterilización preventiva en jornadas CBA")
        }

        // Stepper contador y chips para Plazo de Días
        if (binding.etPlazoDias.text.isNullOrEmpty()) {
            binding.etPlazoDias.setText("8")
        }

        binding.btnPlazoMenos.setOnClickListener {
            val actual = binding.etPlazoDias.text.toString().trim().toIntOrNull() ?: 8
            if (actual > 1) {
                binding.etPlazoDias.setText((actual - 1).toString())
            }
        }

        binding.btnPlazoMas.setOnClickListener {
            val actual = binding.etPlazoDias.text.toString().trim().toIntOrNull() ?: 0
            if (actual < 60) {
                binding.etPlazoDias.setText((actual + 1).toString())
            }
        }

        binding.chipPlazo3.setOnClickListener { binding.etPlazoDias.setText("3") }
        binding.chipPlazo5.setOnClickListener { binding.etPlazoDias.setText("5") }
        binding.chipPlazo8.setOnClickListener { binding.etPlazoDias.setText("8") }
        binding.chipPlazo15.setOnClickListener { binding.etPlazoDias.setText("15") }
        binding.chipPlazo30.setOnClickListener { binding.etPlazoDias.setText("30") }
    }

    // =========================================================================
    // MÓDULO MALETÍN DE MEDICAMENTOS: SUMINISTRO Y DESCUENTO EN CAMPO
    // =========================================================================
    private fun setupModuloMaletinTratamiento() {
        binding.btnAplicarMedicamentoMaletin.setOnClickListener {
            mostrarBottomSheetAplicarMedicamento()
        }
        actualizarVistaMedicamentosAplicados()
    }

    private fun mostrarBottomSheetAplicarMedicamento() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetAplicarMedicamentoBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        val maletinManager = MaletinManager.getInstance(this)
        val medicamentosDisponibles = maletinManager.getMedicamentos().filter { it.cantidadDisponible > 0 }

        if (medicamentosDisponibles.isEmpty()) {
            Toast.makeText(this, "No hay medicamentos con existencias en tu maletín", Toast.LENGTH_LONG).show()
            return
        }

        var medicamentoSeleccionado: MedicamentoMaletin? = null

        // 1. Selector de medicamentos
        val medNombres = medicamentosDisponibles.map { med ->
            val displayStock = if (med.cantidadDisponible % 1.0 == 0.0) med.cantidadDisponible.toInt().toString() else med.cantidadDisponible.toString()
            "${med.nombre} (Disp: $displayStock ${med.unidadMedida})"
        }
        val medAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, medNombres)
        sheetBinding.autoCompleteMedicamento.setAdapter(medAdapter)

        sheetBinding.autoCompleteMedicamento.setOnItemClickListener { _, _, position, _ ->
            val med = medicamentosDisponibles[position]
            medicamentoSeleccionado = med
            sheetBinding.cardInfoMedicamento.visibility = View.VISIBLE
            sheetBinding.tvInfoStockDisponible.text = "✓ Stock disponible: ${med.cantidadDisponible} ${med.unidadMedida} • Lote: ${med.lote}"
            sheetBinding.tvInfoPresentacionVia.text = "Presentación: ${med.presentacion} • Vía sugerida: ${med.viaRecomendada}"
            sheetBinding.tilDosisCantidad.suffixText = med.unidadMedida

            // Preseleccionar vía sugerida
            when {
                med.viaRecomendada.contains("SC", ignoreCase = true) -> sheetBinding.autoCompleteVia.setText("Subcutánea (SC)", false)
                med.viaRecomendada.contains("IM", ignoreCase = true) -> sheetBinding.autoCompleteVia.setText("Intramuscular (IM)", false)
                med.viaRecomendada.contains("Oral", ignoreCase = true) -> sheetBinding.autoCompleteVia.setText("Oral", false)
                med.viaRecomendada.contains("Tópica", ignoreCase = true) -> sheetBinding.autoCompleteVia.setText("Tópica", false)
                med.viaRecomendada.contains("IV", ignoreCase = true) -> sheetBinding.autoCompleteVia.setText("Intravenosa (IV)", false)
            }
        }

        // 2. Pacientes disponibles en el formulario
        val listaPacientes = mutableListOf("Todos los pacientes")
        animalBindingsList.forEachIndexed { index, b ->
            val nombre = b.etPacienteNombreCard.text.toString().trim()
            if (nombre.isNotEmpty()) {
                listaPacientes.add(nombre)
            } else {
                listaPacientes.add("Paciente ${index + 1}")
            }
        }
        val pacienteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listaPacientes)
        sheetBinding.autoCompletePaciente.setAdapter(pacienteAdapter)
        sheetBinding.autoCompletePaciente.setText(listaPacientes.first(), false)

        // 3. Vías de administración
        val vias = arrayOf("Subcutánea (SC)", "Intramuscular (IM)", "Intravenosa (IV)", "Oral", "Tópica", "Oftálmica / Ótica")
        val viaAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vias)
        sheetBinding.autoCompleteVia.setAdapter(viaAdapter)
        sheetBinding.autoCompleteVia.setText(vias[0], false)

        // 4. Stepper y chips de dosis M3 (Enteros)
        sheetBinding.btnDosisMenos.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val actual = sheetBinding.etDosisCantidad.text.toString().toIntOrNull() ?: 1
            val nuevo = maxOf(1, actual - 1)
            sheetBinding.etDosisCantidad.setText(nuevo.toString())
        }

        sheetBinding.btnDosisMas.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val actual = sheetBinding.etDosisCantidad.text.toString().toIntOrNull() ?: 1
            val maxStock = (medicamentoSeleccionado?.cantidadDisponible ?: 999.0).toInt()
            val nuevo = minOf(maxStock, actual + 1)
            sheetBinding.etDosisCantidad.setText(nuevo.toString())
        }

        sheetBinding.chipDosis05.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            sheetBinding.etDosisCantidad.setText("1")
        }
        sheetBinding.chipDosis10.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            sheetBinding.etDosisCantidad.setText("2")
        }
        sheetBinding.chipDosis15.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            sheetBinding.etDosisCantidad.setText("3")
        }
        sheetBinding.chipDosis20.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            sheetBinding.etDosisCantidad.setText("5")
        }
        sheetBinding.chipDosis50.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val maxStock = (medicamentoSeleccionado?.cantidadDisponible ?: 1.0).toInt().coerceAtLeast(1)
            sheetBinding.etDosisCantidad.setText(maxStock.toString())
        }

        sheetBinding.btnCerrarBottomSheet.setOnClickListener {
            bottomSheet.dismiss()
        }

        // 5. Confirmar aplicación y descontar stock
        sheetBinding.btnConfirmarAplicar.setOnClickListener {
            val med = medicamentoSeleccionado
            if (med == null) {
                sheetBinding.tilSelectorMedicamento.error = "Selecciona un medicamento del maletín"
                return@setOnClickListener
            }
            sheetBinding.tilSelectorMedicamento.error = null

            val dosis = sheetBinding.etDosisCantidad.text.toString().toIntOrNull()
            if (dosis == null || dosis <= 0) {
                sheetBinding.tilDosisCantidad.error = "Ingresa una cantidad entera válida (mínimo 1)"
                return@setOnClickListener
            }
            if (dosis > med.cantidadDisponible.toInt()) {
                sheetBinding.tilDosisCantidad.error = "Supera el stock disponible (${med.cantidadDisponible.toInt()} ${med.unidadMedida})"
                return@setOnClickListener
            }
            sheetBinding.tilDosisCantidad.error = null

            val via = sheetBinding.autoCompleteVia.text.toString().trim().ifEmpty { "SC" }
            val paciente = sheetBinding.autoCompletePaciente.text.toString().trim().ifEmpty { "Paciente" }

            val exito = maletinManager.descontarStock(med.id, dosis.toDouble())
            if (!exito) {
                Toast.makeText(this, "No se pudo descontar del maletín (stock insuficiente)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            val aplicado = MedicamentoAplicadoActa(
                idMedicamento = med.id,
                nombre = med.nombre,
                concentracion = med.concentracion,
                cantidad = dosis.toDouble(),
                unidadMedida = med.unidadMedida,
                viaAdministracion = via,
                pacienteDestino = paciente,
                clientUuid = java.util.UUID.randomUUID().toString()
            )
            medicamentosAplicadosList.add(aplicado)

            // Redacción automática en etTratamiento
            if (sheetBinding.swSincronizarTratamientoTexto.isChecked) {
                val textoRedactado = "Suministro de ${med.nombre} ($dosis ${med.unidadMedida} - vía $via) a $paciente"
                concatenarTextoEnCampo(binding.etTratamiento, textoRedactado)
            }

            actualizarVistaMedicamentosAplicados()
            programarGuardado()
            bottomSheet.dismiss()
            Toast.makeText(this, "Medicamento aplicado y descontado del maletín", Toast.LENGTH_SHORT).show()
        }

        bottomSheet.show()
    }

    private fun actualizarVistaMedicamentosAplicados() {
        val total = medicamentosAplicadosList.size
        binding.tvConteoMedicamentosAplicados.text = "$total aplicados"

        if (total == 0) {
            binding.containerMedicamentosAplicados.visibility = View.GONE
            binding.containerMedicamentosAplicados.removeAllViews()
        } else {
            binding.containerMedicamentosAplicados.visibility = View.VISIBLE
            binding.containerMedicamentosAplicados.removeAllViews()

            for (item in medicamentosAplicadosList) {
                val itemBinding = ItemMedicamentoAplicadoActaBinding.inflate(
                    layoutInflater, binding.containerMedicamentosAplicados, false
                )

                val dosisTxt = if (item.cantidad % 1.0 == 0.0) item.cantidad.toInt().toString() else item.cantidad.toString()
                itemBinding.tvNombreMedicamentoAplicado.text = "${item.nombre} • $dosisTxt ${item.unidadMedida}"
                itemBinding.tvDetalleAplicacion.text = "Vía ${item.viaAdministracion} • Paciente: ${item.pacienteDestino ?: "General"}"

                itemBinding.btnEliminarMedicamentoAplicado.setOnClickListener {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("¿Remover medicamento?")
                        .setMessage("Se devolverán $dosisTxt ${item.unidadMedida} de ${item.nombre} al maletín asignado.")
                        .setPositiveButton("Devolver al maletín") { dialog, _ ->
                            dialog.dismiss()
                            MaletinManager.getInstance(this).devolverStock(item.idMedicamento, item.cantidad)
                            medicamentosAplicadosList.remove(item)
                            actualizarVistaMedicamentosAplicados()
                            programarGuardado()
                            Toast.makeText(this, "Stock devuelto al maletín", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                binding.containerMedicamentosAplicados.addView(itemBinding.root)
            }
        }
    }

    private fun concatenarTextoEnCampo(editText: android.widget.EditText, frase: String) {
        val actual = editText.text.toString().trim()
        if (actual.isEmpty()) {
            editText.setText(frase)
        } else if (!actual.contains(frase, ignoreCase = true)) {
            editText.setText("$actual. $frase")
        }
    }

    private fun actualizarNumeracionYBotonesEliminar() {
        for (i in animalBindingsList.indices) {
            val item = animalBindingsList[i]
            item.tvAnimalNumero.text = "Paciente #${i + 1}"
            item.btnEliminarAnimal.visibility = if (animalBindingsList.size > 1) View.VISIBLE else View.GONE
        }
    }

    private fun updateStepper() {
        binding.step1Layout.visibility = View.GONE
        binding.step2Layout.visibility = View.GONE
        binding.step3Layout.visibility = View.GONE

        val colorPrimary = ContextCompat.getColor(this, R.color.primary)
        val colorMuted = ContextCompat.getColor(this, R.color.text_muted)
        val circleGray = R.drawable.circle_gray
        val circleBlue = R.drawable.circle_blue

        binding.tvStep1Num.setBackgroundResource(circleGray)
        binding.tvStep2Num.setBackgroundResource(circleGray)
        binding.tvStep3Num.setBackgroundResource(circleGray)
        binding.tvStep1Num.setTextColor(colorMuted)
        binding.tvStep2Num.setTextColor(colorMuted)
        binding.tvStep3Num.setTextColor(colorMuted)
        binding.tvStep1Text.setTextColor(colorMuted)
        binding.tvStep2Text.setTextColor(colorMuted)
        binding.tvStep3Text.setTextColor(colorMuted)

        when (currentStep) {
            1 -> {
                binding.step1Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(circleBlue)
                binding.tvStep1Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.btnAnterior.visibility = View.GONE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            2 -> {
                binding.step2Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(circleBlue)
                binding.tvStep1Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.tvStep2Num.setBackgroundResource(circleBlue)
                binding.tvStep2Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep2Text.setTextColor(colorPrimary)
                binding.btnAnterior.visibility = View.VISIBLE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            3 -> {
                binding.step3Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(circleBlue)
                binding.tvStep1Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.tvStep2Num.setBackgroundResource(circleBlue)
                binding.tvStep2Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep2Text.setTextColor(colorPrimary)
                binding.tvStep3Num.setBackgroundResource(circleBlue)
                binding.tvStep3Num.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvStep3Text.setTextColor(colorPrimary)
                binding.btnAnterior.visibility = View.VISIBLE
                binding.btnSiguiente.text = "Finalizar y guardar acta"
            }
        }

        binding.scrollForm.smoothScrollTo(0, 0)
    }

    private fun validarPaso1(): Boolean {
        var valido = true

        val solicitud = binding.etSolicitudAtencion.text.toString().trim()
        if (solicitud.isEmpty()) {
            binding.tilSolicitudAtencion.error = "Campo obligatorio"
            valido = false
        } else {
            binding.tilSolicitudAtencion.error = null
        }

        val quienReporta = binding.autoCompleteQuienReporta.text.toString().trim()
        if (quienReporta.isEmpty() || quienReporta.equals("Seleccionar", ignoreCase = true)) {
            binding.tilQuienReporta.error = "Seleccione quién reporta el caso *"
            valido = false
        } else {
            binding.tilQuienReporta.error = null
            if (quienReporta.equals("Otro", ignoreCase = true)) {
                val quienOtro = binding.etQuienReportaOtro.text.toString().trim()
                if (quienOtro.isEmpty()) {
                    binding.tilQuienReportaOtro.error = "Especifique quién reporta *"
                    valido = false
                } else {
                    binding.tilQuienReportaOtro.error = null
                }
            } else {
                binding.tilQuienReportaOtro.error = null
            }
        }

        val lugarAtencion = binding.etLugarAtencion.text.toString().trim()
        if (lugarAtencion.isEmpty()) {
            binding.tvLugarAtencionStatus.text = "Requerido"
            binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#B91C1C"))
            Toast.makeText(this, "Debe capturar la posición actual del lugar de atención.", Toast.LENGTH_SHORT).show()
            valido = false
        } else {
            binding.tvLugarAtencionStatus.setTextColor(Color.parseColor("#047857"))
        }

        val nombre = binding.etPropietarioNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            binding.tilPropietarioNombre.error = "Nombre requerido"
            valido = false
        } else {
            binding.tilPropietarioNombre.error = null
        }

        val cedula = binding.etPropietarioCedula.text.toString().trim()
        if (cedula.isEmpty()) {
            binding.tilPropietarioCedula.error = "Cédula requerida"
            valido = false
        } else {
            binding.tilPropietarioCedula.error = null
        }

        val telefono = binding.etPropietarioTelefono.text.toString().trim()
        if (telefono.isEmpty()) {
            binding.tilPropietarioTelefono.error = "Teléfono requerido"
            valido = false
        } else {
            binding.tilPropietarioTelefono.error = null
        }

        val barrio = binding.etPropietarioBarrio.text.toString().trim()
        if (barrio.isEmpty()) {
            binding.tilPropietarioBarrio.error = "Barrio requerido"
            valido = false
        } else {
            binding.tilPropietarioBarrio.error = null
        }

        val direccion = binding.etPropietarioDireccion.text.toString().trim()
        if (direccion.isEmpty()) {
            binding.tilPropietarioDireccion.error = "Dirección requerida"
            valido = false
        } else {
            binding.tilPropietarioDireccion.error = null
        }

        if (!valido) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.warning_icon4)
                .setTitle("Campos incompletos en Paso 1")
                .setMessage("Por favor diligencie todos los campos obligatorios marcados antes de continuar al siguiente paso.")
                .setPositiveButton("Revisar", null)
                .show()
        }
        return valido
    }

    private fun validarPaso2(): Boolean {
        var valido = true

        if (animalBindingsList.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.baseline_pets_24)
                .setTitle("Sin pacientes agregados")
                .setMessage("Debe agregar al menos un paciente/animal para registrar la atención clínica.")
                .setPositiveButton("Agregar paciente") { dialog, _ ->
                    dialog.dismiss()
                    agregarFormularioAnimal()
                }
                .setNegativeButton("Cerrar", null)
                .show()
            return false
        }

        for (itemBinding in animalBindingsList) {
            val nombre = itemBinding.etPacienteNombreCard.text.toString().trim()
            if (nombre.isEmpty()) {
                itemBinding.tilPacienteNombre.error = "Nombre requerido"
                valido = false
            } else {
                itemBinding.tilPacienteNombre.error = null
            }

            val especie = itemBinding.autoCompleteEspecieCard.text.toString().trim()
            if (especie.isEmpty() || especie.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteEspecie.error = "Seleccione la especie obligatoria"
                valido = false
            } else {
                itemBinding.tilPacienteEspecie.error = null
            }

            val raza = itemBinding.autoCompleteRazaCard.text.toString().trim()
            if (raza.isEmpty() || raza.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteRaza.error = "Seleccione la raza obligatoria"
                valido = false
            } else {
                itemBinding.tilPacienteRaza.error = null
            }

            val sexo = itemBinding.autoCompleteSexoCard.text.toString().trim()
            if (sexo.isEmpty() || sexo.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteSexo.error = "Seleccione el sexo obligatorio *"
                valido = false
            } else {
                itemBinding.tilPacienteSexo.error = null
            }

            val color = itemBinding.autoCompleteColorCard.text.toString().trim()
            if (color.isEmpty() || color.equals("Seleccionar", ignoreCase = true)) {
                itemBinding.tilPacienteColor.error = "Ingrese o seleccione el color obligatorio *"
                valido = false
            } else {
                itemBinding.tilPacienteColor.error = null
            }
        }

        val anamnesis = binding.etAnamnesis.text.toString().trim()
        if (anamnesis.isEmpty()) {
            binding.tilAnamnesis.error = "Anamnesis requerida"
            valido = false
        } else {
            binding.tilAnamnesis.error = null
        }

        val tratamiento = binding.etTratamiento.text.toString().trim()
        if (tratamiento.isEmpty()) {
            binding.tilTratamiento.error = "Tratamiento requerido"
            valido = false
        } else {
            binding.tilTratamiento.error = null
        }

        if (!valido) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.warning_icon4)
                .setTitle("Datos clínicos incompletos")
                .setMessage("Por favor complete los datos clínicos requeridos (diagnóstico, estado reproductivo y tratamiento) en el Paso 2.")
                .setPositiveButton("Revisar", null)
                .show()
        }
        return valido
    }

    private fun validarPaso3(): Boolean {
        var valido = true

        val compromisos = binding.etCompromisos.text.toString().trim()
        if (compromisos.isEmpty()) {
            binding.tilCompromisos.error = "Especifique los compromisos o acuerdos"
            valido = false
        } else {
            binding.tilCompromisos.error = null
        }

        val tipoPrueba = binding.autoCompleteTipoPrueba.text.toString().trim()
        val pruebas = binding.etPruebasComplementarias.text.toString().trim()
        val resultado = binding.etResultadoPruebas.text.toString().trim()

        if (examenesAgregadosList.isEmpty() && ((tipoPrueba.isNotEmpty() && !tipoPrueba.contains("Ninguna", ignoreCase = true)) || pruebas.isNotEmpty())) {
            if (resultado.isEmpty()) {
                binding.tilResultadoPruebas.error = "Ingrese el resultado o presione '+ Añadir examen al acta'"
                valido = false
            } else {
                binding.tilResultadoPruebas.error = null
            }
        } else {
            binding.tilResultadoPruebas.error = null
        }

        val funcionarioNombre = binding.etFuncionarioNombre.text.toString().trim()
        if (funcionarioNombre.isEmpty()) {
            binding.tilFuncionarioNombre.error = "Nombre del funcionario requerido *"
            valido = false
        } else {
            binding.tilFuncionarioNombre.error = null
        }

        val funcionarioCargo = binding.etFuncionarioCargo.text.toString().trim()
        if (funcionarioCargo.isEmpty()) {
            binding.tilFuncionarioCargo.error = "Cargo del funcionario requerido *"
            valido = false
        } else {
            binding.tilFuncionarioCargo.error = null
        }

        val funcionarioMatricula = binding.etFuncionarioMatricula.text.toString().trim()
        if (funcionarioMatricula.isEmpty()) {
            binding.tilFuncionarioMatricula.error = "Matrícula profesional requerida *"
            valido = false
        } else {
            binding.tilFuncionarioMatricula.error = null
        }

        // Si el módulo de rescate está activo, validar campos obligatorios del rescate
        if (binding.swRequiereRescate.isChecked) {
            val motivoRescate = binding.autoCompleteMotivoRescate.text.toString().trim()
            if (motivoRescate.isEmpty()) {
                binding.tilMotivoRescateDropdown.error = "Seleccione la causal de rescate *"
                valido = false
            } else {
                binding.tilMotivoRescateDropdown.error = null
            }

            val personaAtiende = binding.etPersonaAtiendeRescatista.text.toString().trim()
            if (personaAtiende.isEmpty()) {
                binding.tilPersonaAtiendeRescatista.error = "Persona que atiende requerida *"
                valido = false
            } else {
                binding.tilPersonaAtiendeRescatista.error = null
            }

            val personaAtiendeTel = binding.etPersonaAtiendeTelefono.text.toString().trim()
            if (personaAtiendeTel.isEmpty()) {
                binding.tilPersonaAtiendeTelefono.error = "Teléfono de contacto requerido *"
                valido = false
            } else {
                binding.tilPersonaAtiendeTelefono.error = null
            }

            val barrioRescate = binding.etBarrioRescate.text.toString().trim()
            if (barrioRescate.isEmpty()) {
                binding.tilBarrioRescate.error = "Barrio del rescate requerido *"
                valido = false
            } else {
                binding.tilBarrioRescate.error = null
            }

            val testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
            if (testigo1Nombre.isEmpty()) {
                binding.tilTestigo1Nombre.error = "Nombre del Testigo 1 requerido *"
                valido = false
            } else {
                binding.tilTestigo1Nombre.error = null
            }

            val testigo1Cedula = binding.etTestigo1Cedula.text.toString().trim()
            if (testigo1Cedula.isEmpty()) {
                binding.tilTestigo1Cedula.error = "Cédula del Testigo 1 requerida *"
                valido = false
            } else {
                binding.tilTestigo1Cedula.error = null
            }

            val obsRescate = binding.etObservacionesRescate.text.toString().trim()
            if (obsRescate.isEmpty()) {
                binding.tilObservacionesRescate.error = "Observaciones del rescate requeridas *"
                valido = false
            } else {
                binding.tilObservacionesRescate.error = null
            }
        }

        if (!valido) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.warning_icon4)
                .setTitle("Paso 3 incompleto")
                .setMessage("Por favor complete los campos requeridos (compromisos o datos de rescate) antes de registrar el acta.")
                .setPositiveButton("Revisar", null)
                .show()
        }
        return valido
    }

    private fun siNo(valor: Boolean): String = if (valor) "SÍ" else "NO"

    private fun submitActa() {
        if (enviandoActa) return
        enviandoActa = true

        if (peticionId == -1) {
            enviandoActa = false
            Toast.makeText(this, "Error: ID de petición no válido", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = SharedPreferencesManager(this@ActaAtencionActivity)
        var idVeterinario = prefs.getUserId()
        if (idVeterinario == -1) {
            idVeterinario = intent.getIntExtra("ID_USUARIO", -1)
        }

        if (idVeterinario == -1) {
            enviandoActa = false
            Toast.makeText(
                this,
                "Error: No se encontró el usuario veterinario en sesión. Inicie sesión nuevamente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val entidadGuardada = volcarVistasASqliteSincrono(incluirFirmas = true)

        if (!hayRedDisponible()) {
            lifecycleScope.launch {
                encolarActaYFinalizar()
            }
            return
        }

        // Construcción de la lista de animales adaptada a AnimalRapidoSerializer de Django
        val listaAnimales = mutableListOf<AnimalRapidoRequest>()
        for (itemBinding in animalBindingsList) {
            val nombre = itemBinding.etPacienteNombreCard.text.toString().trim().ifEmpty { "Sin nombre" }
            val especieRaw = itemBinding.autoCompleteEspecieCard.text.toString().trim()
            val especie = if (especieRaw.equals("Seleccionar", ignoreCase = true)) "" else especieRaw
            val razaRaw = itemBinding.autoCompleteRazaCard.text.toString().trim()
            val raza = if (razaRaw.equals("Seleccionar", ignoreCase = true)) "" else razaRaw.ifEmpty { "Mestizo" }
            val sexoRaw = itemBinding.autoCompleteSexoCard.text.toString().trim()
            val sexo = if (sexoRaw.equals("Seleccionar", ignoreCase = true)) null else sexoRaw.ifEmpty { null }
            val colorRaw = itemBinding.autoCompleteColorCard.text.toString().trim()
            val color = if (colorRaw.equals("Seleccionar", ignoreCase = true)) "" else colorRaw
            val edad = itemBinding.etPacienteEdadCard.text.toString().trim()
            val peso = itemBinding.etPacientePesoCard.text.toString().trim().toDoubleOrNull()
            val descExtra = itemBinding.etPacienteDescripcionCard.text.toString().trim()
            val esterilizadoStr = itemBinding.autoCompleteEsterilizadoCard.text.toString().trim()
            val esterilizado = esterilizadoStr.equals("Si", ignoreCase = true)

            // Buscar la raza vinculada en el catálogo sincronizado de backend
            val razaObj = listaRazasBackend.firstOrNull {
                it.nombre.equals(raza, ignoreCase = true) &&
                (it.nombreEspecie.isNullOrEmpty() || it.nombreEspecie.equals(especie, ignoreCase = true))
            } ?: listaRazasBackend.firstOrNull {
                it.nombre.equals(raza, ignoreCase = true)
            } ?: listaRazasBackend.firstOrNull {
                it.nombreEspecie?.equals(especie, ignoreCase = true) == true
            }

            val idRazaEncontrada = razaObj?.idRaza

            // Limpieza: no concatenar especie, raza ni edad en caracteristicas/descripcion_paciente.
            // Se envían estrictamente como atributos dedicados e independientes.
            val notasLimpias = descExtra.ifEmpty { null }

            listaAnimales.add(
                AnimalRapidoRequest(
                    nombre = nombre,
                    especie = especie.ifEmpty { null },
                    nombreEspecie = especie.ifEmpty { null },
                    raza = raza.ifEmpty { null },
                    nombreRaza = raza.ifEmpty { null },
                    idRaza = idRazaEncontrada,
                    sexo = sexo,
                    color = color.ifEmpty { null },
                    peso = peso,
                    edad = edad.ifEmpty { null },
                    esterilizado = esterilizado,
                    caracteristicas = notasLimpias
                )
            )
        }

        // Datos del paciente principal (Modo A en Django y columnas directas en SeguimientoPeticionesVisita)
        val primerAnimalBinding = animalBindingsList.firstOrNull()
        val nombrePaciente = primerAnimalBinding?.etPacienteNombreCard?.text?.toString()?.trim()?.ifEmpty { null }
        val especiePrimerRaw = primerAnimalBinding?.autoCompleteEspecieCard?.text?.toString()?.trim()
        val pacienteEspecie = if (especiePrimerRaw.isNullOrEmpty() || especiePrimerRaw.equals("Seleccionar", ignoreCase = true)) null else especiePrimerRaw
        val sexoPrimerRaw = primerAnimalBinding?.autoCompleteSexoCard?.text?.toString()?.trim()
        val pacienteSexo = if (sexoPrimerRaw.isNullOrEmpty() || sexoPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else sexoPrimerRaw
        val colorPrimerRaw = primerAnimalBinding?.autoCompleteColorCard?.text?.toString()?.trim()
        val pacienteColor = if (colorPrimerRaw.isNullOrEmpty() || colorPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else colorPrimerRaw
        val razaPrimerRaw = primerAnimalBinding?.autoCompleteRazaCard?.text?.toString()?.trim()
        val pacienteRaza = if (razaPrimerRaw.isNullOrEmpty() || razaPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else razaPrimerRaw
        val pacienteEdad = primerAnimalBinding?.etPacienteEdadCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pesoPaciente = primerAnimalBinding?.etPacientePesoCard?.text?.toString()?.trim()?.toDoubleOrNull()
        val esterilizadoStr = primerAnimalBinding?.autoCompleteEsterilizadoCard?.text?.toString()?.trim()
        val esterilizacionPaciente = when {
            esterilizadoStr.equals("Si", ignoreCase = true) -> true
            esterilizadoStr.equals("No", ignoreCase = true) -> false
            else -> null
        }
        val descripcionPaciente = primerAnimalBinding?.etPacienteDescripcionCard?.text?.toString()?.trim()?.ifEmpty { null }

        val lugarAtencion = LugarAtencionActa(
            direccion = binding.etLugarAtencion.text.toString().trim(),
            latitud = if (latitudVisita != 0.0) latitudVisita else 2.4419,
            longitud = if (longitudVisita != 0.0) longitudVisita else -76.6063
        )

        val funcionarioNombre = binding.etFuncionarioNombre.text.toString().trim().ifEmpty { "Médico Veterinario" }
        val funcionarioCargoBase = binding.etFuncionarioCargo.text.toString().trim().ifEmpty { "Médico Veterinario Zootecnista" }
        val funcionarioMatricula = binding.etFuncionarioMatricula.text.toString().trim().ifEmpty { null }
        val notificadorIdentificacion = binding.etFuncionarioIdentificacion.text.toString().trim().ifEmpty { null }

        val funcionarioCargo = if (!funcionarioMatricula.isNullOrEmpty() && !funcionarioCargoBase.contains("M.P.", ignoreCase = true)) {
            "$funcionarioCargoBase (M.P. $funcionarioMatricula)"
        } else {
            funcionarioCargoBase
        }

        funcionarioMatricula?.let { prefs.saveUserMatriculaProfesional(it) }

        val listaFuncionarios = listOf(
            FuncionarioActaRequest(
                idUsuario = idVeterinario,
                nombre = funcionarioNombre,
                cargo = funcionarioCargo,
                documentoIdentidad = notificadorIdentificacion,
                matriculaProfesional = funcionarioMatricula,
                tarjetaProfesional = funcionarioMatricula,
                esExterno = false,
                esPrincipal = true
            )
        )

        val quienReporta = binding.autoCompleteQuienReporta.text.toString().trim()
        val textoQuienOtro = binding.etQuienReportaOtro.text.toString().trim()
        val quienReportaOtro = when {
            quienReporta.equals("Otro", ignoreCase = true) -> textoQuienOtro.ifEmpty { "Otro" }
            textoQuienOtro.isNotEmpty() -> textoQuienOtro
            else -> null
        }

        val emailPropietario = binding.etPropietarioEmail.text.toString().trim().ifEmpty { null }
        val plazoDias = binding.etPlazoDias.text.toString().trim().toIntOrNull()
        val observacion = binding.etObservaciones.text.toString().trim().ifEmpty { null }

        val fechaAtencion = binding.etFechaAtencion.text.toString().trim()
        val radicado = binding.etRadicado.text.toString().trim()

        val firmaNotificador = binding.signatureNotificador.toBase64()
        val firmaNotificado = binding.signatureNotificado.toBase64()
        val desparasitacion = binding.swDesparasitacion.isChecked

        // Recopilación de constantes vitales del Paso 2 (sin temperatura)
        val fc = binding.etFrecuenciaCardiaca.text.toString().trim()
        val fr = binding.etFrecuenciaRespiratoria.text.toString().trim()
        val mucosas = binding.etMucosas.text.toString().trim()

        val signosVitales = buildString {
            if (fc.isNotEmpty()) append("FC: ${fc} lpm. ")
            if (fr.isNotEmpty()) append("FR: ${fr} rpm. ")
            if (mucosas.isNotEmpty()) append("Mucosas: $mucosas. ")
        }.trim()

        // Pruebas complementarias y consolidación de exámenes del Paso 3
        val listaFinalExamenes = examenesAgregadosList.toMutableList()
        val tipoPrueba = binding.autoCompleteTipoPrueba.text.toString().trim()
        val pruebaDetalle = binding.etPruebasComplementarias.text.toString().trim()
        val resultadoPruebasCampos = binding.etResultadoPruebas.text.toString().trim().ifEmpty { null }

        val examenEnCampos = when {
            pruebaDetalle.isNotEmpty() -> pruebaDetalle
            tipoPrueba.isNotEmpty() && !tipoPrueba.contains("Ninguna", ignoreCase = true) -> tipoPrueba
            else -> null
        }

        if (examenEnCampos != null && listaFinalExamenes.none { it.nombreExamen.equals(examenEnCampos, ignoreCase = true) }) {
            listaFinalExamenes.add(
                ExamenSeleccionadoActa(
                    nombreExamen = examenEnCampos,
                    resultado = resultadoPruebasCampos ?: "Pendiente"
                )
            )
        }

        val pruebaFinal = when {
            listaFinalExamenes.isNotEmpty() -> listaFinalExamenes.joinToString(" | ") { it.nombreExamen }
            else -> null
        }
        val resultadoPruebas = when {
            listaFinalExamenes.isNotEmpty() -> listaFinalExamenes.joinToString(" | ") { "${it.nombreExamen}: ${it.resultado}" }
            else -> null
        }

        val resumenExamenes = if (listaFinalExamenes.isNotEmpty()) {
            "[Exámenes clínicos (${listaFinalExamenes.size}): " + listaFinalExamenes.joinToString("; ") { "${it.nombreExamen} (${it.resultado})" } + "]"
        } else ""

        val anamnesisBase = binding.etAnamnesis.text.toString().trim()
        val anamnesisConSignos = buildString {
            append(anamnesisBase)
            if (signosVitales.isNotEmpty()) {
                append(" [Examen clínico: $signosVitales]")
            }
            if (resumenExamenes.isNotEmpty()) {
                append(" $resumenExamenes")
            }
        }.trim()

        // Estado de resolución / cierre del Paso 3
        val estadoCierre = binding.autoCompleteEstadoCierre.text.toString().trim().ifEmpty { "En tratamiento" }

        // Si el módulo de rescate está activo, se construye el anexo técnico oficial F-GS-C01-03-33
        val anamnesisFinal = if (binding.swRequiereRescate.isChecked) {
            val causalRescate = binding.autoCompleteMotivoRescate.text.toString().trim()
            val personaAtiende = binding.etPersonaAtiendeRescatista.text.toString().trim()
            val personaAtiendeCedula = binding.etPersonaAtiendeCedula.text.toString().trim()
            val personaAtiendeTel = binding.etPersonaAtiendeTelefono.text.toString().trim()
            val barrioRescate = binding.etBarrioRescate.text.toString().trim()
            val testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
            val testigo1Cedula = binding.etTestigo1Cedula.text.toString().trim()
            val testigo1Tel = binding.etTestigo1Telefono.text.toString().trim()
            val testigo2Nombre = binding.etTestigo2Nombre.text.toString().trim()
            val testigo2Cedula = binding.etTestigo2Cedula.text.toString().trim()
            val testigo2Tel = binding.etTestigo2Telefono.text.toString().trim()
            val obsRescate = binding.etObservacionesRescate.text.toString().trim()
            val vetReceptor = binding.etVeterinarioReceptor.text.toString().trim()
            val fechaRecep = binding.etFechaRecepcion.text.toString().trim()
            val horaRecep = binding.etHoraRecepcion.text.toString().trim()
            val obsRecep = binding.etObservacionesRecepcion.text.toString().trim()

            val anexoRescate = buildString {
                appendLine()
                appendLine()
                appendLine("=======================================================")
                appendLine("ANEXO: ACTA DE RESCATE Y TRASLADO AL CBA")
                appendLine("(Formato Oficial Alcaldía de Popayán F-GS-C01-03-33, Versión 02)")
                appendLine("=======================================================")
                if (causalRescate.isNotEmpty()) appendLine("• Causal de rescate: $causalRescate")
                appendLine("• Persona que atiende / entrega: $personaAtiende ${if (personaAtiendeCedula.isNotEmpty()) "- CC: $personaAtiendeCedula" else ""} ${if (personaAtiendeTel.isNotEmpty()) "- Tel: $personaAtiendeTel" else ""}")
                appendLine("• Barrio de rescate: $barrioRescate")
                appendLine("• Testigo 1 (Certifica traslado): $testigo1Nombre - CC: $testigo1Cedula ${if (testigo1Tel.isNotEmpty()) "- Tel: $testigo1Tel" else ""}")
                if (testigo2Nombre.isNotEmpty()) {
                    appendLine("• Testigo 2: $testigo2Nombre ${if (testigo2Cedula.isNotEmpty()) "- CC: $testigo2Cedula" else ""} ${if (testigo2Tel.isNotEmpty()) "- Tel: $testigo2Tel" else ""}")
                }
                appendLine()
                appendLine("--- EVALUACIÓN CLÍNICA POR SISTEMAS (SÍ / NO) ---")
                appendLine("1. Sistema Respiratorio:")
                appendLine("   - Presenta moco: ${siNo(binding.swRespiratorioMoco.isChecked)}")
                appendLine("   - Se le dificulta respirar: ${siNo(binding.swRespiratorioDificultad.isChecked)}")
                appendLine("   - Tos: ${siNo(binding.swRespiratorioTos.isChecked)}")
                appendLine("   - Lagaña o lagrimeo: ${siNo(binding.swRespiratorioLaganeo.isChecked)}")
                appendLine("2. Sistema Digestivo:")
                appendLine("   - Presenta vómito: ${siNo(binding.swDigestivoVomito.isChecked)}")
                appendLine("   - Presenta diarrea: ${siNo(binding.swDigestivoDiarrea.isChecked)}")
                appendLine("   - Flaco o desnutrido: ${siNo(binding.swDigestivoDesnutrido.isChecked)}")
                appendLine("   - Deshidratado: ${siNo(binding.swDigestivoDeshidratado.isChecked)}")
                appendLine("3. Sistema Músculo Esquelético:")
                appendLine("   - Caída de las patas de atrás: ${siNo(binding.swMusculoCaidaPatas.isChecked)}")
                appendLine("   - Cojo: ${siNo(binding.swMusculoCojo.isChecked)}")
                appendLine("   - Presenta herida abierta: ${siNo(binding.swMusculoHeridaAbierta.isChecked)}")
                appendLine("   - Observa moscos o gusanos: ${siNo(binding.swMusculoGusanos.isChecked)}")
                appendLine("   - Postrado: ${siNo(binding.swMusculoPostrado.isChecked)}")
                appendLine("4. Sistema Dermatológico:")
                appendLine("   - Presenta peladuras: ${siNo(binding.swDermatoPeladuras.isChecked)}")
                appendLine("   - Heridas con sangre: ${siNo(binding.swDermatoHeridasSangre.isChecked)}")
                appendLine("   - Pulgas o garrapatas: ${siNo(binding.swDermatoPulgas.isChecked)}")
                appendLine("5. Sistema Genitourinario:")
                appendLine("   - Presenta masa o secreción: ${siNo(binding.swGenitoMasaSecrecion.isChecked)}")
                appendLine("   - Se encuentra gestante: ${siNo(binding.swGenitoGestante.isChecked)}")
                appendLine("   - Se encuentra en celo: ${siNo(binding.swGenitoCelo.isChecked)}")
                appendLine()
                appendLine("--- OBSERVACIONES DEL RESCATE ---")
                appendLine(obsRescate)
                if (vetReceptor.isNotEmpty() || fechaRecep.isNotEmpty() || obsRecep.isNotEmpty()) {
                    appendLine()
                    appendLine("--- RECEPCIÓN EN CLÍNICA / CBA ---")
                    if (vetReceptor.isNotEmpty()) appendLine("• Veterinario receptor: $vetReceptor")
                    if (fechaRecep.isNotEmpty() || horaRecep.isNotEmpty()) appendLine("• Fecha/Hora de recepción: $fechaRecep $horaRecep".trim())
                    if (obsRecep.isNotEmpty()) appendLine("• Observaciones recepción: $obsRecep")
                }
            }.trim()

            "$anamnesisConSignos\n$anexoRescate"
        } else {
            anamnesisConSignos
        }

        // Compromisos con anexo de rescate si corresponde
        val compromisosBase = binding.etCompromisos.text.toString().trim()
        val compromisosFinal = if (binding.swRequiereRescate.isChecked) {
            if (compromisosBase.isNotEmpty()) {
                "$compromisosBase | Traslado y custodia del paciente al Centro de Bienestar Animal (CBA)."
            } else {
                "Traslado y custodia del paciente al Centro de Bienestar Animal (CBA) bajo formato oficial Popayán F-GS-C01-03-33."
            }
        } else {
            compromisosBase
        }

        val fundamentoLegalBase = binding.etFundamentoLegal.text.toString().trim().ifEmpty { "Ley 1774 de 2016" }
        val fundamentoLegalFinal = if (binding.swRequiereRescate.isChecked) {
            "$fundamentoLegalBase / Formato de Rescate F-GS-C01-03-33"
        } else {
            fundamentoLegalBase
        }

        val notificadoNombre = binding.etNotificadoNombre.text.toString().trim().ifEmpty {
            if (binding.swRequiereRescate.isChecked) {
                binding.etTestigo1Nombre.text.toString().trim().ifEmpty { null }
            } else {
                binding.etPropietarioNombre.text.toString().trim().ifEmpty { null }
            }
        }
        val notificadoId = binding.etNotificadoIdentificacion.text.toString().trim().ifEmpty {
            if (binding.swRequiereRescate.isChecked) {
                binding.etTestigo1Cedula.text.toString().trim().ifEmpty { null }
            } else {
                binding.etPropietarioCedula.text.toString().trim().ifEmpty { null }
            }
        }

        // Estado de carga UI
        setLoadingState(true, "Iniciando registro del acta...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        enviandoActa = false
                        setLoadingState(false)
                        Toast.makeText(this@ActaAtencionActivity, "Sesión no válida. Inicie sesión nuevamente.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // Subida de evidencias multimedia (fotos y videos) a Cloudinary
                val urlsFotos = mutableListOf<String>()
                val urlsVideos = mutableListOf<String>()
                var falloPermanenteEvidencias = false
                var falloRedEvidencias = false

                if (selectedMediaList.isNotEmpty()) {
                    val totalMedia = selectedMediaList.size
                    withContext(Dispatchers.Main) {
                        setLoadingState(true, "Subiendo evidencias a Cloudinary (0/$totalMedia)...")
                    }

                    for ((idx, media) in selectedMediaList.withIndex()) {
                        val numActual = idx + 1
                        val urlExistente = media.remoteUrl?.takeIf { it.startsWith("http") }
                        if (urlExistente != null) {
                            if (media.esVideo) {
                                urlsVideos.add(urlExistente)
                            } else {
                                urlsFotos.add(urlExistente)
                            }
                        } else {
                            val timeoutMs = if (media.esVideo) 120_000L else 45_000L
                            val res = withTimeoutOrNull(timeoutMs) {
                                subirACloudinary(media, numActual, totalMedia)
                            }
                            if (res == null) {
                                falloRedEvidencias = true
                                break
                            }
                            when (res) {
                                is ResultadoSubida.Exito -> {
                                    val secureUrl = res.secureUrl
                                    persistirRemoteUrlEvidencia(media, secureUrl)
                                    if (media.esVideo) {
                                        urlsVideos.add(secureUrl)
                                    } else {
                                        urlsFotos.add(secureUrl)
                                    }
                                }
                                is ResultadoSubida.ErrorRed -> {
                                    falloRedEvidencias = true
                                    break
                                }
                                is ResultadoSubida.ArchivoNoExiste,
                                is ResultadoSubida.ErrorPermanente -> {
                                    falloPermanenteEvidencias = true
                                    break
                                }
                            }
                        }
                    }

                    if (falloRedEvidencias) {
                        encolarActaYFinalizar()
                        return@launch
                    }

                    if (falloPermanenteEvidencias) {
                        withContext(Dispatchers.Main) {
                            enviandoActa = false
                            setLoadingState(false)
                            MaterialAlertDialogBuilder(this@ActaAtencionActivity)
                                .setIcon(R.drawable.warning_icon4)
                                .setTitle("Error al subir evidencias")
                                .setMessage("Uno o más archivos multimedia no se pudieron subir a la nube. Por favor verifica tu conexión a internet y asegúrate de que los videos no superen 10 MB.")
                                .setPositiveButton("Entendido", null)
                                .show()
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.tvLoadingMensaje.text = "Registrando acta en el sistema..."
                }

                val request = SeguimientoPeticionVisitaRequest(
                    idPeticion = peticionId,
                    idVeterinario = idVeterinario,
                    numeroRadicado = radicado,
                    fechaAtencion = fechaAtencion,
                    propietarioNombre = binding.etPropietarioNombre.text.toString().trim(),
                    propietarioCedula = binding.etPropietarioCedula.text.toString().trim(),
                    propietarioTelefono = binding.etPropietarioTelefono.text.toString().trim(),
                    propietarioEmail = emailPropietario,
                    propietarioBarrio = binding.etPropietarioBarrio.text.toString().trim(),
                    propietarioDireccion = binding.etPropietarioDireccion.text.toString().trim(),
                    quienReporta = quienReporta,
                    quienReportaOtro = quienReportaOtro,
                    solicitudAtencionPor = binding.etSolicitudAtencion.text.toString().trim(),
                    lugarAtencion = lugarAtencion,
                    nombrePaciente = nombrePaciente,
                    pacienteEspecie = pacienteEspecie,
                    pacienteSexo = pacienteSexo,
                    pacienteColor = pacienteColor,
                    pacienteRaza = pacienteRaza,
                    pacienteEdad = pacienteEdad,
                    pesoPaciente = pesoPaciente,
                    esterilizacionPaciente = esterilizacionPaciente,
                    descripcionPaciente = descripcionPaciente,
                    animales = listaAnimales,
                    nroAnimalesAtendidos = listaAnimales.size,
                    anamnesisDescripcionQueja = anamnesisFinal,
                    tratamientoRealizado = binding.etTratamiento.text.toString().trim(),
                    desparasitacion = desparasitacion,
                    pruebasComplementarias = pruebaFinal,
                    resultadoPruebas = resultadoPruebas,
                    compromisos = compromisosFinal,
                    fundamentoLegal = fundamentoLegalFinal,
                    plazoDiasCumplimiento = plazoDias,
                    nombreFuncionario = funcionarioNombre,
                    funcionarioCargo = funcionarioCargo,
                    funcionarios = listaFuncionarios,
                    notificadoNombre = notificadoNombre,
                    notificacionesIdentificacion = notificadoId,
                    fechaNotificacion = if (notificadoNombre != null) fechaAtencion else null,
                    notificadorNombre = funcionarioNombre,
                    notificadorIdentificacion = notificadorIdentificacion,
                    notificadorCargo = funcionarioCargo,
                    notificadorMatricula = funcionarioMatricula,
                    matriculaProfesional = funcionarioMatricula,
                    tarjetaProfesional = funcionarioMatricula,
                    firmaNotificador = firmaNotificador,
                    firmaNotificado = firmaNotificado,
                    observacion = observacion,
                    fotos = urlsFotos,
                    videos = urlsVideos,
                    medicamentosUsados = medicamentosAplicadosList.map { it.toMedicamentoUsadoRequest() }.ifEmpty { null },
                    sincronizacionOffline = false
                )

                if (BuildConfig.DEBUG && entidadGuardada != null) {
                    val requestDesdeEntidad = ActaRequestBuilder.build(
                        entity = entidadGuardada,
                        fotosUrls = urlsFotos,
                        videosUrls = urlsVideos,
                        sincronizacionOffline = false
                    )
                    verificarParidadRequest(request, requestDesdeEntidad)
                }

                // 1. Enviar el Acta completa de atención en campo (POST /api/peticiones/seguimiento/crear/)
                val response = try {
                    RetrofitClient.apiService.crearSeguimientoPeticionVisita("Bearer $token", request)
                } catch (e: IOException) {
                    encolarActaYFinalizar()
                    return@launch
                }

                val postCode = response.code()

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val idSeg = body.idSeguimiento ?: 0
                    val alertasFarmacia = body.alertasFarmacia
                    val tieneConflictos = alertasFarmacia?.conflictosStock?.isNotEmpty() == true

                    val idLocalActual = synchronized(dbWriteLock) {
                        entidadBorradorActual?.idLocal?.takeIf { it > 0L }
                            ?: actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, idVeterinario)?.idLocal
                            ?: 0L
                    }

                    // 1. REGISTRAR idSeguimientoRemoto EN EL ACTO antes del PATCH
                    if (idSeg > 0 && idLocalActual > 0L) {
                        synchronized(dbWriteLock) {
                            actaLocalRepository.guardarIdSeguimientoRemoto(idLocalActual, idSeg)
                            entidadBorradorActual = entidadBorradorActual?.copy(idSeguimientoRemoto = idSeg)
                        }
                    }

                    // 2. Cierre y actualización del estado de la petición en el backend (PATCH /api/peticiones/{id}/estado/)
                    if (peticionId != -1) {
                        try {
                            val patchResponse = RetrofitClient.apiService.actualizarEstadoPeticion(
                                "Bearer $token",
                                peticionId,
                                com.example.petvetericano.models.ActualizarEstadoRequest(
                                    estado = estadoCierre,
                                    observacion = null // Se envía null para evitar que el endpoint de estado cree una fila huérfana duplicada en seguimiento_peticiones_visita
                                )
                            )
                            val patchCode = patchResponse.code()
                            when {
                                patchCode == 401 || patchCode >= 500 -> {
                                    encolarActaYFinalizar(idSeguimientoRemotoPrevio = if (idSeg > 0) idSeg else null)
                                    return@launch
                                }
                                else -> {
                                    // 2xx o 4xx (excepto 401): cuenta como éxito, igual que en el Worker
                                    marcarEnvioOnlineExitosoYLimpiarBorrador(codigoHttp = patchCode)
                                }
                            }
                        } catch (e: IOException) {
                            encolarActaYFinalizar(idSeguimientoRemotoPrevio = if (idSeg > 0) idSeg else null)
                            return@launch
                        } catch (e: Exception) {
                            encolarActaYFinalizar(idSeguimientoRemotoPrevio = if (idSeg > 0) idSeg else null)
                            return@launch
                        }
                    } else {
                        marcarEnvioOnlineExitosoYLimpiarBorrador()
                    }

                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        var mensajeExito = if (binding.swRequiereRescate.isChecked) {
                            "Acta y Rescate CBA registrados exitosamente (ID #$idSeg).\n\nEstado del caso: $estadoCierre"
                        } else {
                            "Acta registrada exitosamente (ID #$idSeg).\n\nEstado del caso: $estadoCierre"
                        }

                        if (tieneConflictos) {
                            val descConflictos = alertasFarmacia!!.conflictosStock.joinToString("\n") { c ->
                                "• ${c.nombreMedicamento ?: "Medicamento"}: Solicitado ${c.solicitado}, Disponible ${c.disponible}"
                            }
                            mensajeExito += "\n\n⚠️ Advertencia de stock en farmacia:\n$descConflictos"
                        }
                        MaterialAlertDialogBuilder(this@ActaAtencionActivity)
                            .setIcon(R.drawable.ic_check)
                            .setTitle("Atención registrada con éxito")
                            .setMessage(mensajeExito)
                            .setCancelable(false)
                            .setPositiveButton("Finalizar") { dialog, _ ->
                                dialog.dismiss()
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                            .show()
                    }
                } else if (postCode == 408 || postCode == 429 || postCode >= 500) {
                    encolarActaYFinalizar()
                    return@launch
                } else {
                    val errorStr = response.errorBody()?.string() ?: ""
                    val mensajeError = parsearErrorDjango(errorStr, postCode)
                    withContext(Dispatchers.Main) {
                        enviandoActa = false
                        setLoadingState(false)
                        MaterialAlertDialogBuilder(this@ActaAtencionActivity)
                            .setIcon(R.drawable.warning_icon4)
                            .setTitle("No se pudo registrar el acta")
                            .setMessage(mensajeError)
                            .setPositiveButton("Entendido", null)
                            .show()
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (!actaEnviadaExitosamente) {
                    encolarActaYFinalizar()
                }
                throw ce
            } catch (e: IOException) {
                encolarActaYFinalizar()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    enviandoActa = false
                    setLoadingState(false)
                    MaterialAlertDialogBuilder(this@ActaAtencionActivity)
                        .setIcon(R.drawable.warning_icon4)
                        .setTitle("Error de conexión")
                        .setMessage("No se pudo conectar con el servidor: ${e.localizedMessage ?: e.message}")
                        .setPositiveButton("Entendido", null)
                        .show()
                }
            }
        }
    }

    private fun parsearErrorDjango(errorBody: String, code: Int): String {
        return try {
            if (errorBody.isNotEmpty()) {
                val json = JSONObject(errorBody)
                val errCod = json.optString("error")
                if (errCod == "STOCK_INSUFICIENTE_KIT") {
                    val detalle = json.optString("detalle")
                    val solicitado = json.optInt("solicitado", 0)
                    val disponible = json.optInt("disponible", 0)
                    val medNombre = json.optString("nombre_medicamento", "el medicamento")
                    return if (detalle.isNotEmpty()) {
                        "Stock insuficiente en tu maletín:\n$detalle"
                    } else {
                        "No cuentas con existencias suficientes de $medNombre en tu maletín clínico (Solicitado: $solicitado, Disponible: $disponible)."
                    }
                }
                val primerClave = json.keys().asSequence().firstOrNull()
                if (primerClave != null) {
                    val valor = json.optJSONArray(primerClave)?.optString(0)
                        ?: json.optString(primerClave)
                    if (primerClave.equals("error", ignoreCase = true) || primerClave.equals("detail", ignoreCase = true) || primerClave.equals("detalle", ignoreCase = true)) {
                        valor
                    } else {
                        "Error ($primerClave): $valor"
                    }
                } else {
                    "Error al guardar ($code)"
                }
            } else {
                "Error del servidor ($code)"
            }
        } catch (_: Exception) {
            "Error en la solicitud ($code): $errorBody"
        }
    }

    private fun verificarSiPeticionYaAtendida() {
        if (peticionId == -1) return
        if (!NetworkUtils.hayRedDisponible(this)) return

        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = withTimeoutOrNull(3000L) {
                    RetrofitClient.apiService.listarPeticiones("Bearer $token")
                } ?: return@launch

                if (response.isSuccessful) {
                    val body = response.body()
                    val idVet = obtenerIdVeterinarioActual()
                    if (!body.isNullOrEmpty() && idVet > 0) {
                        PeticionesCacheManager.guardar(this@ActaAtencionActivity, idVet, body)
                    }

                    val peticion = body?.firstOrNull { it.id_peticion == peticionId }
                    val estado = peticion?.estado.orEmpty().lowercase()
                    val yaAtendida = peticion?.tiene_acta == true ||
                        estado.contains("tratamiento") ||
                        estado.contains("observación") ||
                        estado.contains("observacion") ||
                        estado.contains("alta") ||
                        estado.contains("atendida") ||
                        estado.contains("resuelta")

                    if (yaAtendida) {
                        withContext(Dispatchers.Main) {
                            MaterialAlertDialogBuilder(this@ActaAtencionActivity)
                                .setIcon(R.drawable.ic_info_circle)
                                .setTitle(R.string.acta_offline_dialog_ya_atendida_titulo)
                                .setMessage(R.string.acta_offline_dialog_ya_atendida_acta_mensaje)
                                .setCancelable(false)
                                .setPositiveButton(R.string.acta_offline_dialog_boton_regresar) { _, _ ->
                                    finish()
                                }
                                .show()
                        }
                    }
                }
            } catch (_: Exception) {
                // Si ocurre error de red o timeout, la validación estricta del backend protegerá el registro
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, mensaje: String = "Registrando acta en el sistema...") {
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.tvLoadingMensaje.text = mensaje
        binding.btnSiguiente.isEnabled = !isLoading
        binding.btnAnterior.isEnabled = !isLoading
    }

    // ========================================================
    // GESTIÓN DE EVIDENCIA MULTIMEDIA (CLOUDINARY)
    // ========================================================
    private fun initCloudinary() {
        val config = HashMap<String, String>()
        config["cloud_name"] = "aefeig5y"
        try {
            MediaManager.init(this, config)
        } catch (_: Exception) {
            // MediaManager ya fue inicializado previamente en la app
        }
    }

    private fun setupEvidenciaMultimedia() {
        binding.btnAdjuntarFoto.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = false)
        }

        binding.btnAdjuntarVideo.setOnClickListener {
            mostrarBottomSheetEvidencia(esVideo = true)
        }
    }

    private fun mostrarBottomSheetEvidencia(esVideo: Boolean) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(
            this,
            R.style.ThemeOverlay_Vetericano_BottomSheetDialog
        )
        val sheetBinding = com.example.petvetericano.databinding.BottomSheetAdjuntarEvidenciaBinding.inflate(layoutInflater)
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
            sheetBinding.tvSheetSubtitulo.text = "Selecciona el origen para documentar el hallazgo clínico"

            sheetBinding.ivIconCamara.setImageResource(R.drawable.camera)
            sheetBinding.tvTituloCamara.text = "Tomar foto con la cámara"
            sheetBinding.tvSubtituloCamara.text = "Captura en tiempo real durante la atención en campo"

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
            val file = File(obtenerDirectorioEvidenciasActa(), "acta_foto_${System.currentTimeMillis()}.jpg")
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
            val file = File(obtenerDirectorioEvidenciasActa(), "acta_video_${System.currentTimeMillis()}.mp4")
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
        ultimaRutaLocalCopiada = ""
        return try {
            val extension = if (esVideo) ".mp4" else ".jpg"
            val prefix = if (esVideo) "acta_video_" else "acta_foto_"
            val localFile = File(obtenerDirectorioEvidenciasActa(), "${prefix}${System.currentTimeMillis()}${extension}")
            localFile.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!esVideo) {
                optimizarImagen(localFile)
            } else {
                if (localFile.length() > 10L * 1024 * 1024) {
                    val sizeMb = localFile.length() / (1024 * 1024)
                    localFile.delete()
                    runOnUiThread {
                        MaterialAlertDialogBuilder(this)
                            .setIcon(R.drawable.warning_icon4)
                            .setTitle("Video demasiado pesado")
                            .setMessage("El video seleccionado pesa ${sizeMb} MB y supera el límite de 10 MB permitido para almacenamiento en la nube.\n\nPor favor selecciona un video más corto o graba un clip breve.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                    return null
                }
            }

            ultimaRutaLocalCopiada = localFile.absolutePath
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                localFile
            )
        } catch (_: Exception) {
            uri
        }
    }

    private fun optimizarImagen(archivo: File): File {
        return try {
            if (!archivo.exists() || archivo.length() == 0L) return archivo

            // 1. Obtener dimensiones originales sin decodificar bitmap en memoria
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

            // 2. Decodificar la imagen muestreada eficientemente
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(archivo.absolutePath, decodeOptions) ?: return archivo

            // 3. Ajustar orientación EXIF si la cámara rotó la foto
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

            // 4. Guardar archivo comprimido como JPEG al 85%
            val tempFile = File(archivo.parentFile ?: filesDir, "opt_${System.currentTimeMillis()}_${archivo.name}")
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

    private fun mostrarPrevisualizacionMedia(item: MediaAdjunto) {
        if (item.esVideo) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "No se encontró un reproductor de video compatible", Toast.LENGTH_SHORT).show()
            }
        } else {
            val imageView = ImageView(this).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                maxHeight = (resources.displayMetrics.heightPixels * 0.7).toInt()
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(24, 24, 24, 24)
            }
            Glide.with(this)
                .load(item.uri)
                .fitCenter()
                .placeholder(R.drawable.baseline_pets_24)
                .error(R.drawable.baseline_pets_24)
                .into(imageView)

            MaterialAlertDialogBuilder(this)
                .setTitle("Evidencia fotográfica")
                .setView(imageView)
                .setPositiveButton("Cerrar", null)
                .show()
        }
    }

    private fun actualizarVistaMultimedia() {
        val total = selectedMediaList.size
        val fotosCount = selectedMediaList.count { !it.esVideo }
        val videosCount = selectedMediaList.count { it.esVideo }

        if (total == 0) {
            binding.scrollMediaAdjuntos.visibility = View.GONE
            binding.tvConteoMedia.visibility = View.GONE
            binding.cardResumenMultimediaPaso3.visibility = View.GONE
            binding.containerMediaThumbnails.removeAllViews()
        } else {
            binding.scrollMediaAdjuntos.visibility = View.VISIBLE
            binding.tvConteoMedia.visibility = View.VISIBLE
            binding.tvConteoMedia.text = buildString {
                append("✓ ")
                if (fotosCount > 0) append("$fotosCount foto(s) ")
                if (fotosCount > 0 && videosCount > 0) append("y ")
                if (videosCount > 0) append("$videosCount video(s) ")
                append("adjunto(s) • Toca para previsualizar")
            }

            // Resumen de evidencias visible en Paso 3
            binding.cardResumenMultimediaPaso3.visibility = View.VISIBLE
            binding.tvResumenMediaPaso3.text = buildString {
                append("Evidencias adjuntas: ")
                if (fotosCount > 0) append("$fotosCount foto(s) ")
                if (fotosCount > 0 && videosCount > 0) append("• ")
                if (videosCount > 0) append("$videosCount video(s)")
            }
            binding.btnVerMediaDesdePaso3.setOnClickListener {
                currentStep = 2
                updateStepper()
                guardarBorradorEnCambioDePaso()
            }

            binding.containerMediaThumbnails.removeAllViews()
            for (media in selectedMediaList) {
                val itemBinding = ItemActaMediaAdjuntoBinding.inflate(layoutInflater, binding.containerMediaThumbnails, false)

                if (media.esVideo) {
                    Glide.with(this@ActaAtencionActivity)
                        .asBitmap()
                        .load(media.uri)
                        .centerCrop()
                        .placeholder(R.drawable.baseline_pets_24)
                        .error(R.drawable.baseline_pets_24)
                        .into(itemBinding.ivMediaThumbnail)
                    itemBinding.layoutVideoOverlay.visibility = View.VISIBLE
                } else {
                    Glide.with(this@ActaAtencionActivity)
                        .load(media.uri)
                        .centerCrop()
                        .placeholder(R.drawable.baseline_pets_24)
                        .error(R.drawable.baseline_pets_24)
                        .into(itemBinding.ivMediaThumbnail)
                    itemBinding.layoutVideoOverlay.visibility = View.GONE
                }

                itemBinding.cardMediaThumbnail.setOnClickListener {
                    mostrarPrevisualizacionMedia(media)
                }

                itemBinding.btnEliminarMedia.setOnClickListener {
                    selectedMediaList.remove(media)
                    if (media.localPath.isNotBlank()) {
                        val archivo = File(media.localPath)
                        if (archivo.exists() && archivo.absolutePath.contains("acta_offline")) {
                            archivo.delete()
                        }
                    }
                    actualizarVistaMultimedia()
                    programarGuardado()
                }

                binding.containerMediaThumbnails.addView(itemBinding.root)
            }
        }
    }

    private suspend fun subirACloudinary(media: MediaAdjunto, index: Int, total: Int): ResultadoSubida {
        withContext(Dispatchers.Main) {
            binding.tvLoadingMensaje.text = "Subiendo archivo $index de $total a la nube..."
        }
        return CloudinaryUploadHelper.subirUri(this@ActaAtencionActivity, media.uri, media.esVideo)
    }

    // ========================================================
    // PERSISTENCIA OFFLINE DE BORRADOR Y PARIDAD (TAREA 3C)
    // ========================================================
    private fun obtenerIdVeterinarioActual(): Int {
        val prefs = SharedPreferencesManager(this)
        var idVet = prefs.getUserId()
        if (idVet == -1) {
            idVet = intent.getIntExtra("ID_USUARIO", -1)
        }
        return idVet
    }

    private fun obtenerDirectorioEvidenciasActa(): File {
        val idDir = if (peticionId > 0) peticionId.toString() else "temp"
        val dir = File(filesDir, "acta_offline/$idDir")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun setupListenersGuardadoBorrador() {
        val camposTexto = listOf(
            binding.etFechaAtencion,
            binding.etRadicado,
            binding.etSolicitudAtencion,
            binding.autoCompleteQuienReporta,
            binding.etQuienReportaOtro,
            binding.etLugarAtencion,
            binding.etPropietarioNombre,
            binding.etPropietarioCedula,
            binding.etPropietarioTelefono,
            binding.etPropietarioEmail,
            binding.etPropietarioBarrio,
            binding.etPropietarioDireccion,
            binding.etFrecuenciaCardiaca,
            binding.etFrecuenciaRespiratoria,
            binding.etMucosas,
            binding.etAnamnesis,
            binding.etTratamiento,
            binding.autoCompleteTipoPrueba,
            binding.etPruebasComplementarias,
            binding.etResultadoPruebas,
            binding.autoCompleteEstadoCierre,
            binding.etCompromisos,
            binding.etFundamentoLegal,
            binding.etPlazoDias,
            binding.etObservaciones,
            binding.etFuncionarioNombre,
            binding.etFuncionarioCargo,
            binding.etFuncionarioIdentificacion,
            binding.etFuncionarioMatricula,
            binding.etNotificadoNombre,
            binding.etNotificadoIdentificacion,
            binding.autoCompleteMotivoRescate,
            binding.etPersonaAtiendeRescatista,
            binding.etPersonaAtiendeCedula,
            binding.etPersonaAtiendeTelefono,
            binding.etBarrioRescate,
            binding.etTestigo1Nombre,
            binding.etTestigo1Cedula,
            binding.etTestigo1Telefono,
            binding.etTestigo2Nombre,
            binding.etTestigo2Cedula,
            binding.etTestigo2Telefono,
            binding.etObservacionesRescate,
            binding.etVeterinarioReceptor,
            binding.etFechaRecepcion,
            binding.etHoraRecepcion,
            binding.etObservacionesRecepcion
        )
        for (campo in camposTexto) {
            campo.addTextChangedListener { programarGuardado() }
        }
    }

    private fun programarGuardado() {
        if (edicionBloqueadaPorEstado || !huboInteraccionUsuario || isInitialSetup || isRestaurandoBorrador || actualizandoCatalogos || mostrandoDialogoBorrador || actaEnviadaExitosamente) {
            return
        }
        usuarioHaEditado = true
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        guardadoHandler.postDelayed(guardadoDebounceRunnable, 400L)
    }

    private fun ejecutarGuardadoDebounce() {
        if (edicionBloqueadaPorEstado || !huboInteraccionUsuario || isInitialSetup || isRestaurandoBorrador || actualizandoCatalogos || mostrandoDialogoBorrador || actaEnviadaExitosamente || !usuarioHaEditado) {
            return
        }
        val entidad = construirEntidadDesdeVistas(incluirFirmas = false) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            guardarEntidadEnDb(entidad)
        }
    }

    private fun guardarBorradorEnCambioDePaso() {
        if (edicionBloqueadaPorEstado || !huboInteraccionUsuario || isInitialSetup || isRestaurandoBorrador || actualizandoCatalogos || mostrandoDialogoBorrador || actaEnviadaExitosamente) {
            return
        }
        val hayFirmaDibujada = binding.signatureNotificador.hasSignature() || binding.signatureNotificado.hasSignature()
        if (!usuarioHaEditado && !hayFirmaDibujada) {
            return
        }
        usuarioHaEditado = true
        guardarBorradorSincrono(incluirFirmas = true)
    }

    private fun guardarBorradorSincrono(incluirFirmas: Boolean): ActaLocalEntity? {
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        if (edicionBloqueadaPorEstado || !huboInteraccionUsuario || isInitialSetup || isRestaurandoBorrador || actualizandoCatalogos || mostrandoDialogoBorrador || actaEnviadaExitosamente) {
            return null
        }
        val hayFirmaDibujada = binding.signatureNotificador.hasSignature() || binding.signatureNotificado.hasSignature()
        if (!usuarioHaEditado && !hayFirmaDibujada) {
            return null
        }
        usuarioHaEditado = true
        val entidad = construirEntidadDesdeVistas(incluirFirmas = incluirFirmas) ?: return null
        return guardarEntidadEnDb(entidad)
    }

    private fun volcarVistasASqliteSincrono(incluirFirmas: Boolean): ActaLocalEntity? {
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        if (edicionBloqueadaPorEstado || actaEnviadaExitosamente) return null
        usuarioHaEditado = true
        val entidad = construirEntidadDesdeVistas(incluirFirmas = incluirFirmas) ?: return null
        return guardarEntidadEnDb(entidad)
    }

    private fun construirEntidadDesdeVistas(incluirFirmas: Boolean): ActaLocalEntity? {
        val idVet = obtenerIdVeterinarioActual()
        if (peticionId <= 0 || idVet <= 0) return null

        normalizarClientUuidMedicamentos()

        val estadosAnimales = extraerEstadosAnimales()
        val animalesJson = ActaFormSnapshotMapper.serializarAnimales(estadosAnimales, resolverIdRaza)
        val medicamentosJson = ActaFormSnapshotMapper.serializarMedicamentos(medicamentosAplicadosList)
        val examenesJson = ActaFormSnapshotMapper.serializarExamenes(examenesAgregadosList)
        val evidenciasJson = ActaFormSnapshotMapper.serializarEvidencias(serializarEvidenciasActuales())

        val previo = entidadBorradorActual
        val ahora = System.currentTimeMillis()
        val previoNormalizado = if (previo?.estadoSincronizacion == EstadoSincronizacionActa.REQUIERE_ATENCION) {
            previo.copy(
                estadoSincronizacion = EstadoSincronizacionActa.BORRADOR,
                ultimoError = null,
                ultimoCodigoHttp = null,
                reintentos = 0
            )
        } else {
            previo
        }
        val base = previoNormalizado?.copy(
            idPeticion = peticionId,
            idVeterinario = idVet,
            estadoSincronizacion = EstadoSincronizacionActa.BORRADOR,
            pasoActual = currentStep,
            actualizadoEn = ahora
        ) ?: ActaLocalEntity(
            idPeticion = peticionId,
            idVeterinario = idVet,
            estadoSincronizacion = EstadoSincronizacionActa.BORRADOR,
            pasoActual = currentStep,
            actualizadoEn = ahora
        )

        return ActaFormSnapshotMapper.capturar(
            binding = binding,
            baseEntity = base,
            pasoActual = currentStep,
            latitudVisita = latitudVisita,
            longitudVisita = longitudVisita,
            animalesJson = animalesJson,
            medicamentosJson = medicamentosJson,
            examenesJson = examenesJson,
            evidenciasJson = evidenciasJson,
            incluirFirmas = incluirFirmas
        )
    }

    private fun guardarEntidadEnDb(entidad: ActaLocalEntity): ActaLocalEntity {
        return synchronized(dbWriteLock) {
            if (edicionBloqueadaPorEstado) {
                Log.w("ActaAtencionActivity", "Guardado abortado: edición bloqueada por estado para petición ${entidad.idPeticion}")
                return@synchronized entidad
            }
            val filaExistente = actaLocalRepository.obtenerPorPeticionYVeterinario(entidad.idPeticion, entidad.idVeterinario)
            if (filaExistente != null && esFilaBloqueadaParaEscritura(filaExistente)) {
                Log.w(
                    "ActaAtencionActivity",
                    "Guardado abortado por candado defensivo: la fila local para petición ${entidad.idPeticion} está en estado ${filaExistente.estadoSincronizacion} o idSeguimientoRemoto=${filaExistente.idSeguimientoRemoto}"
                )
                return@synchronized entidad
            }
            val idFila = actaLocalRepository.guardarBorrador(entidad)
            val persistida = if (idFila > 0L) entidad.copy(idLocal = idFila) else entidad
            entidadBorradorActual = persistida
            persistida
        }
    }

    private fun extraerEstadosAnimales(): List<AnimalCardState> {
        return animalBindingsList.map { b ->
            AnimalCardState(
                nombre = b.etPacienteNombreCard.text.toString().trim(),
                especie = b.autoCompleteEspecieCard.text.toString().trim(),
                raza = b.autoCompleteRazaCard.text.toString().trim(),
                sexo = b.autoCompleteSexoCard.text.toString().trim(),
                color = b.autoCompleteColorCard.text.toString().trim(),
                edadNumero = b.etEdadNumero.text.toString().trim(),
                edadUnidad = b.autoCompleteEdadUnidad.text.toString().trim(),
                edadTexto = b.etPacienteEdadCard.text.toString().trim(),
                peso = b.etPacientePesoCard.text.toString().trim(),
                esterilizado = b.autoCompleteEsterilizadoCard.text.toString().trim(),
                descripcion = b.etPacienteDescripcionCard.text.toString().trim()
            )
        }
    }

    private fun normalizarClientUuidMedicamentos() {
        for (i in medicamentosAplicadosList.indices) {
            val item = medicamentosAplicadosList[i]
            val unidadesEnteras = item.cantidad.toInt().coerceAtLeast(1)
            val uuidActual = item.clientUuid
            val registroPrevio = if (uuidActual.isNotBlank()) {
                medicamentosPersistidosPrevios.firstOrNull { it.clientUuid == uuidActual }
            } else {
                null
            }

            val uuidFinal = when {
                uuidActual.isBlank() -> java.util.UUID.randomUUID().toString()
                registroPrevio != null &&
                    (registroPrevio.idMedicamento != item.idMedicamento ||
                        registroPrevio.cantidad.toInt().coerceAtLeast(1) != unidadesEnteras) ->
                    java.util.UUID.randomUUID().toString()
                else -> uuidActual
            }

            val actualizado = if (uuidFinal != uuidActual) item.copy(clientUuid = uuidFinal) else item
            medicamentosAplicadosList[i] = actualizado
        }
        medicamentosPersistidosPrevios.clear()
        medicamentosPersistidosPrevios.addAll(medicamentosAplicadosList)
    }

    private fun serializarEvidenciasActuales(): List<EvidenciaLocalItem> {
        return selectedMediaList.mapNotNull { media ->
            val ruta = media.localPath.ifBlank {
                val f = File(obtenerDirectorioEvidenciasActa(), media.nombre)
                if (f.exists()) f.absolutePath else ""
            }
            if (ruta.isBlank()) {
                null
            } else {
                EvidenciaLocalItem(
                    localPath = ruta,
                    esVideo = media.esVideo,
                    etiqueta = media.nombre,
                    remoteUrl = media.remoteUrl
                )
            }
        }
    }

    private fun persistirRemoteUrlEvidencia(media: MediaAdjunto, secureUrl: String) {
        media.remoteUrl = secureUrl
        val nuevoJson = ActaFormSnapshotMapper.serializarEvidencias(serializarEvidenciasActuales())
        synchronized(dbWriteLock) {
            if (edicionBloqueadaPorEstado) {
                Log.w("ActaAtencionActivity", "persistirRemoteUrlEvidencia abortado: edición bloqueada por estado")
                return
            }
            val actual = entidadBorradorActual
                ?: actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, obtenerIdVeterinarioActual())
            if (actual != null && actual.idLocal > 0L) {
                if (esFilaBloqueadaParaEscritura(actual)) {
                    Log.w("ActaAtencionActivity", "persistirRemoteUrlEvidencia abortado por candado defensivo: fila bloqueada")
                    return
                }
                actaLocalRepository.actualizarEvidenciasJson(actual.idLocal, nuevoJson)
                entidadBorradorActual = actual.copy(
                    evidenciasJson = nuevoJson,
                    actualizadoEn = System.currentTimeMillis()
                )
            }
        }
    }

    private fun verificarBorradorExistenteAlAbrir() {
        if (edicionBloqueadaPorEstado) return
        val idVet = obtenerIdVeterinarioActual()
        if (peticionId <= 0 || idVet <= 0) return

        val existente = synchronized(dbWriteLock) {
            actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, idVet)
        } ?: return

        if (esFilaBloqueadaParaEscritura(existente)) {
            return
        }

        if (existente.estadoSincronizacion != EstadoSincronizacionActa.BORRADOR &&
            existente.estadoSincronizacion != EstadoSincronizacionActa.REQUIERE_ATENCION
        ) {
            return
        }

        entidadBorradorActual = existente
        mostrandoDialogoBorrador = true

        val sdfFechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val fechaFormateada = sdfFechaHora.format(Date(existente.actualizadoEn))
        val mensaje = if (existente.estadoSincronizacion == EstadoSincronizacionActa.REQUIERE_ATENCION) {
            getString(
                R.string.acta_offline_dialog_atencion_mensaje,
                existente.ultimoError.orEmpty()
            )
        } else {
            getString(
                R.string.acta_offline_dialog_borrador_mensaje,
                existente.pasoActual,
                fechaFormateada
            )
        }

        val icono = ContextCompat.getDrawable(this, R.drawable.ic_info_circle)?.mutate()?.apply {
            setTint(ContextCompat.getColor(this@ActaAtencionActivity, R.color.primary))
        }

        MaterialAlertDialogBuilder(this)
            .apply {
                if (icono != null) {
                    setIcon(icono)
                }
            }
            .setTitle(R.string.acta_offline_dialog_borrador_titulo)
            .setMessage(mensaje)
            .setCancelable(false)
            .setPositiveButton(R.string.acta_offline_dialog_borrador_continuar) { dialog, _ ->
                dialog.dismiss()
                mostrandoDialogoBorrador = false
                restaurarBorrador(existente)
                Toast.makeText(
                    this,
                    getString(R.string.acta_offline_toast_borrador_restaurado, existente.pasoActual.coerceIn(1, 3)),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.acta_offline_dialog_borrador_descartar) { dialog, _ ->
                dialog.dismiss()
                mostrandoDialogoBorrador = false
                descartarBorrador(existente)
                Toast.makeText(
                    this,
                    getString(R.string.acta_offline_toast_borrador_descartado),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun restaurarBorrador(entidad: ActaLocalEntity) {
        if (edicionBloqueadaPorEstado || esFilaBloqueadaParaEscritura(entidad)) return
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        isRestaurandoBorrador = true
        try {
            val entidadNormalizada = if (entidad.estadoSincronizacion == EstadoSincronizacionActa.REQUIERE_ATENCION) {
                entidad.copy(
                    estadoSincronizacion = EstadoSincronizacionActa.BORRADOR,
                    ultimoError = null,
                    ultimoCodigoHttp = null,
                    reintentos = 0
                )
            } else {
                entidad
            }
            entidadBorradorActual = entidadNormalizada
            latitudVisita = entidadNormalizada.latitudVisita
            longitudVisita = entidadNormalizada.longitudVisita

            ActaFormSnapshotMapper.restaurar(binding, entidadNormalizada)
            sincronizarLabelsSwitches()

            if (entidad.lugarAtencion.isNotBlank()) {
                binding.tvLugarAtencionInfo.text = entidad.lugarAtencion
            }

            estadoRestauradoPorTarjeta.clear()
            binding.containerAnimales.removeAllViews()
            animalBindingsList.clear()
            val animalesRestaurados = ActaFormSnapshotMapper.deserializarAnimales(entidad.animalesJson)
            if (animalesRestaurados.isNotEmpty()) {
                for (estadoAnimal in animalesRestaurados) {
                    agregarFormularioAnimal(estadoAnimal)
                }
            } else {
                agregarFormularioAnimal()
            }

            medicamentosAplicadosList.clear()
            medicamentosPersistidosPrevios.clear()
            val medsRestaurados = ActaFormSnapshotMapper.deserializarMedicamentos(entidad.medicamentosJson)
            for (med in medsRestaurados) {
                val uuidNormalizado = med.clientUuid.ifBlank { java.util.UUID.randomUUID().toString() }
                val medConUuid = if (med.clientUuid == uuidNormalizado) med else med.copy(clientUuid = uuidNormalizado)
                medicamentosAplicadosList.add(medConUuid)
                medicamentosPersistidosPrevios.add(medConUuid)
            }
            actualizarVistaMedicamentosAplicados()

            examenesAgregadosList.clear()
            examenesAgregadosList.addAll(ActaFormSnapshotMapper.deserializarExamenes(entidad.examenesJson))
            renderizarExamenesAgregados()

            selectedMediaList.clear()
            val evidenciasRestauradas = ActaFormSnapshotMapper.deserializarEvidencias(entidad.evidenciasJson)
            for (ev in evidenciasRestauradas) {
                val archivoLocal = File(ev.localPath)
                if (archivoLocal.exists()) {
                    val uriLocal: Uri = try {
                        FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.fileprovider",
                            archivoLocal
                        )
                    } catch (_: Exception) {
                        Uri.fromFile(archivoLocal)
                    }
                    val etiquetaEvidencia = ev.etiqueta.ifBlank { archivoLocal.name }
                    selectedMediaList.add(
                        MediaAdjunto(
                            uri = uriLocal,
                            esVideo = ev.esVideo,
                            nombre = etiquetaEvidencia,
                            localPath = archivoLocal.absolutePath,
                            remoteUrl = ev.remoteUrl
                        )
                    )
                }
            }
            actualizarVistaMultimedia()

            currentStep = entidad.pasoActual.coerceIn(1, 3)
            updateStepper()

            usuarioHaEditado = true
        } finally {
            isRestaurandoBorrador = false
        }
    }

    private fun descartarBorrador(entidad: ActaLocalEntity) {
        if (edicionBloqueadaPorEstado) {
            Log.w("ActaAtencionActivity", "descartarBorrador abortado: edición bloqueada por estado")
            return
        }

        val filaActual = synchronized(dbWriteLock) {
            actaLocalRepository.obtenerPorPeticionYVeterinario(
                entidad.idPeticion,
                entidad.idVeterinario
            )
        }

        if (filaActual != null && esFilaBloqueadaParaEscritura(filaActual)) {
            Log.w("ActaAtencionActivity", "descartarBorrador abortado por candado defensivo: fila bloqueada en BD")
            return
        }
        if (esFilaBloqueadaParaEscritura(entidad)) {
            Log.w("ActaAtencionActivity", "descartarBorrador abortado: entidad bloqueada por estado")
            return
        }

        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)

        val medsDescontados = ActaFormSnapshotMapper.deserializarMedicamentos(entidad.medicamentosJson)
        val maletinManager = MaletinManager.getInstance(this)
        for (med in medsDescontados) {
            if (med.cantidad > 0.0) {
                maletinManager.devolverStock(med.idMedicamento, med.cantidad)
            }
        }
        medicamentosAplicadosList.clear()
        medicamentosPersistidosPrevios.clear()
        actualizarVistaMedicamentosAplicados()

        limpiarArchivosLocalesBorrador(entidad)

        synchronized(dbWriteLock) {
            if (entidad.idLocal > 0L) {
                actaLocalRepository.eliminar(entidad.idLocal)
            } else if (filaActual != null) {
                actaLocalRepository.eliminar(filaActual.idLocal)
            }
            entidadBorradorActual = null
        }

        usuarioHaEditado = false
        huboInteraccionUsuario = false
    }

    private fun limpiarArchivosLocalesBorrador(entidad: ActaLocalEntity?) {
        try {
            val raizArchivos = filesDir.canonicalPath
            val evidencias = entidad?.let { ActaFormSnapshotMapper.deserializarEvidencias(it.evidenciasJson) }.orEmpty()
            for (ev in evidencias) {
                if (ev.localPath.isNotBlank()) {
                    val f = File(ev.localPath)
                    if (f.exists() && f.canonicalPath.startsWith(raizArchivos)) {
                        f.delete()
                    }
                }
            }
            for (media in selectedMediaList) {
                if (media.localPath.isNotBlank()) {
                    val f = File(media.localPath)
                    if (f.exists() && f.canonicalPath.startsWith(raizArchivos)) {
                        f.delete()
                    }
                }
            }
            val dirActa = obtenerDirectorioEvidenciasActa()
            if (dirActa.exists() && dirActa.canonicalPath.startsWith(raizArchivos)) {
                dirActa.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    private fun marcarEnvioOnlineExitosoYLimpiarBorrador(codigoHttp: Int? = null) {
        actaEnviadaExitosamente = true
        edicionBloqueadaPorEstado = true
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)
        val actual = synchronized(dbWriteLock) {
            entidadBorradorActual
                ?: actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, obtenerIdVeterinarioActual())
        }
        limpiarArchivosLocalesBorrador(actual)
        synchronized(dbWriteLock) {
            if (actual != null && actual.idLocal > 0L) {
                actaLocalRepository.marcarEstado(
                    idLocal = actual.idLocal,
                    estado = EstadoSincronizacionActa.ENVIADA,
                    codigoHttp = codigoHttp,
                    error = null
                )
                entidadBorradorActual = actual.copy(
                    estadoSincronizacion = EstadoSincronizacionActa.ENVIADA,
                    ultimoCodigoHttp = codigoHttp,
                    ultimoError = null,
                    actualizadoEn = System.currentTimeMillis()
                )
            }
        }
    }

    private fun hayRedDisponible(): Boolean = NetworkUtils.hayRedDisponible(this)

    private suspend fun encolarActaYFinalizar(idSeguimientoRemotoPrevio: Int? = null) {
        withContext(NonCancellable + Dispatchers.IO) {
            val destruidaOFinalizando = isDestroyed || isFinishing
            val (entidadDesdeVistas, fallbackRadicado) = if (!destruidaOFinalizando) {
                withContext(Dispatchers.Main.immediate) {
                    val entidad = construirEntidadDesdeVistas(incluirFirmas = true)
                    val fallback = binding.etRadicado.text?.toString()?.trim()?.ifEmpty { null } ?: "RAD-VIS"
                    entidad to fallback
                }
            } else {
                null to (entidadBorradorActual?.nroRadicadoAtencion?.takeIf { it.isNotBlank() } ?: "RAD-VIS")
            }
            val idVet = obtenerIdVeterinarioActual()
            var idLocalParaEncolar: Long = -1L
            var nroRadicadoParaDialogo = fallbackRadicado

            synchronized(dbWriteLock) {
                val filaActual = actaLocalRepository.obtenerPorPeticionYVeterinario(peticionId, idVet)
                val radicadoEntidad = (entidadDesdeVistas?.nroRadicadoAtencion?.takeIf { it.isNotBlank() }
                    ?: entidadBorradorActual?.nroRadicadoAtencion?.takeIf { it.isNotBlank() }
                    ?: filaActual?.nroRadicadoAtencion?.takeIf { it.isNotBlank() })
                if (!radicadoEntidad.isNullOrBlank()) {
                    nroRadicadoParaDialogo = radicadoEntidad
                }

                val yaBloqueada = filaActual != null && (
                    filaActual.estadoSincronizacion == EstadoSincronizacionActa.ENVIADA ||
                    filaActual.estadoSincronizacion == EstadoSincronizacionActa.PENDIENTE_ENVIO ||
                    filaActual.estadoSincronizacion == EstadoSincronizacionActa.ENVIANDO ||
                    filaActual.estadoSincronizacion == EstadoSincronizacionActa.ERROR_REINTENTABLE
                )

                if (!yaBloqueada) {
                    val idRemotoFinal = idSeguimientoRemotoPrevio ?: filaActual?.idSeguimientoRemoto ?: entidadBorradorActual?.idSeguimientoRemoto
                    val entidadBase = entidadDesdeVistas ?: entidadBorradorActual ?: filaActual
                    val entidadAEncolar = entidadBase?.copy(
                        idLocal = filaActual?.idLocal ?: entidadBorradorActual?.idLocal ?: 0L,
                        idPeticion = peticionId,
                        idVeterinario = idVet,
                        idSeguimientoRemoto = idRemotoFinal,
                        estadoSincronizacion = EstadoSincronizacionActa.PENDIENTE_ENVIO,
                        sincronizacionOffline = true,
                        reintentos = 0,
                        ultimoError = null,
                        ultimoCodigoHttp = null,
                        actualizadoEn = System.currentTimeMillis()
                    )
                    if (entidadAEncolar != null) {
                        // Escritura directa en repositorio: evita el candado defensivo de guardarEntidadEnDb diseñado para borradores ordinarios.
                        val nuevoId = actaLocalRepository.guardarBorrador(entidadAEncolar)
                        val persistida = if (nuevoId > 0L) entidadAEncolar.copy(idLocal = nuevoId) else entidadAEncolar
                        entidadBorradorActual = persistida
                        idLocalParaEncolar = persistida.idLocal
                    }
                } else {
                    idLocalParaEncolar = filaActual.idLocal
                }
            }

            if (idLocalParaEncolar > 0L) {
                ActaSyncScheduler.enqueueOneTimeSync(applicationContext, idLocalParaEncolar)
            }

            actaEnviadaExitosamente = true
            edicionBloqueadaPorEstado = true
            guardadoHandler.removeCallbacks(guardadoDebounceRunnable)

            withContext(Dispatchers.Main) {
                enviandoActa = false
                if (!isFinishing && !isDestroyed) {
                    setLoadingState(false)
                    setResult(Activity.RESULT_OK)
                    val nroRadicado = nroRadicadoParaDialogo.ifBlank { "RAD-VIS" }
                    MaterialDialogHelper.mostrarExito(
                        context = this@ActaAtencionActivity,
                        titulo = getString(R.string.acta_offline_dialog_encolada_titulo),
                        mensaje = getString(R.string.acta_offline_dialog_encolada_mensaje, nroRadicado),
                        textoBoton = getString(R.string.acta_offline_dialog_encolada_boton),
                        cancelable = false,
                        onAceptar = {
                            finish()
                        }
                    )
                } else if (!isFinishing) {
                    finish()
                }
            }
        }
    }

    private fun verificarParidadRequest(
        requestVistas: SeguimientoPeticionVisitaRequest,
        requestEntidad: SeguimientoPeticionVisitaRequest
    ) {
        try {
            val gson = Gson()
            val treeVistas = gson.toJsonTree(requestVistas)
            val treeEntidad = gson.toJsonTree(requestEntidad)
            if (treeVistas == treeEntidad) return

            val objVistas = treeVistas.asJsonObject
            val objEntidad = treeEntidad.asJsonObject
            val todasLasClaves = (objVistas.keySet() + objEntidad.keySet()).toSortedSet()
            val diferencias = mutableListOf<String>()

            for (clave in todasLasClaves) {
                val valVista = objVistas.get(clave)
                val valEntidad = objEntidad.get(clave)
                if (valVista != valEntidad) {
                    val esFirma = clave.contains("firma", ignoreCase = true)
                    val strVista = truncarValorParidad(valVista?.toString(), esFirma)
                    val strEntidad = truncarValorParidad(valEntidad?.toString(), esFirma)
                    diferencias.add("$clave -> vistas=$strVista | sqlite=$strEntidad")
                }
            }

            if (diferencias.isNotEmpty()) {
                Log.e(
                    "ActaParityCheck",
                    "Diferencias detectadas (${diferencias.size}): ${diferencias.joinToString(" ;; ")}"
                )
            }
        } catch (e: Exception) {
            Log.e("ActaParityCheck", "Error verificando paridad: ${e.message}")
        }
    }

    private fun truncarValorParidad(valor: String?, esFirma: Boolean): String {
        if (valor == null) return "null"
        if (esFirma) {
            return "len=${valor.length},head=${valor.take(24)}..."
        }
        return if (valor.length > 120) "${valor.take(120)}...(len=${valor.length})" else valor
    }

    private fun esFilaBloqueadaParaEscritura(fila: ActaLocalEntity): Boolean {
        val estado = fila.estadoSincronizacion
        return estado == EstadoSincronizacionActa.PENDIENTE_ENVIO ||
            estado == EstadoSincronizacionActa.ENVIANDO ||
            estado == EstadoSincronizacionActa.ERROR_REINTENTABLE ||
            estado == EstadoSincronizacionActa.ENVIADA ||
            fila.idSeguimientoRemoto != null
    }

    private fun verificarFilaBloqueadaAlAbrir(existente: ActaLocalEntity): Boolean {
        val estado = existente.estadoSincronizacion
        val esEnCola = (estado == EstadoSincronizacionActa.PENDIENTE_ENVIO ||
            estado == EstadoSincronizacionActa.ENVIANDO ||
            estado == EstadoSincronizacionActa.ERROR_REINTENTABLE) ||
            (existente.idSeguimientoRemoto != null && estado != EstadoSincronizacionActa.ENVIADA)

        val esYaEnviada = estado == EstadoSincronizacionActa.ENVIADA

        if (!esEnCola && !esYaEnviada) {
            return false
        }

        edicionBloqueadaPorEstado = true
        guardadoHandler.removeCallbacks(guardadoDebounceRunnable)

        val radicado = existente.nroRadicadoAtencion.ifBlank {
            binding.etRadicado.text?.toString()?.trim()?.ifEmpty { null } ?: "RAD-VIS"
        }

        val tituloRes = if (esEnCola) {
            R.string.acta_offline_dialog_en_cola_titulo
        } else {
            R.string.acta_offline_dialog_ya_enviada_titulo
        }

        val mensajeRes = if (esEnCola) {
            R.string.acta_offline_dialog_en_cola_mensaje
        } else {
            R.string.acta_offline_dialog_ya_enviada_mensaje
        }

        val icono = ContextCompat.getDrawable(this, R.drawable.ic_info_circle)?.mutate()?.apply {
            setTint(ContextCompat.getColor(this@ActaAtencionActivity, R.color.primary))
        }

        MaterialAlertDialogBuilder(this)
            .apply {
                if (icono != null) {
                    setIcon(icono)
                }
            }
            .setTitle(tituloRes)
            .setMessage(getString(mensajeRes, radicado))
            .setCancelable(false)
            .setPositiveButton(R.string.acta_offline_dialog_en_cola_boton) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()

        return true
    }
}
