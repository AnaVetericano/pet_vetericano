package com.example.petvetericano

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.network.RetrofitClient
import com.example.petvetericano.network.SesionExpiradaManager
import com.example.petvetericano.offline.ActaLocalRepository
import com.example.petvetericano.offline.ActaSyncScheduler
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"
    private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
    private val JWT_SEGMENT_REGEX = Regex("^[A-Za-z0-9_-]+$")
    private const val MAX_ID_TOKEN_LENGTH = 8192

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
     * Inicia el flujo oficial de Google Sign-In mediante Android Credential Manager.
     * Obtiene un Google ID Token firmado por Google y lo verifica contra POST /api/usuarios/google-login/.
     */
    suspend fun autenticarConGoogle(
        activity: ComponentActivity,
        esFlujoRegistro: Boolean = false,
        onEstadoCarga: (Boolean) -> Unit = {}
    ) {
        onEstadoCarga(true)
        try {
            when (val resultadoToken = obtenerTokenGoogle(activity)) {
                is ResultadoObtenerToken.Cancelado -> {
                    Log.d(TAG, "Inicio de sesión con Google cancelado por el usuario.")
                }
                is ResultadoObtenerToken.Error -> {
                    mostrarMensajeMD3(activity, resultadoToken.mensajeUsuario, esError = true)
                }
                is ResultadoObtenerToken.Exito -> {
                    enviarTokenAlBackend(activity, resultadoToken.credencial, esFlujoRegistro)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Fallo de conexión en autenticación con Google: ${e.javaClass.simpleName}")
            mostrarMensajeMD3(
                activity,
                inicio_sesion.resolverMensajeErrorExcepcion(e),
                esError = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en autenticación con Google: ${e.javaClass.simpleName}: ${e.message}")
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

    private suspend fun obtenerTokenGoogle(activity: ComponentActivity): ResultadoObtenerToken {
        val webClientId = obtenerWebClientId(activity)
        if (webClientId.isEmpty()) {
            Log.e(TAG, "Falta configurar google_web_client_id / default_web_client_id (Web Client ID OAuth).")
            return ResultadoObtenerToken.Error(
                "Falta configurar el Web Client ID de Google OAuth para usar el acceso con Google."
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

        val result = try {
            credentialManager.getCredential(activity, request)
        } catch (_: NoCredentialException) {
            Log.i(TAG, "Sin cuentas filtradas disponibles; intentando selector explícito GetSignInWithGoogleOption.")
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
            val explicitRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            try {
                credentialManager.getCredential(activity, explicitRequest)
            } catch (e: Exception) {
                return mapearExcepcionCredentialManager(e)
            }
        } catch (e: Exception) {
            return mapearExcepcionCredentialManager(e)
        }

        return extraerCredencialDesdeGetCredentialResponse(result)
    }

    private fun mapearExcepcionCredentialManager(e: Exception): ResultadoObtenerToken {
        if (e is GetCredentialCancellationException) {
            return ResultadoObtenerToken.Cancelado
        }
        if (e is GoogleIdTokenParsingException) {
            Log.e(TAG, "Error al interpretar GoogleIdTokenCredential: ${e.message}")
            return ResultadoObtenerToken.Error("No se pudo leer el token emitido por Google.")
        }
        if (e is NoCredentialException) {
            Log.w(TAG, "No hay cuentas de Google disponibles en el dispositivo.")
            return ResultadoObtenerToken.Error(
                "No se encontró una cuenta de Google activa en el dispositivo. Verifica tu cuenta Google o inicia con correo."
            )
        }
        if (e is GetCredentialException) {
            Log.e(TAG, "CredentialManager error (${e.type}): ${e.errorMessage}")
            val detalle = "${e.type} ${e.errorMessage}".lowercase(Locale.ROOT)
            val mensaje = if (
                detalle.contains("developer") ||
                detalle.contains("reauth") ||
                detalle.contains("10:") ||
                detalle.contains("caller not found") ||
                detalle.contains("misconfig")
            ) {
                "Error de configuración OAuth en Google Cloud/Firebase (verifica Web Client ID y SHA-1/SHA-256)."
            } else {
                "No se pudo completar la verificación con Google en el dispositivo. Intenta nuevamente."
            }
            return ResultadoObtenerToken.Error(mensaje)
        }
        Log.e(TAG, "Error inesperado obteniendo credencial Google: ${e.javaClass.simpleName}: ${e.message}")
        return ResultadoObtenerToken.Error("No se pudo obtener la credencial de Google.")
    }

    private fun extraerCredencialDesdeGetCredentialResponse(result: GetCredentialResponse): ResultadoObtenerToken {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleCred.idToken.trim()
            if (!esIdTokenJwtValido(idToken)) {
                Log.e(TAG, "El proveedor no devolvió un ID Token JWT válido.")
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

        Log.e(TAG, "Tipo de credencial inesperado: ${credential.type}")
        return ResultadoObtenerToken.Error("Credencial de Google no compatible.")
    }

    private suspend fun enviarTokenAlBackend(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida,
        esFlujoRegistro: Boolean
    ) {
        RetrofitClient.authToken = null
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
                Log.w(TAG, "Respuesta exitosa de google-login sin cuerpo o token de acceso")
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
        Log.w(TAG, "google-login rechazado por el servidor: HTTP $code")
        val msgError = resolverMensajeErrorGoogleHttp(code, errorRaw)
        mostrarMensajeMD3(activity, msgError, esError = true)
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
        }
        prefs.saveUserId(datos.idUsuario)
        prefs.saveUserRole(datos.idRol)

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
                Log.e(TAG, "Error al sincronizar actas offline pendientes tras login: ${e.message}", e)
            }
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
                extraerPrimerTextoArray(json, "id_token")
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
            Log.w(TAG, "Error al cerrar sesión en FirebaseAuth: ${e.message}")
        }
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error al limpiar estado en CredentialManager: ${e.message}")
        }
    }
}
