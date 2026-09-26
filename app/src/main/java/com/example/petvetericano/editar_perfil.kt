package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class editar_perfil : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { guardarYMostrarImagen(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        setupDarkMode()
        cargarDatosPerfil()
        setupMenuListeners()
    }

    override fun onResume() {
        super.onResume()
        cargarDatosPerfil()
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
        binding.etName.setText(nombre)
        binding.etLastName.setText(apellido)
        if (email.isNotEmpty()) {
            binding.etEmail.setText(email)
        }

        if (rutaFoto.isNotEmpty()) {
            val archivo = File(rutaFoto)
            if (archivo.exists()) {
                binding.ivProfile.setImageURI(Uri.fromFile(archivo))
            }
        }
    }

    private fun guardarCambiosYVolver() {
        // El correo es identidad de login: se muestra bloqueado y NO se envía al backend.
        val nuevoNombre = binding.etName.text.toString().trim()
        val nuevoApellido = binding.etLastName.text.toString().trim()

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
        if (!valido) return

        // Evita doble tap / doble PATCH mientras guarda (M3: botón deshabilitado + loading).
        binding.btnGuardarPerfil.isEnabled = false

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
                    Snackbar.make(binding.root, "Perfil actualizado", Snackbar.LENGTH_SHORT).show()
                    binding.root.postDelayed({ irAMenuInicial() }, 900)
                } else {
                    Log.e("API_ERROR", "Error HTTP: ${respuesta.code()}")
                    Snackbar.make(binding.root, "No se pudo guardar. Reintenta", Snackbar.LENGTH_LONG)
                        .setAction("Reintentar") { guardarCambiosYVolver() }
                        .show()
                }

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error de red: ${e.message}")
                Snackbar.make(binding.root, "Sin conexión. No se guardó", Snackbar.LENGTH_LONG)
                    .setAction("Reintentar") { guardarCambiosYVolver() }
                    .show()
            } finally {
                binding.btnGuardarPerfil.isEnabled = true
            }
        }
    }

    private fun irAMenuInicial() {
        val intent = Intent(this, bienvenida::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
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

        // M3: limpiar el error en cuanto el usuario corrige.
        binding.etName.doOnTextChanged { _, _, _, _ -> binding.tilName.error = null }
        binding.etLastName.doOnTextChanged { _, _, _, _ -> binding.tilLastName.error = null }
        binding.etLastName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                guardarCambiosYVolver()
                true
            } else false
        }

        binding.btnGuardarPerfil.setOnClickListener {
            guardarCambiosYVolver()
        }

        binding.btnLogout.setOnClickListener {
            mostrarConfirmacionCerrarSesion()
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