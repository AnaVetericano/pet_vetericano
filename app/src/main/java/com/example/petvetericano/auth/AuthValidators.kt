package com.example.petvetericano.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * Validadores y parsers de errores de autenticación en Kotlin puro (sin dependencias de android.* ni org.json)
 * para garantizar comportamiento idéntico en producción y pruebas unitarias JVM.
 */
object AuthValidators {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )
    private val IDENTIFICACION_DIGITOS_REGEX = Regex("^[0-9]{5,20}$")
    private val TELEFONO_DIGITOS_REGEX = Regex("^[0-9]{10,15}$")
    private val OTP_REGEX = Regex("^[0-9]{6}$")
    private val MAYUSCULA_REGEX = Regex("[A-Z]")
    private val DIGITO_REGEX = Regex("[0-9]")

    data class ResultadoValidacionDatosGoogle(
        val errorIdentificacion: String? = null,
        val errorTelefono: String? = null
    ) {
        val esValido: Boolean
            get() = errorIdentificacion == null && errorTelefono == null
    }

    data class ResultadoValidacionRegistro(
        val errorNombre: String? = null,
        val errorApellido: String? = null,
        val errorDocumento: String? = null,
        val errorTelefono: String? = null,
        val errorEmail: String? = null,
        val errorPassword: String? = null
    ) {
        val esValido: Boolean
            get() = errorNombre == null &&
                errorApellido == null &&
                errorDocumento == null &&
                errorTelefono == null &&
                errorEmail == null &&
                errorPassword == null
    }

    data class ErrorRegistroParseado(
        val mensajeGeneral: String,
        val errorEmail: String? = null,
        val errorDocumento: String? = null,
        val errorTelefono: String? = null,
        val errorPassword: String? = null,
        val retryAfterSegundos: Int? = null,
        val intentosRestantes: Int? = null,
        val esCorreoDuplicado: Boolean = false
    )

    fun esEmailValido(email: String?): Boolean {
        val limpio = email?.trim().orEmpty()
        return limpio.isNotEmpty() && limpio.length <= 150 && EMAIL_REGEX.matches(limpio)
    }

    fun esIdentificacionValida(identificacion: String?): Boolean {
        val limpio = identificacion?.trim().orEmpty()
        return IDENTIFICACION_DIGITOS_REGEX.matches(limpio)
    }

    fun esTelefonoValido(telefono: String?): Boolean {
        val limpio = telefono?.trim().orEmpty()
        return TELEFONO_DIGITOS_REGEX.matches(limpio)
    }

    fun validarDatosComplementariosGoogle(
        identificacion: String,
        telefono: String
    ): ResultadoValidacionDatosGoogle {
        val docLimpio = identificacion.trim()
        val telLimpio = telefono.trim()

        val errorDoc = when {
            docLimpio.isEmpty() -> "Ingresa tu número de identificación"
            !esIdentificacionValida(docLimpio) -> "Ingresa una identificación válida (5 a 20 dígitos)"
            else -> null
        }
        val errorTel = when {
            telLimpio.isEmpty() -> "Ingresa tu número de teléfono"
            !esTelefonoValido(telLimpio) -> "Ingresa un número de teléfono válido (10 a 15 dígitos)"
            else -> null
        }
        return ResultadoValidacionDatosGoogle(
            errorIdentificacion = errorDoc,
            errorTelefono = errorTel
        )
    }

    fun esPasswordFuerte(password: String?): Boolean {
        val limpio = password?.trim().orEmpty()
        return limpio.length >= 8 &&
            MAYUSCULA_REGEX.containsMatchIn(limpio) &&
            DIGITO_REGEX.containsMatchIn(limpio)
    }

    fun esCodigoOtpValido(codigo: String?): Boolean {
        val limpio = codigo?.trim().orEmpty()
        return OTP_REGEX.matches(limpio)
    }

    fun validarFormularioRegistro(
        nombre: String,
        apellido: String,
        identificacion: String,
        telefono: String,
        email: String,
        password: String
    ): ResultadoValidacionRegistro {
        val nombreLimpio = nombre.trim()
        val apellidoLimpio = apellido.trim()
        val docLimpio = identificacion.trim()
        val telLimpio = telefono.trim()
        val emailLimpio = email.trim()
        val passLimpia = password.trim()

        if (nombreLimpio.isEmpty()) {
            return ResultadoValidacionRegistro(errorNombre = "Ingrese su nombre")
        }
        if (apellidoLimpio.isEmpty()) {
            return ResultadoValidacionRegistro(errorApellido = "Ingrese su apellido")
        }
        if (docLimpio.isEmpty()) {
            return ResultadoValidacionRegistro(errorDocumento = "Ingrese su documento o ID")
        }
        if (docLimpio.length > 20) {
            return ResultadoValidacionRegistro(errorDocumento = "El documento no puede superar 20 caracteres")
        }
        if (!esTelefonoValido(telLimpio)) {
            return ResultadoValidacionRegistro(
                errorTelefono = "Ingrese un número de teléfono válido (10 a 15 dígitos)"
            )
        }
        if (emailLimpio.isEmpty()) {
            return ResultadoValidacionRegistro(errorEmail = "Ingrese su correo electrónico")
        }
        if (!esEmailValido(emailLimpio)) {
            return ResultadoValidacionRegistro(errorEmail = "Ingrese un correo electrónico válido")
        }
        if (passLimpia.isEmpty()) {
            return ResultadoValidacionRegistro(errorPassword = "Ingrese una contraseña")
        }
        if (!esPasswordFuerte(passLimpia)) {
            return ResultadoValidacionRegistro(
                errorPassword = "Mín. 8 caracteres, una mayúscula y un número"
            )
        }
        return ResultadoValidacionRegistro()
    }

    fun parsearErrorRegistroHttp(code: Int, errorRaw: String?): ErrorRegistroParseado {
        val trimmed = errorRaw?.trim().orEmpty()
        val json = if (trimmed.startsWith("{")) {
            try {
                JsonParser.parseString(trimmed).asJsonObject
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val retryAfter = extraerEntero(json, "retry_after")
        val intentosRestantes = extraerEntero(json, "intentos_restantes")

        if (code == 502) {
            val msgJson = extraerMensajePrincipal(json)
            val msgFinal = when {
                msgJson != null && msgJson.contains("Application failed to respond", ignoreCase = true) ->
                    "El servidor en la nube (Railway) no está disponible en este momento (502)."
                !msgJson.isNullOrBlank() -> msgJson
                else -> "No fue posible enviar el correo de verificación (502). Intenta nuevamente."
            }
            return ErrorRegistroParseado(
                mensajeGeneral = msgFinal,
                retryAfterSegundos = retryAfter,
                intentosRestantes = intentosRestantes
            )
        }

        if (code in 500..599) {
            val msgJson = extraerMensajePrincipal(json)
            val msgFinal = if (code == 503 && !msgJson.isNullOrBlank()) {
                msgJson
            } else {
                "Error del servidor ($code). Intenta nuevamente más tarde."
            }
            return ErrorRegistroParseado(
                mensajeGeneral = msgFinal,
                retryAfterSegundos = retryAfter,
                intentosRestantes = intentosRestantes
            )
        }

        if (json == null) {
            val fallback = when (code) {
                429 -> "Demasiados intentos. Espera un momento antes de volver a intentarlo."
                404, 405 -> "Servicio de registro no disponible ($code)."
                else -> "Error al registrar usuario (código $code)."
            }
            return ErrorRegistroParseado(
                mensajeGeneral = fallback,
                retryAfterSegundos = retryAfter,
                intentosRestantes = intentosRestantes
            )
        }

        val errEmail = extraerCampoOArray(json, "email")
        val errDoc = extraerCampoOArray(json, "identificacion")
        val errTel = extraerCampoOArray(json, "telefono")
        val errPass = extraerCampoOArray(json, "password")
        val errCodigo = extraerCampoOArray(json, "codigo")
        val principal = extraerMensajePrincipal(json)

        val esCorreoDup = detectarCorreoDuplicado(errEmail, principal)

        val mensaje = listOfNotNull(
            principal,
            errEmail,
            errDoc,
            errTel,
            errPass,
            errCodigo,
            extraerPrimerCampoDesconocido(json)
        ).firstOrNull { it.isNotBlank() }
            ?: if (code == 429) {
                "Demasiados intentos. Espera un momento antes de volver a intentarlo."
            } else {
                "Datos inválidos. Verifica la información ingresada."
            }

        return ErrorRegistroParseado(
            mensajeGeneral = mensaje.take(240),
            errorEmail = errEmail,
            errorDocumento = errDoc,
            errorTelefono = errTel,
            errorPassword = errPass,
            retryAfterSegundos = retryAfter,
            intentosRestantes = intentosRestantes,
            esCorreoDuplicado = esCorreoDup
        )
    }

    fun parsearErrorGenericoHttp(
        code: Int,
        errorRaw: String?,
        fallback: String = "Error al procesar la solicitud"
    ): String {
        val parsed = parsearErrorRegistroHttp(code, errorRaw)
        return parsed.mensajeGeneral.ifBlank { fallback }
    }

    private fun detectarCorreoDuplicado(errEmail: String?, principal: String?): Boolean {
        val textos = listOfNotNull(errEmail, principal).map { it.lowercase(Locale.ROOT) }
        return textos.any { txt ->
            (txt.contains("correo") || txt.contains("email")) &&
                (txt.contains("ya existe") || txt.contains("already") || txt.contains("registrado"))
        }
    }

    private fun extraerMensajePrincipal(json: JsonObject?): String? {
        if (json == null) return null
        val claves = listOf("error", "detail", "mensaje", "message", "non_field_errors")
        for (clave in claves) {
            val valor = extraerCampoOArray(json, clave)
            if (!valor.isNullOrBlank()) return valor
        }
        return null
    }

    private fun extraerCampoOArray(json: JsonObject, clave: String): String? {
        val el = json.get(clave) ?: return null
        if (el.isJsonNull) return null
        if (el.isJsonPrimitive) {
            return el.asString.trim().takeIf { it.isNotEmpty() && it != "null" }
        }
        if (el.isJsonArray && el.asJsonArray.size() > 0) {
            val first = el.asJsonArray.get(0)
            if (first != null && first.isJsonPrimitive) {
                return first.asString.trim().takeIf { it.isNotEmpty() && it != "null" }
            }
        }
        return null
    }

    private fun extraerEntero(json: JsonObject?, clave: String): Int? {
        if (json == null) return null
        val el = json.get(clave) ?: return null
        if (!el.isJsonPrimitive) return null
        return try {
            el.asInt
        } catch (_: Exception) {
            null
        }
    }

    private fun extraerPrimerCampoDesconocido(json: JsonObject): String? {
        val ignorar = setOf("status", "code", "request_id", "retry_after", "intentos_restantes")
        for ((clave, _) in json.entrySet()) {
            if (clave in ignorar) continue
            val valor = extraerCampoOArray(json, clave)
            if (!valor.isNullOrBlank()) return valor
        }
        return null
    }
}
