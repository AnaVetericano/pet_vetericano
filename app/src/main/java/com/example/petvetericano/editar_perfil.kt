package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding
import java.io.File
import java.io.FileOutputStream

class editar_perfil : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    // Launcher para seleccionar nueva foto de perfil desde la galería
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
        setupBottomNavigation()
    }

    // Se ejecuta también al volver desde OpcionesEditarPerfilActivity
    override fun onResume() {
        super.onResume()
        cargarDatosPerfil()
    }

    // Muestra el nombre, correo y foto real del usuario autenticado
    private fun cargarDatosPerfil() {
        val nombre   = prefs.getUserName()
        val email    = prefs.getUserEmail()
        val rutaFoto = prefs.getProfileImagePath()

        if (nombre.isNotEmpty()) binding.tvName.text  = nombre
        if (email.isNotEmpty())  binding.tvEmail.text = email

        // Cargar foto guardada o dejar la imagen por defecto del XML
        if (rutaFoto.isNotEmpty()) {
            val archivo = File(rutaFoto)
            if (archivo.exists()) {
                binding.ivProfile.setImageURI(Uri.fromFile(archivo))
            }
        }
    }

    // Copia la imagen al almacenamiento interno y guarda la ruta
    private fun guardarYMostrarImagen(uri: Uri) {
        try {
            val archivoDestino = File(filesDir, "profile_picture.jpg")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(archivoDestino).use { output ->
                    input.copyTo(output)
                }
            }

            // Guardar la ruta en SharedPreferences para que persista
            prefs.saveProfileImagePath(archivoDestino.absolutePath)

            // Mostrar la imagen recién guardada en pantalla
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
            // Guardar la preferencia
            sharedPreferences.edit().putBoolean("isDarkMode", isChecked).apply()

            // Aplicar el tema inmediatamente en toda la app
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun setupMenuListeners() {
        // Cambiar foto de perfil desde galería
        binding.btnCamera.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Abre el formulario para modificar datos personales
        binding.btnEditarPerfil.setOnClickListener {
            val intent = Intent(this, OpcionesEditarPerfilActivity::class.java)
            startActivity(intent)
        }

        // Abre la pantalla de switches de permisos
        binding.btnPermisos.setOnClickListener {
            val intent = Intent(this, PermisosActivity::class.java)
            startActivity(intent)
        }

        // Cierra sesión y limpia el token guardado
        binding.btnLogout.setOnClickListener {
            prefs.saveAccessToken("")
            val intent = Intent(this, inicio_sesion::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupBottomNavigation() {
        // Redirige a la pantalla de bienvenida (Inicio)
        binding.ivNavInicio.setOnClickListener {
            val intent = Intent(this, bienvenida::class.java)
            startActivity(intent)
            finish()
        }

        binding.ivNavDocumentos.setOnClickListener {
            Toast.makeText(this, "Documentos", Toast.LENGTH_SHORT).show()
        }

        // Botón principal flotante para reportar petición
        binding.cardNavPrincipal.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.ivNavFavoritos.setOnClickListener {
            Toast.makeText(this, "Favoritos", Toast.LENGTH_SHORT).show()
        }

        // Ya se encuentra en la pantalla de Perfil
        binding.ivNavPerfil.setOnClickListener {
            Toast.makeText(this, "Ya estás en Perfil", Toast.LENGTH_SHORT).show()
        }
    }
}