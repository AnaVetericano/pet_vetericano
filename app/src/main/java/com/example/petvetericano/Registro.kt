package com.example.petvetericano

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityRegistroBinding
import com.example.petvetericano.models.ConfirmarCodigoRegistroRequest
import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONObject

class Registro : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private lateinit var prefs: SharedPreferencesManager
    private var dialogVerificacion: Dialog? = null
    private var dialogProcesando: Dialog? = null
    private var cooldownTimer: CountDownTimer? = null
    private var procesandoSolicitud: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SharedPreferencesManager(this)

        // Estado limpio antes de iniciar registro manual o con Google
        RetrofitClient.authToken = null
        com.example.petvetericano.network.SesionExpiradaManager.refreshMuerto = null

        binding.btnSignUp.setOnClickListener {
            registrarUsuario()
        }

        binding.btnGoogleSignUp.setOnClickListener {
            registrarseConGoogle()
        }

        binding.txtlogin.setOnClickListener {
            val intent = Intent(this, inicio_sesion::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        cooldownTimer?.cancel()
        dialogProcesando?.dismiss()
        dialogVerificacion?.dismiss()
        super.onDestroy()
    }

    private fun registrarseConGoogle() {
        lifecycleScope.launch {
            GoogleAuthHelper.autenticarConGoogle(
                activity = this@Registro,
                esFlujoRegistro = true,
                onEstadoCarga = { cargando ->
                    if (!isFinishing && !isDestroyed) {
                        binding.btnGoogleSignUp.isEnabled = !cargando
                        binding.btnSignUp.isEnabled = !cargando
                        binding.btnGoogleSignUp.text = if (cargando) {
                            "Registrando con Google..."
                        } else {
                            getString(R.string.registrarse_con_google)
                        }
                    }
                }
            )
        }
    }

    private fun registrarUsuario() {
        val email = binding.editTextTextEmailAddress.text.toString().trim()
        val identificacion = binding.edtxtemailorphone.text.toString().trim()
        val nombre = binding.edtxtID.text.toString().trim()
        val apellido = binding.edtxtApellido.text.toString().trim()
        val telefono = binding.edtxtTelefono.text.toString().trim()
        val password = binding.edtxtPassword.text.toString().trim()

        // Limpiamos errores previos en TextInputLayout
        binding.tilPassword.error = null

        if (email.isEmpty()) {
            binding.editTextTextEmailAddress.error = "Ingrese su correo electrónico"
            binding.editTextTextEmailAddress.requestFocus()
            return
        }

        if (identificacion.isEmpty()) {
            binding.edtxtemailorphone.error = "Ingrese su documento o ID"
            binding.edtxtemailorphone.requestFocus()
            return
        }

        if (nombre.isEmpty()) {
            binding.edtxtID.error = "Ingrese su nombre"
            binding.edtxtID.requestFocus()
            return
        }

        if (apellido.isEmpty()) {
            binding.edtxtApellido.error = "Ingrese su apellido"
            binding.edtxtApellido.requestFocus()
            return
        }

        if (telefono.isEmpty() || telefono.length < 10) {
            binding.edtxtTelefono.error = "Ingrese un número de teléfono válido (mínimo 10 dígitos)"
            binding.edtxtTelefono.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese una contraseña"
            binding.edtxtPassword.requestFocus()
            return
        }

        // Reglas de negocio de la contraseña: Mínimo 8 caracteres, una mayúscula y un número
        val contieneMayuscula = Regex("[A-Z]").containsMatchIn(password)
        val contieneNumero = Regex("[0-9]").containsMatchIn(password)

        if (password.length < 8 || !contieneMayuscula || !contieneNumero) {
            binding.tilPassword.error = "Mín. 8 caracteres, una mayúscula y un número"
            binding.edtxtPassword.requestFocus()
            return
        }

        val request = RegisterRequest(
            email = email,
            identificacion = identificacion,
            password = password,
            nombre = nombre,
            apellido = apellido,
            telefono = telefono
        )

        solicitarCodigoVerificacion(request)
    }

    private fun mostrarDialogoProcesando(correoDestino: String) {
        if (isFinishing || isDestroyed) return
        dialogProcesando?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_procesando_registro)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val txtMensaje = dialog.findViewById<TextView>(R.id.txtMensajeProcesandoRegistro)
        txtMensaje?.text =
            "Estamos validando tus datos y enviando el código de 6 dígitos a $correoDestino.\nPor favor espera un momento..."

        dialogProcesando = dialog
        dialog.show()
    }

    private fun ocultarDialogoProcesando() {
        try {
            dialogProcesando?.dismiss()
        } catch (_: Exception) {
        }
        dialogProcesando = null
    }

    private fun solicitarCodigoVerificacion(request: RegisterRequest) {
        if (procesandoSolicitud) return
        procesandoSolicitud = true

        binding.btnSignUp.isEnabled = false
        binding.btnGoogleSignUp.isEnabled = false
        binding.btnSignUp.text = "Enviando código de verificación..."
        mostrarDialogoProcesando(request.email)

        lifecycleScope.launch {
            try {
                RetrofitClient.authToken = null
                val response = RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                    api.solicitarCodigoRegistro(request)
                }

                ocultarDialogoProcesando()

                if (response.isSuccessful) {
                    val cooldownSeg = response.body()?.cooldownSegundos ?: 60
                    val msgExito = response.body()?.mensaje
                        ?: "Te enviamos un código de 6 dígitos a ${request.email}"
                    GoogleAuthHelper.mostrarMensajeMD3(this@Registro, msgExito, esError = false)
                    mostrarDialogoVerificacionCorreo(request, cooldownSeg)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val mensajeError = parsearErrorDjango(errorBody)
                    GoogleAuthHelper.mostrarMensajeMD3(this@Registro, mensajeError, esError = true)
                }
            } catch (_: Exception) {
                ocultarDialogoProcesando()
                GoogleAuthHelper.mostrarMensajeMD3(this@Registro, "Error de conexión con el servidor", esError = true)
            } finally {
                procesandoSolicitud = false
                ocultarDialogoProcesando()
                if (!isFinishing && !isDestroyed) {
                    binding.btnSignUp.isEnabled = true
                    binding.btnGoogleSignUp.isEnabled = true
                    binding.btnSignUp.text = getString(R.string.btn_sign_login_register)
                }
            }
        }
    }

    private fun mostrarDialogoVerificacionCorreo(request: RegisterRequest, cooldownInicialSegundos: Int) {
        dialogVerificacion?.dismiss()
        cooldownTimer?.cancel()

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_verificar_correo_registro)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val txtCerrar = dialog.findViewById<TextView>(R.id.txtCerrarVerificacion)
        val txtCorreoDestino = dialog.findViewById<TextView>(R.id.txtCorreoDestinoVerificacion)
        val edtCodigo = dialog.findViewById<EditText>(R.id.edtCodigoVerificacionRegistro)
        val btnConfirmar = dialog.findViewById<MaterialButton>(R.id.btnConfirmarCodigoRegistro)
        val btnReenviar = dialog.findViewById<MaterialButton>(R.id.btnReenviarCodigoRegistro)
        val txtCorregir = dialog.findViewById<TextView>(R.id.txtCorregirCorreoRegistro)

        txtCorreoDestino.text = request.email

        fun iniciarConteoReenvio(segundos: Int) {
            cooldownTimer?.cancel()
            btnReenviar.isEnabled = false
            cooldownTimer = object : CountDownTimer(segundos * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val seg = (millisUntilFinished / 1000L).coerceAtLeast(1L)
                    btnReenviar.text = "Reenviar código en ${seg}s"
                }

                override fun onFinish() {
                    btnReenviar.isEnabled = true
                    btnReenviar.text = getString(R.string.btn_reenviar_codigo)
                }
            }.start()
        }

        iniciarConteoReenvio(cooldownInicialSegundos)

        val cerrarModal = {
            cooldownTimer?.cancel()
            dialog.dismiss()
        }
        txtCerrar.setOnClickListener { cerrarModal() }
        txtCorregir.setOnClickListener {
            cerrarModal()
            binding.editTextTextEmailAddress.requestFocus()
        }

        btnReenviar.setOnClickListener {
            btnReenviar.isEnabled = false
            btnReenviar.text = "Reenviando..."
            lifecycleScope.launch {
                try {
                    val resp = RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                        api.solicitarCodigoRegistro(request)
                    }
                    if (resp.isSuccessful) {
                        val nuevoCooldown = resp.body()?.cooldownSegundos ?: 60
                        GoogleAuthHelper.mostrarMensajeMD3(
                            this@Registro,
                            resp.body()?.mensaje ?: "Nuevo código enviado a tu correo",
                            esError = false
                        )
                        iniciarConteoReenvio(nuevoCooldown)
                    } else {
                        val errStr = resp.errorBody()?.string()
                        GoogleAuthHelper.mostrarMensajeMD3(
                            this@Registro,
                            parsearErrorDjango(errStr),
                            esError = true
                        )
                        iniciarConteoReenvio(15)
                    }
                } catch (_: Exception) {
                    GoogleAuthHelper.mostrarMensajeMD3(
                        this@Registro,
                        "Error de conexión al reenviar el código",
                        esError = true
                    )
                    btnReenviar.isEnabled = true
                    btnReenviar.text = getString(R.string.btn_reenviar_codigo)
                }
            }
        }

        btnConfirmar.setOnClickListener {
            val codigo = edtCodigo.text.toString().trim()
            if (codigo.length != 6) {
                edtCodigo.error = "Ingresa el código de 6 dígitos"
                edtCodigo.requestFocus()
                return@setOnClickListener
            }

            btnConfirmar.isEnabled = false
            btnConfirmar.text = "Verificando..."

            lifecycleScope.launch {
                try {
                    val confirmReq = ConfirmarCodigoRegistroRequest(
                        email = request.email,
                        codigo = codigo
                    )
                    val response = RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                        api.confirmarCodigoRegistro(confirmReq)
                    }

                    if (response.isSuccessful) {
                        cooldownTimer?.cancel()
                        dialog.dismiss()

                        prefs.saveUserData(name = request.nombre, email = request.email, phone = request.telefono)
                        prefs.saveUserLastName(request.apellido)

                        val intent = Intent(this@Registro, inicio_sesion::class.java)
                        intent.putExtra("REGISTRO_GOOGLE_MSG", "¡Correo verificado y cuenta creada! Inicia sesión para continuar.")
                        intent.putExtra("EMAIL_REGISTRADO", request.email)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val mensajeError = parsearErrorDjango(errorBody)
                        edtCodigo.error = mensajeError
                        GoogleAuthHelper.mostrarMensajeMD3(this@Registro, mensajeError, esError = true)
                    }
                } catch (_: Exception) {
                    GoogleAuthHelper.mostrarMensajeMD3(
                        this@Registro,
                        "Error de conexión con el servidor",
                        esError = true
                    )
                } finally {
                    if (dialog.isShowing) {
                        btnConfirmar.isEnabled = true
                        btnConfirmar.text = getString(R.string.btn_verificar_y_crear_cuenta)
                    }
                }
            }
        }

        dialogVerificacion = dialog
        dialog.show()
    }

    private fun parsearErrorDjango(json: String?): String {
        if (json.isNullOrEmpty()) return "Error al registrar usuario"
        return try {
            val jsonObject = JSONObject(json)
            when {
                jsonObject.has("error") -> jsonObject.getString("error")
                jsonObject.has("detail") -> jsonObject.getString("detail")
                jsonObject.has("mensaje") -> jsonObject.getString("mensaje")
                jsonObject.has("message") -> {
                    val msg = jsonObject.getString("message")
                    if (msg.contains("Application failed to respond", ignoreCase = true)) {
                        "El servidor en la nube (Railway) no está disponible en este momento (502)."
                    } else {
                        msg
                    }
                }
                else -> {
                    val clavesIgnorar = setOf("status", "code", "request_id")
                    val iter = jsonObject.keys()
                    var primeraClaveUtil: String? = null
                    while (iter.hasNext()) {
                        val k = iter.next()
                        if (k !in clavesIgnorar) {
                            primeraClaveUtil = k
                            break
                        }
                    }
                    val clave = primeraClaveUtil ?: jsonObject.keys().next()
                    val valor = jsonObject.get(clave)
                    if (valor is org.json.JSONArray && valor.length() > 0) {
                        "$clave: ${valor.getString(0)}"
                    } else {
                        valor.toString()
                    }
                }
            }
        } catch (_: Exception) {
            "Datos inválidos o usuario ya registrado"
        }
    }
}