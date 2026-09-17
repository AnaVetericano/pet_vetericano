package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
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

        // Sincronizar el token guardado con RetrofitClient
        RetrofitClient.authToken = prefs.getAccessToken()

        setupDarkMode()
        cargarDatosPerfil()
        setupMenuListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        cargarDatosPerfil()
    }

    private fun cargarDatosPerfil() {
        val nombre   = prefs.getUserName()
        val email    = prefs.getUserEmail()
        val rutaFoto = prefs.getProfileImagePath()

        if (nombre.isNotEmpty() && binding.etName.text.isNullOrEmpty()) {
            binding.etName.setText(nombre)
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

    private fun guardarCambiosPerfil() {
        val nuevoNombre = binding.etName.text.toString().trim()
        val nuevoEmail  = binding.etEmail.text.toString().trim()

        if (nuevoNombre.isEmpty() || nuevoEmail.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Asegurar que Retrofit tenga el token más reciente antes de hacer la petición
        RetrofitClient.authToken = prefs.getAccessToken()

        val datosActualizados = mapOf(
            "nombre" to nuevoNombre,
            "email" to nuevoEmail
        )

        lifecycleScope.launch {
            try {
                // Petición PATCH gestionada por AuthInterceptor automáticamente
                val respuesta = RetrofitClient.apiService.actualizarPerfil(datosActualizados)

                if (respuesta.isSuccessful) {
                    val usuarioActualizado = respuesta.body()

                    prefs.saveUserData(
                        name  = usuarioActualizado?.nombre ?: nuevoNombre,
                        email = usuarioActualizado?.email ?: nuevoEmail,
                        phone = prefs.getUserPhone()
                    )

                    Toast.makeText(this@editar_perfil, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("API_ERROR", "Error al actualizar perfil: ${respuesta.code()}")
                    prefs.saveUserData(name = nuevoNombre, email = nuevoEmail, phone = prefs.getUserPhone())
                    Toast.makeText(this@editar_perfil, "Guardado localmente", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("API_ERROR", "Excepción de red: ${e.message}")
                prefs.saveUserData(name = nuevoNombre, email = nuevoEmail, phone = prefs.getUserPhone())
                Toast.makeText(this@editar_perfil, "Guardado localmente (Sin conexión)", Toast.LENGTH_SHORT).show()
            }
        }
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
            guardarCambiosPerfil()
        }

        binding.btnLogout.setOnClickListener {
            prefs.saveAccessToken("")
            RetrofitClient.authToken = null
            val intent = Intent(this, inicio_sesion::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupBottomNavigation() {
        binding.ivNavInicio.setOnClickListener {
            val intent = Intent(this, bienvenida::class.java)
            startActivity(intent)
            finish()
        }

        binding.ivNavDocumentos.setOnClickListener {
            Toast.makeText(this, "Documentos", Toast.LENGTH_SHORT).show()
        }

        binding.cardNavPrincipal.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.ivNavFavoritos.setOnClickListener {
            Toast.makeText(this, "Favoritos", Toast.LENGTH_SHORT).show()
        }

        binding.ivNavPerfil.setOnClickListener {
            Toast.makeText(this, "Ya estás en Perfil", Toast.LENGTH_SHORT).show()
        }
    }
}