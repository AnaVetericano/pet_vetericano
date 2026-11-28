package com.example.petvetericano

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.auth.AuthValidators
import com.example.petvetericano.facial.FacialStorageManager
import com.example.petvetericano.models.CompletarDatosGoogleRequest
import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import com.example.petvetericano.realtime.WebSocketConnectionManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"
    private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
    private val JWT_SEGMENT_REGEX = Regex("^[A-Za-z0-9_-]+$")
    private const val MAX_ID_TOKEN_LENGTH = 8192

    @Volatile
    private var proveedorTokenGooglePrueba: (suspend (ComponentActivity) -> ResultadoObtenerToken)? = null

    @VisibleForTesting
    internal fun configurarParaPruebas(
        proveedorToken: (suspend (ComponentActivity) -> ResultadoObtenerToken)? = null
    ) {
        proveedorTokenGooglePrueba = proveedorToken
    }

    @VisibleForTesting
    internal fun restaurarConfiguracion() {
        proveedorTokenGooglePrueba = null
    }

    data class CredencialGoogleExtraida(
        val idToken: String,
        val email: String = "",
        val nombre: String = "",
        val apellido: String = ""
    )

    internal sealed class ResultadoObtenerToken {
        data class Exito(val credencial: CredencialGoogleExtraida) : ResultadoObtenerToken()
        object Cancelado : ResultadoObtenerToken()
        data class Error(val mensajeUsuario: String) : ResultadoObtenerToken()
    }

    /**
     * Flujo estándar oficial de Google Sign-In con Android Credential Manager:
     * 1. Solicita un Google ID Token criptográfico mediante GetSignInWithGoogleOption.
     * 2. Valida el ID Token en el backend (`POST /api/usuarios/google-login/`).
     * 3. Verifica que el usuario tenga identificación y teléfono registrados (`GET /api/peticiones/perfil/`);
     *    si falta alguno (primer inicio con Google), solicita completarlos vía `PATCH /api/peticiones/perfil/`.
     * 4. Persiste la sesión JWT y navega a la pantalla principal según el rol.
     */
    suspend fun autenticarConGoogle(
        activity: ComponentActivity,
        esFlujoRegistro: Boolean = false,
        onEstadoCarga: (Boolean) -> Unit = {}
    ) {
        val origen = if (esFlujoRegistro) "Registro" else "Login"
        val sha1Actual = obtenerSha1CertificadoApp(activity)
        logInfo("Iniciando flujo Google ($origen) | pkg=${activity.packageName} | SHA-1=$sha1Actual")
        onEstadoCarga(true)
        try {
            when (val resultadoToken = obtenerTokenGoogle(activity, sha1Actual)) {
                is ResultadoObtenerToken.Cancelado -> {
                    logInfo("Flujo de Google ($origen) cancelado por el usuario.")
                }
                is ResultadoObtenerToken.Error -> {
                    logWarn("Fallo obteniendo token de Google ($origen): ${resultadoToken.mensajeUsuario}")
                    mostrarAlertaRechazoGoogleMD3(
                        activity = activity,
                        titulo = "Acceso con Google rechazado",
                        mensaje = resultadoToken.mensajeUsuario
                    )
                }
                is ResultadoObtenerToken.Exito -> {
                    logInfo("ID Token de Google obtenido ($origen). Validando con backend...")
                    enviarTokenAlBackend(activity, resultadoToken.credencial)
                }
            }
        } catch (e: IOException) {
            logWarn("Fallo de conexión en autenticación con Google ($origen): ${e.javaClass.simpleName}: ${e.message}")
            mostrarAlertaRechazoGoogleMD3(
                activity = activity,
                titulo = "Error de conexión",
                mensaje = inicio_sesion.resolverMensajeErrorExcepcion(e)
            )
        } catch (e: Exception) {
            logError("Error en autenticación con Google ($origen): ${e.javaClass.simpleName}: ${e.message}", e)
            mostrarAlertaRechazoGoogleMD3(
                activity = activity,
                titulo = "No se pudo iniciar sesión con Google",
                mensaje = "No se pudo completar el inicio de sesión con Google. Intenta nuevamente."
            )
        } finally {
            onEstadoCarga(false)
        }
    }

    fun mostrarAlertaRechazoGoogleMD3(
        activity: Activity,
        titulo: String,
        mensaje: String
    ) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            try {
                MaterialAlertDialogBuilder(activity)
                    .setIcon(R.drawable.warning_icon4)
                    .setTitle(titulo)
                    .setMessage(mensaje)
                    .setPositiveButton("Entendido") { dialog, _ -> dialog.dismiss() }
                    .show()
                return
            } catch (_: Exception) {
            }
        }
        mostrarMensajeMD3(activity, mensaje, esError = true)
    }

    fun mostrarMensajeMD3(activity: Activity, mensaje: String, esError: Boolean = false) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        if (rootView != null && !activity.isFinishing && !activity.isDestroyed) {
            mostrarSnackbarEnVista(rootView, activity, mensaje, esError)
        } else {
            Toast.makeText(activity, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    fun mostrarMensajeEnDialogoMD3(
        dialog: Dialog,
        context: Context,
        mensaje: String,
        esError: Boolean = false
    ) {
        val dialogRoot = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
            ?: dialog.window?.decorView
        if (dialog.isShowing && dialogRoot != null) {
            mostrarSnackbarEnVista(dialogRoot, context, mensaje, esError)
        } else if (context is Activity) {
            mostrarMensajeMD3(context, mensaje, esError)
        } else {
            Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarSnackbarEnVista(
        anchorView: View,
        context: Context,
        mensaje: String,
        esError: Boolean
    ) {
        val snackbar = Snackbar.make(anchorView, mensaje, Snackbar.LENGTH_LONG)
        val sbView = snackbar.view
        val bgColor = if (esError) R.color.error_dark else R.color.button_primary_bg
        sbView.setBackgroundTintList(ContextCompat.getColorStateList(context, bgColor))
        val textView = sbView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.setTextColor(ContextCompat.getColor(context, R.color.white))
        textView?.maxLines = 5
        snackbar.show()
    }

    private suspend fun obtenerTokenGoogle(
        activity: ComponentActivity,
        sha1Actual: String = ""
    ): ResultadoObtenerToken {
        proveedorTokenGooglePrueba?.let { proveedor ->
            return proveedor(activity)
        }

        val webClientId = obtenerWebClientId(activity)
        if (webClientId.isEmpty()) {
            logError("Falta configurar google_web_client_id / default_web_client_id (Web Client ID OAuth).")
            return ResultadoObtenerToken.Error(
                "Falta configurar el Web Client ID de Google OAuth para usar el acceso con Google."
            )
        }

        val credentialManager = CredentialManager.create(activity)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            extraerCredencialDesdeGetCredentialResponse(response)
        } catch (e: Exception) {
            mapearExcepcionCredentialManager(e = e, sha1Actual = sha1Actual)
        }
    }

    internal fun mapearExcepcionCredentialManager(
        e: Exception,
        sha1Actual: String = ""
    ): ResultadoObtenerToken {
        val sufijoSha = if (sha1Actual.isNotBlank()) " ($sha1Actual)" else ""
        val detalleShaLog = if (sha1Actual.isNotBlank()) " (SHA-1: $sha1Actual)" else ""

        if (e is GetCredentialCancellationException) {
            val msgCancel = e.message.orEmpty()
            val esErrorReauth = msgCancel.contains("[16]") ||
                msgCancel.contains("reauth", ignoreCase = true) ||
                msgCancel.contains("28444") ||
                msgCancel.contains("unregistered", ignoreCase = true)

            if (esErrorReauth) {
                logError(
                    "GetSignInWithGoogleOption rechazado por OAuth/reauth$detalleShaLog: $msgCancel"
                )
                return ResultadoObtenerToken.Error(
                    "Google Play Services rechazó la firma OAuth ([16]/[28444]). Verifica la huella SHA-1$sufijoSha en Firebase / Google Cloud Console."
                )
            }
            return ResultadoObtenerToken.Cancelado
        }
        if (e is GoogleIdTokenParsingException) {
            logError("Error al interpretar GoogleIdTokenCredential: ${e.message}")
            return ResultadoObtenerToken.Error("No se pudo leer el token emitido por Google.")
        }
        if (e is GetCredentialProviderConfigurationException) {
            logError("Falta proveedor de Credential Manager o Google Play Services: ${e.errorMessage}")
            return ResultadoObtenerToken.Error(
                "Google Play Services no está disponible o necesita actualización para iniciar sesión con Google."
            )
        }
        if (e is NoCredentialException) {
            logWarn("NoCredentialException en dispositivo$detalleShaLog: ${e.message}")
            return ResultadoObtenerToken.Error(
                "No se encontró una cuenta de Google disponible en este dispositivo. Verifica tus cuentas de Google en Ajustes e intenta nuevamente."
            )
        }
        if (e is GetCredentialException) {
            logError("CredentialManager error (${e.type}): ${e.errorMessage}")
            val detalle = "${e.type} ${e.errorMessage}".lowercase(Locale.ROOT)
            val esErrorConfigOAuth = detalle.contains("developer") ||
                detalle.contains("reauth") ||
                detalle.contains("10:") ||
                detalle.contains("28444") ||
                detalle.contains("unregistered_on_api_console") ||
                detalle.contains("caller not found") ||
                detalle.contains("misconfig")
            val mensaje = if (esErrorConfigOAuth) {
                "Error de configuración OAuth en Google Cloud/Firebase: verifica Web Client ID y registra la huella SHA-1$sufijoSha."
            } else {
                "No se pudo completar la verificación con Google. Intenta nuevamente."
            }
            return ResultadoObtenerToken.Error(mensaje)
        }
        logError("Error inesperado obteniendo credencial Google: ${e.javaClass.simpleName}: ${e.message}", e)
        return ResultadoObtenerToken.Error("No se pudo obtener la credencial de Google. Intenta nuevamente.")
    }

    private fun extraerCredencialDesdeGetCredentialResponse(result: GetCredentialResponse): ResultadoObtenerToken {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleCred.idToken.trim()
            if (!esIdTokenJwtValido(idToken)) {
                logError("El proveedor no devolvió un ID Token JWT válido.")
                return ResultadoObtenerToken.Error("Google no devolvió un ID Token válido.")
            }

            val email = googleCred.id.trim().lowercase(Locale.ROOT)
            val (nombre, apellido) = extraerNombreYApellidoDeGoogle(
                givenName = googleCred.givenName,
                familyName = googleCred.familyName,
                displayName = googleCred.displayName,
                email = email
            )
            return ResultadoObtenerToken.Exito(
                CredencialGoogleExtraida(
                    idToken = idToken,
                    email = email,
                    nombre = nombre,
                    apellido = apellido
                )
            )
        }

        logError("Tipo de credencial inesperado: ${credential.type}")
        return ResultadoObtenerToken.Error("Credencial de Google no compatible.")
    }

    private suspend fun enviarTokenAlBackend(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida
    ) {
        RetrofitClient.authToken = null
        val request = construirGoogleLoginRequest(credencial)

        val respuesta = withContext(Dispatchers.IO) {
            RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                api.loginConGoogle(request)
            }
        }

        if (!respuesta.isSuccessful) {
            val code = respuesta.code()
            val errorRaw = try {
                respuesta.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            logWarn("google-login rechazado por el servidor: HTTP $code")
            mostrarAlertaRechazoGoogleMD3(
                activity = activity,
                titulo = resolverTituloRechazoGoogleHttp(code),
                mensaje = resolverMensajeErrorGoogleHttp(code, errorRaw)
            )
            return
        }

        val datos = respuesta.body()
        val accessToken = datos?.tokens?.access?.trim().orEmpty()
        if (datos == null || accessToken.isEmpty()) {
            logWarn("Respuesta exitosa de google-login sin cuerpo o token de acceso")
            mostrarAlertaRechazoGoogleMD3(
                activity = activity,
                titulo = "Respuesta inválida",
                mensaje = "Respuesta inválida del servidor. Intenta nuevamente."
            )
            return
        }

        var sesionCompletada = false
        try {
            // Token temporal en memoria únicamente para validar/completar el perfil antes de persistir sesión.
            RetrofitClient.authToken = accessToken

            val perfilResp = withContext(Dispatchers.IO) {
                RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                    api.obtenerPerfil()
                }
            }

            if (!perfilResp.isSuccessful) {
                val perfilCode = perfilResp.code()
                val perfilErrorRaw = try {
                    perfilResp.errorBody()?.string()
                } catch (_: Exception) {
                    null
                }
                logWarn("No se pudo consultar perfil tras google-login: HTTP $perfilCode")
                mostrarAlertaRechazoGoogleMD3(
                    activity = activity,
                    titulo = "No se pudo verificar tu perfil",
                    mensaje = resolverMensajeErrorGoogleHttp(perfilCode, perfilErrorRaw)
                )
                return
            }

            val perfil = perfilResp.body()
            val identificacionActual = perfil?.identificacion?.trim().orEmpty()
            val telefonoActual = perfil?.telefono?.trim().orEmpty()
                .ifEmpty { datos.telefono?.trim().orEmpty() }

            val (identificacionFinal, telefonoFinal) = if (requiereCompletarDatosPerfil(identificacionActual, telefonoActual)) {
                val completados = mostrarDialogoCompletarDatosGoogleMD3(
                    activity = activity,
                    email = datos.email.ifBlank { credencial.email },
                    identificacionInicial = identificacionActual,
                    telefonoInicial = telefonoActual
                )
                if (completados == null) {
                    RetrofitClient.authToken = null
                    cerrarSesionGoogle(activity)
                    mostrarMensajeMD3(
                        activity,
                        activity.getString(R.string.google_completar_datos_cancelado),
                        esError = false
                    )
                    return
                }
                completados
            } else {
                Pair(identificacionActual, telefonoActual)
            }

            completarInicioDeSesionExitoso(
                activity = activity,
                datos = datos,
                credencial = credencial,
                identificacion = identificacionFinal,
                telefono = telefonoFinal
            )
            sesionCompletada = true
        } finally {
            if (!sesionCompletada) {
                RetrofitClient.authToken = null
            }
        }
    }

    internal fun requiereCompletarDatosPerfil(
        identificacion: String?,
        telefono: String?
    ): Boolean {
        return identificacion?.trim().isNullOrEmpty() || telefono?.trim().isNullOrEmpty()
    }

    private suspend fun mostrarDialogoCompletarDatosGoogleMD3(
        activity: ComponentActivity,
        email: String,
        identificacionInicial: String,
        telefonoInicial: String
    ): Pair<String, String>? = suspendCancellableCoroutine { cont ->
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_completar_datos_google)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val tvEmail = dialog.findViewById<TextView>(R.id.tvEmailCompletarGoogle)
        val tilIdentificacion = dialog.findViewById<TextInputLayout>(R.id.tilIdentificacionGoogle)
        val edtIdentificacion = dialog.findViewById<TextInputEditText>(R.id.edtIdentificacionGoogle)
        val tilTelefono = dialog.findViewById<TextInputLayout>(R.id.tilTelefonoGoogle)
        val edtTelefono = dialog.findViewById<TextInputEditText>(R.id.edtTelefonoGoogle)
        val btnGuardar = dialog.findViewById<MaterialButton>(R.id.btnGuardarDatosGoogle)
        val btnCancelar = dialog.findViewById<TextView>(R.id.btnCancelarCompletarGoogle)
        val btnCerrar = dialog.findViewById<TextView>(R.id.btnCerrarCompletarGoogle)

        val filtroSoloDigitos = InputFilter { source, start, end, _, _, _ ->
            val sb = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c.isDigit()) {
                    sb.append(c)
                }
            }
            if (sb.length == end - start) null else sb.toString()
        }

        edtIdentificacion.filters = arrayOf(filtroSoloDigitos, InputFilter.LengthFilter(20))
        edtTelefono.filters = arrayOf(filtroSoloDigitos, InputFilter.LengthFilter(15))
        tvEmail.text = email

        val docExistente = identificacionInicial.trim()
        if (docExistente.isNotEmpty()) {
            edtIdentificacion.setText(docExistente)
            tilIdentificacion.isEnabled = false
            edtIdentificacion.isEnabled = false
            edtIdentificacion.isFocusable = false
            edtIdentificacion.isClickable = false
            edtTelefono.requestFocus()
        } else {
            edtIdentificacion.requestFocus()
        }

        val telExistente = telefonoInicial.filter { it.isDigit() }
        if (telExistente.isNotEmpty()) {
            edtTelefono.setText(telExistente)
        }

        edtIdentificacion.doOnTextChanged { _, _, _, _ -> tilIdentificacion.error = null }
        edtTelefono.doOnTextChanged { _, _, _, _ -> tilTelefono.error = null }

        var guardando = false

        val cancelar = {
            if (!guardando) {
                dialog.dismiss()
                if (cont.isActive) cont.resume(null)
            }
        }

        btnCancelar.setOnClickListener { cancelar() }
        btnCerrar.setOnClickListener { cancelar() }
        dialog.setOnCancelListener {
            if (cont.isActive) cont.resume(null)
        }
        cont.invokeOnCancellation {
            if (dialog.isShowing) dialog.dismiss()
        }

        btnGuardar.setOnClickListener {
            if (guardando) return@setOnClickListener

            val doc = if (docExistente.isNotEmpty()) {
                docExistente
            } else {
                edtIdentificacion.text?.toString()?.trim().orEmpty()
            }
            val tel = edtTelefono.text?.toString()?.trim().orEmpty()

            val validacion = AuthValidators.validarDatosComplementariosGoogle(doc, tel)
            if (!validacion.esValido) {
                tilIdentificacion.error = validacion.errorIdentificacion
                tilTelefono.error = validacion.errorTelefono
                if (validacion.errorIdentificacion != null && docExistente.isEmpty()) {
                    edtIdentificacion.requestFocus()
                } else if (validacion.errorTelefono != null) {
                    edtTelefono.requestFocus()
                }
                return@setOnClickListener
            }

            guardando = true
            tilIdentificacion.error = null
            tilTelefono.error = null
            btnGuardar.isEnabled = false
            btnCancelar.isEnabled = false
            btnCerrar.isEnabled = false
            btnGuardar.text = "Guardando..."

            activity.lifecycleScope.launch {
                try {
                    val req = CompletarDatosGoogleRequest(
                        identificacion = doc,
                        telefono = tel
                    )
                    val resp = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.completarDatosPerfil(req)
                        }
                    }

                    if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@launch

                    if (resp.isSuccessful) {
                        val body = resp.body()
                        val docGuardado = body?.identificacion?.trim()?.takeIf { it.isNotEmpty() } ?: doc
                        val telGuardado = body?.telefono?.trim()?.takeIf { it.isNotEmpty() } ?: tel
                        dialog.dismiss()
                        if (cont.isActive) cont.resume(Pair(docGuardado, telGuardado))
                    } else {
                        val errBody = try {
                            resp.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        val errParsed = AuthValidators.parsearErrorRegistroHttp(resp.code(), errBody)
                        errParsed.errorDocumento?.let { tilIdentificacion.error = it }
                        errParsed.errorTelefono?.let { tilTelefono.error = it }
                        mostrarMensajeEnDialogoMD3(
                            dialog,
                            activity,
                            errParsed.mensajeGeneral,
                            esError = true
                        )
                    }
                } catch (e: Exception) {
                    if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                        mostrarMensajeEnDialogoMD3(
                            dialog,
                            activity,
                            inicio_sesion.resolverMensajeErrorExcepcion(e),
                            esError = true
                        )
                    }
                } finally {
                    guardando = false
                    if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                        btnGuardar.isEnabled = true
                        btnCancelar.isEnabled = true
                        btnCerrar.isEnabled = true
                        btnGuardar.text = activity.getString(R.string.btn_guardar_y_continuar)
                    }
                }
            }
        }

        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show()
        } else if (cont.isActive) {
            cont.resume(null)
        }
    }

    private fun completarInicioDeSesionExitoso(
        activity: Activity,
        datos: LoginResponse,
        credencial: CredencialGoogleExtraida,
        identificacion: String,
        telefono: String
    ) {
        val accessToken = datos.tokens.access.trim()
        val refreshToken = datos.tokens.refresh.trim()

        RetrofitClient.authToken = accessToken
        SesionExpiradaManager.refreshMuerto = null

        val prefs = SharedPreferencesManager(activity)
        prefs.limpiarDatosGoogleLegacy()
        val nombreFinal = datos.nombre ?: credencial.nombre
        val apellidoFinal = datos.apellido ?: credencial.apellido
        val telefonoFinal = telefono.trim()
        val identificacionFinal = identificacion.trim()
        val fotoFinal = datos.fotoPerfil ?: datos.foto ?: ""
        val matriculaFinal = datos.matriculaProfesional?.trim().orEmpty()

        prefs.saveUserData(
            name = nombreFinal,
            email = datos.email,
            phone = telefonoFinal
        )
        prefs.saveUserLastName(apellidoFinal)
        if (identificacionFinal.isNotEmpty()) {
            prefs.saveUserIdentification(identificacionFinal)
        }
        if (matriculaFinal.isNotEmpty()) {
            prefs.saveUserMatriculaProfesional(matriculaFinal)
        }
        prefs.saveProfileImagePath(fotoFinal)
        prefs.saveAccessToken(accessToken)
        if (refreshToken.isNotEmpty()) {
            prefs.saveRefreshToken(refreshToken)
            FacialStorageManager(activity).actualizarRefreshTokenSiCoincide(
                userId = datos.idUsuario,
                nuevoRefreshToken = refreshToken,
                nombre = nombreFinal,
                apellido = apellidoFinal,
                fotoPerfil = fotoFinal,
                telefono = telefonoFinal,
                matriculaProfesional = matriculaFinal.ifEmpty { null }
            )
        }
        prefs.saveUserId(datos.idUsuario)
        prefs.saveUserRole(datos.idRol)

        // Notificaciones: registra el token FCM, pide el permiso, recupera NP por REST y conecta WS.
        FcmTokenManager.onLoginExitoso(activity.applicationContext)
        WebSocketConnectionManager.onLoginExitoso(activity.applicationContext)

        val rolStr = if (datos.idRol == inicio_sesion.ROL_VETERINARIO) "Médico Veterinario" else "Ciudadano"
        val nombreMostrar = datos.nombre?.trim().orEmpty()
        val saludo = if (nombreMostrar.isNotEmpty()) "¡Hola, $rolStr $nombreMostrar! Bienvenido." else "¡Hola, $rolStr! Bienvenido."
        Toast.makeText(activity, saludo, Toast.LENGTH_SHORT).show()

        encolarSincronizacionActasPendientes(activity.applicationContext, datos.idUsuario)

        val destino = if (datos.idRol == inicio_sesion.ROL_VETERINARIO) {
            Intent(activity, dahsboard_veterianrio::class.java)
        } else {
            Intent(activity, bienvenida::class.java)
        }

        destino.putExtra("TOKEN", accessToken)
        destino.putExtra("EMAIL", datos.email)
        destino.putExtra("ID_ROL", datos.idRol)
        destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        activity.startActivity(destino)
        activity.finish()
    }

    private fun encolarSincronizacionActasPendientes(context: Context, idVeterinario: Int) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ActaLocalRepository.getInstance(appContext).resetAuthErrorsToPending(idVeterinario)
                ActaSyncScheduler.enqueuePendingForCurrentUser(appContext)
            } catch (e: Exception) {
                logError("Error al sincronizar actas offline pendientes tras login: ${e.message}", e)
            }
        }
    }

    internal fun obtenerSha1CertificadoApp(context: Context): String {
        return try {
            val pm = context.packageManager
            val firmas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
            val certBytes = firmas?.firstOrNull()?.toByteArray() ?: return ""
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { "%02X".format(Locale.ROOT, it) }
        } catch (e: Exception) {
            logWarn("No se pudo calcular el SHA-1 del certificado del APK: ${e.message}")
            ""
        }
    }

    internal fun obtenerWebClientId(context: Context): String {
        val customId = context.getString(R.string.google_web_client_id)
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val defaultWebClientId = if (resId != 0) context.getString(resId) else null
        return seleccionarWebClientId(
            customId = customId,
            defaultWebClientId = defaultWebClientId,
            buildConfigWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        )
    }

    internal fun seleccionarWebClientId(
        customId: String?,
        defaultWebClientId: String?,
        buildConfigWebClientId: String? = null
    ): String {
        val candidatos = listOf(customId, defaultWebClientId, buildConfigWebClientId)
        for (raw in candidatos) {
            val candidato = raw?.trim().orEmpty()
            if (esWebClientIdValido(candidato)) {
                return candidato
            }
        }
        return ""
    }

    internal fun esWebClientIdValido(clientId: String?): Boolean {
        val limpio = clientId?.trim().orEmpty()
        return limpio.isNotEmpty() &&
            !limpio.any { it.isWhitespace() } &&
            limpio.endsWith(GOOGLE_CLIENT_ID_SUFFIX) &&
            limpio.length > GOOGLE_CLIENT_ID_SUFFIX.length
    }

    internal fun esIdTokenJwtValido(rawToken: String?): Boolean {
        val token = rawToken?.trim().orEmpty()
        if (token.isEmpty() || token.length > MAX_ID_TOKEN_LENGTH) return false
        if (token.lowercase(Locale.ROOT).startsWith("android_account:")) return false
        if (token.any { it.isWhitespace() }) return false
        val partes = token.split('.')
        if (partes.size != 3) return false
        return partes.all { it.isNotEmpty() && JWT_SEGMENT_REGEX.matches(it) }
    }

    internal fun construirGoogleLoginRequest(credencial: CredencialGoogleExtraida): GoogleLoginRequest {
        val token = credencial.idToken.trim()
        require(esIdTokenJwtValido(token)) {
            "Se requiere un Google ID Token JWT auténtico."
        }
        return GoogleLoginRequest(idToken = token)
    }

    internal fun extraerNombreYApellidoDeGoogle(
        givenName: String?,
        familyName: String?,
        displayName: String?,
        email: String
    ): Pair<String, String> {
        val nombreDado = givenName?.trim().orEmpty()
        val apellidoDado = familyName?.trim().orEmpty()
        if (nombreDado.isNotEmpty()) {
            return Pair(nombreDado, apellidoDado)
        }

        val completo = displayName?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (completo.isNotEmpty()) {
            val partes = completo.split(" ", limit = 2)
            return Pair(
                partes[0],
                apellidoDado.ifEmpty { partes.getOrNull(1).orEmpty() }
            )
        }

        return extraerNombreApellidoDeCorreo(email)
    }

    private fun extraerNombreApellidoDeCorreo(email: String): Pair<String, String> {
        val localPart = email.substringBefore("@")
            .replace(Regex("[0-9_\\-]+"), ".")
            .trim('.')
        val partes = localPart.split(".")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val capitalizar: (String) -> String = { texto ->
            texto.lowercase(Locale.ROOT).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }

        return when {
            partes.size >= 2 -> Pair(capitalizar(partes[0]), capitalizar(partes[1]))
            partes.size == 1 -> Pair(capitalizar(partes[0]), "")
            else -> Pair("Usuario", "")
        }
    }

    internal fun resolverTituloRechazoGoogleHttp(code: Int): String {
        return when {
            code == 401 -> "Identidad de Google rechazada"
            code == 403 -> "Acceso con Google no permitido"
            code == 503 -> "Servicio de Google no disponible"
            code in 500..599 -> "Error del servidor"
            else -> "No se pudo iniciar sesión con Google"
        }
    }

    internal fun resolverMensajeErrorGoogleHttp(code: Int, errorRaw: String?): String {
        val mensajeJson = extraerMensajeJsonSeguro(errorRaw)
        if (code == 503 && !mensajeJson.isNullOrEmpty()) {
            return mensajeJson
        }
        if (code in 500..599) {
            return "Error del servidor ($code). Intenta nuevamente más tarde."
        }
        if (code == 404 || code == 405) {
            return "El servicio de acceso con Google no está disponible en el servidor ($code)."
        }
        if (!mensajeJson.isNullOrEmpty()) {
            return mensajeJson
        }
        return when (code) {
            401 -> "No se pudo validar tu identidad con Google. Intenta nuevamente."
            403 -> "Tu cuenta no tiene permitido ingresar con Google."
            else -> "No se pudo autenticar con Google (código $code)."
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
                extraerPrimerTextoArray(json, "id_token"),
                extraerPrimerTextoArray(json, "email")
            )
            candidatos
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && it != "null" }
                ?.take(200)
        } catch (_: Exception) {
            null
        }
    }

    private fun extraerMensajePrincipalOrFallback(el: com.google.gson.JsonElement?): String? {
        return if (el != null && el.isJsonPrimitive) el.asString else null
    }

    private fun extraerTextoCampo(json: com.google.gson.JsonObject, clave: String): String? {
        return extraerMensajePrincipalOrFallback(json.get(clave))
    }

    private fun extraerPrimerTextoArray(json: com.google.gson.JsonObject, clave: String): String? {
        val el = json.get(clave) ?: return null
        if (!el.isJsonArray) return null
        val arr = el.asJsonArray
        if (arr.size() == 0) return null
        return extraerMensajePrincipalOrFallback(arr.get(0))
    }

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }

    private fun logError(msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }

    /**
     * Limpia el estado de credenciales de Google y Firebase al cerrar sesión en la app.
     */
    suspend fun cerrarSesionGoogle(context: Context) {
        try {
            SharedPreferencesManager(context).limpiarDatosGoogleLegacy()
        } catch (_: Exception) {
        }
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            logWarn("Error al cerrar sesión en FirebaseAuth: ${e.message}")
        }
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            logWarn("Error al limpiar estado en CredentialManager: ${e.message}")
        }
    }
}
