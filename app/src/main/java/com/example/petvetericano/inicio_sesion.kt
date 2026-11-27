package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityInicioSesionBinding
import android.util.Log
import com.example.petvetericano.facial.FacialAuthSessionHelper
import com.example.petvetericano.facial.FacialStorageManager
import com.example.petvetericano.facial.ReconocimientoFacialActivity
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.TokenData
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.realtime.WebSocketConnectionManager
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlin.math.ceil

class inicio_sesion : AppCompatActivity() {

    private lateinit var binding: ActivityInicioSesionBinding

    private val facialVerifyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                completarLoginConRostro()
            } else {
                val msg = result.data?.getStringExtra(ReconocimientoFacialActivity.EXTRA_RESULTADO_MENSAJE)
                if (!msg.isNullOrBlank()) {
                    GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, msg, esError = true)
                }
            }
        }

    companion object {
        const val ROL_VETERINARIO = 3
        private const val TAG = "inicio_sesion"

        internal fun resolverMensajeErrorHttp(code: Int, errorRaw: String?): String {
            if (code in 500..599) {
                return "Error del servidor ($code). Intenta nuevamente más tarde."
            }

            if (code == 404) {
                return "Servicio de inicio de sesión no disponible (404)."
            }

            val mensajeJson = extraerMensajeJsonSeguro(errorRaw)
            if (!mensajeJson.isNullOrEmpty()) {
                return mensajeJson
            }

            if (code == 429) {
                return "Cuenta bloqueada temporalmente por demasiados intentos fallidos. Intenta en 15 minutos."
            }

            return if (code == 400 || code == 401 || code == 403) {
                "Correo o contraseña incorrectos"
            } else {
                "No se pudo iniciar sesión (código $code)"
            }
        }

        internal fun resolverMensajeErrorExcepcion(e: Throwable): String {
            return when (e) {
                is SocketTimeoutException ->
                    "Tiempo de espera agotado al conectar con el servidor. Verifica tu conexión e intenta nuevamente."
                is IOException ->
                    "Error de conexión con el servidor. Verifica tu acceso a internet e intenta nuevamente."
                else ->
                    "No se pudo procesar la respuesta del servidor. Intenta nuevamente."
            }
        }

        private fun extraerMensajeJsonSeguro(errorRaw: String?): String? {
            if (errorRaw.isNullOrBlank()) return null
            val trimmed = errorRaw.trim()
            if (!trimmed.startsWith("{")) return null

            return try {
                val json = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
                val candidatos = listOfNotNull(
                    extraerPrimerTextoArray(json, "non_field_errors"),
                    extraerTextoCampo(json, "detail"),
                    extraerTextoCampo(json, "error"),
                    extraerTextoCampo(json, "mensaje"),
                    extraerPrimerTextoArray(json, "email"),
                    extraerPrimerTextoArray(json, "password")
                )
                candidatos
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && it != "null" }
                    ?.take(200)
            } catch (_: Exception) {
                null
            }
        }

        private fun extraerTextoCampo(json: com.google.gson.JsonObject, clave: String): String? {
            val el = json.get(clave) ?: return null
            return if (el.isJsonPrimitive) el.asString else null
        }

        private fun extraerPrimerTextoArray(json: com.google.gson.JsonObject, clave: String): String? {
            val el = json.get(clave) ?: return null
            if (!el.isJsonArray) return null
            val arr = el.asJsonArray
            if (arr.size() == 0) return null
            val first = arr.get(0)
            return if (first != null && first.isJsonPrimitive) first.asString else null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInicioSesionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val emailRegistrado = intent.getStringExtra("EMAIL_REGISTRADO")
        if (!emailRegistrado.isNullOrEmpty()) {
            binding.editTextText.setText(emailRegistrado)
        }

        val mensajeRegistro = intent.getStringExtra("REGISTRO_GOOGLE_MSG")
            ?: intent.getStringExtra("LOGOUT_MSG")
        if (!mensajeRegistro.isNullOrEmpty()) {
            binding.root.post {
                GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, mensajeRegistro, esError = false)
            }
        }

        binding.btnIniciarSesion.setOnClickListener {
            iniciarSesion()
        }

        binding.btnGoogleLogin.setOnClickListener {
            iniciarSesionConGoogle()
        }

        binding.btnFacialLogin.setOnClickListener {
            iniciarSesionConRostro()
        }

        binding.txtRegistrarse.setOnClickListener {
            val intent = Intent(this, Registro::class.java)
            startActivity(intent)
        }
        binding.txtRecoverPassword.setOnClickListener {
            val correoActual = binding.editTextText.text?.toString()?.trim().orEmpty()
            val dialogRecuperar = RecuperarContrasenia(this@inicio_sesion, emailInicial = correoActual)
            dialogRecuperar.show()
        }
    }

    private fun iniciarSesionConGoogle() {
        lifecycleScope.launch {
            GoogleAuthHelper.autenticarConGoogle(
                activity = this@inicio_sesion,
                esFlujoRegistro = false,
                onEstadoCarga = { cargando ->
                    if (!isFinishing && !isDestroyed) {
                        binding.btnGoogleLogin.isEnabled = !cargando
                        binding.btnIniciarSesion.isEnabled = !cargando
                        binding.btnFacialLogin.isEnabled = !cargando
                        binding.btnGoogleLogin.text = if (cargando) {
                            "Conectando con Google..."
                        } else {
                            getString(R.string.continuar_con_google)
                        }
                    }
                }
            )
        }
    }

    private fun iniciarSesionConRostro() {
        val storage = FacialStorageManager(this)
        val perfil = storage.obtenerPerfilFacial()
        if (perfil == null || perfil.refreshToken.isBlank()) {
            GoogleAuthHelper.mostrarMensajeMD3(
                this,
                getString(R.string.facial_no_configurado),
                esError = false
            )
            return
        }

        val bloqueoMs = storage.obtenerBloqueoRestanteMs()
        if (bloqueoMs > 0L) {
            val seg = ceil(bloqueoMs / 1000.0).toInt().coerceAtLeast(1)
            GoogleAuthHelper.mostrarMensajeMD3(
                this,
                getString(R.string.facial_error_bloqueo, seg),
                esError = true
            )
            return
        }

        val emailIngresado = binding.editTextText.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (emailIngresado.isNotEmpty() && emailIngresado != perfil.email.trim().lowercase(Locale.ROOT)) {
            GoogleAuthHelper.mostrarMensajeMD3(
                this,
                getString(R.string.facial_cuenta_distinta, FacialStorageManager.enmascararCorreo(perfil.email)),
                esError = true
            )
            return
        }

        facialVerifyLauncher.launch(ReconocimientoFacialActivity.crearIntentVerificar(this))
    }

    private fun completarLoginConRostro() {
        val perfil = FacialStorageManager(this).obtenerPerfilFacial()
        if (perfil == null) {
            GoogleAuthHelper.mostrarMensajeMD3(
                this,
                getString(R.string.facial_no_configurado),
                esError = true
            )
            return
        }

        binding.btnIniciarSesion.isEnabled = false
        binding.btnGoogleLogin.isEnabled = false
        binding.btnFacialLogin.isEnabled = false
        binding.btnFacialLogin.text = getString(R.string.facial_verificando_servidor)

        lifecycleScope.launch {
            try {
                when (val resultado = FacialAuthSessionHelper.completarSesionConPerfilVerificado(this@inicio_sesion, perfil)) {
                    is FacialAuthSessionHelper.ResultadoLoginFacial.Exito -> {
                        val rolStr = if (resultado.idRol == ROL_VETERINARIO) "Médico Veterinario" else "Ciudadano"
                        val nombreMostrar = resultado.nombre.trim()
                        val saludo = if (nombreMostrar.isNotEmpty()) {
                            "¡Hola, $rolStr $nombreMostrar! Bienvenido."
                        } else {
                            "¡Hola, $rolStr! Bienvenido."
                        }
                        Toast.makeText(this@inicio_sesion, saludo, Toast.LENGTH_SHORT).show()

                        val destino = if (resultado.idRol == ROL_VETERINARIO) {
                            Intent(this@inicio_sesion, dahsboard_veterianrio::class.java)
                        } else {
                            Intent(this@inicio_sesion, bienvenida::class.java)
                        }
                        destino.putExtra("TOKEN", resultado.accessToken)
                        destino.putExtra("EMAIL", resultado.email)
                        destino.putExtra("ID_ROL", resultado.idRol)
                        destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(destino)
                        finish()
                    }
                    is FacialAuthSessionHelper.ResultadoLoginFacial.RequiereLoginManual -> {
                        if (binding.editTextText.text.isNullOrBlank()) {
                            binding.editTextText.setText(perfil.email)
                        }
                        GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, resultado.mensaje, esError = true)
                    }
                    is FacialAuthSessionHelper.ResultadoLoginFacial.ErrorRed -> {
                        GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, resultado.mensaje, esError = true)
                    }
                }
            } finally {
                if (!isFinishing && !isDestroyed) {
                    binding.btnIniciarSesion.isEnabled = true
                    binding.btnGoogleLogin.isEnabled = true
                    binding.btnFacialLogin.isEnabled = true
                    binding.btnFacialLogin.setText(R.string.facial_btn_iniciar_sesion)
                }
            }
        }
    }

    private fun iniciarSesion() {
        val email = binding.editTextText.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        // Limpiar errores previos
        binding.editTextText.error = null
        binding.tilPassword.error = null

        if (email.isEmpty()) {
            binding.editTextText.error = "Ingrese su correo electrónico"
            binding.editTextText.requestFocus()
            return
        }

        if (!com.example.petvetericano.auth.AuthValidators.esEmailValido(email)) {
            binding.editTextText.error = "Ingrese un correo electrónico válido"
            binding.editTextText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese su contraseña"
            binding.edtPassword.requestFocus()
            return
        }

        val loginRequest = LoginRequest(email = email, password = password)
        binding.btnIniciarSesion.isEnabled = false
        binding.btnGoogleLogin.isEnabled = false
        binding.btnFacialLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                // Evita que un token previo vencido/inválido en memoria interfiera con /usuarios/login/
                RetrofitClient.authToken = null
                val respuesta = RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                    api.login(loginRequest)
                }

                if (respuesta.isSuccessful) {
                    val datos = respuesta.body()
                    val tokens: TokenData? = datos?.tokens
                    val accessToken = tokens?.access?.trim().orEmpty()
                    val refreshToken = tokens?.refresh?.trim().orEmpty()

                    if (datos != null && accessToken.isNotEmpty()) {
                        val rolStr = if (datos.idRol == ROL_VETERINARIO) "Médico Veterinario" else "Ciudadano"
                        val nombreMostrar = datos.nombre?.trim().orEmpty()
                        val saludo = if (nombreMostrar.isNotEmpty()) "¡Hola, $rolStr $nombreMostrar! Bienvenido." else "¡Hola, $rolStr! Bienvenido."
                        Toast.makeText(this@inicio_sesion, saludo, Toast.LENGTH_SHORT).show()

                        RetrofitClient.authToken = accessToken
                        SesionExpiradaManager.refreshMuerto = null

                        val prefs = SharedPreferencesManager(this@inicio_sesion)
                        prefs.saveUserData(
                            name  = datos.nombre ?: "",
                            email = datos.email,
                            phone = datos.telefono?.trim().orEmpty()
                        )
                        prefs.saveUserLastName(datos.apellido ?: "")
                        if (!datos.matriculaProfesional.isNullOrBlank()) {
                            prefs.saveUserMatriculaProfesional(datos.matriculaProfesional)
                        }
                        prefs.saveProfileImagePath(datos.fotoPerfil ?: datos.foto ?: "")
                        prefs.saveAccessToken(accessToken)
                        if (refreshToken.isNotEmpty()) {
                            prefs.saveRefreshToken(refreshToken)
                            FacialStorageManager(this@inicio_sesion).actualizarRefreshTokenSiCoincide(
                                userId = datos.idUsuario,
                                nuevoRefreshToken = refreshToken,
                                nombre = datos.nombre,
                                apellido = datos.apellido,
                                fotoPerfil = datos.fotoPerfil ?: datos.foto,
                                telefono = datos.telefono,
                                matriculaProfesional = datos.matriculaProfesional
                            )
                            com.example.petvetericano.auth.GoogleLinkedSessionVault(this@inicio_sesion)
                                .actualizarRefreshTokenSiCoincide(
                                    userId = datos.idUsuario,
                                    nuevoRefreshToken = refreshToken,
                                    nombre = datos.nombre,
                                    apellido = datos.apellido,
                                    fotoPerfil = datos.fotoPerfil ?: datos.foto,
                                    telefono = datos.telefono?.trim(),
                                    matriculaProfesional = datos.matriculaProfesional
                                )
                        }
                        prefs.saveUserId(datos.idUsuario)
                        prefs.saveUserRole(datos.idRol)

                        // Notificaciones: registra el token FCM, pide el permiso, recupera NP por REST y conecta WS.
                        FcmTokenManager.onLoginExitoso(applicationContext)
                        WebSocketConnectionManager.onLoginExitoso(applicationContext)

                        encolarSincronizacionActasPendientes(datos.idUsuario)

                        val intent = if (datos.idRol == ROL_VETERINARIO) {
                            Intent(this@inicio_sesion, dahsboard_veterianrio::class.java)
                        } else {
                            Intent(this@inicio_sesion, bienvenida::class.java)
                        }

                        intent.putExtra("TOKEN", accessToken)
                        intent.putExtra("EMAIL", datos.email)
                        intent.putExtra("ID_ROL", datos.idRol)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                    } else {
                        Log.w(TAG, "Respuesta exitosa de login sin cuerpo o token de acceso")
                        GoogleAuthHelper.mostrarMensajeMD3(
                            this@inicio_sesion,
                            "Respuesta inválida del servidor. Intenta nuevamente.",
                            esError = true
                        )
                    }
                } else {
                    val code = respuesta.code()
                    val errorRaw = try {
                        respuesta.errorBody()?.string()
                    } catch (_: Exception) {
                        null
                    }
                    if (code in 500..599) {
                        Log.e(TAG, "Error del servidor durante inicio de sesión: HTTP $code")
                    } else {
                        Log.w(TAG, "Inicio de sesión rechazado: HTTP $code")
                    }
                    val msgError = resolverMensajeErrorHttp(code, errorRaw)
                    GoogleAuthHelper.mostrarMensajeMD3(this@inicio_sesion, msgError, esError = true)
                }
            } catch (e: IOException) {
                Log.w(TAG, "Fallo de conexión durante inicio de sesión: ${e.javaClass.simpleName}")
                GoogleAuthHelper.mostrarMensajeMD3(
                    this@inicio_sesion,
                    resolverMensajeErrorExcepcion(e),
                    esError = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante inicio de sesión: ${e.javaClass.simpleName}")
                GoogleAuthHelper.mostrarMensajeMD3(
                    this@inicio_sesion,
                    resolverMensajeErrorExcepcion(e),
                    esError = true
                )
            } finally {
                if (!isFinishing && !isDestroyed) {
                    binding.btnIniciarSesion.isEnabled = true
                    binding.btnGoogleLogin.isEnabled = true
                    binding.btnFacialLogin.isEnabled = true
                }
            }
        }
    }

    private fun encolarSincronizacionActasPendientes(idVeterinario: Int) {
        val appContext = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ActaLocalRepository.getInstance(appContext).resetAuthErrorsToPending(idVeterinario)
                ActaSyncScheduler.enqueuePendingForCurrentUser(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error al sincronizar actas offline pendientes tras login: ${e.message}", e)
            }
        }
    }
}
