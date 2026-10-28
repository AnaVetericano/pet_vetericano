package com.example.petvetericano

import android.content.Context
import android.os.SystemClock
import android.text.InputFilter
import android.text.Spanned
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast

/**
 * Utilidades centralizadas para validación y filtrado de campos en el Acta de Seres Sintientes.
 * Permite evitar letras en campos numéricos, números en campos de texto,
 * validar formatos de correo y límites de caracteres con alertas Toast controladas (anti-spam).
 */
object ActaFieldValidators {

    private var ultimoToastTiempo: Long = 0L
    private var ultimoMensajeToast: String = ""
    private const val INTERVALO_MINIMO_TOAST_MS = 1400L

    /**
     * Muestra un Toast evitando que se sature la pantalla si el usuario teclea rápido
     * o mantiene presionada una tecla.
     */
    fun mostrarToastAlerta(context: Context?, mensaje: String) {
        if (context == null) return
        val ahora = SystemClock.elapsedRealtime()
        if (ahora - ultimoToastTiempo < INTERVALO_MINIMO_TOAST_MS && ultimoMensajeToast == mensaje) {
            return
        }
        ultimoToastTiempo = ahora
        ultimoMensajeToast = mensaje
        Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
    }

    /**
     * Filtro para campos donde solo se permiten letras (nombres, cargos, etc.)
     * Bloquea números y caracteres especiales inadecuados.
     */
    class SoloLetrasInputFilter(
        private val context: Context?,
        private val maxLength: Int,
        private val nombreCampo: String
    ) : InputFilter {

        // Permite letras (incluyendo tildes, ñ, ü), espacios y signos habituales en nombres (punto, guión, apóstrofe)
        private val regexLetras = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s\\.\\-\\']+$")

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val textoIngresado = source.subSequence(start, end).toString()

            // 1. Si no hay texto nuevo (por ejemplo borrado), permitir
            if (textoIngresado.isEmpty()) return null

            // 2. Verificar que solo contenga letras
            if (!regexLetras.matches(textoIngresado)) {
                mostrarToastAlerta(context, "Solo se reciben letras en: $nombreCampo")
                return ""
            }

            // 3. Verificar límite de caracteres
            val caracteresActuales = dest.length - (dend - dstart)
            val espacioDisponible = maxLength - caracteresActuales
            if (espacioDisponible <= 0) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength caracteres) en: $nombreCampo")
                return ""
            }

            if (textoIngresado.length > espacioDisponible) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength caracteres) en: $nombreCampo")
                return textoIngresado.substring(0, espacioDisponible)
            }

            return null
        }
    }

    /**
     * Filtro para campos donde solo se permiten dígitos numéricos (cédula, teléfono, edad, etc.)
     * Bloquea letras y símbolos.
     */
    class SoloNumerosInputFilter(
        private val context: Context?,
        private val maxLength: Int,
        private val nombreCampo: String
    ) : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val textoIngresado = source.subSequence(start, end).toString()
            if (textoIngresado.isEmpty()) return null

            // 1. Verificar que solo contenga dígitos del 0 al 9
            for (c in textoIngresado) {
                if (!c.isDigit()) {
                    mostrarToastAlerta(context, "Solo se reciben números en: $nombreCampo")
                    return ""
                }
            }

            // 2. Control de longitud máxima
            val caracteresActuales = dest.length - (dend - dstart)
            val espacioDisponible = maxLength - caracteresActuales
            if (espacioDisponible <= 0) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength dígitos) en: $nombreCampo")
                return ""
            }

            if (textoIngresado.length > espacioDisponible) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength dígitos) en: $nombreCampo")
                return textoIngresado.substring(0, espacioDisponible)
            }

            return null
        }
    }

    /**
     * Filtro para números decimales (peso del animal)
     */
    class DecimalInputFilter(
        private val context: Context?,
        private val maxLength: Int,
        private val nombreCampo: String
    ) : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val textoIngresado = source.subSequence(start, end).toString()
            if (textoIngresado.isEmpty()) return null

            // Caracteres permitidos: dígitos y un solo punto/coma
            val textoResultante = StringBuilder(dest).replace(dstart, dend, textoIngresado).toString()

            // Validar formato decimal simple
            if (!textoResultante.matches(Regex("^\\d*(\\.\\d*)?$")) && !textoResultante.matches(Regex("^\\d*(,\\d*)?$"))) {
                mostrarToastAlerta(context, "Ingrese solo números decimales válidos en: $nombreCampo")
                return ""
            }

            val caracteresActuales = dest.length - (dend - dstart)
            val espacioDisponible = maxLength - caracteresActuales
            if (espacioDisponible <= 0) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength caracteres) en: $nombreCampo")
                return ""
            }

            if (textoIngresado.length > espacioDisponible) {
                mostrarToastAlerta(context, "Límite alcanzado ($maxLength caracteres) en: $nombreCampo")
                return textoIngresado.substring(0, espacioDisponible)
            }

            return null
        }
    }

    /**
     * Filtro para campos de texto general pero con límite máximo estricto
     * (dirección, barrio, observaciones, etc.)
     */
    class LimiteTextoInputFilter(
        private val context: Context?,
        private val maxLength: Int,
        private val nombreCampo: String
    ) : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val textoIngresado = source.subSequence(start, end).toString()
            if (textoIngresado.isEmpty()) return null

            val caracteresActuales = dest.length - (dend - dstart)
            val espacioDisponible = maxLength - caracteresActuales
            if (espacioDisponible <= 0) {
                mostrarToastAlerta(context, "Límite de $maxLength caracteres alcanzado en: $nombreCampo")
                return ""
            }

            if (textoIngresado.length > espacioDisponible) {
                mostrarToastAlerta(context, "Límite de $maxLength caracteres alcanzado en: $nombreCampo")
                return textoIngresado.substring(0, espacioDisponible)
            }

            return null
        }
    }

    // --- MÉTODOS DE VALIDACIÓN LÓGICA ---

    /**
     * Valida si un formato de correo electrónico es válido según estándares.
     */
    fun esEmailValido(email: String): Boolean {
        val trimEmail = email.trim()
        if (trimEmail.isEmpty()) return false
        return Patterns.EMAIL_ADDRESS.matcher(trimEmail).matches()
    }

    /**
     * Valida un número de cédula o documento de identidad:
     * Debe contener únicamente dígitos y tener una longitud de entre 6 y 15 dígitos.
     */
    fun esCedulaValida(cedula: String): Boolean {
        val trim = cedula.trim()
        if (trim.length < 6 || trim.length > 15) return false
        return trim.all { it.isDigit() }
    }

    /**
     * Valida un número de teléfono / celular:
     * Debe tener entre 7 (fijo) y 10 dígitos (celular).
     */
    fun esTelefonoValido(telefono: String): Boolean {
        val trim = telefono.trim()
        if (trim.length < 7 || trim.length > 10) return false
        return trim.all { it.isDigit() }
    }

    /**
     * Valida que un nombre contenga solo letras y tenga al menos 3 caracteres.
     */
    fun esNombreValido(nombre: String): Boolean {
        val trim = nombre.trim()
        if (trim.length < 3) return false
        // No debe contener números
        return !trim.any { it.isDigit() }
    }
}

