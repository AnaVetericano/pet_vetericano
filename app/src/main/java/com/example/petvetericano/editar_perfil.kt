package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class editar_perfil : AppCompatActivity() {

    companion object {
        const val MIN_LENGTH_NOMBRE = 2
        const val MAX_LENGTH_NOMBRE = 50
        const val MIN_LENGTH_APELLIDO = 2
        const val MAX_LENGTH_APELLIDO = 50
        const val MAX_LENGTH_TELEFONO = 10
        const val MAX_LENGTH_EMAIL = 100

        private val REGEX_LETRAS_ESPACIOS = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$")
    }

    private lateinit var binding: ActivityEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    // Estado original para dirty-check + foto pendiente de guardar.
    private var nombreOriginal = ""
    private var apellidoOriginal = ""
    private var telefonoOriginal = ""
    private var fotoOriginal = ""
    private var cargandoInicial = true
    private var fotoPendienteUri: Uri? = null
    private var guardando = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { previsualizarFotoSeleccionada(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        configurarFiltrosDeCampos()
        setupDarkMode()
        cargarDatosPerfil()
        sincronizarConApi()
        setupMenuListeners()

        // Interceptar atrás del sistema si hay cambios sin guardar.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hayCambiosSinGuardar()) {
                    mostrarConfirmacionDescartar(
                        onDescartar = {
                            descartarCambios()
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    )
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!hayCambiosSinGuardar() && !guardando) {
            cargarDatosPerfil()
            sincronizarConApi()
        }
    }

    /**
     * Configura filtros estrictos de longitud y tipo de carácter para todos los campos.
     * Nombre y Apellido comparten exactamente la misma lógica y límite (50 caracteres).
     */
    private fun configurarFiltrosDeCampos() {
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

        binding.etName.filters = arrayOf(filtroSoloLetras, InputFilter.LengthFilter(MAX_LENGTH_NOMBRE))
        binding.etLastName.filters = arrayOf(filtroSoloLetras, InputFilter.LengthFilter(MAX_LENGTH_APELLIDO))
        binding.etPhone.filters = arrayOf(filtroSoloDigitos, InputFilter.LengthFilter(MAX_LENGTH_TELEFONO))
        binding.etEmail.filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH_EMAIL))

        binding.tilEmail.isEnabled = false
        binding.etEmail.isEnabled = false
        binding.etEmail.isFocusable = false
        binding.etEmail.isClickable = false
        binding.etEmail.isCursorVisible = false
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
        val telefono = normalizarTelefono(prefs.getUserPhone())
        val rutaFoto = prefs.getProfileImagePath().trim()

        if (apellido.isEmpty() && nombre.contains(" ")) {
            val partes = nombre.split("\\s+".toRegex(), limit = 2)
            nombre = normalizarTextoPersona(partes[0], MAX_LENGTH_NOMBRE)
            apellido = normalizarTextoPersona(partes.getOrElse(1) { "" }, MAX_LENGTH_APELLIDO)
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, email, telefono)
            }
            if (apellido.isNotEmpty()) {
                prefs.saveUserLastName(apellido)
            }
        } else if (apellido.isNotEmpty() && nombre.endsWith(apellido, ignoreCase = true) && nombre.length > apellido.length) {
            nombre = normalizarTextoPersona(nombre.substring(0, nombre.length - apellido.length), MAX_LENGTH_NOMBRE)
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, email, telefono)
            }
        }

        cargandoInicial = true
        binding.etName.setText(nombre)
        binding.etLastName.setText(apellido)
        binding.etPhone.setText(telefono)
        if (email.isNotEmpty()) {
            binding.etEmail.setText(email)
        }

        nombreOriginal = nombre
        apellidoOriginal = apellido
        telefonoOriginal = telefono
        fotoOriginal = rutaFoto

        binding.tilName.error = null
        binding.tilLastName.error = null
        binding.tilPhone.error = null
        cargandoInicial = false
        actualizarEstadoBotones()

        // Mostrar foto previsualizada si hay una pendiente; de lo contrario la guardada en servidor o la default
        val uriPendiente = fotoPendienteUri
        if (uriPendiente != null) {
            ProfilePhotoHelper.previsualizarUri(this, binding.ivProfile, uriPendiente)
        } else {
            ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivProfile, fotoOriginal)
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
                    val teniaCambios = hayCambiosSinGuardar()

                    val remotoNombre = normalizarTextoPersona(perfil.nombre ?: "", MAX_LENGTH_NOMBRE)
                    val remotoApellido = normalizarTextoPersona(perfil.apellido ?: "", MAX_LENGTH_APELLIDO)
                    val remotoEmail = (perfil.email ?: "").trim().take(MAX_LENGTH_EMAIL)
                    val remotoTelefono = normalizarTelefono(perfil.telefono ?: "")
                    val remotaFoto = perfil.fotoPerfil?.trim() ?: ""

                    if (remotoNombre.isNotEmpty()) {
                        prefs.saveUserData(
                            remotoNombre,
                            remotoEmail.ifEmpty { prefs.getUserEmail() },
                            remotoTelefono.ifEmpty { prefs.getUserPhone() }
                        )
                    }
                    if (remotoApellido.isNotEmpty()) {
                        prefs.saveUserLastName(remotoApellido)
                    }
                    prefs.saveProfileImagePath(remotaFoto)
                    fotoOriginal = remotaFoto

                    if (!teniaCambios && !guardando) {
                        cargarDatosPerfil()
                    }
                }
            } catch (e: Exception) {
                Log.d("EditarPerfil", "No se pudo sincronizar perfil remoto: ${e.message}")
            }
        }
    }

    private fun previsualizarFotoSeleccionada(uri: Uri) {
        fotoPendienteUri = uri
        ProfilePhotoHelper.previsualizarUri(this, binding.ivProfile, uri)
        actualizarEstadoBotones()
        Snackbar.make(
            binding.root,
            "Vista previa lista. Presiona \"Guardar Cambios\" para confirmar.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun hayCambiosSinGuardar(): Boolean {
        if (cargandoInicial) return false
        val actualNombre = normalizarTextoPersona(binding.etName.text?.toString() ?: "", MAX_LENGTH_NOMBRE)
        val actualApellido = normalizarTextoPersona(binding.etLastName.text?.toString() ?: "", MAX_LENGTH_APELLIDO)
        val actualTelefono = normalizarTelefono(binding.etPhone.text?.toString() ?: "")
        return actualNombre != nombreOriginal ||
                actualApellido != apellidoOriginal ||
                actualTelefono != telefonoOriginal ||
                fotoPendienteUri != null
    }

    private fun actualizarEstadoBotones() {
        if (guardando) {
            binding.btnGuardarPerfil.isEnabled = false
            binding.btnGuardarPerfil.text = "Guardando…"
            binding.btnDescartarPerfil.visibility = View.GONE
            return
        }
        val hayCambios = hayCambiosSinGuardar()
        binding.btnGuardarPerfil.isEnabled = hayCambios
        binding.btnGuardarPerfil.text = "Guardar Cambios"
        binding.btnDescartarPerfil.visibility = if (hayCambios) View.VISIBLE else View.GONE
    }

    private fun descartarCambios() {
        cargandoInicial = true
        binding.etName.setText(nombreOriginal)
        binding.etLastName.setText(apellidoOriginal)
        binding.etPhone.setText(telefonoOriginal)
        binding.tilName.error = null
        binding.tilLastName.error = null
        binding.tilPhone.error = null
        fotoPendienteUri = null
        ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivProfile, fotoOriginal)
        cargandoInicial = false
        actualizarEstadoBotones()
        Snackbar.make(binding.root, "Cambios descartados", Snackbar.LENGTH_SHORT).show()
    }

    private fun mostrarConfirmacionDescartar(onDescartar: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Descartar cambios?")
            .setMessage("Tienes cambios sin guardar. Se perderán si sales.")
            .setNegativeButton("Seguir editando") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Descartar") { dialog, _ ->
                dialog.dismiss()
                if (onDescartar != null) onDescartar() else descartarCambios()
            }
            .show()
    }

    private fun mostrarConfirmacionGuardar() {
        val nuevoNombre = normalizarTextoPersona(binding.etName.text?.toString() ?: "", MAX_LENGTH_NOMBRE)
        val nuevoApellido = normalizarTextoPersona(binding.etLastName.text?.toString() ?: "", MAX_LENGTH_APELLIDO)
        val nuevoTelefono = normalizarTelefono(binding.etPhone.text?.toString() ?: "")

        if (!validarCampos(nuevoNombre, nuevoApellido, nuevoTelefono)) return
        if (!hayCambiosSinGuardar()) return

        val cambios = mutableListOf<CambioPerfilItem>()
        if (nuevoNombre != nombreOriginal) {
            cambios.add(CambioPerfilItem("Nombre", nombreOriginal, nuevoNombre))
        }
        if (nuevoApellido != apellidoOriginal) {
            cambios.add(CambioPerfilItem("Apellido", apellidoOriginal, nuevoApellido))
        }
        if (nuevoTelefono != telefonoOriginal) {
            cambios.add(CambioPerfilItem("Teléfono", telefonoOriginal, nuevoTelefono))
        }
        if (fotoPendienteUri != null) {
            val estadoFotoAnterior = if (fotoOriginal.isNotEmpty()) "Foto actual" else "Sin foto"
            cambios.add(CambioPerfilItem("Foto de perfil", estadoFotoAnterior, "Nueva imagen seleccionada"))
        }

        ProfilePhotoHelper.mostrarDialogoGuardarCambiosMD3(
            activity = this,
            cambios = cambios,
            onConfirmarGuardar = {
                ejecutarGuardado(nuevoNombre, nuevoApellido, nuevoTelefono)
            }
        )
    }

    /**
     * Valida Nombre y Apellido aplicando exactamente la misma lógica de negocio a ambos.
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
                til.error = "Ingresa tu $etiqueta"
                false
            }
            valor.length < minLength -> {
                til.error = "El $etiqueta debe tener al menos $minLength caracteres"
                false
            }
            valor.length > maxLength -> {
                til.error = "El $etiqueta no puede superar los $maxLength caracteres"
                false
            }
            !REGEX_LETRAS_ESPACIOS.matches(valor) -> {
                til.error = "El $etiqueta solo debe contener letras y espacios"
                false
            }
            else -> {
                til.error = null
                true
            }
        }
    }

    private fun validarCampos(nuevoNombre: String, nuevoApellido: String, nuevoTelefono: String): Boolean {
        val nombreValido = validarCampoNombreOApellido(
            valor = nuevoNombre,
            til = binding.tilName,
            etiqueta = "nombre",
            minLength = MIN_LENGTH_NOMBRE,
            maxLength = MAX_LENGTH_NOMBRE
        )

        val apellidoValido = validarCampoNombreOApellido(
            valor = nuevoApellido,
            til = binding.tilLastName,
            etiqueta = "apellido",
            minLength = MIN_LENGTH_APELLIDO,
            maxLength = MAX_LENGTH_APELLIDO
        )

        val telefonoValido = when {
            nuevoTelefono.isEmpty() -> {
                binding.tilPhone.error = "Ingresa tu número de teléfono"
                false
            }
            nuevoTelefono.length != MAX_LENGTH_TELEFONO || !nuevoTelefono.all { it.isDigit() } -> {
                binding.tilPhone.error = "El teléfono debe tener exactamente $MAX_LENGTH_TELEFONO dígitos"
                false
            }
            else -> {
                binding.tilPhone.error = null
                true
            }
        }

        return nombreValido && apellidoValido && telefonoValido
    }

    private fun guardarCambiosYVolver() {
        mostrarConfirmacionGuardar()
    }

    private fun ejecutarGuardado(nuevoNombre: String, nuevoApellido: String, nuevoTelefono: String) {
        guardando = true
        actualizarEstadoBotones()

        lifecycleScope.launch {
            try {
                val token = prefs.getAccessToken()
                if (token.isNotEmpty()) {
                    RetrofitClient.authToken = token
                }

                val uriParaSubir = fotoPendienteUri
                val fotoBase64 = if (uriParaSubir != null) {
                    withContext(Dispatchers.IO) {
                        ProfilePhotoHelper.uriABase64DataUri(this@editar_perfil, uriParaSubir)
                    }
                } else {
                    null
                }

                if (uriParaSubir != null && fotoBase64 == null) {
                    guardando = false
                    actualizarEstadoBotones()
                    Snackbar.make(binding.root, "No se pudo procesar la imagen seleccionada", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val request = ActualizarPerfilRequest(
                    nombre = nuevoNombre,
                    apellido = nuevoApellido,
                    email = prefs.getUserEmail().take(MAX_LENGTH_EMAIL),
                    telefono = nuevoTelefono,
                    fotoPerfil = fotoBase64
                )

                val respuesta = RetrofitClient.apiService.actualizarPerfil(request)

                if (respuesta.isSuccessful) {
                    val body = respuesta.body()
                    val nombreGuardado = normalizarTextoPersona(
                        body?.nombre?.takeIf { it.isNotBlank() } ?: nuevoNombre,
                        MAX_LENGTH_NOMBRE
                    )
                    val apellidoGuardado = normalizarTextoPersona(
                        body?.apellido?.takeIf { it.isNotBlank() } ?: nuevoApellido,
                        MAX_LENGTH_APELLIDO
                    )
                    val telefonoGuardado = normalizarTelefono(
                        body?.telefono?.takeIf { it.isNotBlank() } ?: nuevoTelefono
                    )

                    prefs.saveUserData(nombreGuardado, prefs.getUserEmail(), telefonoGuardado)
                    prefs.saveUserLastName(apellidoGuardado)

                    if (uriParaSubir != null || !body?.fotoPerfil.isNullOrEmpty()) {
                        val nuevaFotoRemota = body?.fotoPerfil?.trim()?.takeIf { it.isNotEmpty() }
                            ?: fotoBase64
                            ?: fotoOriginal
                        prefs.saveProfileImagePath(nuevaFotoRemota)
                        fotoOriginal = nuevaFotoRemota
                    }

                    nombreOriginal = nombreGuardado
                    apellidoOriginal = apellidoGuardado
                    telefonoOriginal = telefonoGuardado
                    fotoPendienteUri = null
                    guardando = false
                    actualizarEstadoBotones()
                    ProfilePhotoHelper.cargarFotoPerfil(this@editar_perfil, binding.ivProfile, fotoOriginal)
                    Snackbar.make(binding.root, "Perfil actualizado", Snackbar.LENGTH_SHORT).show()
                    binding.root.postDelayed({ irAMenuInicial() }, 900)
                } else {
                    Log.e("API_ERROR", "Error HTTP: ${respuesta.code()}")
                    guardando = false
                    actualizarEstadoBotones()
                    Snackbar.make(binding.root, "No se pudo guardar. Reintenta", Snackbar.LENGTH_LONG)
                        .setAction("Reintentar") { mostrarConfirmacionGuardar() }
                        .show()
                }

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error de red: ${e.message}")
                guardando = false
                actualizarEstadoBotones()
                Snackbar.make(binding.root, "Sin conexión. No se guardó", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { mostrarConfirmacionGuardar() }
                    .show()
            }
        }
    }

    private fun irAMenuInicial() {
        val intent = Intent(this, bienvenida::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

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

    private fun setupMenuListeners() {
        binding.btnCamera.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.etName.doOnTextChanged { _, _, _, _ ->
            binding.tilName.error = null
            if (!cargandoInicial) actualizarEstadoBotones()
        }
        binding.etLastName.doOnTextChanged { _, _, _, _ ->
            binding.tilLastName.error = null
            if (!cargandoInicial) actualizarEstadoBotones()
        }
        binding.etPhone.doOnTextChanged { _, _, _, _ ->
            binding.tilPhone.error = null
            if (!cargandoInicial) actualizarEstadoBotones()
        }

        binding.etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                guardarCambiosYVolver()
                true
            } else false
        }

        binding.btnGuardarPerfil.setOnClickListener {
            guardarCambiosYVolver()
        }

        binding.btnDescartarPerfil.setOnClickListener {
            mostrarConfirmacionDescartar()
        }

        binding.btnLogout.setOnClickListener {
            if (hayCambiosSinGuardar()) {
                mostrarConfirmacionDescartar(
                    onDescartar = { mostrarConfirmacionCerrarSesion() }
                )
            } else {
                mostrarConfirmacionCerrarSesion()
            }
        }
    }

    private fun mostrarConfirmacionCerrarSesion() {
        ProfilePhotoHelper.mostrarDialogoCerrarSesionMD3(
            activity = this,
            emailUsuario = prefs.getUserEmail(),
            esVeterinario = false,
            onConfirmarCerrarSesion = { cerrarSesion() }
        )
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            GoogleAuthHelper.cerrarSesionGoogle(this@editar_perfil)
            prefs.clearSession()
            RetrofitClient.authToken = null

            val intent = Intent(this@editar_perfil, inicio_sesion::class.java).apply {
                putExtra("LOGOUT_MSG", "Has cerrado sesión correctamente. ¡Hasta pronto!")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
