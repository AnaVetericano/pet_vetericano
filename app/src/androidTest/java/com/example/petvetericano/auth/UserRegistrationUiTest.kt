package com.example.petvetericano.auth

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.R
import com.example.petvetericano.Registro
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserRegistrationUiTest {

    private lateinit var server: MockWebServer
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null
        server = MockWebServer()
        server.start()
        RetrofitClient.configurarParaPruebas(
            baseUrl = server.url("/api/").toString(),
            deshabilitarRespaldoLocal = true
        )
        Intents.init()
        Intents.intending(hasComponent(inicio_sesion::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        RetrofitClient.restaurarConfiguracion()
        SharedPreferencesManager(appContext).clearSession()
        server.shutdown()
    }

    @Test
    fun registro_validacionesLocalesBloqueanEnvioSinLlamarAlServidor() {
        ActivityScenario.launch(Registro::class.java).use {
            // Envío vacío -> error en nombre
            onView(withId(R.id.btnSignUp)).perform(scrollTo(), click())
            onView(withId(R.id.tilNombre)).check(matches(AuthUiTestHelper.tieneErrorEnTextInputLayout("nombre")))

            // Correo inválido -> error inline en tilEmail
            onView(withId(R.id.edtxtID)).perform(scrollTo(), replaceText("Maria"), closeSoftKeyboard())
            onView(withId(R.id.edtxtApellido)).perform(scrollTo(), replaceText("Gomez"), closeSoftKeyboard())
            onView(withId(R.id.edtxtemailorphone)).perform(scrollTo(), replaceText("1061222333"), closeSoftKeyboard())
            onView(withId(R.id.edtxtTelefono)).perform(scrollTo(), replaceText("3001112233"), closeSoftKeyboard())
            onView(withId(R.id.editTextTextEmailAddress))
                .perform(scrollTo(), replaceText("correo-invalido"), closeSoftKeyboard())
            onView(withId(R.id.edtxtPassword)).perform(scrollTo(), replaceText("ClaveSegura1"), closeSoftKeyboard())
            onView(withId(R.id.btnSignUp)).perform(scrollTo(), click())

            onView(withId(R.id.tilEmail)).check(matches(AuthUiTestHelper.tieneErrorEnTextInputLayout("válido")))
            assertEquals("No debe llamar al servidor con correo inválido", 0, server.requestCount)
        }
    }

    @Test
    fun registro_correoDuplicado400_muestraErrorInlineYNoAbreModalOtp() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"email":["Ya existe un usuario registrado con este correo electrónico."]}"""
            )
        )

        ActivityScenario.launch(Registro::class.java).use {
            onView(withId(R.id.edtxtID)).perform(scrollTo(), replaceText("Maria"), closeSoftKeyboard())
            onView(withId(R.id.edtxtApellido)).perform(scrollTo(), replaceText("Gomez"), closeSoftKeyboard())
            onView(withId(R.id.edtxtemailorphone)).perform(scrollTo(), replaceText("1061222333"), closeSoftKeyboard())
            onView(withId(R.id.edtxtTelefono)).perform(scrollTo(), replaceText("3001112233"), closeSoftKeyboard())
            onView(withId(R.id.editTextTextEmailAddress))
                .perform(scrollTo(), replaceText("duplicado@vetericano.com"), closeSoftKeyboard())
            onView(withId(R.id.edtxtPassword)).perform(scrollTo(), replaceText("ClaveSegura1"), closeSoftKeyboard())
            onView(withId(R.id.btnSignUp)).perform(scrollTo(), click())

            AuthUiTestHelper.esperarAsercion {
                onView(withId(R.id.tilEmail)).check(matches(AuthUiTestHelper.tieneErrorEnTextInputLayout("Ya existe")))
            }
            onView(withId(R.id.edtCodigoVerificacionRegistro)).check(doesNotExist())
        }
    }
}
