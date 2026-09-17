package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityInicioSesionBinding
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class inicio_sesion : AppCompatActivity() {

    private lateinit var binding: ActivityInicioSesionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }

        binding.txtRegistrarse.setOnClickListener {
            val intent = Intent(this, Registro::class.java)
            startActivity(intent)
        }

        /*
         * ---------------------------------------------------------
         * NUEVA INTEGRACIÓN: ABRIR EL DIALOG DE RECUPERAR CONTRASEÑA
         * ---------------------------------------------------------
         */

        // Usamos binding.txtRecoverPassword para referenciar el TextView azul de tu XML.
        // .setOnClickListener se encarga de "escuchar" cuándo el usuario toca ese texto en la pantalla.
        // Si no usas .setOnClickListener, el texto será solo decorativo y tocarlo no hará nada.
        binding.txtRecoverPassword.setOnClickListener {

            // val define una variable inmutable (que no cambiará de valor después de ser asignada).
            // RecuperarContrasenia(this@inicio_sesion) crea una nueva instancia (un objeto real en memoria)
            // de la clase Dialog que creamos antes.
            // this@inicio_sesion le pasa el "Contexto" explícito de esta Activity. El contexto es
            // fundamental porque el Dialog necesita saber a qué pantalla "pertenece" para poder
            // dibujarse encima de ella y oscurecer el fondo correcto. Si pasaras un contexto nulo
            // o inválido, la aplicación sufriría un "Crash" (se cerraría de golpe) con una
            // excepción de tipo WindowManager.BadTokenException.
            val dialogRecuperar = RecuperarContrasenia(this@inicio_sesion)

            // .show() es el comando nativo de la clase Dialog que le dice al sistema operativo Android:
            // "Toma este Dialog que acabo de crear en memoria y hazlo visible en la pantalla del usuario ahora mismo".
            // Si omites la llamada a .show(), el objeto dialogRecuperar existirá en la memoria RAM del teléfono,
            // pero el usuario jamás verá la ventana blanca ni el fondo oscuro. Visualmente no pasará absolutamente nada.
            dialogRecuperar.show()
        }
    }

    private fun iniciarSesion() {

        // .trim() elimina los espacios en blanco al principio y al final del texto.
        // Si no se usa .trim() y el usuario escribe por error " correo@gmail.com ",
        // la API podría rechazar el login porque no coincide exactamente con el texto de la base de datos.
        val email = binding.editTextText.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.editTextText.error = "Ingrese su correo electrónico"
            binding.editTextText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.edtPassword.error = "Ingrese su contraseña"
            binding.edtPassword.requestFocus()
            return
        }

        val loginRequest = LoginRequest(
            email = email,
            password = password
        )

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.login(loginRequest)

                if (respuesta.isSuccessful) {
                    val datos = respuesta.body()

                    if (datos != null) {

                        Toast.makeText(
                            this@inicio_sesion,
                            datos.mensaje,
                            Toast.LENGTH_SHORT
                        ).show()

                        // ✅ NUEVO: guardar email y token en SharedPreferences
                        val prefs = SharedPreferencesManager(this@inicio_sesion)
                        prefs.saveUserData(
                            name  = "",          // el nombre lo completamos desde el dashboard
                            email = datos.email,
                            phone = ""
                        )
                        prefs.saveAccessToken(datos.tokens.access)

                        val intent = Intent(
                            this@inicio_sesion,
                            bienvenida::class.java
                        )
                        intent.putExtra("TOKEN", datos.tokens.access)
                        intent.putExtra("EMAIL", datos.email)
                        intent.putExtra("ID_ROL", datos.idRol)

                        // FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK limpia el historial de pantallas.
                        // Si no se usan estas flags, el usuario podría presionar el botón "Atrás" físico del celular
                        // estando ya dentro del dashboard y volvería accidentalmente a la pantalla de Login,
                        // lo cual es un fallo de seguridad y flujo de usuario.
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }

                } else {
                    Toast.makeText(
                        this@inicio_sesion,
                        "Correo o contraseña incorrectos",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@inicio_sesion,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}