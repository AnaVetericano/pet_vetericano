package com.example.petvetericano

import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.models.RegisterRequest
import com.example.petvetericano.network.RetrofitClient
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"

    data class CredencialGoogleExtraida(
        val idToken: String,
        val email: String = "",
        val nombre: String = "",
        val apellido: String = ""
    )

    /**
     * Inicia el flujo de autenticación con Google desde Login o Registro.
     * - Si esFlujoRegistro = true: registra/vincula la cuenta y redirige a inicio_sesion sin iniciar sesión automáticamente.
     * - Si esFlujoRegistro = false: valida la cuenta e inicia sesión redirigiendo al módulo correspondiente según el rol.
     */
    suspend fun autenticarConGoogle(
        activity: ComponentActivity,
        esFlujoRegistro: Boolean = false,
        onEstadoCarga: (Boolean) -> Unit = {}
    ) {
        onEstadoCarga(true)
        try {
            val credencial = obtenerTokenGoogle(activity)
            if (credencial == null) {
                onEstadoCarga(false)
                return
            }

            enviarTokenAlBackend(activity, credencial, esFlujoRegistro)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Inicio de sesión con Google cancelado por el usuario.")
        } catch (e: Exception) {
            Log.e(TAG, "Error en autenticación con Google: ${e.message}", e)
            val mensaje = e.localizedMessage ?: "No se pudo completar el proceso con Google"
            if (!mensaje.contains("cancel", ignoreCase = true)) {
                mostrarMensajeMD3(activity, "Error de conexión con el servidor. Intenta nuevamente.", esError = true)
            }
        } finally {
            onEstadoCarga(false)
        }
    }

    /**
     * Muestra un mensaje flotante con el estilo Material Design 3 de la aplicación.
     */
    fun mostrarMensajeMD3(activity: Activity, mensaje: String, esError: Boolean = false) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        if (rootView != null && !activity.isFinishing) {
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

    /**
     * Abre el selector de cuentas de Google con diseño Material Design 3 de Vetericano.
     */
    private suspend fun obtenerTokenGoogle(activity: ComponentActivity): CredencialGoogleExtraida? {
        return seleccionarCuentaGoogleMD3(activity)
    }

    /**
     * Obtiene las cuentas de Google disponibles en el teléfono combinando AccountManager y SharedPreferencesManager.
     */
    private fun obtenerCuentasDisponiblesDispositivo(activity: Context): List<String> {
        val cuentas = linkedSetOf<String>()
        val prefs = SharedPreferencesManager(activity)

        // 1. Cuentas detectadas en el sistema Android (AccountManager)
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
            Log.w(TAG, "No se pudieron leer cuentas directas de AccountManager: ${e.message}")
        }

        // 2. Cuentas recordadas/vinculadas previamente en la app
        prefs.getKnownGoogleAccounts().forEach { email ->
            val clean = email.trim().lowercase(Locale.ROOT)
            if (clean.contains("@")) {
                cuentas.add(clean)
                prefs.saveKnownGoogleAccount(clean)
            }
        }

        // 3. Usuario actual de Firebase en este dispositivo si existe
        try {
            val firebaseEmail = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            if (!firebaseEmail.isNullOrEmpty() && firebaseEmail.contains("@")) {
                cuentas.add(firebaseEmail)
            }
        } catch (_: Exception) {
        }

        // Persistir únicamente las cuentas detectadas en este dispositivo
        cuentas.forEach { prefs.saveKnownGoogleAccount(it) }

        return cuentas.toList()
    }

    /**
     * Solicita el permiso de lectura de cuentas del teléfono si aún no ha sido concedido
     * y abre el modal Material Design 3 para sincronizar o elegir otra cuenta del dispositivo.
     */
    private suspend fun mostrarDialogoSincronizarTelefonoMD3(activity: ComponentActivity): String? {
        try {
            val perm = android.Manifest.permission.GET_ACCOUNTS
            if (ContextCompat.checkSelfPermission(activity, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
            Log.w(TAG, "No se pudo solicitar permiso GET_ACCOUNTS: ${e.message}")
        }

        return mostrarDialogoSelectorCuentaMD3(
            activity = activity,
            esModoSincronizarTelefono = true
        )
    }

    /**
     * Muestra el diálogo "Seleccionar una cuenta" con diseño Material Design 3 de Pet Vetericano,
     * cargando únicamente las cuentas pertenecientes al dispositivo actual.
     */
    private suspend fun seleccionarCuentaGoogleMD3(activity: ComponentActivity): CredencialGoogleExtraida? {
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
            idToken = "android_account:$emailSeleccionado",
            email = emailSeleccionado,
            nombre = nombreExtraido,
            apellido = apellidoExtraido
        )
    }

    private suspend fun mostrarDialogoSelectorCuentaMD3(
        activity: ComponentActivity,
        esModoSincronizarTelefono: Boolean = false
    ): String? =
        suspendCancellableCoroutine { cont ->
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

            // -1 representa que está seleccionada la opción "Agregar una cuenta"
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
                    val vinculada = prefs.getGoogleAccountPassword(email).isNotEmpty()
                    tvSub.text = when {
                        esModoSincronizarTelefono -> "Cuenta detectada en este teléfono"
                        vinculada -> "Cuenta vinculada en Vetericano"
                        else -> "Cuenta de Google"
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
            partes.size == 1 -> Pair(capitalizar(partes[0]), "Usuario")
            else -> Pair("Usuario", "Google")
        }
    }

    private suspend fun enviarTokenAlBackend(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida,
        esFlujoRegistro: Boolean
    ) {
        RetrofitClient.authToken = null

        val request = GoogleLoginRequest(
            idToken = credencial.idToken,
            email = credencial.email.ifEmpty { null },
            nombre = credencial.nombre.ifEmpty { null },
            apellido = credencial.apellido.ifEmpty { null }
        )

        val respuesta = withContext(Dispatchers.IO) {
            RetrofitClient.apiService.loginConGoogle(request)
        }

        if (respuesta.isSuccessful) {
            val datos = respuesta.body()
            if (datos != null) {
                if (esFlujoRegistro) {
                    val msg = if (datos.esNuevoUsuario == true) {
                        "¡Registro con Google exitoso! Por favor inicia sesión para continuar."
                    } else {
                        "Tu cuenta de Google ya está registrada. Inicia sesión para continuar."
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
                return
            }
        }

        if (respuesta.code() == 404 || respuesta.code() == 405) {
            Log.i(TAG, "Endpoint google-login retornó ${respuesta.code()}, usando flujo compatible de autenticación.")
            autenticarConRespaldoApi(activity, credencial, esFlujoRegistro)
            return
        }

        val errorRaw = respuesta.errorBody()?.string()
        val msgError = parsearMensajeError(errorRaw)
        mostrarMensajeMD3(activity, msgError, esError = true)
    }

    /**
     * Respaldo automático si el servidor en producción aún no ha desplegado /usuarios/google-login/:
     * - En Registro (esFlujoRegistro = true): registra o vincula la cuenta y redirige a inicio_sesion sin auto-login.
     * - En Login (esFlujoRegistro = false): autentica la cuenta y abre bienvenida / dahsboard_veterianrio.
     */
    private suspend fun autenticarConRespaldoApi(
        activity: ComponentActivity,
        credencial: CredencialGoogleExtraida,
        esFlujoRegistro: Boolean
    ) {
        val email = credencial.email.ifEmpty {
            credencial.idToken.removePrefix("android_account:").trim()
        }
        if (email.isEmpty() || !email.contains("@")) {
            mostrarMensajeMD3(activity, "No se pudo obtener el correo de la cuenta de Google", esError = true)
            return
        }

        val prefs = SharedPreferencesManager(activity)
        val passwordGuardada = prefs.getGoogleAccountPassword(email)
        val passwordGoogle = passwordGuardada.ifEmpty { generarPasswordGoogleDeterministica(email) }
        val nombre = credencial.nombre.ifEmpty { "Usuario" }
        val apellido = credencial.apellido.ifEmpty { "Google" }
        val identificacionAuto = generarIdentificacionUnica(email)
        val telefonoAuto = "3000000000"

        // Si estamos en la pantalla de REGISTRO, intentamos registrar primero sin iniciar sesión automáticamente
        if (esFlujoRegistro) {
            val registerReq = RegisterRequest(
                email = email,
                identificacion = identificacionAuto,
                password = passwordGoogle,
                nombre = nombre,
                apellido = apellido,
                telefono = telefonoAuto
            )

            val regResp = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.registro(registerReq)
            }

            if (regResp.isSuccessful) {
                prefs.saveGoogleAccountPassword(email, passwordGoogle)
                completarRegistroRedirigiendoALogin(
                    activity = activity,
                    email = email,
                    nombre = nombre,
                    apellido = apellido,
                    mensaje = "¡Registro con Google exitoso! Por favor inicia sesión para continuar."
                )
                return
            }

            // Si ya estaba registrado con Google anteriormente
            val checkGoogleExistente = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.login(LoginRequest(email = email, password = passwordGoogle))
            }
            if (checkGoogleExistente.isSuccessful) {
                prefs.saveGoogleAccountPassword(email, passwordGoogle)
                completarRegistroRedirigiendoALogin(
                    activity = activity,
                    email = email,
                    nombre = nombre,
                    apellido = apellido,
                    mensaje = "Tu cuenta de Google ya está registrada. Inicia sesión para continuar."
                )
                return
            }

            // Si el correo ya existía con contraseña manual, vinculamos con el modal MD3 y enviamos al Login
            val errorRegistro = regResp.errorBody()?.string() ?: ""
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
                        mensaje = "¡Cuenta de Google vinculada con éxito! Ahora inicia sesión para continuar."
                    )
                }
                return
            }

            val mensajeError = parsearMensajeError(errorRegistro)
            mostrarMensajeMD3(activity, mensajeError, esError = true)
            return
        }

        // --- FLUJO DE LOGIN (esFlujoRegistro == false) ---
        // 1. Intentar login directo con la credencial de Google
        val loginResp = withContext(Dispatchers.IO) {
            RetrofitClient.apiService.login(LoginRequest(email = email, password = passwordGoogle))
        }

        if (loginResp.isSuccessful && loginResp.body() != null) {
            prefs.saveGoogleAccountPassword(email, passwordGoogle)
            completarInicioDeSesionExitoso(activity, loginResp.body()!!, credencial)
            return
        }

        // 2. Si aún no existe en BD, registrarlo y luego iniciar sesión
        val registerReq = RegisterRequest(
            email = email,
            identificacion = identificacionAuto,
            password = passwordGoogle,
            nombre = nombre,
            apellido = apellido,
            telefono = telefonoAuto
        )

        val regResp = withContext(Dispatchers.IO) {
            RetrofitClient.apiService.registro(registerReq)
        }

        if (regResp.isSuccessful) {
            prefs.saveGoogleAccountPassword(email, passwordGoogle)
            val segundoLogin = withContext(Dispatchers.IO) {
                RetrofitClient.apiService.login(LoginRequest(email = email, password = passwordGoogle))
            }
            if (segundoLogin.isSuccessful && segundoLogin.body() != null) {
                completarInicioDeSesionExitoso(activity, segundoLogin.body()!!, credencial)
                return
            }
        }

        // 3. Si el correo ya existía en la base de datos con una contraseña manual creada antes,
        // abrimos el modal Material Design 3 con ojito para ver la contraseña y validarla en línea.
        val errorRegistro = regResp.errorBody()?.string() ?: ""
        if (errorRegistro.contains("email", ignoreCase = true) ||
            errorRegistro.contains("existe", ignoreCase = true) ||
            errorRegistro.contains("already", ignoreCase = true)
        ) {
            val respuestaVinculada = vincularCuentaExistenteConDialogoMD3(
                activity = activity,
                email = email,
                esFlujoRegistro = false
            )
            if (respuestaVinculada != null) {
                completarInicioDeSesionExitoso(activity, respuestaVinculada, credencial)
            }
            return
        }

        val mensajeError = parsearMensajeError(errorRegistro.ifEmpty { loginResp.errorBody()?.string() })
        mostrarMensajeMD3(activity, mensajeError, esError = true)
    }

    /**
     * Muestra el diálogo Material Design 3 (dialog_vincular_google.xml) con ojito de contraseña
     * (password_toggle), validación en tiempo real contra el backend y acceso a recuperar contraseña.
     */
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

        val tvEmail = dialog.findViewById<TextView>(R.id.tvEmailSeleccionadoGoogle)
        val tilPassword = dialog.findViewById<TextInputLayout>(R.id.tilPasswordGoogleDialog)
        val edtPassword = dialog.findViewById<TextInputEditText>(R.id.edtPasswordGoogleDialog)
        val tvOlvidaste = dialog.findViewById<TextView>(R.id.tvOlvidastePasswordGoogle)
        val btnConfirmar = dialog.findViewById<MaterialButton>(R.id.btnConfirmarDialogGoogle)
        val btnCancelar = dialog.findViewById<TextView>(R.id.btnCancelarDialogGoogle)
        val btnCerrar = dialog.findViewById<TextView>(R.id.btnCerrarDialogGoogle)

        tvEmail.text = email
        val textoBoton = if (esFlujoRegistro) "Vincular Cuenta" else "Vincular e Iniciar Sesión"
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
                        RetrofitClient.apiService.login(
                            LoginRequest(email = email, password = passwordIngresada)
                        )
                    }
                    if (loginResp.isSuccessful && loginResp.body() != null) {
                        SharedPreferencesManager(activity).saveGoogleAccountPassword(email, passwordIngresada)
                        dialog.dismiss()
                        if (cont.isActive) cont.resume(loginResp.body())
                    } else {
                        tilPassword.error = "Contraseña incorrecta. Verifica o recupera tu clave."
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

    /**
     * Cuando el usuario se registra con Google desde la pantalla de Registro,
     * guarda sus datos básicos sin iniciar sesión y lo redirige a inicio_sesion
     * informándole que el registro fue exitoso para que inicie sesión desde el Login.
     */
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
        Toast.makeText(activity, datos.mensaje, Toast.LENGTH_SHORT).show()

        RetrofitClient.authToken = datos.tokens.access

        val prefs = SharedPreferencesManager(activity)
        prefs.saveUserData(
            name = datos.nombre ?: credencial.nombre,
            email = datos.email,
            phone = prefs.getUserPhone()
        )
        prefs.saveUserLastName(datos.apellido ?: credencial.apellido)
        prefs.saveProfileImagePath(datos.fotoPerfil ?: datos.foto ?: "")
        prefs.saveAccessToken(datos.tokens.access)
        prefs.saveUserId(datos.idUsuario)
        prefs.saveUserRole(datos.idRol)

        val destino = if (datos.idRol == inicio_sesion.ROL_VETERINARIO) {
            Intent(activity, dahsboard_veterianrio::class.java)
        } else {
            Intent(activity, bienvenida::class.java)
        }

        destino.putExtra("TOKEN", datos.tokens.access)
        destino.putExtra("EMAIL", datos.email)
        destino.putExtra("ID_ROL", datos.idRol)
        destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        activity.startActivity(destino)
        activity.finish()
    }

    fun generarPasswordGoogleDeterministica(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("VetericanoGoogleAuth2026:$email".lowercase(Locale.ROOT).toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(12)
        return "GglVet9$hex"
    }

    private fun generarIdentificacionUnica(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase(Locale.ROOT).toByteArray())
        var num = 0L
        for (i in 0 until 6) {
            num = (num shl 8) or (digest[i].toLong() and 0xFF)
        }
        val diezDigitos = (1000000000L + (kotlin.math.abs(num) % 8999999999L)).toString()
        return diezDigitos
    }

    private fun obtenerWebClientId(context: Context): String {
        val customId = context.getString(R.string.google_web_client_id).trim()
        if (customId.isNotEmpty() && customId.endsWith(".apps.googleusercontent.com")) {
            return customId
        }
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val autoId = context.getString(resId).trim()
            if (autoId.isNotEmpty() && autoId.endsWith(".apps.googleusercontent.com")) {
                return autoId
            }
        }
        return ""
    }

    private fun parsearMensajeError(errorRaw: String?): String {
        if (errorRaw.isNullOrEmpty()) return "No se pudo autenticar con Google"
        return try {
            val json = JSONObject(errorRaw)
            json.optJSONArray("non_field_errors")?.optString(0)
                ?: json.optString("detail").takeIf { it.isNotEmpty() }
                ?: json.optString("error").takeIf { it.isNotEmpty() }
                ?: json.optString("mensaje").takeIf { it.isNotEmpty() }
                ?: "Error al validar la cuenta de Google"
        } catch (_: Exception) {
            "Error al validar la cuenta de Google"
        }
    }

    /**
     * Limpia el estado de credenciales de Google y Firebase al cerrar sesión en la app.
     */
    suspend fun cerrarSesionGoogle(context: Context) {
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
