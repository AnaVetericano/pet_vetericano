package com.example.petvetericano

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

    // Lista de ViewBindings dinámicos para cada tarjeta de animal
    private val animalBindingsList = mutableListOf<ItemAnimalFormularioBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityActaAtencionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        peticionId = intent.getIntExtra("PETICION_ID", -1)

        setupToolbar()
        setupUI()
        setupDropdowns()
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
                        if (binding.etNotificadoNombre.text.isNullOrEmpty()) {
                            binding.etNotificadoNombre.setText(binding.etPropietarioNombre.text.toString().trim())
                        }
                        if (binding.etNotificadoIdentificacion.text.isNullOrEmpty()) {
                            binding.etNotificadoIdentificacion.setText(binding.etPropietarioCedula.text.toString().trim())
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

        binding.btnClearSignatureNotificador.setOnClickListener {
            binding.signatureNotificador.clear()
        }

        binding.btnClearSignatureNotificado.setOnClickListener {
            binding.signatureNotificado.clear()
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

        // Dropdown Paso 3: Estado de Cierre de la Petición
        val estadosCierre = arrayOf("Atendida", "En Proceso", "Cerrada")
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

        val lugarAtencion = binding.etLugarAtencion.text.toString().trim()
        if (lugarAtencion.isEmpty()) {
            binding.tilLugarAtencion.error = "Lugar de atención requerido"
            valido = false
        } else {
            binding.tilLugarAtencion.error = null
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

        for ((index, itemBinding) in animalBindingsList.withIndex()) {
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
            Toast.makeText(this, "Complete los datos clínicos requeridos", Toast.LENGTH_SHORT).show()
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
            latitud = 0.0,
            longitud = 0.0
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
        val estadoCierre = binding.autoCompleteEstadoCierre.text.toString().trim().ifEmpty { "Atendida" }

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