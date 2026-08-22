package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.petvetericano.databinding.ActivityEditarPerfilBinding

class editar_perfil : AppCompatActivity() {

    private lateinit var binding: ActivityEditarPerfilBinding

    // Launcher para seleccionar nueva foto de perfil desde la galería
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { binding.ivProfile.setImageURI(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDarkMode()
        setupMenuListeners()
        setupBottomNavigation()
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
        // Cambiar foto de perfil desde galería
        binding.btnCamera.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnEditarPerfil.setOnClickListener {
            Toast.makeText(this, "Editar Perfil", Toast.LENGTH_SHORT).show()
        }

        binding.btnPermisos.setOnClickListener {
            Toast.makeText(this, "Permisos", Toast.LENGTH_SHORT).show()
        }

        binding.btnLenguaje.setOnClickListener {
            Toast.makeText(this, "Lenguaje", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            val intent = Intent(this, inicio_sesion::class.java)
            startActivity(intent)
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