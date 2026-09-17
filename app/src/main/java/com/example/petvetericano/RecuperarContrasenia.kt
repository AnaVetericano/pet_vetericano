package com.example.petvetericano

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.petvetericano.models.ConfirmarPasswordRequest
import com.example.petvetericano.models.GenericResponse
import com.example.petvetericano.models.RecuperarPasswordRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecuperarContrasenia(context: Context) : Dialog(context) {

    init {
        // 1. Asignar el diseño XML del modal
        setContentView(R.layout.dialog_recuperar_contrasenia)

        // 2. Fondo transparente para esquinas redondeadas
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 3. Referenciar vistas del XML
        val txtCerrar = findViewById<TextView>(R.id.txtCerrar)
        val txtCancelar = findViewById<TextView>(R.id.txtCancelar)

        val edtCorreo = findViewById<EditText>(R.id.edtCorreo)
        val btnEnviarCodigo = findViewById<Button>(R.id.btnEnviarCodigo)

        val edtCodigo = findViewById<EditText>(R.id.edtCodigo)
        val edtNuevaContrasena = findViewById<EditText>(R.id.edtNuevaContrasena)
        val btnCambiarContrasena = findViewById<Button>(R.id.btnCambiarContrasena)

        // Botones de cerrar / cancelar
        txtCerrar.setOnClickListener { dismiss() }
        txtCancelar.setOnClickListener { dismiss() }

        // ========================================================
        // PASO 1: ENVIAR CÓDIGO AL CORREO
        // ========================================================
        btnEnviarCodigo.setOnClickListener {
            val email = edtCorreo.text.toString().trim()

            // Validación de campo vacío
            if (email.isEmpty()) {
                edtCorreo.error = "Ingresa tu correo electrónico"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }

            // Deshabilitar temporalmente el botón para evitar doble clic
            btnEnviarCodigo.isEnabled = false
            btnEnviarCodigo.text = "Enviando..."

            // Ejecutar la petición HTTP en segundo plano (Corrutinas)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val request = RecuperarPasswordRequest(email = email)
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.solicitarRecuperacion(request)
                    }

                    if (response.isSuccessful) {
                        val cuerpo = response.body()
                        val mensaje = cuerpo?.mensaje ?: "Código enviado a tu correo"
                        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()

                        // Mover el foco automáticamente al campo de código
                        edtCodigo.requestFocus()
                    } else {
                        // Extraer el mensaje de error que responde Django (ej: "No existe un usuario con este correo")
                        val errorMsg = obtenerMensajeError(response.errorBody()?.string())
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    // Restaurar el botón
                    btnEnviarCodigo.isEnabled = true
                    btnEnviarCodigo.text = "Enviar Código"
                }
            }
        }

        // ========================================================
        // PASO 2: CONFIRMAR CÓDIGO Y CAMBIAR CONTRASEÑA
        // ========================================================
        btnCambiarContrasena.setOnClickListener {
            val email = edtCorreo.text.toString().trim()
            val codigo = edtCodigo.text.toString().trim()
            val nuevaPassword = edtNuevaContrasena.text.toString().trim()

            // Validaciones locales
            if (email.isEmpty()) {
                edtCorreo.error = "El correo es obligatorio"
                edtCorreo.requestFocus()
                return@setOnClickListener
            }
            if (codigo.isEmpty() || codigo.length != 6) {
                edtCodigo.error = "Ingresa el código de 6 dígitos"
                edtCodigo.requestFocus()
                return@setOnClickListener
            }
            if (nuevaPassword.isEmpty()) {
                edtNuevaContrasena.error = "Ingresa la nueva contraseña"
                edtNuevaContrasena.requestFocus()
                return@setOnClickListener
            }

            btnCambiarContrasena.isEnabled = false
            btnCambiarContrasena.text = "PROCESANDO..."

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val request = ConfirmarPasswordRequest(
                        email = email,
                        codigo = codigo,
                        nuevaPassword = nuevaPassword
                    )
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.apiService.confirmarRecuperacion(request)
                    }

                    if (response.isSuccessful) {
                        val mensaje = response.body()?.mensaje ?: "Contraseña actualizada exitosamente"
                        Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()

                        // Cerrar el modal para que el usuario inicie sesión con su nueva clave
                        dismiss()
                    } else {
                        val errorMsg = obtenerMensajeError(response.errorBody()?.string())
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnCambiarContrasena.isEnabled = true
                    btnCambiarContrasena.text = "CAMBIAR CONTRASEÑA"
                }
            }
        }
    }

    /**
     * Función auxiliar para leer el error JSON que envía Django: {"error": "..."}
     */
    private fun obtenerMensajeError(errorJson: String?): String {
        if (errorJson.isNullOrEmpty()) return "Ocurrió un error en el servidor"
        return try {
            val generic = Gson().fromJson(errorJson, GenericResponse::class.java)
            generic.error ?: generic.detail ?: generic.mensaje ?: "Error al procesar la solicitud"
        } catch (e: Exception) {
            "Error en la solicitud"
        }
    }
}