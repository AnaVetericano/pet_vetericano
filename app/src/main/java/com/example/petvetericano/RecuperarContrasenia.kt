package com.example.petvetericano

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.example.petvetericano.auth.AuthValidators
import com.example.petvetericano.models.ConfirmarPasswordRequest
import com.example.petvetericano.models.RecuperarPasswordRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecuperarContrasenia(
    context: Context,
    private val emailInicial: String = ""
) : Dialog(context) {

    private val dialogJob = SupervisorJob()
    private val dialogScope = CoroutineScope(dialogJob + Dispatchers.Main.immediate)
    private var cooldownTimer: CountDownTimer? = null
    private var solicitudEnCurso: Boolean = false

    init {
        setContentView(R.layout.dialog_recuperar_contrasenia)
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val txtCerrar = findViewById<TextView>(R.id.txtCerrar)
        val txtCancelar = findViewById<TextView>(R.id.txtCancelar)

        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)
        val btnEnviarCodigo = findViewById<Button>(R.id.btnEnviarCodigo)

        val edtCodigo = findViewById<EditText>(R.id.edtCodigo)
        val tilNuevaContrasena = findViewById<TextInputLayout>(R.id.tilNuevaContrasena)
        val edtNuevaContrasena = findViewById<EditText>(R.id.edtNuevaContrasena)
        val btnCambiarContrasena = findViewById<Button>(R.id.btnCambiarContrasena)

        if (emailInicial.isNotBlank()) {
            edtCorreo.setText(emailInicial.trim())
            edtCorreo.setSelection(edtCorreo.text.length)
        }

        edtCorreo.doAfterTextChanged { edtCorreo.error = null }
        edtCodigo.doAfterTextChanged { edtCodigo.error = null }
        edtNuevaContrasena.doAfterTextChanged { tilNuevaContrasena?.error = null }

        txtCerrar.setOnClickListener { dismiss() }
        txtCancelar.setOnClickListener { dismiss() }

        setOnDismissListener {
            cooldownTimer?.cancel()
            cooldownTimer = null
            dialogScope.cancel()
        }

        fun iniciarConteoReenvio(segundos: Int) {
            cooldownTimer?.cancel()
            val duracionSeg = segundos.coerceAtLeast(1)
            btnEnviarCodigo.isEnabled = false
            cooldownTimer = object : CountDownTimer(duracionSeg * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isShowing) return
                    val seg = (millisUntilFinished / 1000L).coerceAtLeast(1L)
                    btnEnviarCodigo.text = "Reenviar código en ${seg}s"
                }

                override fun onFinish() {
                    if (!isShowing) return
                    btnEnviarCodigo.isEnabled = !solicitudEnCurso
                    btnEnviarCodigo.text = context.getString(R.string.btn_enviar_codigo_de_verificacion)
                }
            }.start()
        }

        fun mostrarFeedback(mensaje: String, esError: Boolean) {
            if (!isShowing) return
            GoogleAuthHelper.mostrarMensajeEnDialogoMD3(this, context, mensaje, esError = esError)
            Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
        }

        // ========================================================
        // PASO 1: ENVIAR CÓDIGO AL CORREO
        // ========================================================
        btnEnviarCodigo.setOnClickListener {
            if (solicitudEnCurso) return@setOnClickListener
            val email = edtCorreo.text.toString().trim()

            if (email.isEmpty()) {
                edtCorreo.error = "Ingresa tu correo electrónico"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }
            if (!AuthValidators.esEmailValido(email)) {
                edtCorreo.error = "Ingresa un correo electrónico válido"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }

            solicitudEnCurso = true
            btnEnviarCodigo.isEnabled = false
            btnCambiarContrasena.isEnabled = false
            btnEnviarCodigo.text = "Enviando..."

            dialogScope.launch {
                var inicioCooldown = false
                try {
                    val request = RecuperarPasswordRequest(email = email)
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.solicitarRecuperacion(request)
                        }
                    }

                    if (!isShowing) return@launch

                    if (response.isSuccessful) {
                        val mensaje = response.body()?.mensaje ?: "Código enviado a tu correo"
                        mostrarFeedback(mensaje, esError = false)
                        edtCodigo.requestFocus()
                        iniciarConteoReenvio(60)
                        inicioCooldown = true
                    } else {
                        val errRaw = response.errorBody()?.string()
                        val parsed = AuthValidators.parsearErrorRegistroHttp(response.code(), errRaw)
                        edtCorreo.error = parsed.mensajeGeneral
                        mostrarFeedback(parsed.mensajeGeneral, esError = true)
                        if (response.code() == 429 && (parsed.retryAfterSegundos ?: 0) > 0) {
                            iniciarConteoReenvio(parsed.retryAfterSegundos!!)
                            inicioCooldown = true
                        }
                    }
                } catch (e: Exception) {
                    if (isShowing) {
                        mostrarFeedback(inicio_sesion.resolverMensajeErrorExcepcion(e), esError = true)
                    }
                } finally {
                    solicitudEnCurso = false
                    if (isShowing) {
                        btnCambiarContrasena.isEnabled = true
                        if (!inicioCooldown) {
                            btnEnviarCodigo.isEnabled = true
                            btnEnviarCodigo.text = context.getString(R.string.btn_enviar_codigo_de_verificacion)
                        }
                    }
                }
            }
        }

        // ========================================================
        // PASO 2: CONFIRMAR CÓDIGO Y CAMBIAR CONTRASEÑA
        // ========================================================
        btnCambiarContrasena.setOnClickListener {
            if (solicitudEnCurso) return@setOnClickListener
            val email = edtCorreo.text.toString().trim()
            val codigo = edtCodigo.text.toString().trim()
            val nuevaPassword = edtNuevaContrasena.text.toString().trim()

            edtCorreo.error = null
            edtCodigo.error = null
            tilNuevaContrasena?.error = null

            if (email.isEmpty()) {
                edtCorreo.error = "El correo es obligatorio"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }
            if (!AuthValidators.esEmailValido(email)) {
                edtCorreo.error = "Ingresa un correo electrónico válido"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }
            if (!AuthValidators.esCodigoOtpValido(codigo)) {
                edtCodigo.error = "Ingresa el código numérico de 6 dígitos"
                edtCodigo.requestFocus()
                return@setOnClickListener
            }
            if (nuevaPassword.isEmpty()) {
                tilNuevaContrasena?.error = "Ingresa la nueva contraseña"
                edtNuevaContrasena.requestFocus()
                return@setOnClickListener
            }
            if (!AuthValidators.esPasswordFuerte(nuevaPassword)) {
                tilNuevaContrasena?.error = "Mín. 8 caracteres, una mayúscula y un número"
                edtNuevaContrasena.requestFocus()
                return@setOnClickListener
            }

            solicitudEnCurso = true
            btnCambiarContrasena.isEnabled = false
            btnEnviarCodigo.isEnabled = false
            btnCambiarContrasena.text = "Procesando..."

            dialogScope.launch {
                try {
                    val request = ConfirmarPasswordRequest(
                        email = email,
                        codigo = codigo,
                        nuevaPassword = nuevaPassword
                    )
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.confirmarRecuperacion(request)
                        }
                    }

                    if (!isShowing) return@launch

                    if (response.isSuccessful) {
                        val mensaje = response.body()?.mensaje ?: "Contraseña actualizada exitosamente"
                        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                        dismiss()
                    } else {
                        val errRaw = response.errorBody()?.string()
                        val errorMsg = AuthValidators.parsearErrorGenericoHttp(response.code(), errRaw)
                        edtCodigo.error = errorMsg
                        mostrarFeedback(errorMsg, esError = true)
                    }
                } catch (e: Exception) {
                    if (isShowing) {
                        mostrarFeedback(inicio_sesion.resolverMensajeErrorExcepcion(e), esError = true)
                    }
                } finally {
                    solicitudEnCurso = false
                    if (isShowing) {
                        btnCambiarContrasena.isEnabled = true
                        btnCambiarContrasena.text = context.getString(R.string.lbl_cambiar_contrasena)
                        if (cooldownTimer == null) {
                            btnEnviarCodigo.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun dismiss() {
        cooldownTimer?.cancel()
        cooldownTimer = null
        dialogScope.cancel()
        super.dismiss()
    }
}
