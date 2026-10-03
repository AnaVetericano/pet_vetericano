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
        val nombreCompleto = "${prefs.getUserName()} ${prefs.getUserLastName()}".trim()
        if (nombreCompleto.isNotEmpty()) {
            binding.etFuncionarioNombre.setText(nombreCompleto)
            binding.etFuncionarioCargo.setText("Médico Veterinario")
        }
    }

    private fun setupDropdowns() {
        val quienReportaOpciones = arrayOf("Propietario", "Comunidad", "Policia", "Fundacion", "Otro")
        val adapterReporta = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, quienReportaOpciones)
        binding.autoCompleteQuienReporta.setAdapter(adapterReporta)
        binding.autoCompleteQuienReporta.setText(quienReportaOpciones[0], false)

        binding.autoCompleteQuienReporta.setOnItemClickListener { _, _, position, _ ->
            val seleccion = quienReportaOpciones[position]
            if (seleccion.equals("Otro", ignoreCase = true)) {
                binding.tilQuienReportaOtro.visibility = View.VISIBLE
            } else {
                binding.tilQuienReportaOtro.visibility = View.GONE
                binding.etQuienReportaOtro.text?.clear()
            }
        }
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

        val lugarAtencion = LugarAtencionActa(
            direccion = binding.etLugarAtencion.text.toString().trim(),
            latitud = 0.0,
            longitud = 0.0
        )

        val funcionarioNombre = binding.etFuncionarioNombre.text.toString().trim()
        val funcionarioCargo = binding.etFuncionarioCargo.text.toString().trim()

        val listaFuncionarios = if (funcionarioNombre.isNotEmpty()) {
            listOf(
                FuncionarioActaRequest(
                    idUsuario = idVeterinario,
                    nombre = funcionarioNombre,
                    cargo = funcionarioCargo.ifEmpty { "Médico Veterinario" },
                    esExterno = false,
                    esPrincipal = true
                )
            )
        } else null

        val quienReporta = binding.autoCompleteQuienReporta.text.toString().trim()
        val quienReportaOtro = if (quienReporta.equals("Otro", ignoreCase = true)) {
            binding.etQuienReportaOtro.text.toString().trim().ifEmpty { null }
        } else null

        val emailPropietario = binding.etPropietarioEmail.text.toString().trim().ifEmpty { null }
        val plazoDias = binding.etPlazoDias.text.toString().trim().toIntOrNull()
        val fundamentoLegal = binding.etFundamentoLegal.text.toString().trim().ifEmpty { "Ley 1774 de 2016" }
        val observacion = binding.etObservaciones.text.toString().trim().ifEmpty { null }

        val notificadoNombre = binding.etNotificadoNombre.text.toString().trim().ifEmpty { null }
        val notificadoId = binding.etNotificadoIdentificacion.text.toString().trim().ifEmpty { null }
        val fechaAtencion = binding.etFechaAtencion.text.toString().trim()

        val request = SeguimientoPeticionVisitaRequest(
            idPeticion = peticionId,
            idVeterinario = idVeterinario,
            numeroRadicado = binding.etRadicado.text.toString().trim(),
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
            animales = listaAnimales,
            nroAnimalesAtendidos = listaAnimales.size,
            anamnesisDescripcionQueja = binding.etAnamnesis.text.toString().trim(),
            tratamientoRealizado = binding.etTratamiento.text.toString().trim(),
            desparasitacion = binding.swDesparasitacion.isChecked,
            pruebasComplementarias = binding.etPruebasComplementarias.text.toString().trim().ifEmpty { null },
            resultadoPruebas = binding.etResultadoPruebas.text.toString().trim().ifEmpty { null },
            compromisos = binding.etCompromisos.text.toString().trim(),
            fundamentoLegal = fundamentoLegal,
            plazoDiasCumplimiento = plazoDias,
            funcionarios = listaFuncionarios,
            notificadoNombre = notificadoNombre,
            notificacionesIdentificacion = notificadoId,
            fechaNotificacion = if (notificadoNombre != null) fechaAtencion else null,
            notificadorNombre = funcionarioNombre.ifEmpty { null },
            notificadorIdentificacion = null,
            notificadorCargo = funcionarioCargo.ifEmpty { null },
            observacion = observacion
        )

        // Estado de carga UI
        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = SharedPreferencesManager(this@ActaAtencionActivity)
                val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }

                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        Toast.makeText(this@ActaAtencionActivity, "Sesión no válida. Inicie sesión nuevamente.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val response = RetrofitClient.apiService.crearSeguimientoPeticionVisita("Bearer $token", request)

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val idSeg = body.idSeguimiento ?: 0
                        Toast.makeText(
                            this@ActaAtencionActivity,
                            "Acta registrada exitosamente (ID #$idSeg)",
                            Toast.LENGTH_LONG
                        ).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    } else {
                        val errorStr = response.errorBody()?.string() ?: ""
                        val mensajeError = parsearErrorDjango(errorStr, response.code())
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