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

        // Extraemos los valores previamente guardados y los seteamos en los EditText.
        // Si omites esto, el usuario verá los campos en blanco cada vez que entre a la pantalla, perdiendo la experiencia de edición.
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
            // .text obtiene el contenido escrito. .toString() lo convierte a cadena de texto.
            // .trim() elimina los espacios en blanco accidentales al inicio o al final del texto.
            // Si no usas .trim(), un espacio en blanco al final de un correo ("correo@gmail.com ") generaría un error de validación cuando intentes autenticar al usuario más adelante.
            val newName = binding.etName.text.toString().trim()
            val newEmail = binding.etEmail.text.toString().trim()
            val newPhone = binding.etPhone.text.toString().trim()

            // Validación de seguridad. El operador || (OR) comprueba si el nombre O el correo están vacíos.
            // .isEmpty() es más eficiente que evaluar si el string es igual a "".
            if (newName.isEmpty() || newEmail.isEmpty()) {
                // Toast.makeText crea un mensaje emergente nativo. Toast.LENGTH_SHORT indica que durará poco tiempo en pantalla.
                Toast.makeText(this, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                // return@setOnClickListener aborta la ejecución de este bloque de clic de forma inmediata.
                // Si no incluyes esto, el código seguiría su curso e intentaría guardar datos vacíos, sobrescribiendo información válida.
                return@setOnClickListener
            }

            // Llamamos a la función del gestor para persistir los datos limpios y validados.
            prefs.saveUserData(newName, newEmail, newPhone)

            Toast.makeText(this, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()

            // Finaliza la actividad para regresar al usuario a su menú de manera automática tras un guardado exitoso.
            finish()
        }
    }
}