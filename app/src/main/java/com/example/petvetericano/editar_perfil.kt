package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
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
        val nombre   = prefs.getUserName()
        val apellido = prefs.getUserLastName()
        val email    = prefs.getUserEmail()
        val rutaFoto = prefs.getProfileImagePath()

        // 💡 Limpiamos por si el nombre guardado localmente traía el apellido pegado por error previo
        val nombreLimPIO = if (apellido.isNotEmpty() && nombre.endsWith(apellido)) {
            nombre.replace(apellido, "").trim()
        } else {
            nombre
        }

        if (nombreLimPIO.isNotEmpty() && binding.etName.text.isNullOrEmpty()) {
            binding.etName.setText(nombreLimPIO)
        }
        if (apellido.isNotEmpty() && binding.etLastName.text.isNullOrEmpty()) {
            binding.etLastName.setText(apellido)
        }
        if (email.isNotEmpty() && binding.etEmail.text.isNullOrEmpty()) {
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
        val nuevoNombre   = binding.etName.text.toString().trim()
        val nuevoApellido = binding.etLastName.text.toString().trim()
        val nuevoEmail    = binding.etEmail.text.toString().trim()

        if (nuevoNombre.isEmpty() || nuevoApellido.isEmpty() || nuevoEmail.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ActualizarPerfilRequest(
            nombre = nuevoNombre,
            apellido = nuevoApellido,
            email = nuevoEmail
        )

        lifecycleScope.launch {
            try {
                val token = prefs.getAccessToken()
                if (token.isNotEmpty()) {
                    RetrofitClient.authToken = token
                }

                val respuesta = RetrofitClient.apiService.actualizarPerfil(request)

                if (respuesta.isSuccessful) {
                    // 💡 Guardamos cada dato de forma independiente y limpia
                    prefs.saveUserData(nuevoNombre, nuevoEmail, prefs.getUserPhone())
                    prefs.saveUserLastName(nuevoApellido)

                    Toast.makeText(this@editar_perfil, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("API_ERROR", "Error HTTP: ${respuesta.code()}")
                    prefs.saveUserData(nuevoNombre, nuevoEmail, prefs.getUserPhone())
                    prefs.saveUserLastName(nuevoApellido)
                    Toast.makeText(this@editar_perfil, "Guardado localmente", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error de red: ${e.message}")
                prefs.saveUserData(nuevoNombre, nuevoEmail, prefs.getUserPhone())
                prefs.saveUserLastName(nuevoApellido)
                Toast.makeText(this@editar_perfil, "Guardado localmente (Sin conexión)", Toast.LENGTH_SHORT).show()
            } finally {
                irAMenuInicial()
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