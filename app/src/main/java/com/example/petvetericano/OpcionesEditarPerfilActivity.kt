package com.example.petvetericano

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityOpcionesEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class OpcionesEditarPerfilActivity : AppCompatActivity() {

    companion object {
        const val TITULO_PROFESIONAL_FIJO = "Médico Veterinario Zootecnista"
        private const val MENSAJE_RECOMENDACION_MATRICULA =
            "Recomendado: Ingresa tu matrícula profesional para validar actas"
    }

    private lateinit var binding: ActivityOpcionesEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    // Selector para cambiar foto de perfil desde la galería
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { guardarYMostrarImagen(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOpcionesEditarPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        setupListeners()
        cargarDatosPerfil()
        sincronizarConApi()

        // Acción de clic para el botón de regreso
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Acción de clic para cambiar foto de perfil
        binding.btnChangePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Acción de clic para guardar la información ingresada
        binding.btnSave.setOnClickListener {
            guardarDatosPerfil()
        }

        // Acción de clic para cerrar sesión del veterinario
        binding.btnLogout.setOnClickListener {
            mostrarConfirmacionCerrarSesion()
        }
    }

    private fun setupListeners() {
        // Mensaje dinámico: recomienda ingresar la matrícula y se oculta automáticamente cuando se escribe
        binding.etMatriculaProfesional.doOnTextChanged { text, _, _, _ ->
            if (text.isNullOrBlank()) {
                binding.tilMatriculaProfesional.helperText = MENSAJE_RECOMENDACION_MATRICULA
            } else {
                binding.tilMatriculaProfesional.helperText = null
            }
        }
    }

    private fun cargarDatosPerfil() {
        val nombre = prefs.getUserName()
        val email = prefs.getUserEmail()
        val phone = prefs.getUserPhone()
        val identificacion = prefs.getUserIdentification()
        val matricula = prefs.getUserMatriculaProfesional()

        actualizarEncabezadoUsuario()

        binding.etName.setText(nombre)
        binding.etEmail.setText(email)
        binding.etPhone.setText(phone)
        binding.etIdentificacion.setText(identificacion)
        binding.etMatriculaProfesional.setText(matricula)

        // Estado inicial del mensaje de recomendación para la matrícula
        if (matricula.isNotBlank()) {
            binding.tilMatriculaProfesional.helperText = null
        } else {
            binding.tilMatriculaProfesional.helperText = MENSAJE_RECOMENDACION_MATRICULA
        }

        // Badge oficial superior
        binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO

        // Cargar foto de perfil persistida si existe
        val rutaFoto = prefs.getProfileImagePath().trim()
        if (rutaFoto.isNotEmpty()) {
            val archivo = File(rutaFoto)
            if (archivo.exists()) {
                binding.ivEditProfile.setImageURI(Uri.fromFile(archivo))
            } else {
                binding.ivEditProfile.setImageResource(R.drawable.smithimg)
            }
        } else {
            binding.ivEditProfile.setImageResource(R.drawable.smithimg)
        }
    }

    private fun actualizarEncabezadoUsuario() {
        val nombre = prefs.getUserName().trim()
        val apellido = prefs.getUserLastName().trim()
        val nombreCompleto = if (apellido.isNotEmpty() && !nombre.contains(apellido)) "$nombre $apellido" else nombre
        binding.tvProfileUserName.text = nombreCompleto.ifEmpty { "Médico Veterinario" }
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
            binding.ivEditProfile.setImageURI(Uri.fromFile(archivoDestino))
            Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            binding.ivEditProfile.setImageURI(uri)
        }
    }

    private fun guardarDatosPerfil() {
        val newName = binding.etName.text.toString().trim()
        val newEmail = binding.etEmail.text.toString().trim()
        val newPhone = binding.etPhone.text.toString().trim()
        val newIdentificacion = binding.etIdentificacion.text.toString().trim()
        val newMatricula = binding.etMatriculaProfesional.text.toString().trim()

        if (newName.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(this, "Completa los campos obligatorios (Nombre y Correo)", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Persistir de inmediato en memoria local (SharedPreferencesManager)
        prefs.saveUserData(newName, newEmail, newPhone)
        prefs.saveUserIdentification(newIdentificacion)
        prefs.saveUserMatriculaProfesional(newMatricula)

        actualizarEncabezadoUsuario()
        binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO

        // 2. Sincronizar asíncronamente con el Backend / API
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isNotEmpty()) {
            RetrofitClient.authToken = token
            lifecycleScope.launch {
                try {
                    val request = ActualizarPerfilRequest(
                        nombre = newName,
                        apellido = prefs.getUserLastName(),
                        email = newEmail,
                        telefono = newPhone,
                        identificacion = newIdentificacion.ifEmpty { null },
                        matriculaProfesional = newMatricula.ifEmpty { null }
                    )
                    RetrofitClient.apiService.actualizarPerfil(request)
                } catch (e: Exception) {
                    Log.e("OpcionesPerfil", "Error sincronizando con API: ${e.message}")
                }
            }
        }

        Toast.makeText(this, "Perfil profesional actualizado con éxito", Toast.LENGTH_SHORT).show()
        finish()
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

                    if (binding.etName.text.isNullOrEmpty() && !perfil.nombre.isNullOrEmpty()) {
                        binding.etName.setText(perfil.nombre)
                        prefs.saveUserData(perfil.nombre, prefs.getUserEmail(), prefs.getUserPhone())
                    }
                    if (!perfil.apellido.isNullOrEmpty()) {
                        prefs.saveUserLastName(perfil.apellido)
                    }
                    if (binding.etEmail.text.isNullOrEmpty() && !perfil.email.isNullOrEmpty()) {
                        binding.etEmail.setText(perfil.email)
                        prefs.saveUserData(prefs.getUserName(), perfil.email, prefs.getUserPhone())
                    }
                    if (binding.etPhone.text.isNullOrEmpty() && !perfil.telefono.isNullOrEmpty()) {
                        binding.etPhone.setText(perfil.telefono)
                        prefs.saveUserData(prefs.getUserName(), prefs.getUserEmail(), perfil.telefono)
                    }
                    if (binding.etIdentificacion.text.isNullOrEmpty() && !perfil.identificacion.isNullOrEmpty()) {
                        binding.etIdentificacion.setText(perfil.identificacion)
                        prefs.saveUserIdentification(perfil.identificacion)
                    }
                    if (binding.etMatriculaProfesional.text.isNullOrEmpty() && !perfil.matriculaProfesional.isNullOrEmpty()) {
                        binding.etMatriculaProfesional.setText(perfil.matriculaProfesional)
                        prefs.saveUserMatriculaProfesional(perfil.matriculaProfesional)
                        binding.tilMatriculaProfesional.helperText = null
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
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Se cerrará la sesión actual de médico veterinario. Tendrás que ingresar tus credenciales nuevamente para acceder al sistema.")
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
        prefs.clearSession()
        RetrofitClient.authToken = null

        val intent = android.content.Intent(this, inicio_sesion::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}