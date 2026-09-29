package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class editar_perfil : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    // M3 Opción A: estado original para dirty-check + foto pendiente de confirmar.
    private var nombreOriginal = ""
    private var apellidoOriginal = ""
    private var cargandoInicial = true
    private var fotoPendienteUri: Uri? = null
    private var guardando = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mostrarConfirmacionFoto(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        setupDarkMode()
        cargarDatosPerfil()
        setupMenuListeners()

        // M3: interceptar atrás del sistema si hay cambios sin guardar.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hayCambiosSinGuardar()) {
                    mostrarConfirmacionDescartar(
                        onDescartar = {
                            descartarCambios()
                            // Tras descartar ya no hay cambios: salir.
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
        // M3: no pisar lo que el usuario está editando al volver de otra pantalla.
        // Solo recargar si no hay edición en curso.
        if (!hayCambiosSinGuardar() && !guardando) {
            cargarDatosPerfil()
        }
    }

    private fun cargarDatosPerfil() {
        var nombre = prefs.getUserName().trim()
        var apellido = prefs.getUserLastName().trim()
        val email = prefs.getUserEmail().trim()
        val rutaFoto = prefs.getProfileImagePath().trim()

        // Caso 1: dato local migrado mal (bienvenida antigua guardaba "Nombre Apellido"
        // todo junto en USER_NAME y USER_LASTNAME quedaba vacio).
        if (apellido.isEmpty() && nombre.contains(" ")) {
            val partes = nombre.split("\\s+".toRegex(), limit = 2)
            nombre = partes[0]
            apellido = partes.getOrElse(1) { "" }
            // Persistimos ya separado para no volver a caer en lo mismo.
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, prefs.getUserEmail(), prefs.getUserPhone())
            }
            if (apellido.isNotEmpty()) {
                prefs.saveUserLastName(apellido)
            }
        } else if (apellido.isNotEmpty() && nombre.endsWith(apellido)) {
            // Caso 2: nombre traia el apellido pegado al final por error previo.
            nombre = nombre.removeSuffix(apellido).trim()
            if (nombre.isNotEmpty()) {
                prefs.saveUserData(nombre, prefs.getUserEmail(), prefs.getUserPhone())
            }
        }

        // Pintamos SIEMPRE desde prefs ya saneados (no desde lo visible,
        // que en primera carga aún está vacío y partía mal los datos).
        cargandoInicial = true
        binding.etName.setText(nombre)
        binding.etLastName.setText(apellido)
        if (email.isNotEmpty()) {
            binding.etEmail.setText(email)
        }

        // M3 Opción A: fijar baseline del dirty-check DESPUÉS de pintar.
        nombreOriginal = nombre
        apellidoOriginal = apellido
        binding.tilName.error = null
        binding.tilLastName.error = null
        cargandoInicial = false
        actualizarEstadoBotones()

        if (rutaFoto.isNotEmpty()) {
            val archivo = File(rutaFoto)
            if (archivo.exists()) {
                binding.ivProfile.setImageURI(Uri.fromFile(archivo))
            }
        }
    }

    // ---------- M3 Opción A: dirty-check + confirmar/descartar ----------

    private fun hayCambiosSinGuardar(): Boolean {
        if (cargandoInicial) return false
        val actualNombre = binding.etName.text.toString().trim()
        val actualApellido = binding.etLastName.text.toString().trim()
        return actualNombre != nombreOriginal || actualApellido != apellidoOriginal
    }

    private fun actualizarEstadoBotones() {
        if (guardando) {
            // M3: estado loading, ambos bloqueados.
            binding.btnGuardarPerfil.isEnabled = false
            binding.btnGuardarPerfil.text = "Guardando…"
            binding.btnDescartarPerfil.visibility = View.GONE
            return
        }
        val hayCambios = hayCambiosSinGuardar()
        // M3 Buttons: Filled solo habilitado si hay cambios (si no, se ve atenuado).
        binding.btnGuardarPerfil.isEnabled = hayCambios
        binding.btnGuardarPerfil.text = "Guardar Cambios"
        // M3: Descartar (Outlined) solo visible cuando hay algo que deshacer.
        binding.btnDescartarPerfil.visibility = if (hayCambios) View.VISIBLE else View.GONE
    }

    private fun descartarCambios() {
        cargandoInicial = true
        binding.etName.setText(nombreOriginal)
        binding.etLastName.setText(apellidoOriginal)
        binding.tilName.error = null
        binding.tilLastName.error = null
        fotoPendienteUri = null
        cargandoInicial = false
        actualizarEstadoBotones()
        Snackbar.make(binding.root, "Cambios descartados", Snackbar.LENGTH_SHORT).show()
    }

    private fun mostrarConfirmacionDescartar(onDescartar: (() -> Unit)? = null) {
        // M3 Basic dialog: acción destructiva leve -> Text button.
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
        val nuevoNombre = binding.etName.text.toString().trim()
        val nuevoApellido = binding.etLastName.text.toString().trim()
        if (!validarCampos(nuevoNombre, nuevoApellido)) return
        if (!hayCambiosSinGuardar()) return
        // M3: confirmar antes del PATCH mostrando qué va a cambiar.
        val resumen = "Nombre: $nombreOriginal → $nuevoNombre\nApellido: $apellidoOriginal → $nuevoApellido"
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Guardar cambios?")
            .setMessage("Se actualizará tu perfil:\n$resumen")
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Guardar") { dialog, _ ->
                dialog.dismiss()
                ejecutarGuardado(nuevoNombre, nuevoApellido)
            }
            .show()
    }

    private fun validarCampos(nuevoNombre: String, nuevoApellido: String): Boolean {
        // M3 Forms: error pegado al campo, no Toast genérico.
        var valido = true
        if (nuevoNombre.isEmpty()) {
            binding.tilName.error = "Ingresa tu nombre"
            valido = false
        } else binding.tilName.error = null

        if (nuevoApellido.isEmpty()) {
            binding.tilLastName.error = "Ingresa tu apellido"
            valido = false
        } else binding.tilLastName.error = null
        return valido
    }

    private fun guardarCambiosYVolver() {
        // Punto único de entrada: valida + confirma. El PATCH real está en ejecutarGuardado().
        mostrarConfirmacionGuardar()
    }

    private fun ejecutarGuardado(nuevoNombre: String, nuevoApellido: String) {
        // El correo es identidad de login: se muestra bloqueado y NO se envía al backend.
        // Evita doble tap / doble PATCH mientras guarda (M3: botón deshabilitado + loading).
        guardando = true
        actualizarEstadoBotones()

        val request = ActualizarPerfilRequest(
            nombre = nuevoNombre,
            apellido = nuevoApellido,
            email = prefs.getUserEmail()
        )

        lifecycleScope.launch {
            try {
                val token = prefs.getAccessToken()
                if (token.isNotEmpty()) {
                    RetrofitClient.authToken = token
                }

                val respuesta = RetrofitClient.apiService.actualizarPerfil(request)

                if (respuesta.isSuccessful) {
                    respuesta.body()?.let { perfil ->
                        prefs.saveUserData(perfil.nombre, perfil.email, prefs.getUserPhone())
                        prefs.saveUserLastName(perfil.apellido ?: nuevoApellido)
                    }
                    // M3: baseline nuevo = lo guardado, ya no hay dirty.
                    nombreOriginal = nuevoNombre
                    apellidoOriginal = nuevoApellido
                    guardando = false
                    actualizarEstadoBotones()
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

    private fun mostrarConfirmacionFoto(uri: Uri) {
        // M3 Opción A: preview sin persistir + confirmación antes de guardar la foto.
        fotoPendienteUri = uri
        try {
            binding.ivProfile.setImageURI(uri)
        } catch (_: Exception) { }
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Usar esta foto como perfil?")
            .setMessage("Se actualizará tu foto de perfil en este dispositivo.")
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                fotoPendienteUri = null
                // Revertir preview a la foto estable guardada.
                val rutaFoto = prefs.getProfileImagePath().trim()
                if (rutaFoto.isNotEmpty()) {
                    val archivo = File(rutaFoto)
                    if (archivo.exists()) binding.ivProfile.setImageURI(Uri.fromFile(archivo))
                }
            }
            .setPositiveButton("Confirmar") { dialog, _ ->
                dialog.dismiss()
                guardarYMostrarImagen(uri)
                fotoPendienteUri = null
            }
            .setOnCancelListener {
                fotoPendienteUri = null
                val rutaFoto = prefs.getProfileImagePath().trim()
                if (rutaFoto.isNotEmpty()) {
                    val archivo = File(rutaFoto)
                    if (archivo.exists()) binding.ivProfile.setImageURI(Uri.fromFile(archivo))
                }
            }
            .show()
    }

    private fun guardarYMostrarImagen(uri: Uri) {
        try {
            val archivoDestino = File(filesDir, "profile_picture.jpg")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(archivoDestino).use { output ->
                    input.copyTo(output)
                }
            }

            prefs.saveProfileImagePath(archivoDestino.absolutePath)
            binding.ivProfile.setImageURI(Uri.fromFile(archivoDestino))

            Toast.makeText(this, "Foto actualizada", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar la foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

        // M3: limpiar el error en cuanto el usuario corrige + re-evaluar dirty-check.
        binding.etName.doOnTextChanged { _, _, _, _ ->
            binding.tilName.error = null
            if (!cargandoInicial) actualizarEstadoBotones()
        }
        binding.etLastName.doOnTextChanged { _, _, _, _ ->
            binding.tilLastName.error = null
            if (!cargandoInicial) actualizarEstadoBotones()
        }
        binding.etLastName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                guardarCambiosYVolver()
                true
            } else false
        }

        binding.btnGuardarPerfil.setOnClickListener {
            guardarCambiosYVolver()
        }

        // M3 Outlined: revierte al baseline sin llamar al backend.
        binding.btnDescartarPerfil.setOnClickListener {
            mostrarConfirmacionDescartar()
        }

        binding.btnLogout.setOnClickListener {
            // Si hay edición sin guardar, avisar antes de cerrar sesión.
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
        // M3: las acciones con consecuencia (cerrar sesion) siempre piden confirmacion.
        AlertDialog.Builder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Tendrás que volver a iniciar sesión para usar la app.")
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Cerrar sesión") { dialog, _ ->
                dialog.dismiss()
                cerrarSesion()
            }
            .show()
    }

    private fun cerrarSesion() {
        // Limpieza completa: token + datos cacheados + token en memoria del interceptor.
        prefs.clearSession()
        RetrofitClient.authToken = null

        val intent = Intent(this, inicio_sesion::class.java).apply {
            // Limpia la pila: al dar atras ya no vuelve al menu, sale de la app.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

// cam