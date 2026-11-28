package com.example.petvetericano.auth

import android.view.View
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

internal object AuthUiTestHelper {

    fun esperarHasta(timeoutMs: Long = 8_000L, condicion: () -> Boolean) {
        val inicio = System.currentTimeMillis()
        while (System.currentTimeMillis() - inicio < timeoutMs) {
            if (condicion()) return
            Thread.sleep(50)
        }
        if (!condicion()) {
            throw AssertionError("La condición esperada no se cumplió tras ${timeoutMs}ms.")
        }
    }

    fun esperarAsercion(timeoutMs: Long = 8_000L, asercion: () -> Unit) {
        val inicio = System.currentTimeMillis()
        var ultimoError: Throwable? = null
        while (System.currentTimeMillis() - inicio < timeoutMs) {
            try {
                asercion()
                return
            } catch (e: Throwable) {
                ultimoError = e
                Thread.sleep(100)
            }
        }
        throw ultimoError ?: AssertionError("La aserción esperada falló tras ${timeoutMs}ms.")
    }

    fun tieneErrorEnTextInputLayout(subcadenaEsperada: String? = null): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                if (subcadenaEsperada != null) {
                    description.appendText("TextInputLayout con error conteniendo '$subcadenaEsperada'")
                } else {
                    description.appendText("TextInputLayout con algún mensaje de error")
                }
            }

            override fun matchesSafely(view: View): Boolean {
                if (view !is TextInputLayout) return false
                val err = view.error?.toString().orEmpty()
                if (err.isEmpty()) return false
                return subcadenaEsperada == null || err.contains(subcadenaEsperada, ignoreCase = true)
            }
        }
    }
}
