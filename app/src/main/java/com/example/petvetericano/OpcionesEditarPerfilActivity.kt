package com.example.petvetericano

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityOpcionesEditarPerfilBinding


class OpcionesEditarPerfilActivity : AppCompatActivity() {

    // Declaración de la variable binding usando inicialización tardía (lateinit).
    // ActivityOpcionesEditarPerfilBinding es una clase autogenerada por Android Studio que mapea tu XML.
    // Si no usamos lateinit, Kotlin nos obligaría a inicializarla aquí mismo con un valor nulo, complicando el código con chequeos de nulos.
    // Si no usas ViewBinding en absoluto, tendrías que usar findViewById repetidamente, lo cual es más lento y propenso a crasheos.

    private lateinit var binding: ActivityOpcionesEditarPerfilBinding

    // Declaración del gestor que maneja la memoria local.
    private lateinit var prefs: SharedPreferencesManager

    // registerForActivityResult es la forma moderna de pedirle un resultado a otra aplicación (la galería).
    // ActivityResultContracts.GetContent() le indica al sistema que queremos obtener un contenido (archivo).
    // La variable 'uri' representa la ruta interna del archivo en el teléfono.
    // Usamos 'uri?.let' para asegurar que si el usuario abre la galería pero presiona atrás sin elegir nada (uri es null), el código dentro de las llaves no se ejecute. Si no usamos esto, la app se cerraría forzosamente al intentar cargar una imagen vacía.
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { binding.ivEditProfile.setImageURI(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // layoutInflater toma tu archivo XML y lo infla (lo convierte en objetos visuales reales en la memoria RAM).
        binding = ActivityOpcionesEditarPerfilBinding.inflate(layoutInflater)

        // setContentView proyecta esa vista ya construida en la pantalla del dispositivo.
        setContentView(binding.root)

        // Instanciamos la clase gestora pasándole 'this' (el contexto actual de la actividad).
        // Sin el contexto, SharedPreferences no tendría permisos para acceder a la carpeta de almacenamiento de la app.
        prefs = SharedPreferencesManager(this)

        // Extraemos los valores previamente guardados y los seteamos en el encabezado y EditText.
        val nombre = prefs.getUserName()
        val apellido = prefs.getUserLastName()
        val nombreCompleto = if (apellido.isNotEmpty()) "$nombre $apellido" else nombre
        binding.tvProfileUserName.text = nombreCompleto.ifEmpty { "Veterinario" }

        binding.etName.setText(prefs.getUserName())
        binding.etEmail.setText(prefs.getUserEmail())
        binding.etPhone.setText(prefs.getUserPhone())

        // Acción de clic para el botón de regreso.
        binding.btnBack.setOnClickListener {
            // finish() destruye esta pantalla, la saca de la pila de memoria y revela la pantalla que estaba detrás.
            finish()
        }

        // Acción de clic para el botón de la cámara.
        binding.btnChangePhoto.setOnClickListener {
            // .launch("image/*") ejecuta el contrato definido arriba. "image/*" es un filtro MIME que restringe el selector de archivos para que solo muestre imágenes, bloqueando PDFs o videos.
            pickImageLauncher.launch("image/*")
        }

        // Acción de clic para guardar la información ingresada.
        binding.btnSave.setOnClickListener {
            val newName = binding.etName.text.toString().trim()
            val newEmail = binding.etEmail.text.toString().trim()
            val newPhone = binding.etPhone.text.toString().trim()

            // Validación de seguridad. El operador || (OR) comprueba si el nombre O el correo están vacíos.
            if (newName.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Llamamos a la función del gestor para persistir los datos limpios y validados.
            prefs.saveUserData(newName, newEmail, newPhone)
            val apellidoActual = prefs.getUserLastName()
            binding.tvProfileUserName.text = if (apellidoActual.isNotEmpty()) "$newName $apellidoActual" else newName

            Toast.makeText(this, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()

            // Finaliza la actividad para regresar al usuario a su menú de manera automática tras un guardado exitoso.
            finish()
        }

        // Acción de clic para cerrar sesión del veterinario
        binding.btnLogout.setOnClickListener {
            mostrarConfirmacionCerrarSesion()
        }
    }

    private fun mostrarConfirmacionCerrarSesion() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
        com.example.petvetericano.network.RetrofitClient.authToken = null

        val intent = android.content.Intent(this, inicio_sesion::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}