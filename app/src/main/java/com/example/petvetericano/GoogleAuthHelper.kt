package com.example.petvetericano

import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.petvetericano.facial.FacialStorageManager
import com.example.petvetericano.models.ConfirmarCodigoRegistroRequest
import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import com.example.petvetericano.realtime.WebSocketConnectionManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
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
import java.util.UUID
import kotlin.coroutines.resume

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"
    private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
    private val JWT_SEGMENT_REGEX = Regex("^[A-Za-z0-9_-]+$")
    private const val MAX_ID_TOKEN_LENGTH = 8192
    private const val DURACION_MINIMA_CANCELACION_USUARIO_MS = 1800L

    data class CredencialGoogleExtraida(
        val idToken: String,
        val email: String = "",
        val nombre: String = "",
        val apellido: String = ""
    )

    internal sealed class ResultadoObtenerToken {
        data class Exito(val credencial: CredencialGoogleExtraida) : ResultadoObtenerToken()
        object Cancelado : ResultadoObtenerToken()
        data class Error(
            val mensajeUsuario: String,
            val usarRespaldoSelectorMD3: Boolean = false
        ) : ResultadoObtenerToken()
    }

    /**
     * Inicia el flujo de Google Sign-In:
     * 1. Intenta obtener un Google ID Token criptográfico mediante Android Credential Manager.
     * 2. Si Google Play Services rechaza la firma OAuth del entorno local ([28444] / [16] Account reauth failed)
     *    o el servidor aún no tiene configurada la variable GOOGLE_OAUTH_CLIENT_IDS (HTTP 503/404/405),
     *    activa automáticamente el selector Material Design 3 de cuentas del dispositivo y autentica/vincula
     *    contra el backend en línea.
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
                    if (resultadoToken.usarRespaldoSelectorMD3) {
                        logWarn(
                            "Activando selector MD3 de respaldo ($origen) tras rechazo de Credential Manager: " +
                                resultadoToken.mensajeUsuario
                        )
                        val credencialLocal = seleccionarCuentaGoogleMD3(activity)
                        if (credencialLocal != null) {
                            autenticarConRespaldoApi(activity, credencialLocal, esFlujoRegistro)
                        }
                    } else {
                        logWarn("Fallo obteniendo token de Google ($origen): ${resultadoToken.mensajeUsuario}")
                        mostrarMensajeMD3(activity, resultadoToken.mensajeUsuario, esError = true)
                    }
                }
                is ResultadoObtenerToken.Exito -> {
                    logInfo("ID Token de Google obtenido ($origen) para ${resultadoToken.credencial.email}. Validando con backend...")
                    enviarTokenAlBackend(activity, resultadoToken.credencial, esFlujoRegistro)
                }
            }
        } catch (e: IOException) {
            logWarn("Fallo de conexión en autenticación con Google ($origen): ${e.javaClass.simpleName}: ${e.message}")
            mostrarMensajeMD3(
                activity,
                inicio_sesion.resolverMensajeErrorExcepcion(e),
                esError = true
            )
        } catch (e: Exception) {
            logError("Error en autenticación con Google ($origen): ${e.javaClass.simpleName}: ${e.message}", e)
            mostrarMensajeMD3(
                activity,
                "No se pudo completar el inicio de sesión con Google. Intenta nuevamente.",
                esError = true
            )
        } finally {
            onEstadoCarga(false)
        }
    }

    fun mostrarMensajeMD3(activity: Activity, mensaje: String, esError: Boolean = false) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        if (rootView != null && !activity.isFinishing && !activity.isDestroyed) {
            val snackbar = Snackbar.make(rootView, mensaje, Snackbar.LENGTH_LONG)
            val sbView = snackbar.view
            val bgColor = if (esError) R.color.error_dark else R.color.blue
            sbView.setBackgroundTintList(ContextCompat.getColorStateList(activity, bgColor))
            val textView = sbView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView?.setTextColor(ContextCompat.getColor(activity, R.color.white))
            textView?.maxLines = 3
            snackbar.show()
        } else {
            Toast.makeText(activity, mensaje, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun obtenerTokenGoogle(
        activity: ComponentActivity,
        sha1Actual: String = ""
    ): ResultadoObtenerToken {
        val webClientId = obtenerWebClientId(activity)
        if (webClientId.isEmpty()) {
            logError("Falta configurar google_web_client_id / default_web_client_id (Web Client ID OAuth).")
            return ResultadoObtenerToken.Error(
                mensajeUsuario = "Falta configurar el Web Client ID de Google OAuth para usar el acceso con Google.",
                usarRespaldoSelectorMD3 = true
            )
        }

        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val t0GetGoogleId = System.currentTimeMillis()
        val result = try {
            credentialManager.getCredential(activity, request)
        } catch (noCred: NoCredentialException) {
            val duracionPrimaryMs = System.currentTimeMillis() - t0GetGoogleId
            logWarn(
                "GetGoogleIdOption lanzó NoCredentialException tras ${duracionPrimaryMs}ms (${noCred.message}); " +
                    "activando selector MD3 directo para evitar doble diálogo tras fallo OAuth [28444]."
            )
            return mapearExcepcionCredentialManager(
                e = noCred,
                vinoDeFallbackNoCredential = true,
                duracionMs = duracionPrimaryMs,
                sha1Actual = sha1Actual
            )
        } catch (e: Exception) {
            val duracionPrimaryMs = System.currentTimeMillis() - t0GetGoogleId
            return mapearExcepcionCredentialManager(
                e = e,
                vinoDeFallbackNoCredential = false,
                duracionMs = duracionPrimaryMs,
                sha1Actual = sha1Actual
            )
        }

        return extraerCredencialDesdeGetCredentialResponse(result)
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

    internal fun mapearExcepcionCredentialManager(
        e: Exception,
        vinoDeFallbackNoCredential: Boolean = false,
        duracionMs: Long = Long.MAX_VALUE,
        sha1Actual: String = ""
    ): ResultadoObtenerToken {
        if (e is GetCredentialCancellationException) {
            val msgCancel = e.message.orEmpty()
            val esErrorReauth = msgCancel.contains("[16]") ||
                msgCancel.contains("reauth", ignoreCase = true) ||
                msgCancel.contains("28444") ||
                msgCancel.contains("unregistered", ignoreCase = true)

            if (esErrorReauth || vinoDeFallbackNoCredential) {
                val sufijoSha = if (sha1Actual.isNotBlank()) " (SHA-1 del APK: $sha1Actual)" else ""
                logError(
                    "GetSignInWithGoogleOption rechazado con GetCredentialCancellationException " +
                        "(${duracionMs}ms, reauth=$esErrorReauth). Causa OAuth [28444]/[16] UNREGISTERED_ON_API_CONSOLE$sufijoSha: $msgCancel"
                )
                return ResultadoObtenerToken.Error(
                    mensajeUsuario = if (esErrorReauth) {
                        "Google Play Services rechazó la firma OAuth ([16] Account reauth failed). Registra la huella SHA-1$sufijoSha en Firebase / Google Cloud Console."
                    } else {
                        "Google rechazó la autenticación o se cerró el selector. Verifica que la huella SHA-1$sufijoSha esté registrada en Firebase / Google Cloud OAuth."
                    },
                    usarRespaldoSelectorMD3 = true
                )
            }
            if (duracionMs < DURACION_MINIMA_CANCELACION_USUARIO_MS) {
                val sufijoSha = if (sha1Actual.isNotBlank()) " (SHA-1: $sha1Actual)" else ""
                logWarn(
                    "Cancelación prematura (${duracionMs}ms) en CredentialManager$sufijoSha: ${e.message}"
                )
                return ResultadoObtenerToken.Error(
                    mensajeUsuario = "No se pudo abrir o completar el selector de Google$sufijoSha. Intenta nuevamente.",
                    usarRespaldoSelectorMD3 = true
                )
            }
            return ResultadoObtenerToken.Cancelado
        }
        if (e is GoogleIdTokenParsingException) {
            logError("Error al interpretar GoogleIdTokenCredential: ${e.message}")
            return ResultadoObtenerToken.Error(
                mensajeUsuario = "No se pudo leer el token emitido por Google.",
                usarRespaldoSelectorMD3 = false
            )
        }
        if (e is GetCredentialProviderConfigurationException) {
            logError("Falta proveedor de Credential Manager o Google Play Services: ${e.errorMessage}")
            return ResultadoObtenerToken.Error(
                mensajeUsuario = "Google Play Services no está disponible o actualizado para iniciar sesión con Google.",
                usarRespaldoSelectorMD3 = true
            )
        }
        if (e is NoCredentialException) {
            val sufijoSha = if (sha1Actual.isNotBlank()) " (SHA-1: $sha1Actual)" else ""
            logWarn("NoCredentialException en dispositivo$sufijoSha: ${e.message}")
            return ResultadoObtenerToken.Error(
                mensajeUsuario = "No se encontró una cuenta de Google autorizada en el dispositivo o falta registrar la huella SHA-1$sufijoSha en Firebase.",
                usarRespaldoSelectorMD3 = true
            )
        }
        if (e is GetCredentialException) {
            logError("CredentialManager error (${e.type}): ${e.errorMessage}")
            val detalle = "${e.type} ${e.errorMessage}".lowercase(Locale.ROOT)
            val sufijoSha = if (sha1Actual.isNotBlank()) " (SHA-1: $sha1Actual)" else ""
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
                "No se pudo completar la verificación con Google en el dispositivo. Intenta nuevamente."
            }
            return ResultadoObtenerToken.Error(
                mensajeUsuario = mensaje,
                usarRespaldoSelectorMD3 = true
            )
        }
        logError("Error inesperado obteniendo credencial Google: ${e.javaClass.simpleName}: ${e.message}", e)
        return ResultadoObtenerToken.Error(
            mensajeUsuario = "No se pudo obtener la credencial de Google.",
            usarRespaldoSelectorMD3 = true
        )
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
            SharedPreferencesManagerFallback.guardarCuentaConocidaSeguro(email)
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

    private object SharedPreferencesManagerFallback {
        var ultimoEmailSeleccionado: String = ""
        fun guardarCuentaConocidaSeguro(email: String) {
            ultimoEmailSeleccionado = email
        }
    }

    private suspend fun enviarTokenAlBackend(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida,
        esFlujoRegistro: Boolean
    ) {
        RetrofitClient.authToken = null
        if (credencial.email.isNotEmpty()) {
            SharedPreferencesManager(activity).saveKnownGoogleAccount(credencial.email)
        }
        val request = construirGoogleLoginRequest(credencial)

        val respuesta = withContext(Dispatchers.IO) {
            RetrofitClient.apiService.loginConGoogle(request)
        }

        if (respuesta.isSuccessful) {
            val datos = respuesta.body()
            val accessToken = datos?.tokens?.access?.trim().orEmpty()
            if (datos != null && accessToken.isNotEmpty()) {
                if (esFlujoRegistro) {
                    val msg = if (datos.esNuevoUsuario == true) {
                        "¡Registro con Google exitoso! Pulsa 'Continuar con Google' para iniciar sesión."
                    } else {
                        "Tu cuenta ya está registrada. Pulsa 'Continuar con Google' para iniciar sesión."
                    }
                    completarRegistroRedirigiendoALogin(
                        activity = activity,
                        email = datos.email,
                        nombre = datos.nombre ?: credencial.nombre,
                        apellido = datos.apellido ?: credencial.apellido,
                        mensaje = msg
                    )
                } else {
                    completarInicioDeSesionExitoso(activity, datos, credencial)
                }
            } else {
                logWarn("Respuesta exitosa de google-login sin cuerpo o token de acceso")
                mostrarMensajeMD3(
                    activity,
                    "Respuesta inválida del servidor. Intenta nuevamente.",
                    esError = true
                )
            }
            return
        }

        val code = respuesta.code()
        val errorRaw = try {
            respuesta.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        logWarn("google-login rechazado por el servidor: HTTP $code")

        // Si el backend en la nube aún no tiene configurada la variable GOOGLE_OAUTH_CLIENT_IDS (HTTP 503)
        // o la ruta /usuarios/google-login/ no está activa (404/405), usamos el flujo compatible con la cuenta ya verificada.
        if (code == 503 || code == 404 || code == 405) {
            logInfo("Endpoint google-login retornó HTTP $code; autenticando mediante flujo de respaldo con la cuenta ${credencial.email}.")
            autenticarConRespaldoApi(activity, credencial, esFlujoRegistro)
            return
        }

        val msgError = resolverMensajeErrorGoogleHttp(code, errorRaw)
        mostrarMensajeMD3(activity, msgError, esError = true)
    }

    /**
     * Obtiene las cuentas de Google disponibles en el teléfono combinando AccountManager,
     * FirebaseAuth y las cuentas recordadas en el dispositivo.
     */
    private fun obtenerCuentasDisponiblesDispositivo(activity: Context): List<String> {
        val cuentas = linkedSetOf<String>()
        val prefs = SharedPreferencesManager(activity)

        try {
            val accountManager = AccountManager.get(activity)
            accountManager.getAccountsByType("com.google").forEach { acc ->
                val email = acc.name?.trim()?.lowercase(Locale.ROOT) ?: ""
                if (email.contains("@")) cuentas.add(email)
            }
            accountManager.accounts.forEach { acc ->
                val email = acc.name?.trim()?.lowercase(Locale.ROOT) ?: ""
                if (email.contains("@") && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    cuentas.add(email)
                }
            }
        } catch (e: Exception) {
            logWarn("No se pudieron leer cuentas directas de AccountManager: ${e.message}")
        }

        prefs.getKnownGoogleAccounts().forEach { email ->
            val clean = email.trim().lowercase(Locale.ROOT)
            if (clean.contains("@")) {
                cuentas.add(clean)
                prefs.saveKnownGoogleAccount(clean)
            }
        }

        try {
            val firebaseEmail = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            if (!firebaseEmail.isNullOrEmpty() && firebaseEmail.contains("@")) {
                cuentas.add(firebaseEmail)
            }
        } catch (_: Exception) {
        }

        cuentas.forEach { prefs.saveKnownGoogleAccount(it) }
        return cuentas.toList()
    }

    private suspend fun mostrarDialogoSincronizarTelefonoMD3(activity: ComponentActivity): String? {
        try {
            val perm = android.Manifest.permission.GET_ACCOUNTS
            if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val key = "google_perm_${UUID.randomUUID()}"
                    var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null
                    launcher = activity.activityResultRegistry.register(
                        key,
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        launcher?.unregister()
                        if (cont.isActive) cont.resume(granted)
                    }
                    cont.invokeOnCancellation { launcher.unregister() }
                    launcher.launch(perm)
                }
            }
        } catch (e: Exception) {
            logWarn("No se pudo solicitar permiso GET_ACCOUNTS: ${e.message}")
        }

        return mostrarDialogoSelectorCuentaMD3(
            activity = activity,
            esModoSincronizarTelefono = true
        )
    }

    private suspend fun seleccionarCuentaGoogleMD3(activity: ComponentActivity): CredencialGoogleExtraida? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val emailSeleccionado = mostrarDialogoSelectorCuentaMD3(
            activity = activity,
            esModoSincronizarTelefono = false
        )
        if (emailSeleccionado.isNullOrEmpty()) {
            return null
        }

        SharedPreferencesManager(activity).saveKnownGoogleAccount(emailSeleccionado)
        val (nombreExtraido, apellidoExtraido) = extraerNombreApellidoDeCorreo(emailSeleccionado)
        return CredencialGoogleExtraida(
            idToken = "",
            email = emailSeleccionado,
            nombre = nombreExtraido,
            apellido = apellidoExtraido
        )
    }

    private suspend fun mostrarDialogoSelectorCuentaMD3(
        activity: ComponentActivity,
        esModoSincronizarTelefono: Boolean = false
    ): String? = suspendCancellableCoroutine { cont ->
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_seleccionar_cuenta_google)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val prefs = SharedPreferencesManager(activity)
        val tvTituloDialog = dialog.findViewById<TextView>(R.id.tvTituloSelectorGoogle)
        val tvSubtituloDialog = dialog.findViewById<TextView>(R.id.tvSubtituloSelectorGoogle)
        val llListaCuentas = dialog.findViewById<LinearLayout>(R.id.llListaCuentasGoogle)
        val cardAgregar = dialog.findViewById<MaterialCardView>(R.id.itemAgregarCuentaGoogle)
        val llContenedorNueva = dialog.findViewById<LinearLayout>(R.id.llContenedorNuevaCuentaGoogle)
        val tilNuevaCuenta = dialog.findViewById<TextInputLayout>(R.id.tilNuevaCuentaGoogle)
        val edtNuevaCuenta = dialog.findViewById<TextInputEditText>(R.id.edtNuevaCuentaGoogle)
        val chipGmail = dialog.findViewById<MaterialCardView>(R.id.chipDominioGmail)
        val chipUnicauca = dialog.findViewById<MaterialCardView>(R.id.chipDominioUnicauca)
        val btnImportarSistema = dialog.findViewById<MaterialButton>(R.id.btnImportarCuentaSistema)
        val btnCancelar = dialog.findViewById<MaterialButton>(R.id.btnCancelarSelectorGoogle)
        val btnAceptar = dialog.findViewById<MaterialButton>(R.id.btnAceptarSelectorGoogle)
        val btnCerrar = dialog.findViewById<TextView>(R.id.btnCerrarSelectorGoogle)

        if (esModoSincronizarTelefono) {
            tvTituloDialog.text = "Sincronizar cuenta del teléfono"
            tvSubtituloDialog.text = "Selecciona o agrega una cuenta del dispositivo"
        }

        val cuentasDisponibles = obtenerCuentasDisponiblesDispositivo(activity).toMutableList()
        var indiceSeleccionado = if (cuentasDisponibles.isNotEmpty()) 0 else -1

        val cardAvatarAgregar = cardAgregar.findViewById<MaterialCardView>(R.id.cardAvatarCuentaGoogle)
        val tvAvatarAgregar = cardAgregar.findViewById<TextView>(R.id.tvAvatarInicialGoogle)
        val tvTituloAgregar = cardAgregar.findViewById<TextView>(R.id.tvEmailItemGoogle)
        val tvSubtituloAgregar = cardAgregar.findViewById<TextView>(R.id.tvSubtituloItemGoogle)
        val rbAgregar = cardAgregar.findViewById<MaterialRadioButton>(R.id.rbSeleccionCuentaGoogle)

        tvAvatarAgregar.text = "+"
        tvTituloAgregar.text = "Agregar una cuenta"
        tvSubtituloAgregar.text = "Usar otra cuenta de Google"

        val colorBlue = ContextCompat.getColor(activity, R.color.blue)
        val colorWhite = ContextCompat.getColor(activity, R.color.white)
        val colorPrimarySoft = ContextCompat.getColor(activity, R.color.primary_soft)
        val colorPrimaryLight = ContextCompat.getColor(activity, R.color.primary_light)
        val colorInputBg = ContextCompat.getColor(activity, R.color.input_background)
        val density = activity.resources.displayMetrics.density

        fun aplicarEstiloTarjeta(
            card: MaterialCardView,
            avatarCard: MaterialCardView,
            avatarText: TextView,
            radio: MaterialRadioButton,
            seleccionado: Boolean
        ) {
            radio.isChecked = seleccionado
            if (seleccionado) {
                card.setCardBackgroundColor(colorPrimarySoft)
                card.strokeColor = colorBlue
                card.strokeWidth = (2f * density).toInt()
                avatarCard.setCardBackgroundColor(colorBlue)
                avatarText.setTextColor(colorWhite)
            } else {
                card.setCardBackgroundColor(colorInputBg)
                card.strokeColor = colorPrimaryLight
                card.strokeWidth = (1f * density).toInt()
                avatarCard.setCardBackgroundColor(colorPrimarySoft)
                avatarText.setTextColor(colorBlue)
            }
        }

        fun aplicarDominioRapido(dominio: String) {
            val actual = edtNuevaCuenta.text?.toString()?.trim() ?: ""
            val usuario = actual.substringBefore("@").trim()
            val nuevoTexto = if (usuario.isNotEmpty()) "$usuario$dominio" else dominio
            edtNuevaCuenta.setText(nuevoTexto)
            edtNuevaCuenta.setSelection(
                if (usuario.isNotEmpty()) nuevoTexto.length else 0
            )
            edtNuevaCuenta.requestFocus()
        }

        chipGmail?.setOnClickListener { aplicarDominioRapido("@gmail.com") }
        chipUnicauca?.setOnClickListener { aplicarDominioRapido("@unicauca.edu.co") }

        fun renderizarCuentas() {
            llListaCuentas.removeAllViews()
            val inflater = LayoutInflater.from(activity)

            cuentasDisponibles.forEachIndexed { index, email ->
                val itemView = inflater.inflate(R.layout.item_cuenta_google_md3, llListaCuentas, false)
                val cardItem = itemView.findViewById<MaterialCardView>(R.id.cardItemCuentaGoogle)
                val cardAvatar = itemView.findViewById<MaterialCardView>(R.id.cardAvatarCuentaGoogle)
                val tvInicial = itemView.findViewById<TextView>(R.id.tvAvatarInicialGoogle)
                val tvEmail = itemView.findViewById<TextView>(R.id.tvEmailItemGoogle)
                val tvSub = itemView.findViewById<TextView>(R.id.tvSubtituloItemGoogle)
                val rbItem = itemView.findViewById<MaterialRadioButton>(R.id.rbSeleccionCuentaGoogle)

                val inicial = email.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
                tvInicial.text = inicial
                tvEmail.text = email
                tvSub.text = if (esModoSincronizarTelefono) {
                    "Cuenta detectada en este teléfono"
                } else {
                    "Cuenta de Google"
                }

                val estaSeleccionado = (indiceSeleccionado == index)
                aplicarEstiloTarjeta(cardItem, cardAvatar, tvInicial, rbItem, estaSeleccionado)

                cardItem.setOnClickListener {
                    indiceSeleccionado = index
                    tilNuevaCuenta.error = null
                    renderizarCuentas()
                }

                llListaCuentas.addView(itemView)
            }

            val agregarSeleccionado = (indiceSeleccionado == -1)
            aplicarEstiloTarjeta(
                cardAgregar,
                cardAvatarAgregar,
                tvAvatarAgregar,
                rbAgregar,
                agregarSeleccionado
            )
            if (esModoSincronizarTelefono) {
                btnImportarSistema.visibility = View.GONE
            } else {
                btnImportarSistema.visibility = View.VISIBLE
                btnImportarSistema.text = if (cuentasDisponibles.isEmpty()) {
                    "Seleccionar cuenta de Google de mi teléfono"
                } else {
                    "Sincronizar otra cuenta del teléfono"
                }
            }
            llContenedorNueva.visibility = if (agregarSeleccionado) View.VISIBLE else View.GONE
        }

        cardAgregar.setOnClickListener {
            indiceSeleccionado = -1
            renderizarCuentas()
            edtNuevaCuenta.requestFocus()
        }

        edtNuevaCuenta.doOnTextChanged { _, _, _, _ ->
            tilNuevaCuenta.error = null
        }

        btnImportarSistema.setOnClickListener {
            activity.lifecycleScope.launch {
                val cuentaImportada = mostrarDialogoSincronizarTelefonoMD3(activity)
                if (!cuentaImportada.isNullOrEmpty()) {
                    if (!cuentasDisponibles.contains(cuentaImportada)) {
                        cuentasDisponibles.add(cuentaImportada)
                    }
                    indiceSeleccionado = cuentasDisponibles.indexOf(cuentaImportada)
                    renderizarCuentas()
                }
            }
        }

        renderizarCuentas()

        val cancelar = {
            dialog.dismiss()
            if (cont.isActive) cont.resume(null)
        }

        btnCancelar.setOnClickListener { cancelar() }
        btnCerrar.setOnClickListener { cancelar() }
        dialog.setOnCancelListener { if (cont.isActive) cont.resume(null) }

        btnAceptar.setOnClickListener {
            if (indiceSeleccionado in cuentasDisponibles.indices) {
                val cuentaElegida = cuentasDisponibles[indiceSeleccionado]
                dialog.dismiss()
                if (cont.isActive) cont.resume(cuentaElegida)
            } else {
                val nuevoCorreo = edtNuevaCuenta.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                if (nuevoCorreo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(nuevoCorreo).matches()) {
                    tilNuevaCuenta.error = "Ingresa un correo válido o sincronízalo del teléfono"
                    edtNuevaCuenta.requestFocus()
                    return@setOnClickListener
                }
                prefs.saveKnownGoogleAccount(nuevoCorreo)
                dialog.dismiss()
                if (cont.isActive) cont.resume(nuevoCorreo)
            }
        }

        dialog.show()
    }

    /**
     * Respaldo de autenticación con Google cuando Google Play Services local rechaza el SHA-1
     * o el servidor en la nube aún no tiene activo GOOGLE_OAUTH_CLIENT_IDS.
     *
     * SEGURIDAD:
     * - Nunca inicia sesión ni crea cuentas solo con escribir el correo (sin contraseña o sin OTP).
     * - En LOGIN (`esFlujoRegistro == false`), siempre exige ingresar y validar la contraseña de la cuenta
     *   en el servidor (`vincularCuentaExistenteConDialogoMD3`).
     * - En REGISTRO (`esFlujoRegistro == true`), siempre solicita una contraseña segura y verifica que el
     *   correo realmente exista mediante código OTP de 6 dígitos (`solicitarCodigoRegistro` + `confirmarCodigoRegistro`).
     */
    private suspend fun autenticarConRespaldoApi(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida,
        esFlujoRegistro: Boolean
    ) {
        val email = credencial.email.trim().lowercase(Locale.ROOT)
        if (email.isEmpty() || !email.contains("@")) {
            mostrarMensajeMD3(activity, "No se pudo obtener el correo de la cuenta de Google.", esError = true)
            return
        }

        RetrofitClient.authToken = null
        val prefs = SharedPreferencesManager(activity)
        prefs.saveKnownGoogleAccount(email)
        val (nombreFallback, apellidoFallback) = extraerNombreApellidoDeCorreo(email)
        val nombre = credencial.nombre.trim().ifEmpty { nombreFallback }
        val apellido = credencial.apellido.trim().ifEmpty { apellidoFallback.ifEmpty { "Google" } }
        val identificacionAuto = generarIdentificacionUnica(email)
        val telefonoAuto = "3000000000"

        if (esFlujoRegistro) {
            val passwordElegida = solicitarPasswordParaRegistroGoogleMD3(activity, email) ?: return
            val registerReq = RegisterRequest(
                email = email,
                identificacion = identificacionAuto,
                password = passwordElegida,
                nombre = nombre,
                apellido = apellido,
                telefono = telefonoAuto
            )

            val solResp = withContext(Dispatchers.IO) {
                RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                    api.solicitarCodigoRegistro(registerReq)
                }
            }

            if (solResp.isSuccessful) {
                val cooldownSeg = solResp.body()?.cooldownSegundos ?: 60
                val msgOtp = solResp.body()?.mensaje
                    ?: "Te enviamos un código de verificación de 6 dígitos a $email"
                mostrarMensajeMD3(activity, msgOtp, esError = false)

                val verificado = verificarCodigoOtpRegistroGoogleMD3(
                    activity = activity,
                    request = registerReq,
                    cooldownInicialSegundos = cooldownSeg
                )
                if (verificado) {
                    completarRegistroRedirigiendoALogin(
                        activity = activity,
                        email = email,
                        nombre = nombre,
                        apellido = apellido,
                        mensaje = "¡Correo verificado y cuenta creada! Inicia sesión con tu correo y contraseña."
                    )
                }
                return
            }

            val errorRegistro = try {
                solResp.errorBody()?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (errorRegistro.contains("email", ignoreCase = true) ||
                errorRegistro.contains("existe", ignoreCase = true) ||
                errorRegistro.contains("already", ignoreCase = true)
            ) {
                val respuestaVinculada = vincularCuentaExistenteConDialogoMD3(
                    activity = activity,
                    email = email,
                    esFlujoRegistro = true
                )
                if (respuestaVinculada != null) {
                    completarRegistroRedirigiendoALogin(
                        activity = activity,
                        email = email,
                        nombre = respuestaVinculada.nombre ?: nombre,
                        apellido = respuestaVinculada.apellido ?: apellido,
                        mensaje = "¡Cuenta verificada! Inicia sesión con tu correo y contraseña."
                    )
                }
                return
            }

            val mensajeError = resolverMensajeErrorGoogleHttp(solResp.code(), errorRegistro)
            mostrarMensajeMD3(activity, mensajeError, esError = true)
            return
        }

        // --- FLUJO DE LOGIN (esFlujoRegistro == false) ---
        // Siempre exigimos la contraseña de la cuenta; nunca permitimos entrar ni auto-registrar solo con el correo.
        val respuestaVinculada = vincularCuentaExistenteConDialogoMD3(
            activity = activity,
            email = email,
            esFlujoRegistro = false
        )
        if (respuestaVinculada != null) {
            completarInicioDeSesionExitoso(activity, respuestaVinculada, credencial)
        }
    }

    private suspend fun solicitarPasswordParaRegistroGoogleMD3(
        activity: ComponentActivity,
        email: String
    ): String? = suspendCancellableCoroutine { cont ->
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_vincular_google)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo = dialog.findViewById<TextView>(R.id.tvTituloDialogGoogle)
        val tvMensaje = dialog.findViewById<TextView>(R.id.tvMensajeDialogGoogle)
        val tvEmail = dialog.findViewById<TextView>(R.id.tvEmailSeleccionadoGoogle)
        val tilPassword = dialog.findViewById<TextInputLayout>(R.id.tilPasswordGoogleDialog)
        val edtPassword = dialog.findViewById<TextInputEditText>(R.id.edtPasswordGoogleDialog)
        val tvOlvidaste = dialog.findViewById<TextView>(R.id.tvOlvidastePasswordGoogle)
        val btnConfirmar = dialog.findViewById<MaterialButton>(R.id.btnConfirmarDialogGoogle)
        val btnCancelar = dialog.findViewById<TextView>(R.id.btnCancelarDialogGoogle)
        val btnCerrar = dialog.findViewById<TextView>(R.id.btnCerrarDialogGoogle)

        tvTitulo?.text = "Configura tu contraseña"
        tvMensaje?.text = "Crea una contraseña segura (mín. 8 caracteres, una mayúscula y un número). Luego enviaremos un código de 6 dígitos a tu correo para verificar que es real:"
        tvEmail.text = email
        tvOlvidaste.visibility = View.GONE
        btnConfirmar.text = "Enviar código de verificación"

        edtPassword.doOnTextChanged { _, _, _, _ ->
            tilPassword.error = null
        }

        val cancelar = {
            dialog.dismiss()
            if (cont.isActive) cont.resume(null)
        }

        btnCancelar.setOnClickListener { cancelar() }
        btnCerrar.setOnClickListener { cancelar() }
        dialog.setOnCancelListener {
            if (cont.isActive) cont.resume(null)
        }

        btnConfirmar.setOnClickListener {
            val pwd = edtPassword.text?.toString()?.trim() ?: ""
            val tieneMayus = Regex("[A-Z]").containsMatchIn(pwd)
            val tieneNum = Regex("[0-9]").containsMatchIn(pwd)
            if (pwd.length < 8 || !tieneMayus || !tieneNum) {
                tilPassword.error = "Mín. 8 caracteres, una mayúscula y un número"
                edtPassword.requestFocus()
                return@setOnClickListener
            }
            dialog.dismiss()
            if (cont.isActive) cont.resume(pwd)
        }

        dialog.show()
    }

    private suspend fun verificarCodigoOtpRegistroGoogleMD3(
        activity: ComponentActivity,
        request: RegisterRequest,
        cooldownInicialSegundos: Int
    ): Boolean = suspendCancellableCoroutine { cont ->
        val dialog = Dialog(activity)
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
        var timer: CountDownTimer? = null

        fun iniciarConteo(segundos: Int) {
            timer?.cancel()
            btnReenviar.isEnabled = false
            timer = object : CountDownTimer(segundos * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val seg = (millisUntilFinished / 1000L).coerceAtLeast(1L)
                    btnReenviar.text = "Reenviar código en ${seg}s"
                }

                override fun onFinish() {
                    btnReenviar.isEnabled = true
                    btnReenviar.text = activity.getString(R.string.btn_reenviar_codigo)
                }
            }.start()
        }

        iniciarConteo(cooldownInicialSegundos)

        val cancelar = {
            timer?.cancel()
            dialog.dismiss()
            if (cont.isActive) cont.resume(false)
        }

        txtCerrar.setOnClickListener { cancelar() }
        txtCorregir.setOnClickListener { cancelar() }
        dialog.setOnCancelListener {
            timer?.cancel()
            if (cont.isActive) cont.resume(false)
        }
        cont.invokeOnCancellation {
            timer?.cancel()
            dialog.dismiss()
        }

        btnReenviar.setOnClickListener {
            btnReenviar.isEnabled = false
            btnReenviar.text = "Reenviando..."
            activity.lifecycleScope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.solicitarCodigoRegistro(request)
                        }
                    }
                    if (resp.isSuccessful) {
                        val nuevoCooldown = resp.body()?.cooldownSegundos ?: 60
                        mostrarMensajeMD3(
                            activity,
                            resp.body()?.mensaje ?: "Nuevo código enviado a tu correo",
                            esError = false
                        )
                        iniciarConteo(nuevoCooldown)
                    } else {
                        val errStr = resp.errorBody()?.string()
                        mostrarMensajeMD3(
                            activity,
                            extraerMensajeJsonSeguro(errStr) ?: "No se pudo reenviar el código",
                            esError = true
                        )
                        iniciarConteo(15)
                    }
                } catch (_: Exception) {
                    mostrarMensajeMD3(activity, "Error de conexión al reenviar el código", esError = true)
                    btnReenviar.isEnabled = true
                    btnReenviar.text = activity.getString(R.string.btn_reenviar_codigo)
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

            activity.lifecycleScope.launch {
                try {
                    val confirmReq = ConfirmarCodigoRegistroRequest(
                        email = request.email,
                        codigo = codigo
                    )
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.confirmarCodigoRegistro(confirmReq)
                        }
                    }
                    if (response.isSuccessful) {
                        timer?.cancel()
                        dialog.dismiss()
                        if (cont.isActive) cont.resume(true)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val msgErr = extraerMensajeJsonSeguro(errorBody) ?: "Código inválido o expirado"
                        edtCodigo.error = msgErr
                        mostrarMensajeMD3(activity, msgErr, esError = true)
                    }
                } catch (_: Exception) {
                    mostrarMensajeMD3(activity, "Error de conexión con el servidor", esError = true)
                } finally {
                    if (dialog.isShowing) {
                        btnConfirmar.isEnabled = true
                        btnConfirmar.text = activity.getString(R.string.btn_verificar_y_crear_cuenta)
                    }
                }
            }
        }

        dialog.show()
    }

    private suspend fun vincularCuentaExistenteConDialogoMD3(
        activity: ComponentActivity,
        email: String,
        esFlujoRegistro: Boolean = false
    ): LoginResponse? = suspendCancellableCoroutine { cont ->
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_vincular_google)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo = dialog.findViewById<TextView>(R.id.tvTituloDialogGoogle)
        val tvMensaje = dialog.findViewById<TextView>(R.id.tvMensajeDialogGoogle)
        val tvEmail = dialog.findViewById<TextView>(R.id.tvEmailSeleccionadoGoogle)
        val tilPassword = dialog.findViewById<TextInputLayout>(R.id.tilPasswordGoogleDialog)
        val edtPassword = dialog.findViewById<TextInputEditText>(R.id.edtPasswordGoogleDialog)
        val tvOlvidaste = dialog.findViewById<TextView>(R.id.tvOlvidastePasswordGoogle)
        val btnConfirmar = dialog.findViewById<MaterialButton>(R.id.btnConfirmarDialogGoogle)
        val btnCancelar = dialog.findViewById<TextView>(R.id.btnCancelarDialogGoogle)
        val btnCerrar = dialog.findViewById<TextView>(R.id.btnCerrarDialogGoogle)

        tvTitulo?.text = if (esFlujoRegistro) "Confirma tu contraseña" else "Ingresa tu contraseña"
        tvMensaje?.text = "Por seguridad, ingresa la contraseña de tu cuenta para verificar tu identidad:"
        tvEmail.text = email
        val textoBoton = if (esFlujoRegistro) "Verificar Cuenta" else "Iniciar Sesión"
        btnConfirmar.text = textoBoton

        edtPassword.doOnTextChanged { _, _, _, _ ->
            tilPassword.error = null
        }

        val cancelar = {
            dialog.dismiss()
            if (cont.isActive) cont.resume(null)
        }

        btnCancelar.setOnClickListener { cancelar() }
        btnCerrar.setOnClickListener { cancelar() }
        dialog.setOnCancelListener {
            if (cont.isActive) cont.resume(null)
        }

        tvOlvidaste.setOnClickListener {
            dialog.dismiss()
            if (cont.isActive) cont.resume(null)
            RecuperarContrasenia(activity).show()
        }

        btnConfirmar.setOnClickListener {
            val passwordIngresada = edtPassword.text?.toString()?.trim() ?: ""
            if (passwordIngresada.isEmpty()) {
                tilPassword.error = "Ingresa tu contraseña para continuar"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            tilPassword.error = null
            btnConfirmar.isEnabled = false
            btnConfirmar.text = "Verificando..."

            activity.lifecycleScope.launch {
                try {
                    val loginResp = withContext(Dispatchers.IO) {
                        RetrofitClient.ejecutarConRespaldoLocalDebug { api ->
                            api.login(LoginRequest(email = email, password = passwordIngresada))
                        }
                    }
                    if (loginResp.isSuccessful && loginResp.body() != null) {
                        SharedPreferencesManager(activity).clearGoogleAccountPassword(email)
                        dialog.dismiss()
                        if (cont.isActive) cont.resume(loginResp.body())
                    } else {
                        val errRaw = try {
                            loginResp.errorBody()?.string()
                        } catch (_: Exception) {
                            null
                        }
                        val msgError = inicio_sesion.resolverMensajeErrorHttp(loginResp.code(), errRaw)
                        tilPassword.error = msgError
                        btnConfirmar.isEnabled = true
                        btnConfirmar.text = textoBoton
                    }
                } catch (e: Exception) {
                    tilPassword.error = "Error de conexión. Intenta nuevamente."
                    btnConfirmar.isEnabled = true
                    btnConfirmar.text = textoBoton
                }
            }
        }

        dialog.show()
    }

    private fun generarIdentificacionUnica(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase(Locale.ROOT).toByteArray())
        var num = 0L
        for (i in 0 until 6) {
            num = (num shl 8) or (digest[i].toLong() and 0xFF)
        }
        return (1000000000L + (kotlin.math.abs(num) % 8999999999L)).toString()
    }

    private fun completarRegistroRedirigiendoALogin(
        activity: Activity,
        email: String,
        nombre: String,
        apellido: String,
        mensaje: String
    ) {
        val prefs = SharedPreferencesManager(activity)
        prefs.saveUserData(
            name = nombre,
            email = email,
            phone = prefs.getUserPhone()
        )
        prefs.saveUserLastName(apellido)

        val destino = Intent(activity, inicio_sesion::class.java).apply {
            putExtra("REGISTRO_GOOGLE_MSG", mensaje)
            putExtra("EMAIL_REGISTRADO", email)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        activity.startActivity(destino)
        activity.finish()
    }

    private fun completarInicioDeSesionExitoso(
        activity: Activity,
        datos: LoginResponse,
        credencial: CredencialGoogleExtraida
    ) {
        val accessToken = datos.tokens.access.trim()
        val refreshToken = datos.tokens.refresh.trim()

        RetrofitClient.authToken = accessToken
        SesionExpiradaManager.refreshMuerto = null

        val prefs = SharedPreferencesManager(activity)
        prefs.limpiarDatosGoogleLegacy()
        prefs.saveUserData(
            name = datos.nombre ?: credencial.nombre,
            email = datos.email,
            phone = datos.telefono?.trim().orEmpty()
        )
        prefs.saveUserLastName(datos.apellido ?: credencial.apellido)
        if (!datos.matriculaProfesional.isNullOrBlank()) {
            prefs.saveUserMatriculaProfesional(datos.matriculaProfesional)
        }
        prefs.saveProfileImagePath(datos.fotoPerfil ?: datos.foto ?: "")
        prefs.saveAccessToken(accessToken)
        if (refreshToken.isNotEmpty()) {
            prefs.saveRefreshToken(refreshToken)
            FacialStorageManager(activity).actualizarRefreshTokenSiCoincide(
                userId = datos.idUsuario,
                nuevoRefreshToken = refreshToken,
                nombre = datos.nombre ?: credencial.nombre,
                apellido = datos.apellido ?: credencial.apellido,
                fotoPerfil = datos.fotoPerfil ?: datos.foto,
                telefono = datos.telefono?.trim(),
                matriculaProfesional = datos.matriculaProfesional
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
        val emailLimpio = credencial.email.trim().lowercase(Locale.ROOT).ifEmpty { null }
        val nombreLimpio = credencial.nombre.trim().ifEmpty { null }
        val apellidoLimpio = credencial.apellido.trim().ifEmpty { null }
        return GoogleLoginRequest(
            idToken = token,
            email = emailLimpio,
            nombre = nombreLimpio,
            apellido = apellidoLimpio
        )
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
