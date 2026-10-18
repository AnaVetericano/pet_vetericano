package com.example.petvetericano

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityOpcionesEditarPerfilBinding
import com.example.petvetericano.models.ActualizarPerfilRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpcionesEditarPerfilActivity : AppCompatActivity() {

    companion object {
        const val TITULO_PROFESIONAL_FIJO = "Médico Veterinario Zootecnista"
        private const val MENSAJE_RECOMENDACION_MATRICULA =
            "Recomendado: Ingresa tu matrícula profesional para validar actas"
    }

    private lateinit var binding: ActivityOpcionesEditarPerfilBinding
    private lateinit var prefs: SharedPreferencesManager

    private var fotoOriginal: String = ""
    private var fotoPendienteUri: Uri? = null
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

        setupListeners()
        cargarDatosPerfil()
        sincronizarConApi()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fotoPendienteUri != null && !guardando) {
                    mostrarConfirmacionSalirSinGuardar()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Acción de clic para el botón de regreso
        binding.btnBack.setOnClickListener {
            if (fotoPendienteUri != null && !guardando) {
                mostrarConfirmacionSalirSinGuardar()
            } else {
                finish()
            }
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
        val apellido = prefs.getUserLastName()
        val email = prefs.getUserEmail()
        val phone = prefs.getUserPhone()
        val identificacion = prefs.getUserIdentification()
        val matricula = prefs.getUserMatriculaProfesional()

        actualizarEncabezadoUsuario()

        binding.etName.setText(nombre)
        binding.etLastName.setText(apellido)
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

        // Cargar foto de perfil desde servidor/caché o mostrar icono default (@drawable/ic_profile_default)
        fotoOriginal = prefs.getProfileImagePath().trim()
        val uriPendiente = fotoPendienteUri
        if (uriPendiente != null) {
            ProfilePhotoHelper.previsualizarUri(this, binding.ivEditProfile, uriPendiente)
        } else {
            ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, fotoOriginal)
        }
    }

    private fun actualizarEncabezadoUsuario() {
        val nombre = prefs.getUserName().trim()
        val apellido = prefs.getUserLastName().trim()
        val nombreCompleto = if (apellido.isNotEmpty() && !nombre.contains(apellido)) "$nombre $apellido" else nombre
        binding.tvProfileUserName.text = nombreCompleto.ifEmpty { "Médico Veterinario" }
    }

    private fun previsualizarFotoSeleccionada(uri: Uri) {
        fotoPendienteUri = uri
        ProfilePhotoHelper.previsualizarUri(this, binding.ivEditProfile, uri)
        Snackbar.make(
            binding.root,
            "Vista previa lista. Presiona \"Guardar Cambios\" para confirmar.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun mostrarConfirmacionSalirSinGuardar() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Descartar cambios?")
            .setMessage("Seleccionaste una nueva foto de perfil que aún no has guardado.")
            .setNegativeButton("Seguir editando") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Descartar") { dialog, _ ->
                dialog.dismiss()
                fotoPendienteUri = null
                ProfilePhotoHelper.cargarFotoPerfil(this, binding.ivEditProfile, fotoOriginal)
                finish()
            }
            .show()
    }

    private fun guardarDatosPerfil() {
        val newName = binding.etName.text.toString().trim()
        val newLastName = binding.etLastName.text.toString().trim()
        val newEmail = binding.etEmail.text.toString().trim()
        val newPhone = binding.etPhone.text.toString().trim()
        val newIdentificacion = binding.etIdentificacion.text.toString().trim()
        val newMatricula = binding.etMatriculaProfesional.text.toString().trim()

        if (newName.isEmpty() || newEmail.isEmpty()) {
            Toast.makeText(this, "Completa los campos obligatorios (Nombre y Correo)", Toast.LENGTH_SHORT).show()
            return
        }

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

                val request = ActualizarPerfilRequest(
                    nombre = newName,
                    apellido = newLastName,
                    email = newEmail,
                    telefono = newPhone,
                    identificacion = newIdentificacion.ifEmpty { null },
                    matriculaProfesional = newMatricula.ifEmpty { null },
                    fotoPerfil = fotoBase64
                )
                val response = RetrofitClient.apiService.actualizarPerfil(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    prefs.saveUserData(
                        body?.nombre?.takeIf { it.isNotBlank() } ?: newName,
                        body?.email?.takeIf { it.isNotBlank() } ?: newEmail,
                        body?.telefono ?: newPhone
                    )
                    prefs.saveUserLastName(body?.apellido ?: newLastName)
                    prefs.saveUserIdentification(body?.identificacion ?: newIdentificacion)
                    prefs.saveUserMatriculaProfesional(body?.matriculaProfesional ?: newMatricula)

                    if (uriParaSubir != null || !body?.fotoPerfil.isNullOrEmpty()) {
                        val nuevaFotoRemota = body?.fotoPerfil?.trim()?.takeIf { it.isNotEmpty() }
                            ?: fotoBase64
                            ?: fotoOriginal
                        prefs.saveProfileImagePath(nuevaFotoRemota)
                        fotoOriginal = nuevaFotoRemota
                    }

                    fotoPendienteUri = null
                    guardando = false
                    actualizarEncabezadoUsuario()
                    binding.tvRolBadge.text = TITULO_PROFESIONAL_FIJO

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

                    if (!perfil.nombre.isNullOrEmpty()) {
                        binding.etName.setText(perfil.nombre)
                        prefs.saveUserData(perfil.nombre, prefs.getUserEmail(), prefs.getUserPhone())
                    }
                    if (!perfil.apellido.isNullOrEmpty()) {
                        binding.etLastName.setText(perfil.apellido)
                        prefs.saveUserLastName(perfil.apellido)
                    }
                    if (!perfil.email.isNullOrEmpty()) {
                        binding.etEmail.setText(perfil.email)
                        prefs.saveUserData(prefs.getUserName(), perfil.email, prefs.getUserPhone())
                    }
                    if (!perfil.telefono.isNullOrEmpty()) {
                        binding.etPhone.setText(perfil.telefono)
                        prefs.saveUserData(prefs.getUserName(), prefs.getUserEmail(), perfil.telefono)
                    }
                    if (!perfil.identificacion.isNullOrEmpty()) {
                        binding.etIdentificacion.setText(perfil.identificacion)
                        prefs.saveUserIdentification(perfil.identificacion)
                    }
                    if (!perfil.matriculaProfesional.isNullOrEmpty()) {
                        binding.etMatriculaProfesional.setText(perfil.matriculaProfesional)
                        prefs.saveUserMatriculaProfesional(perfil.matriculaProfesional)
                        binding.tilMatriculaProfesional.helperText = null
                    }

                    val fotoRemota = perfil.fotoPerfil?.trim() ?: ""
                    prefs.saveProfileImagePath(fotoRemota)
                    fotoOriginal = fotoRemota
                    if (fotoPendienteUri == null) {
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