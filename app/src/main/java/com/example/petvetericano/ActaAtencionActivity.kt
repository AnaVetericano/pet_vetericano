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
import com.example.petvetericano.models.AnimalRapidoRequest
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

enum class TipoActa {
    SERES_SINTIENTES,
    RESCATE
}

class ActaAtencionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActaAtencionBinding
    private var currentStep = 1
    private var peticionId: Int = -1
    private var tipoActaActual = TipoActa.SERES_SINTIENTES

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
        setupSelectorTipoActa()
        setupLugarAtencion()
        setupDesparasitacionSwitch()
        setupAutoScrollOnFocus()
        updateStepper()

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
                        if (tipoActaActual == TipoActa.SERES_SINTIENTES) {
                            if (binding.etNotificadoNombre.text.isNullOrEmpty()) {
                                binding.etNotificadoNombre.setText(binding.etPropietarioNombre.text.toString().trim())
                            }
                            if (binding.etNotificadoIdentificacion.text.isNullOrEmpty()) {
                                binding.etNotificadoIdentificacion.setText(binding.etPropietarioCedula.text.toString().trim())
                            }
                        } else {
                            if (binding.etNotificadoNombre.text.isNullOrEmpty()) {
                                binding.etNotificadoNombre.setText("Testigo: " + binding.etTestigo1Nombre.text.toString().trim())
                            }
                            if (binding.etNotificadoIdentificacion.text.isNullOrEmpty()) {
                                binding.etNotificadoIdentificacion.setText(binding.etTestigo1Cedula.text.toString().trim())
                            }
                            if (binding.etVeterinarioReceptor.text.isNullOrEmpty()) {
                                binding.etVeterinarioReceptor.setText(binding.etFuncionarioNombre.text.toString().trim())
                            }
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

    private fun setupSelectorTipoActa() {
        actualizarModoActaUI()

        binding.btnCambiarTipoActa.setOnClickListener {
            when (tipoActaActual) {
                TipoActa.SERES_SINTIENTES -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Acceder a Acta de Rescate")
                        .setMessage("¿Desea acceder al formato de Acta de Rescate? Se cargarán los campos para verificación de ausencia de propietario, testigos y la lista de chequeo clínico por sistemas de la Alcaldía de Popayán.")
                        .setPositiveButton("Sí, acceder") { _, _ ->
                            tipoActaActual = TipoActa.RESCATE
                            actualizarModoActaUI()
                            Toast.makeText(this, "Formato cambiado a Acta de Rescate", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
                TipoActa.RESCATE -> {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Acceder a Acta de Seres Sintientes")
                        .setMessage("¿Desea acceder al formato de Acta de Seres Sintientes (Atención en Campo)? Se habilitarán los campos de propietario y evaluación clínica general.")
                        .setPositiveButton("Sí, acceder") { _, _ ->
                            tipoActaActual = TipoActa.SERES_SINTIENTES
                            actualizarModoActaUI()
                            Toast.makeText(this, "Formato cambiado a Acta de Seres Sintientes", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }
        }
    }

    private fun actualizarModoActaUI() {
        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()

        when (tipoActaActual) {
            TipoActa.SERES_SINTIENTES -> {
                binding.topAppBar.title = "Acta de atención en campo"
                binding.tvTipoActaActual.text = "Acta de Seres Sintientes"
                binding.tvTipoActaSubtitulo.text = "Atención veterinaria en campo"
                binding.btnCambiarTipoActa.text = "Acta de rescate"
                binding.ivTipoActaIcon.setImageResource(R.drawable.baseline_pets_24)

                // Stepper indicadores
                binding.tvStep1Text.text = "Datos\nGenerales"
                binding.tvStep2Text.text = "Pacientes\ny Clínica"
                binding.tvStep3Text.text = "Pruebas\ny Cierre"

                // Paso 1
                binding.layoutDatosPropietario.visibility = View.VISIBLE
                binding.layoutDatosRescatePaso1.visibility = View.GONE

                // Paso 2
                binding.layoutClinicaSeresSintientes.visibility = View.VISIBLE
                binding.layoutChecklistRescate.visibility = View.GONE

                // Paso 3
                binding.layoutProcedimientosSeresSintientes.visibility = View.VISIBLE
                binding.layoutRecepcionVeterinaria.visibility = View.GONE
                binding.tvFirma1Titulo.text = "Firma del funcionario / notificador"
                binding.tvFirma2Titulo.text = "Firma del ciudadano notificado"

                if (binding.etRadicado.text.toString().contains("RESC")) {
                    binding.etRadicado.setText("RAD-VIS-$year-$randomSuffix")
                }
            }
            TipoActa.RESCATE -> {
                binding.topAppBar.title = "Formato de Rescate (Popayán)"
                binding.tvTipoActaActual.text = "Formato de Rescate"
                binding.tvTipoActaSubtitulo.text = "F-GS-C01-03-33 (Alcaldía de Popayán)"
                binding.btnCambiarTipoActa.text = "Acta seres sintientes"
                binding.ivTipoActaIcon.setImageResource(R.drawable.baseline_pets_24)

                // Stepper indicadores
                binding.tvStep1Text.text = "Reporte y\nTestigos"
                binding.tvStep2Text.text = "Paciente y\nSistemas"
                binding.tvStep3Text.text = "Firmas y\nRecepción"

                // Paso 1
                binding.layoutDatosPropietario.visibility = View.GONE
                binding.layoutDatosRescatePaso1.visibility = View.VISIBLE

                // Paso 2
                binding.layoutClinicaSeresSintientes.visibility = View.GONE
                binding.layoutChecklistRescate.visibility = View.VISIBLE

                // Paso 3
                binding.layoutProcedimientosSeresSintientes.visibility = View.GONE
                binding.layoutRecepcionVeterinaria.visibility = View.VISIBLE
                binding.tvFirma1Titulo.text = "Firma del rescatista"
                binding.tvFirma2Titulo.text = "Firma del testigo (sin propietario)"

                if (binding.etRadicado.text.toString().contains("VIS")) {
                    binding.etRadicado.setText("RAD-RESC-$year-$randomSuffix")
                }

                // Autocompletar datos de recepción por defecto si están vacíos
                if (binding.etVeterinarioReceptor.text.isNullOrEmpty()) {
                    binding.etVeterinarioReceptor.setText(binding.etFuncionarioNombre.text.toString().trim())
                }
                if (binding.etFechaRecepcion.text.isNullOrEmpty()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    binding.etFechaRecepcion.setText(sdf.format(Date()))
                }
                if (binding.etHoraRecepcion.text.isNullOrEmpty()) {
                    val hf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    binding.etHoraRecepcion.setText(hf.format(Date()))
                }
            }
        }
        updateStepper()
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

        // Datos por defecto de recepción en rescate
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

    private fun setupLugarAtencion() {
        val dirPrevia = intent.getStringExtra("DIRECCION_ACTUAL")
        if (!dirPrevia.isNullOrBlank()) {
            binding.etLugarAtencion.setText(dirPrevia)
            binding.tvLugarAtencionInfo.text = "📍 $dirPrevia (Reportada en petición)"
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
                        // Fallback a getCurrentLocation si lastLocation es nulo
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
                binding.tvLugarAtencionInfo.text = "📍 $dir"
                binding.tvLugarAtencionStatus.text = "✓ Guardada"
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

        // Dropdown Paso 3: Selector de pruebas rápidas / complementarias
        val pruebasOpciones = arrayOf(
            "Ninguna / No requerida",
            "Test Rápido Parvovirus Canino",
            "Test Rápido Distemper / Moquillo",
            "Test Rápido Hemoparásitos (Ehrlichia / Anaplasma)",
            "Test Rápido Triple Felina (VIF / FeLV)",
            "Raspado Cutáneo / Ectoparásitos",
            "Coprológico directo",
            "Otro examen específico"
        )
        val adapterPruebas = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, pruebasOpciones)
        binding.autoCompleteTipoPrueba.setAdapter(adapterPruebas)
        binding.autoCompleteTipoPrueba.setText(pruebasOpciones[0], false)

        binding.autoCompleteTipoPrueba.setOnItemClickListener { _, _, position, _ ->
            val seleccion = pruebasOpciones[position]
            if (position > 0) {
                if (binding.etPruebasComplementarias.text.isNullOrEmpty() || pruebasOpciones.contains(binding.etPruebasComplementarias.text.toString())) {
                    binding.etPruebasComplementarias.setText(seleccion)
                }
                if (binding.etResultadoPruebas.text.isNullOrEmpty()) {
                    binding.etResultadoPruebas.setText("Negativo")
                }
            } else {
                binding.etPruebasComplementarias.text?.clear()
                binding.etResultadoPruebas.text?.clear()
            }
        }

        // Dropdown Paso 3: Estado de Cierre de la Petición (Ciclo de Vida Clínico)
        val estadosCierre = arrayOf("En tratamiento", "En observación", "Alta médica")
        val adapterCierre = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosCierre)
        binding.autoCompleteEstadoCierre.setAdapter(adapterCierre)
        binding.autoCompleteEstadoCierre.setText(estadosCierre[0], false)
    }

    /**
     * Agrega dinámicamente un formulario con View Binding para un paciente/animal
     */
    private fun agregarFormularioAnimal() {
        val itemBinding = ItemAnimalFormularioBinding.inflate(layoutInflater, binding.containerAnimales, false)
        val numeroAnimal = animalBindingsList.size + 1
        itemBinding.tvAnimalNumero.text = "Paciente #$numeroAnimal"

        // Configuración de Material 3 Dropdowns para el paciente
        val especies = arrayOf("Canino", "Felino", "Ave", "Equino", "Bovino", "Porcino", "Otro")
        val adapterEspecie = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, especies)
        itemBinding.autoCompleteEspecieCard.setAdapter(adapterEspecie)
        itemBinding.autoCompleteEspecieCard.setText(especies[0], false)

        val sexos = arrayOf("Macho", "Hembra", "Desconocido")
        val adapterSexo = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sexos)
        itemBinding.autoCompleteSexoCard.setAdapter(adapterSexo)
        itemBinding.autoCompleteSexoCard.setText(sexos[0], false)

        val esterilizadoOpciones = arrayOf("Si", "No", "No se sabe")
        val adapterEsterilizado = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, esterilizadoOpciones)
        itemBinding.autoCompleteEsterilizadoCard.setAdapter(adapterEsterilizado)
        itemBinding.autoCompleteEsterilizadoCard.setText(esterilizadoOpciones[1], false)

        itemBinding.btnEliminarAnimal.setOnClickListener {
            binding.containerAnimales.removeView(itemBinding.root)
            animalBindingsList.remove(itemBinding)
            actualizarNumeracionYBotonesEliminar()
        }

        binding.containerAnimales.addView(itemBinding.root)
        animalBindingsList.add(itemBinding)
        actualizarNumeracionYBotonesEliminar()
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

        // Restablecer estilos visuales del stepper (Material 3)
        val colorPrimary = getColor(R.color.primary)
        val colorMuted = getColor(R.color.text_muted)
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
                binding.tvStep1Num.setTextColor(getColor(R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.btnAnterior.visibility = View.GONE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            2 -> {
                binding.step2Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(circleBlue)
                binding.tvStep1Num.setTextColor(getColor(R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.tvStep2Num.setBackgroundResource(circleBlue)
                binding.tvStep2Num.setTextColor(getColor(R.color.white))
                binding.tvStep2Text.setTextColor(colorPrimary)
                binding.btnAnterior.visibility = View.VISIBLE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            3 -> {
                binding.step3Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(circleBlue)
                binding.tvStep1Num.setTextColor(getColor(R.color.white))
                binding.tvStep1Text.setTextColor(colorPrimary)
                binding.tvStep2Num.setBackgroundResource(circleBlue)
                binding.tvStep2Num.setTextColor(getColor(R.color.white))
                binding.tvStep2Text.setTextColor(colorPrimary)
                binding.tvStep3Num.setBackgroundResource(circleBlue)
                binding.tvStep3Num.setTextColor(getColor(R.color.white))
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

        if (tipoActaActual == TipoActa.SERES_SINTIENTES) {
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
        } else {
            // MODO RESCATE (Testigos y ausencia de propietario)
            val personaAtiende = binding.etPersonaAtiendeRescatista.text.toString().trim()
            if (personaAtiende.isEmpty()) {
                binding.tilPersonaAtiendeRescatista.error = "Persona que atiende requerida"
                valido = false
            } else {
                binding.tilPersonaAtiendeRescatista.error = null
            }

            val personaAtiendeTel = binding.etPersonaAtiendeTelefono.text.toString().trim()
            if (personaAtiendeTel.isEmpty()) {
                binding.tilPersonaAtiendeTelefono.error = "Teléfono requerido"
                valido = false
            } else {
                binding.tilPersonaAtiendeTelefono.error = null
            }

            val testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
            if (testigo1Nombre.isEmpty()) {
                binding.tilTestigo1Nombre.error = "Testigo 1 requerido"
                valido = false
            } else {
                binding.tilTestigo1Nombre.error = null
            }

            val testigo1Cedula = binding.etTestigo1Cedula.text.toString().trim()
            if (testigo1Cedula.isEmpty()) {
                binding.tilTestigo1Cedula.error = "Cédula Testigo 1 requerida"
                valido = false
            } else {
                binding.tilTestigo1Cedula.error = null
            }

            val barrioRescate = binding.etBarrioRescate.text.toString().trim()
            if (barrioRescate.isEmpty()) {
                binding.tilBarrioRescate.error = "Barrio del rescate requerido"
                valido = false
            } else {
                binding.tilBarrioRescate.error = null
            }
        }

        if (!valido) {
            Toast.makeText(this, "Por favor complete los campos obligatorios", Toast.LENGTH_SHORT).show()
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

        if (tipoActaActual == TipoActa.SERES_SINTIENTES) {
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
        } else {
            // MODO RESCATE
            val obsRescate = binding.etObservacionesRescate.text.toString().trim()
            if (obsRescate.isEmpty()) {
                binding.tilObservacionesRescate.error = "Ingrese observaciones del rescate *"
                valido = false
            } else {
                binding.tilObservacionesRescate.error = null
            }
        }

        if (!valido) {
            Toast.makeText(this, "Complete los datos clínicos requeridos", Toast.LENGTH_SHORT).show()
        }
        return valido
    }

    private fun validarPaso3(): Boolean {
        var valido = true

        if (tipoActaActual == TipoActa.SERES_SINTIENTES) {
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

            if ((tipoPrueba.isNotEmpty() && !tipoPrueba.contains("Ninguna", ignoreCase = true)) || pruebas.isNotEmpty()) {
                if (resultado.isEmpty()) {
                    binding.tilResultadoPruebas.error = "Ingrese el resultado de la prueba complementaria"
                    valido = false
                } else {
                    binding.tilResultadoPruebas.error = null
                }
            } else {
                binding.tilResultadoPruebas.error = null
            }
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

        if (!valido) {
            Toast.makeText(this, "Complete los campos obligatorios del cierre", Toast.LENGTH_SHORT).show()
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
            val raza = itemBinding.etPacienteRazaCard.text.toString().trim().ifEmpty { "Mestizo" }
            val sexo = itemBinding.autoCompleteSexoCard.text.toString().trim()
            val color = itemBinding.etPacienteColorCard.text.toString().trim()
            val edad = itemBinding.etPacienteEdadCard.text.toString().trim()
            val peso = itemBinding.etPacientePesoCard.text.toString().trim().toDoubleOrNull()
            val descExtra = itemBinding.etPacienteDescripcionCard.text.toString().trim()
            val esterilizadoStr = itemBinding.autoCompleteEsterilizadoCard.text.toString().trim()
            val esterilizado = esterilizadoStr.equals("Si", ignoreCase = true)

            // Empaquetamos especie, raza y edad en caracteristicas (campo oficial del modelo Animal en Django)
            val caracteristicasDetalle = buildString {
                append("Especie: $especie. ")
                append("Raza: $raza. ")
                if (edad.isNotEmpty()) append("Edad: $edad. ")
                if (descExtra.isNotEmpty()) append("Notas: $descExtra")
            }.trim()

            listaAnimales.add(
                AnimalRapidoRequest(
                    nombre = nombre,
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
        val pacienteColor = primerAnimalBinding?.etPacienteColorCard?.text?.toString()?.trim()?.ifEmpty { null }
        val pacienteRaza = primerAnimalBinding?.etPacienteRazaCard?.text?.toString()?.trim()?.ifEmpty { null }
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
        val fundamentoLegal = binding.etFundamentoLegal.text.toString().trim().ifEmpty { "Ley 1774 de 2016" }
        val observacion = binding.etObservaciones.text.toString().trim().ifEmpty { null }

        val notificadoNombre = binding.etNotificadoNombre.text.toString().trim().ifEmpty { null }
        val notificadoId = binding.etNotificadoIdentificacion.text.toString().trim().ifEmpty { null }
        val fechaAtencion = binding.etFechaAtencion.text.toString().trim()
        val radicado = binding.etRadicado.text.toString().trim()

        val firmaNotificador = binding.signatureNotificador.toBase64()
        val firmaNotificado = binding.signatureNotificado.toBase64()
        val desparasitacion = binding.swDesparasitacion.isChecked

        // Recopilación de constantes vitales del Paso 2 para enriquecer la anamnesis clínica
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

        val anamnesisBase = binding.etAnamnesis.text.toString().trim()
        val anamnesisFinal = if (signosVitales.isNotEmpty()) {
            "$anamnesisBase [Examen clínico: $signosVitales]"
        } else {
            anamnesisBase
        }

        // Pruebas complementarias del Paso 3
        val tipoPrueba = binding.autoCompleteTipoPrueba.text.toString().trim()
        val pruebaDetalle = binding.etPruebasComplementarias.text.toString().trim()
        val pruebaFinal = when {
            pruebaDetalle.isNotEmpty() -> pruebaDetalle
            tipoPrueba.isNotEmpty() && !tipoPrueba.contains("Ninguna", ignoreCase = true) -> tipoPrueba
            else -> null
        }
        val resultadoPruebas = binding.etResultadoPruebas.text.toString().trim().ifEmpty { null }

        // Estado de resolución / cierre del Paso 3
        val estadoCierre = binding.autoCompleteEstadoCierre.text.toString().trim().ifEmpty { "En tratamiento" }

        val request = if (tipoActaActual == TipoActa.SERES_SINTIENTES) {
            SeguimientoPeticionVisitaRequest(
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
                compromisos = binding.etCompromisos.text.toString().trim(),
                fundamentoLegal = fundamentoLegal,
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
        } else {
            // MODO RESCATE (Formato Oficial F-GS-C01-03-33)
            val personaAtiende = binding.etPersonaAtiendeRescatista.text.toString().trim()
            val personaAtiendeTel = binding.etPersonaAtiendeTelefono.text.toString().trim()
            val testigo1Nombre = binding.etTestigo1Nombre.text.toString().trim()
            val testigo1Cedula = binding.etTestigo1Cedula.text.toString().trim()
            val testigo1Tel = binding.etTestigo1Telefono.text.toString().trim()
            val testigo2Nombre = binding.etTestigo2Nombre.text.toString().trim()
            val testigo2Cedula = binding.etTestigo2Cedula.text.toString().trim()
            val testigo2Tel = binding.etTestigo2Telefono.text.toString().trim()
            val barrioRescate = binding.etBarrioRescate.text.toString().trim()
            val lugarRescate = binding.etLugarAtencion.text.toString().trim()

            val obsRescate = binding.etObservacionesRescate.text.toString().trim()
            val vetReceptor = binding.etVeterinarioReceptor.text.toString().trim()
            val fechaRecep = binding.etFechaRecepcion.text.toString().trim()
            val horaRecep = binding.etHoraRecepcion.text.toString().trim()
            val obsRecep = binding.etObservacionesRecepcion.text.toString().trim()

            val checklistDetalle = buildString {
                appendLine("=== ACTA DE RESCATE (Formato F-GS-C01-03-33, Versión 02) ===")
                appendLine("• Persona que atiende al rescatista: $personaAtiende")
                if (personaAtiendeTel.isNotEmpty()) appendLine("• Teléfono de contacto: $personaAtiendeTel")
                appendLine("• Testigo 1 (Certifica no propietario): $testigo1Nombre - CC: $testigo1Cedula ${if (testigo1Tel.isNotEmpty()) "- Tel: $testigo1Tel" else ""}")
                if (testigo2Nombre.isNotEmpty()) {
                    appendLine("• Testigo 2: $testigo2Nombre - CC: $testigo2Cedula ${if (testigo2Tel.isNotEmpty()) "- Tel: $testigo2Tel" else ""}")
                }
                appendLine("• Barrio de rescate: $barrioRescate")
                appendLine("• Lugar de rescate: $lugarRescate")
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

            val propietarioNombreRescate = if (personaAtiende.isNotEmpty()) {
                "Animal Rescatado (Atendió: $personaAtiende)"
            } else {
                "Animal Rescatado (Sin propietario verificado)"
            }

            SeguimientoPeticionVisitaRequest(
                idPeticion = peticionId,
                idVeterinario = idVeterinario,
                numeroRadicado = radicado,
                fechaAtencion = fechaAtencion,
                propietarioNombre = propietarioNombreRescate,
                propietarioCedula = "Sin propietario",
                propietarioTelefono = personaAtiendeTel.ifEmpty { "N/A" },
                propietarioEmail = null,
                propietarioBarrio = barrioRescate,
                propietarioDireccion = lugarRescate,
                quienReporta = "Rescate en campo",
                quienReportaOtro = "Formato Rescate F-GS-C01-03-33",
                solicitudAtencionPor = binding.etSolicitudAtencion.text.toString().trim().ifEmpty { "Operativo de rescate animal en vía pública" },
                lugarAtencion = lugarAtencion,
                nombrePaciente = nombrePaciente ?: "Paciente Rescatado",
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
                anamnesisDescripcionQueja = checklistDetalle,
                tratamientoRealizado = "Evaluación primaria de rescate y estabilización en campo",
                desparasitacion = false,
                pruebasComplementarias = null,
                resultadoPruebas = null,
                compromisos = "Recepción y traslado de animal rescatado para custodia y valoración médica",
                fundamentoLegal = "Formato de Rescate F-GS-C01-03-33 / Ley 1774 de 2016",
                plazoDiasCumplimiento = null,
                nombreFuncionario = funcionarioNombre,
                funcionarioCargo = funcionarioCargo,
                funcionarios = listaFuncionarios,
                notificadoNombre = testigo1Nombre,
                notificacionesIdentificacion = testigo1Cedula,
                fechaNotificacion = fechaAtencion,
                notificadorNombre = funcionarioNombre,
                notificadorIdentificacion = notificadorIdentificacion,
                notificadorCargo = funcionarioCargo,
                firmaNotificador = firmaNotificador,
                firmaNotificado = firmaNotificado,
                observacion = observacion
            )
        }

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
                        Toast.makeText(
                            this@ActaAtencionActivity,
                            "Acta registrada exitosamente (ID #$idSeg) - Estado: $estadoCierre",
                            Toast.LENGTH_LONG
                        ).show()
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
                    "Error ($primerClave): $valor"
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

    private fun setLoadingState(isLoading: Boolean) {
        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSiguiente.isEnabled = !isLoading
        binding.btnAnterior.isEnabled = !isLoading
    }
}