package com.example.petvetericano

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
import com.example.petvetericano.models.AnimalActa
import com.example.petvetericano.models.FuncionarioActa
import com.example.petvetericano.models.LugarAtencionActa
import com.example.petvetericano.models.SeguimientoPeticionVisitaRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActaAtencionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActaAtencionBinding
    private var currentStep = 1
    private var peticionId: Int = -1

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

        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()
        setupUI()
        updateStepper()

        binding.btnSiguiente.setOnClickListener {
            if (currentStep < 3) {
                currentStep++
                updateStepper()
            } else {
                submitActa()
            }
        }

        binding.btnAnterior.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepper()
            }
        }
    }

    private fun setupUI() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        binding.etFechaAtencion.setText(currentDate)
        
        // El número radicado real viene del backend, generamos uno temporal en la UI
        val randomSuffix = (100..999).random()
        binding.etRadicado.setText("RAD-VIS-2026-$randomSuffix")
    }

    private fun setupSpinners() {
        val solicitudPor = arrayOf("Oficial", "Comunidad", "Veeduria")
        binding.spinnerSolicitudAtencion.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, solicitudPor)

        val quienReporta = arrayOf("Propietario", "Policia", "Fundacion")
        binding.spinnerQuienReporta.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, quienReporta)

        val especies = arrayOf("Canino", "Felino", "Ave", "Equino", "Bovino", "Porcino", "Otro")
        binding.spinnerEspecie.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, especies)

        val sexos = arrayOf("Macho", "Hembra", "Desconocido")
        binding.spinnerSexo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sexos)

        val esterilizado = arrayOf("Si", "No", "No se sabe")
        binding.spinnerEsterilizado.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, esterilizado)
    }

    private fun updateStepper() {
        // Ocultar todos los layouts
        binding.step1Layout.visibility = View.GONE
        binding.step2Layout.visibility = View.GONE
        binding.step3Layout.visibility = View.GONE

        // Reiniciar colores
        binding.tvStep1Num.setBackgroundResource(R.drawable.circle_gray)
        binding.tvStep2Num.setBackgroundResource(R.drawable.circle_gray)
        binding.tvStep3Num.setBackgroundResource(R.drawable.circle_gray)
        binding.tvStep1Num.setTextColor(android.graphics.Color.parseColor("#64748B"))
        binding.tvStep2Num.setTextColor(android.graphics.Color.parseColor("#64748B"))
        binding.tvStep3Num.setTextColor(android.graphics.Color.parseColor("#64748B"))

        when (currentStep) {
            1 -> {
                binding.step1Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep1Num.setTextColor(android.graphics.Color.WHITE)
                
                binding.btnAnterior.visibility = View.GONE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            2 -> {
                binding.step2Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep1Num.setTextColor(android.graphics.Color.WHITE)
                binding.tvStep2Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep2Num.setTextColor(android.graphics.Color.WHITE)
                
                binding.btnAnterior.visibility = View.VISIBLE
                binding.btnSiguiente.text = "Siguiente paso"
            }
            3 -> {
                binding.step3Layout.visibility = View.VISIBLE
                binding.tvStep1Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep1Num.setTextColor(android.graphics.Color.WHITE)
                binding.tvStep2Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep2Num.setTextColor(android.graphics.Color.WHITE)
                binding.tvStep3Num.setBackgroundResource(R.drawable.circle_blue)
                binding.tvStep3Num.setTextColor(android.graphics.Color.WHITE)
                
                binding.btnAnterior.visibility = View.VISIBLE
                binding.btnSiguiente.text = "Terminar petición"
            }
        }
    }

    private fun submitActa() {
        if (peticionId == -1) {
            Toast.makeText(this, "ID de petición inválido", Toast.LENGTH_SHORT).show()
            return
        }

        // Obtener datos del Step 1
        val radicado = binding.etRadicado.text.toString()
        val fechaAtencion = binding.etFechaAtencion.text.toString()
        val propietarioNombre = binding.etPropietarioNombre.text.toString()
        val propietarioCedula = binding.etPropietarioCedula.text.toString()
        val propietarioBarrio = binding.etPropietarioBarrio.text.toString()
        val propietarioDireccion = binding.etPropietarioDireccion.text.toString()
        val propietarioTelefono = binding.etPropietarioTelefono.text.toString()
        val solicitudPor = binding.spinnerSolicitudAtencion.selectedItem?.toString() ?: ""
        val quienReporta = binding.spinnerQuienReporta.selectedItem?.toString() ?: ""
        val lugarAtencionDir = binding.etLugarAtencion.text.toString()

        // Obtener datos del Step 2
        val pacienteNombre = binding.etPacienteNombre.text.toString()
        val pacienteEspecie = binding.spinnerEspecie.selectedItem?.toString() ?: ""
        val pacienteRaza = binding.etPacienteRaza.text.toString()
        val pacienteSexo = binding.spinnerSexo.selectedItem?.toString() ?: ""
        val pacienteColor = binding.etPacienteColor.text.toString()
        val pacienteEdad = binding.etPacienteEdad.text.toString()
        val pacientePeso = binding.etPacientePeso.text.toString().toDoubleOrNull() ?: 0.0
        val pacienteDesc = binding.etPacienteDescripcion.text.toString()
        val esterilizadoStr = binding.spinnerEsterilizado.selectedItem?.toString() ?: "No"
        val esterilizado = esterilizadoStr.equals("Si", ignoreCase = true)
        
        val anamnesis = binding.etAnamnesis.text.toString()
        val tratamiento = binding.etTratamiento.text.toString()

        // Obtener datos del Step 3
        val desparasitacion = binding.swDesparasitacion.isChecked
        val pruebasComplementarias = binding.etPruebasComplementarias.text.toString()
        val resultadoPruebas = binding.etResultadoPruebas.text.toString()
        val compromisos = binding.etCompromisos.text.toString()
        val plazoDias = binding.etPlazoDias.text.toString().toIntOrNull() ?: 0
        val funcionarioNombre = binding.etFuncionarioNombre.text.toString()
        val funcionarioCargo = binding.etFuncionarioCargo.text.toString()

        // Validaciones básicas
        if (propietarioNombre.isEmpty() || pacienteNombre.isEmpty()) {
            Toast.makeText(this, "Por favor complete los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // Construir Request
        val animalesList = listOf(
            AnimalActa(
                nombre = pacienteNombre,
                especie = pacienteEspecie,
                sexo = pacienteSexo,
                color = pacienteColor,
                raza = pacienteRaza,
                edad = pacienteEdad,
                peso = pacientePeso,
                esterilizado = esterilizado,
                descripcion = pacienteDesc
            )
        )

        val lugarAtencion = LugarAtencionActa(
            direccion = lugarAtencionDir,
            latitud = 0.0,
            longitud = 0.0
        )

        val funcionariosList = listOf(
            FuncionarioActa(
                id_usuario = null,
                es_principal = true,
                nombre = funcionarioNombre,
                cargo = funcionarioCargo
            )
        )

        val request = SeguimientoPeticionVisitaRequest(
            id_peticion = peticionId,
            numero_radicado = radicado,
            fecha_atencion = fechaAtencion,
            propietario_nombre = propietarioNombre,
            propietario_cedula = propietarioCedula,
            propietario_telefono = propietarioTelefono,
            propietario_barrio = propietarioBarrio,
            propietario_direccion = propietarioDireccion,
            quien_reporta = quienReporta,
            solicitud_atencion_por = solicitudPor,
            lugar_atencion = lugarAtencion,
            animales = animalesList,
            anamnesis_descripcion_queja = anamnesis,
            tratamiento_realizado = tratamiento,
            desparasitacion = desparasitacion,
            pruebas_complementarias = pruebasComplementarias,
            resultado_pruebas = resultadoPruebas,
            compromisos = compromisos,
            fundamento_legal = "Ley 1774 de 2016",
            plazo_dias_cumplimiento = plazoDias,
            funcionarios = funcionariosList
        )

        // Enviar a la API
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Obtener el token del SharedPreferences (asumiendo que se guarda así)
                val sharedPreferences = getSharedPreferences("PetVetericanoPrefs", MODE_PRIVATE)
                val token = sharedPreferences.getString("access_token", "") ?: ""

                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ActaAtencionActivity, "Token no encontrado. Por favor inicia sesión.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val response = RetrofitClient.apiService.crearSeguimientoPeticionVisita("Bearer $token", request)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ActaAtencionActivity, "Acta registrada exitosamente", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ActaAtencionActivity, "Error al guardar: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActaAtencionActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
