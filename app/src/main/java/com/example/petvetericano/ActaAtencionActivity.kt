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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityActaAtencionBinding
import com.example.petvetericano.databinding.ItemAnimalFormularioBinding
import com.example.petvetericano.databinding.ItemExamenAgregadoBinding
import com.example.petvetericano.models.AnimalRapidoRequest
import com.example.petvetericano.models.ExamenSeleccionadoActa
import com.example.petvetericano.models.FuncionarioActaRequest
import com.example.petvetericano.models.LugarAtencionActa
import com.example.petvetericano.models.SeguimientoPeticionVisitaRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Lista de ViewBindings dinámicos para cada tarjeta de animal
    private val animalBindingsList = mutableListOf<ItemAnimalFormularioBinding>()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        setupToolbar()
        setupUI()
        setupDropdowns()
        setupLugarAtencion()
        setupDesparasitacionSwitch()
        setupModuloRescate()
        setupChecklistSwitches()
        setupAutoScrollOnFocus()
        setupAceleradoresCampo()
        setupSeccionExamenesMultiples()
        cargarCatalogosBackend()
        updateStepper()
        verificarSiPeticionYaAtendida()

        // Agregar por defecto al menos un formulario de paciente al iniciar
        agregarFormularioAnimal()

        binding.btnAddAnimal.setOnClickListener {
            agregarFormularioAnimal()
        }

        binding.btnSiguiente.setOnClickListener {
            when (currentStep) {
                1 -> {
                    if (validarPaso1()) {
                        currentStep = 2
                        updateStepper()
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
            }
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        binding.etFechaAtencion.setText(currentDate)

        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        binding.etRadicado.setText("RAD-VIS-$year-$randomSuffix")

        val prefs = SharedPreferencesManager(this)
        val nombreGuardado = prefs.getUserName().trim()
        val apellidoGuardado = prefs.getUserLastName().trim()
        val nombreCompleto = if (nombreGuardado.isNotEmpty() || apellidoGuardado.isNotEmpty()) {
            "$nombreGuardado $apellidoGuardado".trim()
        } else {
            "Médico Veterinario"
        }
        binding.etFuncionarioNombre.setText(nombreCompleto)
        binding.etFuncionarioCargo.setText("Médico Veterinario")

        val identificacion = prefs.getUserIdentification()
        if (identificacion.isNotEmpty()) {
            binding.etFuncionarioIdentificacion.setText(identificacion)
        }

        // Datos por defecto para recepción en rescate
        binding.etVeterinarioReceptor.setText(nombreCompleto)
        binding.etFechaRecepcion.setText(currentDate)
        val hf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.etHoraRecepcion.setText(hf.format(Date()))

        binding.btnClearSignatureNotificador.setOnClickListener {
            binding.signatureNotificador.clear()
        }

        binding.btnClearSignatureNotificado.setOnClickListener {
            binding.signatureNotificado.clear()
        }
    }

    private fun setupModuloRescate() {
        val motivosRescate = arrayOf(
            "Por salud crítica / Urgencia médica",
            "Por maltrato animal / Aprehensión preventiva",
            "Abandono / Vía pública sin tenedor",
            "Entrega voluntaria por incapacidad de tenencia",
            "Riesgo inminente para la comunidad o el animal"
        )
        val adapterMotivo = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, motivosRescate)
        binding.autoCompleteMotivoRescate.setAdapter(adapterMotivo)
        binding.autoCompleteMotivoRescate.setText(motivosRescate[0], false)

        binding.autoCompleteMotivoRescate.setOnItemClickListener { _, _, position, _ ->
            val causalSeleccionada = motivosRescate[position]
            actualizarObservacionRescatePorCausal(causalSeleccionada)
        }

        // Estado inicial: Rescate inactivo por defecto
        binding.swRequiereRescate.isChecked = false
        binding.cardBadgeRescateActivo.visibility = View.GONE
        binding.layoutModuloRescateIntegrado.visibility = View.GONE
        binding.tvEstadoRequiereRescate.text = "No requerido (atención normal)"
        binding.tvEstadoRequiereRescate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

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
        }

        // Botón para sincronizar manualmente en cualquier momento
        binding.btnCargarDatosRescate.setOnClickListener {
            autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura = true)
            Toast.makeText(this, "Datos de la atención sincronizados en el acta de rescate", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autocompletarDatosRescateDesdeSeresSintientes(forzarSobrescritura: Boolean) {
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
        val especie = primerAnimal?.autoCompleteEspecieCard?.text?.toString()?.trim() ?: "Canino"
        val solicitud = binding.etSolicitudAtencion.text.toString().trim()
        val anamnesis = binding.etAnamnesis.text.toString().trim()

        val textoGenerado = buildString {
            if (causal.isNotEmpty()) appendLine("Causal: $causal.")
            appendLine("Se efectúa rescate y retiro preventivo de $nombrePaciente ($especie) para su traslado inmediato al Centro de Bienestar Animal (CBA).")
            if (solicitud.isNotEmpty()) appendLine("Motivo de reporte: $solicitud.")
            if (anamnesis.isNotEmpty()) appendLine("Estado clínico inicial: $anamnesis.")
        }.trim()

        binding.etObservacionesRescate.setText(textoGenerado)
    }

    private fun setupChecklistSwitches() {
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

        for ((switch, label) in switchLabelPairs) {
            switch.isChecked = false
            label.text = "NO"
            label.setTextColor(colorSecundario)

            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    label.text = "SÍ"
                    label.setTextColor(colorPrimario)
                } else {
                    label.text = "NO"
                    label.setTextColor(colorSecundario)
                }
            }
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

    private fun setupDesparasitacionSwitch() {
        binding.swDesparasitacion.isChecked = false
        binding.tvEstadoDesparasitacion.text = "No aplicada"
        binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        binding.swDesparasitacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvEstadoDesparasitacion.text = "Sí aplicada"
                binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                binding.tvEstadoDesparasitacion.text = "No aplicada"
                binding.tvEstadoDesparasitacion.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun setupAutoScrollOnFocus() {
        val camposCriticos = listOf(
            binding.etTemperatura,
            binding.etFrecuenciaCardiaca,
            binding.etFrecuenciaRespiratoria,
            binding.etMucosas,
            binding.etAnamnesis,
            binding.etTratamiento,
            binding.etCompromisos,
            binding.etObservaciones,
            binding.etNotificadoNombre,
            binding.etNotificadoIdentificacion,
            binding.etObservacionesRescate,
            binding.etObservacionesRecepcion
        )

        for (campo in camposCriticos) {
            campo.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.postDelayed({
                        val rect = Rect()
                        view.getDrawingRect(rect)
                        binding.scrollForm.requestChildRectangleOnScreen(view, rect, false)
                    }, 280)
                }
            }
        }
    }

    private fun setupDropdowns() {
        val quienReportaOpciones = arrayOf("Propietario", "Comunidad", "Policia", "Fundacion", "Otro")
        val adapterReporta = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, quienReportaOpciones)
        binding.autoCompleteQuienReporta.setAdapter(adapterReporta)
        binding.autoCompleteQuienReporta.setText(quienReportaOpciones[0], false)

        val actualizarVisibilidadQuienReporta = { seleccion: String ->
            if (seleccion.equals("Otro", ignoreCase = true)) {
                binding.tilQuienReportaOtro.visibility = View.VISIBLE
            } else {
                binding.tilQuienReportaOtro.visibility = View.GONE
            }
        }

        binding.autoCompleteQuienReporta.setOnItemClickListener { _, _, position, _ ->
            actualizarVisibilidadQuienReporta(quienReportaOpciones[position])
        }

        binding.autoCompleteQuienReporta.addTextChangedListener {
            actualizarVisibilidadQuienReporta(binding.autoCompleteQuienReporta.text.toString().trim())
        }

        // Dropdown Paso 3: Selector de pruebas rápidas / complementarias (Sincronizado con backend)
        actualizarDropdownExamenes()

        // Dropdown Paso 3: Estado de Cierre de la Petición
        val estadosCierre = arrayOf("En tratamiento", "En observación", "Alta médica")
        val adapterCierre = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosCierre)
        binding.autoCompleteEstadoCierre.setAdapter(adapterCierre)
        binding.autoCompleteEstadoCierre.setText(estadosCierre[0], false)
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

        val adapterPruebas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresExamenes)
        binding.autoCompleteTipoPrueba.setAdapter(adapterPruebas)
        if (binding.autoCompleteTipoPrueba.text.isNullOrEmpty()) {
            binding.autoCompleteTipoPrueba.setText(nombresExamenes[0], false)
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
                // Actualizar dropdown de exámenes clínicos en el Paso 3
                actualizarDropdownExamenes()

                // Actualizar especies y razas de cada formulario de animal instanciado
                for (itemBinding in animalBindingsList) {
                    actualizarOpcionesEspecies(itemBinding)
                    val especieActual = itemBinding.autoCompleteEspecieCard.text.toString().trim()
                    actualizarOpcionesRazas(itemBinding, especieActual)
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
        if (actual.isEmpty() || !listaNombres.contains(actual)) {
            val predeterminado = listaNombres.firstOrNull { it.contains("Canino", ignoreCase = true) }
                ?: listaNombres.firstOrNull() ?: "Canino"
            itemBinding.autoCompleteEspecieCard.setText(predeterminado, false)
        }
    }

    /**
     * Actualiza el listado de razas seleccionables para un paciente según su especie,
     * consumiendo estrictamente las razas registradas en el backend/web.
     */
    private fun actualizarOpcionesRazas(itemBinding: ItemAnimalFormularioBinding, especie: String) {
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
            val offlineList = catalogoRazasOffline[especie] ?: listOf("Mestizo / Criollo")
            razasEspecie.addAll(offlineList)
        }

        // Garantizar que exista una opción de mestizo o criollo al inicio
        if (!razasEspecie.any { it.contains("Mestizo", ignoreCase = true) || it.contains("Criollo", ignoreCase = true) }) {
            razasEspecie.add(0, "Mestizo / Criollo")
        }

        val adapterRazas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, razasEspecie)
        itemBinding.autoCompleteRazaCard.setAdapter(adapterRazas)

        // Si el campo está vacío o la raza actual no coincide con la nueva especie, sugerir la primera válida
        val valorActual = itemBinding.autoCompleteRazaCard.text.toString().trim()
        if (valorActual.isEmpty() || !razasEspecie.contains(valorActual)) {
            val razaSugerida = razasEspecie.firstOrNull { it.contains("Mestizo", ignoreCase = true) } ?: razasEspecie.first()
            itemBinding.autoCompleteRazaCard.setText(razaSugerida, false)
        }
    }

    /**
     * Agrega dinámicamente un formulario con View Binding para un paciente/animal con aceleradores M3
     */
    private fun agregarFormularioAnimal() {
        val itemBinding = ItemAnimalFormularioBinding.inflate(layoutInflater, binding.containerAnimales, false)
        val numeroAnimal = animalBindingsList.size + 1
        itemBinding.tvAnimalNumero.text = "Paciente #$numeroAnimal"

        // 1. Selector de Especies asistido por catálogo web
        actualizarOpcionesEspecies(itemBinding)

        // 2. Selector de Sexo
        val sexos = arrayOf("Macho", "Hembra", "Desconocido")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sexos)
        itemBinding.autoCompleteSexoCard.setAdapter(adapterSexo)
        itemBinding.autoCompleteSexoCard.setText(sexos[0], false)

        // 3. Selector de Estado Reproductivo (Esterilizado)
        val esterilizadoOpciones = arrayOf("Si", "No", "No se sabe")
        val adapterEsterilizado = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, esterilizadoOpciones)
        itemBinding.autoCompleteEsterilizadoCard.setAdapter(adapterEsterilizado)
        itemBinding.autoCompleteEsterilizadoCard.setText(esterilizadoOpciones[1], false)

        // 4. Selector de Color con catálogo frecuente
        val adapterColor = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, coloresFrecuentes)
        itemBinding.autoCompleteColorCard.setAdapter(adapterColor)

        // 5. Catálogo de Razas dinámicas reactivas a la especie del backend
        val especieInicial = itemBinding.autoCompleteEspecieCard.text.toString().trim()
        actualizarOpcionesRazas(itemBinding, especieInicial)

        itemBinding.autoCompleteEspecieCard.setOnItemClickListener { _, _, position, _ ->
            val especieSeleccionada = itemBinding.autoCompleteEspecieCard.adapter?.getItem(position)?.toString() ?: ""
            if (especieSeleccionada.isNotEmpty()) {
                actualizarOpcionesRazas(itemBinding, especieSeleccionada)
            }
        }
        itemBinding.autoCompleteEspecieCard.addTextChangedListener {
            val especieActual = it?.toString().orEmpty().trim()
            if (especieActual.isNotEmpty()) {
                actualizarOpcionesRazas(itemBinding, especieActual)
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
            val especie = itemBinding.autoCompleteEspecieCard.text.toString().trim().ifEmpty { "Paciente" }
            itemBinding.etPacienteNombreCard.setText("Comunitario ($especie)")
        }

        // 9. Eliminación de paciente de la lista
        itemBinding.btnEliminarAnimal.setOnClickListener {
            binding.containerAnimales.removeView(itemBinding.root)
            animalBindingsList.remove(itemBinding)
            actualizarNumeracionYBotonesEliminar()
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
        if (quienReporta.equals("Otro", ignoreCase = true)) {
            val quienOtro = binding.etQuienReportaOtro.text.toString().trim()
            if (quienOtro.isEmpty()) {
                binding.tilQuienReportaOtro.error = "Especifique quién reporta"
                valido = false
            } else {
                binding.tilQuienReportaOtro.error = null
            }
        } else {
            binding.tilQuienReportaOtro.error = null
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
            Toast.makeText(this, "Por favor complete los campos obligatorios del Paso 1", Toast.LENGTH_SHORT).show()
        }
        return valido
    }

    private fun validarPaso2(): Boolean {
        var valido = true

        if (animalBindingsList.isEmpty()) {
            Toast.makeText(this, "Debe agregar al menos un paciente/animal", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Complete los datos clínicos requeridos en el Paso 2", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Complete los campos obligatorios antes de finalizar", Toast.LENGTH_SHORT).show()
        }
        return valido
    }

    private fun siNo(valor: Boolean): String = if (valor) "SÍ" else "NO"

    private fun submitActa() {
        if (peticionId == -1) {
            Toast.makeText(this, "Error: ID de petición no válido", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = SharedPreferencesManager(this@ActaAtencionActivity)
        var idVeterinario = prefs.getUserId()
        if (idVeterinario == -1) {
            idVeterinario = intent.getIntExtra("ID_USUARIO", -1)
        }

        if (idVeterinario == -1) {
            Toast.makeText(
                this,
                "Error: No se encontró el usuario veterinario en sesión. Inicie sesión nuevamente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Construcción de la lista de animales adaptada a AnimalRapidoSerializer de Django
        val listaAnimales = mutableListOf<AnimalRapidoRequest>()
        for (itemBinding in animalBindingsList) {
            val nombre = itemBinding.etPacienteNombreCard.text.toString().trim().ifEmpty { "Sin nombre" }
            val especie = itemBinding.autoCompleteEspecieCard.text.toString().trim()
            val raza = itemBinding.autoCompleteRazaCard.text.toString().trim().ifEmpty { "Mestizo" }
            val sexo = itemBinding.autoCompleteSexoCard.text.toString().trim()
            val color = itemBinding.autoCompleteColorCard.text.toString().trim()
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

            val caracteristicasDetalle = buildString {
                append("Especie: $especie. ")
                append("Raza: $raza. ")
                if (edad.isNotEmpty()) append("Edad: $edad. ")
                if (descExtra.isNotEmpty()) append("Notas: $descExtra")
            }.trim()

            listaAnimales.add(
                AnimalRapidoRequest(
                    nombre = nombre,
                    idRaza = idRazaEncontrada,
                    sexo = sexo,
                    color = color.ifEmpty { null },
                    peso = peso,
                    esterilizado = esterilizado,
                    caracteristicas = caracteristicasDetalle
                )
            )
        }

        // Datos del paciente principal (Modo A en Django y columnas directas en SeguimientoPeticionesVisita)
        val primerAnimalBinding = animalBindingsList.firstOrNull()
        val nombrePaciente = primerAnimalBinding?.etPacienteNombreCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pacienteEspecie = primerAnimalBinding?.autoCompleteEspecieCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pacienteSexo = primerAnimalBinding?.autoCompleteSexoCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pacienteColor = primerAnimalBinding?.autoCompleteColorCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pacienteRaza = primerAnimalBinding?.autoCompleteRazaCard?.text?.toString()?.trim()?.ifEmpty { null }
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
        val funcionarioCargo = binding.etFuncionarioCargo.text.toString().trim().ifEmpty { "Médico Veterinario" }
        val notificadorIdentificacion = binding.etFuncionarioIdentificacion.text.toString().trim().ifEmpty { null }

        val listaFuncionarios = listOf(
            FuncionarioActaRequest(
                idUsuario = idVeterinario,
                nombre = funcionarioNombre,
                cargo = funcionarioCargo,
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

        // Recopilación de constantes vitales del Paso 2
        val temp = binding.etTemperatura.text.toString().trim()
        val fc = binding.etFrecuenciaCardiaca.text.toString().trim()
        val fr = binding.etFrecuenciaRespiratoria.text.toString().trim()
        val mucosas = binding.etMucosas.text.toString().trim()

        val signosVitales = buildString {
            if (temp.isNotEmpty()) append("Temp: ${temp}°C. ")
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
            firmaNotificador = firmaNotificador,
            firmaNotificado = firmaNotificado,
            observacion = observacion
        )

        // Estado de carga UI
        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        Toast.makeText(this@ActaAtencionActivity, "Sesión no válida. Inicie sesión nuevamente.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 1. Enviar el Acta completa de atención en campo (POST /api/peticiones/seguimiento/crear/)
                val response = RetrofitClient.apiService.crearSeguimientoPeticionVisita("Bearer $token", request)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val idSeg = body.idSeguimiento ?: 0

                    // 2. Cierre y actualización del estado de la petición en el backend (PATCH /api/peticiones/{id}/estado/)
                    if (peticionId != -1) {
                        try {
                            RetrofitClient.apiService.actualizarEstadoPeticion(
                                "Bearer $token",
                                peticionId,
                                com.example.petvetericano.models.ActualizarEstadoRequest(
                                    estado = estadoCierre,
                                    observacion = "Acta de atención en campo registrada con radicado $radicado (ID Acta: #$idSeg)"
                                )
                            )
                        } catch (_: Exception) {
                            // Si falla la actualización secundaria del estado, el acta principal ya quedó registrada exitosamente
                        }
                    }

                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        val mensajeExito = if (binding.swRequiereRescate.isChecked) {
                            "Acta y Rescate CBA registrados exitosamente (ID #$idSeg) - Estado: $estadoCierre"
                        } else {
                            "Acta registrada exitosamente (ID #$idSeg) - Estado: $estadoCierre"
                        }
                        Toast.makeText(this@ActaAtencionActivity, mensajeExito, Toast.LENGTH_LONG).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                } else {
                    val errorStr = response.errorBody()?.string() ?: ""
                    val mensajeError = parsearErrorDjango(errorStr, response.code())
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        Toast.makeText(this@ActaAtencionActivity, mensajeError, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    Toast.makeText(
                        this@ActaAtencionActivity,
                        "Error de conexión con el servidor: ${e.localizedMessage ?: e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun parsearErrorDjango(errorBody: String, code: Int): String {
        return try {
            if (errorBody.isNotEmpty()) {
                val json = JSONObject(errorBody)
                val primerClave = json.keys().asSequence().firstOrNull()
                if (primerClave != null) {
                    val valor = json.optJSONArray(primerClave)?.optString(0)
                        ?: json.optString(primerClave)
                    if (primerClave.equals("error", ignoreCase = true) || primerClave.equals("detail", ignoreCase = true)) {
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
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                if (response.isSuccessful) {
                    val peticion = response.body()?.firstOrNull { it.id_peticion == peticionId }
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
                                .setTitle("Petición ya atendida")
                                .setMessage("Esta petición ya cuenta con un Acta de Seres Sintientes registrada.\n\nEl sistema no permite crear actas adicionales para el mismo caso para proteger la trazabilidad médica.")
                                .setCancelable(false)
                                .setPositiveButton("Regresar") { _, _ ->
                                    finish()
                                }
                                .show()
                        }
                    }
                }
            } catch (_: Exception) {
                // Si ocurre error de red, la validación estricta del backend protegerá el registro
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSiguiente.isEnabled = !isLoading
        binding.btnAnterior.isEnabled = !isLoading
    }
}