// Extensiones para aplicar filtros limpiamente en EditText
fun EditText.aplicarFiltroSoloLetras(maxLength: Int, nombreCampo: String) {
    val filtrosExistentes = this.filters?.filterNot { it is InputFilter.LengthFilter || it is ActaFieldValidators.SoloLetrasInputFilter }.orEmpty().toMutableList()
    filtrosExistentes.add(ActaFieldValidators.SoloLetrasInputFilter(this.context, maxLength, nombreCampo))
    this.filters = filtrosExistentes.toTypedArray()
}

fun EditText.aplicarFiltroSoloNumeros(maxLength: Int, nombreCampo: String) {
    val filtrosExistentes = this.filters?.filterNot { it is InputFilter.LengthFilter || it is ActaFieldValidators.SoloNumerosInputFilter }.orEmpty().toMutableList()
    filtrosExistentes.add(ActaFieldValidators.SoloNumerosInputFilter(this.context, maxLength, nombreCampo))
    this.filters = filtrosExistentes.toTypedArray()
}

fun EditText.aplicarFiltroDecimal(maxLength: Int, nombreCampo: String) {
    val filtrosExistentes = this.filters?.filterNot { it is InputFilter.LengthFilter || it is ActaFieldValidators.DecimalInputFilter }.orEmpty().toMutableList()
    filtrosExistentes.add(ActaFieldValidators.DecimalInputFilter(this.context, maxLength, nombreCampo))
    this.filters = filtrosExistentes.toTypedArray()
}

fun EditText.aplicarFiltroTextoConLimite(maxLength: Int, nombreCampo: String) {
    val filtrosExistentes = this.filters?.filterNot { it is InputFilter.LengthFilter || it is ActaFieldValidators.LimiteTextoInputFilter }.orEmpty().toMutableList()
    filtrosExistentes.add(ActaFieldValidators.LimiteTextoInputFilter(this.context, maxLength, nombreCampo))
    this.filters = filtrosExistentes.toTypedArray()
}
