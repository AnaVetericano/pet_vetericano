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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.auth.AuthValidators
import com.example.petvetericano.databinding.ActivityRegistroBinding
import com.example.petvetericano.models.ConfirmarCodigoRegistroRequest
import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class Registro : AppCompatActivity() {

    private lateinit var binding: ActivityRegistroBinding
    private lateinit var prefs: SharedPreferencesManager
    private var dialogVerificacion: Dialog? = null
    private var dialogProcesando: Dialog? = null
    private var cooldownTimer: CountDownTimer? = null
    private var procesandoSolicitud: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paddingIzq = binding.main.paddingLeft
        val paddingSup = binding.main.paddingTop
        val paddingDer = binding.main.paddingRight
        val paddingInf = binding.main.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(
                paddingIzq + bars.left,
                paddingSup + bars.top,
                paddingDer + bars.right,
                paddingInf + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.main)

        prefs = SharedPreferencesManager(this)

        // Estado limpio antes de iniciar registro manual o con Google
        RetrofitClient.authToken = null
        com.example.petvetericano.network.SesionExpiradaManager.refreshMuerto = null

        binding.btnBackRegistro.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.edtxtID.doAfterTextChanged { binding.tilNombre.error = null }
        binding.edtxtApellido.doAfterTextChanged { binding.tilApellido.error = null }
        binding.edtxtemailorphone.doAfterTextChanged { binding.tilDocumento.error = null }
        binding.edtxtTelefono.doAfterTextChanged { binding.tilTelefono.error = null }
        binding.editTextTextEmailAddress.doAfterTextChanged { binding.tilEmail.error = null }
        binding.edtxtPassword.doAfterTextChanged { binding.tilPassword.error = null }

        binding.btnSignUp.setOnClickListener {
            registrarUsuario()
        }

        binding.btnGoogleSignUp.setOnClickListener {
            registrarseConGoogle()
        }

        binding.txtlogin.setOnClickListener {
            val intent = Intent(this, inicio_sesion::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
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
        if (procesandoSolicitud) return
        lifecycleScope.launch {
            GoogleAuthHelper.autenticarConGoogle(
                activity = this@Registro,
                esFlujoRegistro = true,
                onEstadoCarga = { cargando ->
                    procesandoSolicitud = cargando
                    if (!isFinishing && !isDestroyed) {
                        binding.btnGoogleSignUp.isEnabled = !cargando
                        binding.btnSignUp.isEnabled = !cargando
                        binding.btnBackRegistro.isEnabled = !cargando
                        binding.txtlogin.isEnabled = !cargando
                        binding.btnGoogleSignUp.text = if (cargando) {
                            "Registrando con Google..."
                        } else {
                            getString(R.string.continuar_con_google)
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

        binding.tilNombre.error = null
        binding.tilApellido.error = null
        binding.tilDocumento.error = null
        binding.tilTelefono.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        val validacion = AuthValidators.validarFormularioRegistro(
            nombre = nombre,
            apellido = apellido,
            identificacion = identificacion,
            telefono = telefono,
            email = email,
            password = password
        )

        if (!validacion.esValido) {
            when {
                validacion.errorNombre != null -> {
                    binding.tilNombre.error = validacion.errorNombre
                    binding.edtxtID.requestFocus()
                }
                validacion.errorApellido != null -> {
                    binding.tilApellido.error = validacion.errorApellido
                    binding.edtxtApellido.requestFocus()
                }
                validacion.errorDocumento != null -> {
                    binding.tilDocumento.error = validacion.errorDocumento
                    binding.edtxtemailorphone.requestFocus()
                }
                validacion.errorTelefono != null -> {
                    binding.tilTelefono.error = validacion.errorTelefono
                    binding.edtxtTelefono.requestFocus()
                }
                validacion.errorEmail != null -> {
                    binding.tilEmail.error = validacion.errorEmail
                    binding.editTextTextEmailAddress.requestFocus()
                }
                validacion.errorPassword != null -> {
                    binding.tilPassword.error = validacion.errorPassword
                    binding.edtxtPassword.requestFocus()
                }
            }
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
                if (isFinishing || isDestroyed) return@launch

                if (response.isSuccessful) {
                    val cooldownSeg = response.body()?.cooldownSegundos ?: 60
                    val msgExito = response.body()?.mensaje
                        ?: "Te enviamos un código de 6 dígitos a ${request.email}"
                    GoogleAuthHelper.mostrarMensajeMD3(this@Registro, msgExito, esError = false)
                    mostrarDialogoVerificacionCorreo(request, cooldownSeg)
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorParsed = AuthValidators.parsearErrorRegistroHttp(response.code(), errorBody)
                    errorParsed.errorEmail?.let { binding.tilEmail.error = it }
                    errorParsed.errorDocumento?.let { binding.tilDocumento.error = it }
                    errorParsed.errorTelefono?.let { binding.tilTelefono.error = it }
                    errorParsed.errorPassword?.let { binding.tilPassword.error = it }

                    GoogleAuthHelper.mostrarMensajeMD3(
                        this@Registro,
                        errorParsed.mensajeGeneral,
                        esError = true
                    )
                }
            } catch (e: Exception) {
                ocultarDialogoProcesando()
                if (!isFinishing && !isDestroyed) {
                    GoogleAuthHelper.mostrarMensajeMD3(
                        this@Registro,
                        inicio_sesion.resolverMensajeErrorExcepcion(e),
                        esError = true
                    )
                }
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
        if (isFinishing || isDestroyed) return
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
        edtCodigo.doAfterTextChanged { edtCodigo.error = null }

        var accionOtpEnCurso = false

        fun iniciarConteoReenvio(segundos: Int) {
            cooldownTimer?.cancel()
            val segInicial = segundos.coerceAtLeast(1)
            btnReenviar.isEnabled = false
            cooldownTimer = object : CountDownTimer(segInicial * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!dialog.isShowing) return
                    val seg = (millisUntilFinished / 1000L).coerceAtLeast(1L)
                    btnReenviar.text = "Reenviar código en ${seg}s"
                }

                override fun onFinish() {
                    cooldownTimer = null
                    if (!dialog.isShowing) return
                    btnReenviar.isEnabled = !accionOtpEnCurso
                    btnReenviar.text = getString(R.string.btn_reenviar_codigo)
                }
            }.start()
        }

        iniciarConteoReenvio(cooldownInicialSegundos)

        val cerrarModal = {
            cooldownTimer?.cancel()
            cooldownTimer = null
            dialog.dismiss()
        }
        txtCerrar.setOnClickListener { cerrarModal() }
        txtCorregir.setOnClickListener {
            cerrarModal()
            binding.editTextTextEmailAddress.requestFocus()
        }
        dialog.setOnDismissListener {
            cooldownTimer?.cancel()
            cooldownTimer = null
        }

        btnReenviar.setOnClickListener {
            if (accionOtpEnCurso) return@setOnClickListener
            accionOtpEnCurso = true
            btnReenviar.isEnabled = false
            btnConfirmar.isEnabled = false
            btnReenviar.text = "Reenviando..."
            lifecycleScope.launch {
                try {
                    val resp = RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                        api.solicitarCodigoRegistro(request)
                    }
                    if (!dialog.isShowing || isFinishing || isDestroyed) return@launch

                    if (resp.isSuccessful) {
                        val nuevoCooldown = resp.body()?.cooldownSegundos ?: 60
                        edtCodigo.setText("")
                        edtCodigo.error = null
                        GoogleAuthHelper.mostrarMensajeEnDialogoMD3(
                            dialog,
                            this@Registro,
                            resp.body()?.mensaje ?: "Nuevo código enviado a tu correo",
                            esError = false
                        )
                        iniciarConteoReenvio(nuevoCooldown)
                    } else {
                        val errStr = resp.errorBody()?.string()
                        val errorParsed = AuthValidators.parsearErrorRegistroHttp(resp.code(), errStr)
                        GoogleAuthHelper.mostrarMensajeEnDialogoMD3(
                            dialog,
                            this@Registro,
                            errorParsed.mensajeGeneral,
                            esError = true
                        )
                        val espera = errorParsed.retryAfterSegundos ?: 15
                        iniciarConteoReenvio(espera)
                    }
                } catch (e: Exception) {
                    if (dialog.isShowing && !isFinishing && !isDestroyed) {
                        GoogleAuthHelper.mostrarMensajeEnDialogoMD3(
                            dialog,
                            this@Registro,
                            inicio_sesion.resolverMensajeErrorExcepcion(e),
                            esError = true
                        )
                        btnReenviar.isEnabled = true
                        btnReenviar.text = getString(R.string.btn_reenviar_codigo)
                    }
                } finally {
                    accionOtpEnCurso = false
                    if (dialog.isShowing && !isFinishing && !isDestroyed) {
                        btnConfirmar.isEnabled = true
                    }
                }
            }
        }

        btnConfirmar.setOnClickListener {
            if (accionOtpEnCurso) return@setOnClickListener
            val codigo = edtCodigo.text.toString().trim()
            if (!AuthValidators.esCodigoOtpValido(codigo)) {
                edtCodigo.error = "Ingresa el código numérico de 6 dígitos"
                edtCodigo.requestFocus()
                return@setOnClickListener
            }

            accionOtpEnCurso = true
            btnConfirmar.isEnabled = false
            btnReenviar.isEnabled = false
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

                    if (isFinishing || isDestroyed) return@launch

                    if (response.isSuccessful) {
                        cooldownTimer?.cancel()
                        cooldownTimer = null
                        dialog.dismiss()

                        val intent = Intent(this@Registro, inicio_sesion::class.java)
                        intent.putExtra("REGISTRO_GOOGLE_MSG", "¡Correo verificado y cuenta creada! Inicia sesión para continuar.")
                        intent.putExtra("EMAIL_REGISTRADO", request.email)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else if (dialog.isShowing) {
                        val errorBody = response.errorBody()?.string()
                        val errorParsed = AuthValidators.parsearErrorRegistroHttp(response.code(), errorBody)
                        if (errorParsed.intentosRestantes == 0) {
                            edtCodigo.setText("")
                            cooldownTimer?.cancel()
                            cooldownTimer = null
                            btnReenviar.isEnabled = true
                            btnReenviar.text = getString(R.string.btn_reenviar_codigo)
                        }
                        edtCodigo.error = errorParsed.mensajeGeneral
                        GoogleAuthHelper.mostrarMensajeEnDialogoMD3(
                            dialog,
                            this@Registro,
                            errorParsed.mensajeGeneral,
                            esError = true
                        )
                    }
                } catch (e: Exception) {
                    if (dialog.isShowing && !isFinishing && !isDestroyed) {
                        GoogleAuthHelper.mostrarMensajeEnDialogoMD3(
                            dialog,
                            this@Registro,
                            inicio_sesion.resolverMensajeErrorExcepcion(e),
                            esError = true
                        )
                    }
                } finally {
                    accionOtpEnCurso = false
                    if (dialog.isShowing && !isFinishing && !isDestroyed) {
                        btnConfirmar.isEnabled = true
                        btnConfirmar.text = getString(R.string.btn_verificar_y_crear_cuenta)
                        if (cooldownTimer == null) {
                            btnReenviar.isEnabled = true
                        }
                    }
                }
            }
        }

        dialogVerificacion = dialog
        dialog.show()
    }
}
