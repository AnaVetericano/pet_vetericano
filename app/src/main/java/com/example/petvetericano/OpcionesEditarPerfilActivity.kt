package com.example.petvetericano

import android.net.Uri
import android.view.View
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityOpcionesEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpcionesEditarPerfilActivity : AppCompatActivity() {

    companion object {
        const val TITULO_PROFESIONAL_FIJO = "Médico Veterinario Zootecnista"
        private const val MENSAJE_RECOMENDACION_MATRICULA =
            "Recomendado: Ingresa tu matrícula profesional para validar actas"
        private const val MENSAJE_CEDULA_SENSIBLE =
            "No se puede modificar por ser un dato sensible"

        const val MIN_LENGTH_NOMBRE = 2
        const val MAX_LENGTH_NOMBRE = 50
        const val MIN_LENGTH_APELLIDO = 2
        const val MAX_LENGTH_APELLIDO = 50
        const val MAX_LENGTH_EMAIL = 100
        const val MAX_LENGTH_TELEFONO = 10
        const val MIN_LENGTH_MATRICULA = 4
        const val MAX_LENGTH_MATRICULA = 15
        const val MAX_LENGTH_IDENTIFICACION = 12

        private val REGEX_LETRAS_ESPACIOS = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$")
        private val REGEX_MATRICULA = Regex("^[a-zA-Z0-9-]+$")
    }

    private lateinit var binding: ActivityOpcionesEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    private var nombreOriginal: String = ""
    private var apellidoOriginal: String = ""
    private var emailOriginal: String = ""
    private var telefonoOriginal: String = ""
    private var matriculaOriginal: String = ""
    private var fotoOriginal: String = ""
    private var fotoPendienteUri: Uri? = null
    private var fotoEliminada: Boolean = false
    private var guardando: Boolean = false

    // Selector para previsualizar foto de perfil desde la galería; se guarda al presionar "Guardar Cambios"
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { previsualizarFotoSeleccionada(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOpcionesEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        configurarFiltrosYRestricciones()
        setupListeners()
        cargarDatosPerfil()
        sincronizarConApi()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tieneCambiosFotoPendiente() && !guardando) {
                    mostrarConfirmacionSalirSinGuardar()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Acción de clic para el botón de regreso
        binding.btnBack.setOnClickListener {
            if (tieneCambiosFotoPendiente() && !guardando) {
                mostrarConfirmacionSalirSinGuardar()
            } else {
                finish()
            }
        }

        // Acción de clic para cambiar o eliminar foto de perfil (PopupMenu compacto)
        binding.btnChangePhoto.setOnClickListener {
            ProfilePhotoHelper.mostrarMenuOpcionesFoto(
                context = this,
                anchorView = binding.btnChangePhoto,
                tieneFoto = tieneFotoVisible(),
                onCambiarFoto = { pickImageLauncher.launch("image/*") },
                onEliminarFoto = { eliminarFoto() }
            )
        }

        binding.ivEditProfile.setOnClickListener {
            binding.btnChangePhoto.performClick()
        }

        // Acción de clic para guardar la información ingresada (con diálogo MD3 de confirmación)
        binding.btnSave.setOnClickListener {
            mostrarConfirmacionGuardarDatosPerfil()
        }

        // Acción de clic para cerrar sesión del veterinario
        binding.btnLogout.setOnClickListener {
            mostrarConfirmacionCerrarSesion()
        }

        setupDarkMode()
    }

    /**
     * Inicializa y controla el switch de Modo Oscuro con persistencia y cambio reactivo en caliente.
     */
    private fun setupDarkMode() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("isDarkMode", isChecked).apply()

            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    /**
     * Configura los límites máximos de caracteres por cada tipo de dato
     * y bloquea la edición del campo Cédula/D.I. por tratarse de un dato sensible.
     */
    private fun configurarFiltrosYRestricciones() {
        val filtroSoloLetras = InputFilter { source, start, end, _, _, _ ->
            val sb = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c.isLetter() || c == ' ') {
                    sb.append(c)
                }
            }
            if (sb.length == end - start) null else sb.toString()
        }

        val filtroSoloDigitos = InputFilter { source, start, end, _, _, _ ->
            val sb = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c.isDigit()) {
                    sb.append(c)
                }
            }
            if (sb.length == end - start) null else sb.toString()
        }

        val filtroMatricula = InputFilter { source, start, end, _, _, _ ->
            val sb = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c.isLetterOrDigit() || c == '-') {
                    sb.append(c.uppercaseChar())
                }
            }
            sb.toString()
        }

        // Nombre y Apellido con exactamente el mismo filtro y límite (50 caracteres)
        binding.etName.filters = arrayOf(filtroSoloLetras, InputFilter.LengthFilter(MAX_LENGTH_NOMBRE))
        binding.etLastName.filters = arrayOf(filtroSoloLetras, InputFilter.LengthFilter(MAX_LENGTH_APELLIDO))
        binding.etEmail.filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH_EMAIL))
        binding.etPhone.filters = arrayOf(filtroSoloDigitos, InputFilter.LengthFilter(MAX_LENGTH_TELEFONO))
        binding.etMatriculaProfesional.filters = arrayOf(filtroMatricula, InputFilter.LengthFilter(MAX_LENGTH_MATRICULA))
        binding.etIdentificacion.filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH_IDENTIFICACION))

        // Bloquear estrictamente la cédula / documento de identidad por ser un dato sensible
        binding.tilIdentificacion.isEnabled = false
        binding.tilIdentificacion.helperText = MENSAJE_CEDULA_SENSIBLE
        binding.etIdentificacion.isEnabled = false
        binding.etIdentificacion.isFocusable = false
        binding.etIdentificacion.isClickable = false
        binding.etIdentificacion.isCursorVisible = false
        binding.etIdentificacion.keyListener = null
    }

    private fun setupListeners() {
        // Misma lógica de limpieza de errores para Nombre y Apellido
        binding.etName.doOnTextChanged { _, _, _, _ ->
            binding.tilName.error = null
        }
        binding.etLastName.doOnTextChanged { _, _, _, _ ->
            binding.tilLastName.error = null
        }
        binding.etEmail.doOnTextChanged { _, _, _, _ ->
            binding.tilEmail.error = null
        }
        binding.etPhone.doOnTextChanged { _, _, _, _ ->
            binding.tilPhone.error = null
        }

        // Mensaje dinámico: recomienda ingresar la matrícula y se oculta automáticamente cuando se escribe
        binding.etMatriculaProfesional.doOnTextChanged { text, _, _, _ ->
            binding.tilMatriculaProfesional.error = null
            if (text.isNullOrBlank()) {
                binding.tilMatriculaProfesional.helperText = MENSAJE_RECOMENDACION_MATRICULA
            } else {
                binding.tilMatriculaProfesional.helperText = null
            }
        }
    }

    private fun normalizarTextoPersona(valor: String, maxLength: Int): String {
        return valor.trim().replace("\\s+".toRegex(), " ").take(maxLength)
    }

    private fun normalizarTelefono(valor: String): String {
        val soloDigitos = valor.filter { it.isDigit() }
        val sinIndicativo = if (soloDigitos.length > MAX_LENGTH_TELEFONO && soloDigitos.startsWith("57")) {
            soloDigitos.removePrefix("57")
        } else {
            soloDigitos
        }
        return sinIndicativo.take(MAX_LENGTH_TELEFONO)
    }

    private fun cargarDatosPerfil() {
        var nombre = normalizarTextoPersona(prefs.getUserName(), MAX_LENGTH_NOMBRE)
        var apellido = normalizarTextoPersona(prefs.getUserLastName(), MAX_LENGTH_APELLIDO)
        val email = prefs.getUserEmail().trim().take(MAX_LENGTH_EMAIL)
        val phone = normalizarTelefono(prefs.getUserPhone())
        val identificacion = prefs.getUserIdentification().trim().take(MAX_LENGTH_IDENTIFICACION)
        val matricula = prefs.getUserMatriculaProfesional().trim().uppercase().take(MAX_LENGTH_MATRICULA)

        // Misma lógica de separación/limpieza entre Nombre y Apellido que en editar_perfil
        if (apellido.isEmpty() && nombre.contains(" ")) {
            val partes = nombre.split("\\s+".toRegex(), limit = 2)
            nombre = normalizarTextoPersona(partes[0], MAX_LENGTH_NOMBRE)
            apellido = normalizarTextoPersona(partes.getOrElse(1) { "" }, MAX_LENGTH_APELLIDO)
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, email, phone)
            }
            if (apellido.isNotEmpty()) {
                prefs.saveUserLastName(apellido)
            }
        } else if (apellido.isNotEmpty() && nombre.endsWith(apellido, ignoreCase = true) && nombre.length > apellido.length) {
            nombre = normalizarTextoPersona(nombre.substring(0, nombre.length - apellido.length), MAX_LENGTH_NOMBRE)
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, email, phone)
            }
        }

        actualizarEncabezadoUsuario()

        binding.etName.setText(nombre)
        binding.etLastName.setText(apellido)
        binding.etEmail.setText(email)
        binding.etPhone.setText(phone)
        binding.etIdentificacion.setText(identificacion)
        binding.etMatriculaProfesional.setText(matricula)

        nombreOriginal = nombre
        apellidoOriginal = apellido
        emailOriginal = email
        telefonoOriginal = phone
        matriculaOriginal = matricula

        // Estado inicial del mensaje de recomendación para la matrícula
        if (matricula.isNotBlank()) {
            binding.tilMatriculaProfesional.helperText = null
        } else {
            binding.tilMatriculaProfesional.helperText = MENSAJE_RECOMENDACION_MATRICULA
        }

        // Badge oficial superior
        binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO

        // Cargar foto de perfil desde servidor/caché o mostrar icono default (@drawable/ic_profile_default)
        fotoOriginal = prefs.getProfileImagePath().trim()
        val uriPendiente = fotoPendienteUri
        if (uriPendiente != null) {
            ProfilePhotoHelper.previsualizarUri(this, binding.ivEditProfile, uriPendiente)
        } else if (fotoEliminada) {
            ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, "")
        } else {
            ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, fotoOriginal)
        }
    }

    private fun actualizarEncabezadoUsuario() {
        val nombre = normalizarTextoPersona(prefs.getUserName(), MAX_LENGTH_NOMBRE)
        val apellido = normalizarTextoPersona(prefs.getUserLastName(), MAX_LENGTH_APELLIDO)
        val nombreCompleto = if (apellido.isNotEmpty() && !nombre.contains(apellido, ignoreCase = true)) {
            "$nombre $apellido"
        } else {
            nombre
        }
        binding.tvProfileUserName.text = nombreCompleto.ifEmpty { "Médico Veterinario" }
    }

    private fun tieneFotoVisible(): Boolean {
        return fotoPendienteUri != null || (!fotoEliminada && fotoOriginal.isNotEmpty())
    }

    private fun tieneCambiosFotoPendiente(): Boolean {
        return fotoPendienteUri != null || (fotoEliminada && fotoOriginal.isNotEmpty())
    }

    private fun previsualizarFotoSeleccionada(uri: Uri) {
        fotoPendienteUri = uri
        fotoEliminada = false
        ProfilePhotoHelper.previsualizarUri(this, binding.ivEditProfile, uri)
        Snackbar.make(
            binding.root,
            "Vista previa lista. Presiona \"Guardar Cambios\" para confirmar.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun eliminarFoto() {
        fotoPendienteUri = null
        fotoEliminada = true
        ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, "")
        Snackbar.make(
            binding.root,
            "Foto eliminada. Se mostrará la imagen por defecto.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun mostrarConfirmacionSalirSinGuardar() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Descartar cambios?")
            .setMessage("Tienes cambios en tu foto de perfil que aún no has guardado.")
            .setNegativeButton("Seguir editando") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Descartar") { dialog, _ ->
                dialog.dismiss()
                fotoPendienteUri = null
                fotoEliminada = false
                ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, fotoOriginal)
                finish()
            }
            .show()
    }

    /**
     * Valida Nombres y Apellidos con exactamente la misma regla de negocio.
     */
    private fun validarCampoNombreOApellido(
        valor: String,
        til: TextInputLayout,
        etiqueta: String,
        minLength: Int,
        maxLength: Int
    ): Boolean {
        return when {
            valor.isEmpty() -> {
                til.error = "Ingresa tus $etiqueta"
                false
            }
            valor.length < minLength -> {
                til.error = "Debe tener al menos $minLength caracteres"
                false
            }
            valor.length > maxLength -> {
                til.error = "No puede superar los $maxLength caracteres"
                false
            }
            !REGEX_LETRAS_ESPACIOS.matches(valor) -> {
                til.error = "Solo se permiten letras y espacios"
                false
            }
            else -> {
                til.error = null
                true
            }
        }
    }

    private fun validarCamposVeterinario(
        newName: String,
        newLastName: String,
        newEmail: String,
        newPhone: String,
        newMatricula: String
    ): Boolean {
        val nombreValido = validarCampoNombreOApellido(
            valor = newName,
            til = binding.tilName,
            etiqueta = "nombres",
            minLength = MIN_LENGTH_NOMBRE,
            maxLength = MAX_LENGTH_NOMBRE
        )

        val apellidoValido = validarCampoNombreOApellido(
            valor = newLastName,
            til = binding.tilLastName,
            etiqueta = "apellidos",
            minLength = MIN_LENGTH_APELLIDO,
            maxLength = MAX_LENGTH_APELLIDO
        )

        val emailValido = when {
            newEmail.isEmpty() -> {
                binding.tilEmail.error = "Ingresa tu correo institucional"
                false
            }
            newEmail.length > MAX_LENGTH_EMAIL || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> {
                binding.tilEmail.error = "Ingresa un correo electrónico válido (máx. $MAX_LENGTH_EMAIL caracteres)"
                false
            }
            else -> {
                binding.tilEmail.error = null
                true
            }
        }

        val telefonoValido = when {
            newPhone.isEmpty() -> {
                binding.tilPhone.error = "Ingresa tu teléfono de contacto"
                false
            }
            newPhone.length != MAX_LENGTH_TELEFONO || !newPhone.all { it.isDigit() } -> {
                binding.tilPhone.error = "El teléfono debe tener exactamente $MAX_LENGTH_TELEFONO dígitos"
                false
            }
            else -> {
                binding.tilPhone.error = null
                true
            }
        }

        val matriculaValida = when {
            newMatricula.isEmpty() -> {
                binding.tilMatriculaProfesional.error = null
                true
            }
            newMatricula.length < MIN_LENGTH_MATRICULA || newMatricula.length > MAX_LENGTH_MATRICULA -> {
                binding.tilMatriculaProfesional.error =
                    "La matrícula debe tener entre $MIN_LENGTH_MATRICULA y $MAX_LENGTH_MATRICULA caracteres"
                false
            }
            !REGEX_MATRICULA.matches(newMatricula) -> {
                binding.tilMatriculaProfesional.error = "Solo letras, números y guiones"
                false
            }
            else -> {
                binding.tilMatriculaProfesional.error = null
                true
            }
        }

        return nombreValido && apellidoValido && emailValido && telefonoValido && matriculaValida
    }

    private fun mostrarConfirmacionGuardarDatosPerfil() {
        val newName = normalizarTextoPersona(binding.etName.text?.toString() ?: "", MAX_LENGTH_NOMBRE)
        val newLastName = normalizarTextoPersona(binding.etLastName.text?.toString() ?: "", MAX_LENGTH_APELLIDO)
        val newEmail = (binding.etEmail.text?.toString() ?: "").trim().take(MAX_LENGTH_EMAIL)
        val newPhone = normalizarTelefono(binding.etPhone.text?.toString() ?: "")
        val newMatricula = (binding.etMatriculaProfesional.text?.toString() ?: "")
            .trim()
            .uppercase()
            .take(MAX_LENGTH_MATRICULA)

        if (!validarCamposVeterinario(newName, newLastName, newEmail, newPhone, newMatricula)) {
            Toast.makeText(this, "Revisa los campos marcados en el formulario", Toast.LENGTH_SHORT).show()
            return
        }

        val cambios = mutableListOf<CambioPerfilItem>()
        if (newName != nombreOriginal) {
            cambios.add(CambioPerfilItem("Nombres", nombreOriginal, newName))
        }
        if (newLastName != apellidoOriginal) {
            cambios.add(CambioPerfilItem("Apellidos", apellidoOriginal, newLastName))
        }
        if (newEmail != emailOriginal) {
            cambios.add(CambioPerfilItem("Correo institucional", emailOriginal, newEmail))
        }
        if (newPhone != telefonoOriginal) {
            cambios.add(CambioPerfilItem("Teléfono de contacto", telefonoOriginal, newPhone))
        }
        if (newMatricula != matriculaOriginal) {
            cambios.add(CambioPerfilItem("Matrícula profesional", matriculaOriginal, newMatricula))
        }
        if (fotoPendienteUri != null) {
            val estadoFotoAnterior = if (fotoOriginal.isNotEmpty()) "Foto actual" else "Sin foto"
            cambios.add(CambioPerfilItem("Foto de perfil", estadoFotoAnterior, "Nueva imagen seleccionada"))
        } else if (fotoEliminada && fotoOriginal.isNotEmpty()) {
            cambios.add(CambioPerfilItem("Foto de perfil", "Foto actual", "Eliminar (usar imagen por defecto)"))
        }

        if (cambios.isEmpty()) {
            Snackbar.make(binding.root, "No hay cambios nuevos por guardar", Snackbar.LENGTH_SHORT).show()
            return
        }

        ProfilePhotoHelper.mostrarDialogoGuardarCambiosMD3(
            activity = this,
            cambios = cambios,
            onConfirmarGuardar = {
                ejecutarGuardadoPerfilVeterinario(newName, newLastName, newEmail, newPhone, newMatricula)
            }
        )
    }

    private fun ejecutarGuardadoPerfilVeterinario(
        newName: String,
        newLastName: String,
        newEmail: String,
        newPhone: String,
        newMatricula: String
    ) {
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) {
            Toast.makeText(this, "Sesión no válida. Inicia sesión nuevamente.", Toast.LENGTH_SHORT).show()
            return
        }

        guardando = true
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Guardando..."
        RetrofitClient.authToken = token

        lifecycleScope.launch {
            try {
                val uriParaSubir = fotoPendienteUri
                val fotoBase64 = if (uriParaSubir != null) {
                    withContext(Dispatchers.IO) {
                        ProfilePhotoHelper.uriABase64DataUri(this@OpcionesEditarPerfilActivity, uriParaSubir)
                    }
                } else {
                    null
                }

                if (uriParaSubir != null && fotoBase64 == null) {
                    guardando = false
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Guardar Cambios"
                    Toast.makeText(this@OpcionesEditarPerfilActivity, "No se pudo procesar la imagen seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fotoParaEnviar = when {
                    uriParaSubir != null -> fotoBase64
                    fotoEliminada -> ""
                    else -> fotoOriginal.takeIf { it.isNotEmpty() }
                }

                // La cédula (identificacion) no se envía para modificación ya que es un dato sensible bloqueado
                val request = ActualizarPerfilRequest(
                    nombre = newName,
                    apellido = newLastName,
                    email = newEmail,
                    telefono = newPhone,
                    identificacion = null,
                    matriculaProfesional = newMatricula.ifEmpty { null },
                    fotoPerfil = fotoParaEnviar
                )
                val response = RetrofitClient.apiService.actualizarPerfil(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    val nombreGuardado = normalizarTextoPersona(
                        body?.nombre?.takeIf { it.isNotBlank() } ?: newName,
                        MAX_LENGTH_NOMBRE
                    )
                    val apellidoGuardado = normalizarTextoPersona(
                        body?.apellido?.takeIf { it.isNotBlank() } ?: newLastName,
                        MAX_LENGTH_APELLIDO
                    )
                    val emailGuardado = (body?.email?.takeIf { it.isNotBlank() } ?: newEmail)
                        .trim()
                        .take(MAX_LENGTH_EMAIL)
                    val telefonoGuardado = normalizarTelefono(
                        body?.telefono?.takeIf { it.isNotBlank() } ?: newPhone
                    )
                    val matriculaGuardada = (body?.matriculaProfesional ?: newMatricula)
                        .trim()
                        .uppercase()
                        .take(MAX_LENGTH_MATRICULA)

                    prefs.saveUserData(nombreGuardado, emailGuardado, telefonoGuardado)
                    prefs.saveUserLastName(apellidoGuardado)
                    if (!body?.identificacion.isNullOrBlank()) {
                        prefs.saveUserIdentification(body!!.identificacion!!.trim().take(MAX_LENGTH_IDENTIFICACION))
                    }
                    prefs.saveUserMatriculaProfesional(matriculaGuardada)

                    if (fotoEliminada) {
                        prefs.saveProfileImagePath("")
                        fotoOriginal = ""
                    } else if (uriParaSubir != null || !body?.fotoPerfil.isNullOrEmpty()) {
                        val nuevaFotoRemota = body?.fotoPerfil?.trim()?.takeIf { it.isNotEmpty() }
                            ?: fotoBase64
                            ?: fotoOriginal
                        prefs.saveProfileImagePath(nuevaFotoRemota)
                        fotoOriginal = nuevaFotoRemota
                    }

                    nombreOriginal = nombreGuardado
                    apellidoOriginal = apellidoGuardado
                    emailOriginal = emailGuardado
                    telefonoOriginal = telefonoGuardado
                    matriculaOriginal = matriculaGuardada
                    fotoPendienteUri = null
                    fotoEliminada = false
                    guardando = false
                    actualizarEncabezadoUsuario()
                    binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO
                    ProfilePhotoHelper.cargarFotoPerfil(
                        this@OpcionesEditarPerfilActivity,
                        binding.ivEditProfile,
                        fotoOriginal
                    )

                    Toast.makeText(this@OpcionesEditarPerfilActivity, "Perfil profesional actualizado con éxito", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: ""
                    Log.e("OpcionesPerfil", "Error API ${response.code()}: $errorMsg")
                    guardando = false
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "Guardar Cambios"
                    Toast.makeText(this@OpcionesEditarPerfilActivity, "Error al sincronizar con el servidor (${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("OpcionesPerfil", "Error sincronizando con API: ${e.message}", e)
                guardando = false
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "Guardar Cambios"
                Toast.makeText(this@OpcionesEditarPerfilActivity, "Sin conexión al servidor. Intenta nuevamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sincronizarConApi() {
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        RetrofitClient.authToken = token
        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.obtenerPerfil()
                if (respuesta.isSuccessful) {
                    val perfil = respuesta.body() ?: return@launch

                    val remotoNombre = normalizarTextoPersona(perfil.nombre ?: "", MAX_LENGTH_NOMBRE)
                    val remotoApellido = normalizarTextoPersona(perfil.apellido ?: "", MAX_LENGTH_APELLIDO)
                    val remotoEmail = (perfil.email ?: "").trim().take(MAX_LENGTH_EMAIL)
                    val remotoTelefono = normalizarTelefono(perfil.telefono ?: "")
                    val remotaIdentificacion = (perfil.identificacion ?: "").trim().take(MAX_LENGTH_IDENTIFICACION)
                    val remotaMatricula = (perfil.matriculaProfesional ?: "").trim().uppercase().take(MAX_LENGTH_MATRICULA)

                    if (remotoNombre.isNotEmpty()) {
                        binding.etName.setText(remotoNombre)
                        prefs.saveUserData(remotoNombre, prefs.getUserEmail(), prefs.getUserPhone())
                        nombreOriginal = remotoNombre
                    }
                    if (remotoApellido.isNotEmpty()) {
                        binding.etLastName.setText(remotoApellido)
                        prefs.saveUserLastName(remotoApellido)
                        apellidoOriginal = remotoApellido
                    }
                    if (remotoEmail.isNotEmpty()) {
                        binding.etEmail.setText(remotoEmail)
                        prefs.saveUserData(prefs.getUserName(), remotoEmail, prefs.getUserPhone())
                        emailOriginal = remotoEmail
                    }
                    if (remotoTelefono.isNotEmpty()) {
                        binding.etPhone.setText(remotoTelefono)
                        prefs.saveUserData(prefs.getUserName(), prefs.getUserEmail(), remotoTelefono)
                        telefonoOriginal = remotoTelefono
                    }
                    if (remotaIdentificacion.isNotEmpty()) {
                        binding.etIdentificacion.setText(remotaIdentificacion)
                        prefs.saveUserIdentification(remotaIdentificacion)
                    }
                    if (remotaMatricula.isNotEmpty()) {
                        binding.etMatriculaProfesional.setText(remotaMatricula)
                        prefs.saveUserMatriculaProfesional(remotaMatricula)
                        binding.tilMatriculaProfesional.helperText = null
                        matriculaOriginal = remotaMatricula
                    }

                    val fotoRemota = perfil.fotoPerfil?.trim() ?: ""
                    prefs.saveProfileImagePath(fotoRemota)
                    fotoOriginal = fotoRemota
                    if (fotoPendienteUri == null && !fotoEliminada) {
                        ProfilePhotoHelper.cargarFotoPerfil(
                            this@OpcionesEditarPerfilActivity,
                            binding.ivEditProfile,
                            fotoOriginal
                        )
                    }

                    binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO
                    actualizarEncabezadoUsuario()
                }
            } catch (e: Exception) {
                Log.d("OpcionesPerfil", "No se pudo sincronizar perfil remoto: ${e.message}")
            }
        }
    }

    private fun mostrarConfirmacionCerrarSesion() {
        ProfilePhotoHelper.mostrarDialogoCerrarSesionMD3(
            activity = this,
            emailUsuario = prefs.getUserEmail(),
            esVeterinario = true,
            onConfirmarCerrarSesion = { cerrarSesion() }
        )
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            GoogleAuthHelper.cerrarSesionGoogle(this@OpcionesEditarPerfilActivity)
            prefs.clearSession()
            RetrofitClient.authToken = null

            val intent = android.content.Intent(this@OpcionesEditarPerfilActivity, inicio_sesion::class.java).apply {
                putExtra("LOGOUT_MSG", "Sesión profesional cerrada correctamente. ¡Hasta pronto!")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
