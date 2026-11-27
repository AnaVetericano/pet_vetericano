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
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.R
import com.example.petvetericano.Registro
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountVerificationUiTest {

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
    }

    @After
    fun tearDown() {
        Intents.release()
        RetrofitClient.restaurarConfiguracion()
        SharedPreferencesManager(appContext).clearSession()
        server.shutdown()
    }

    @Test
    fun verificacionOtp_codigoErroneoMuestraError_yCodigoValidoRedirigeALogin() {
        Intents.intending(hasComponent(inicio_sesion::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Código enviado","email":"verificar@vetericano.com","cooldown_segundos":60,"expira_en_segundos":600}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":"El código de verificación es incorrecto. Te quedan 4 intento(s).","intentos_restantes":4}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"mensaje":"Correo verificado y cuenta creada exitosamente.","email":"verificar@vetericano.com"}"""
            )
        )

        ActivityScenario.launch(Registro::class.java).use {
            onView(withId(R.id.edtxtID)).perform(scrollTo(), replaceText("Carlos"), closeSoftKeyboard())
            onView(withId(R.id.edtxtApellido)).perform(scrollTo(), replaceText("Diaz"), closeSoftKeyboard())
            onView(withId(R.id.edtxtemailorphone)).perform(scrollTo(), replaceText("1061888999"), closeSoftKeyboard())
            onView(withId(R.id.edtxtTelefono)).perform(scrollTo(), replaceText("3109998877"), closeSoftKeyboard())
            onView(withId(R.id.editTextTextEmailAddress))
                .perform(scrollTo(), replaceText("verificar@vetericano.com"), closeSoftKeyboard())
            onView(withId(R.id.edtxtPassword)).perform(scrollTo(), replaceText("ClaveSegura1"), closeSoftKeyboard())
            onView(withId(R.id.btnSignUp)).perform(scrollTo(), click())

            AuthUiTestHelper.esperarHasta { server.requestCount >= 1 }
            onView(withId(R.id.edtCodigoVerificacionRegistro)).inRoot(isDialog()).check(matches(isDisplayed()))

            // Código erróneo
            onView(withId(R.id.edtCodigoVerificacionRegistro))
                .inRoot(isDialog())
                .perform(replaceText("000000"), closeSoftKeyboard())
            onView(withId(R.id.btnConfirmarCodigoRegistro)).inRoot(isDialog()).perform(click())

            AuthUiTestHelper.esperarAsercion {
                onView(withId(R.id.edtCodigoVerificacionRegistro))
                    .inRoot(isDialog())
                    .check(matches(hasErrorText(containsString("incorrecto"))))
            }

            // Código válido
            onView(withId(R.id.edtCodigoVerificacionRegistro))
                .inRoot(isDialog())
                .perform(replaceText("123456"), closeSoftKeyboard())
            onView(withId(R.id.btnConfirmarCodigoRegistro)).inRoot(isDialog()).perform(click())

            AuthUiTestHelper.esperarAsercion {
                Intents.intended(
                    allOf(
                        hasComponent(inicio_sesion::class.java.name),
                        hasExtra("EMAIL_REGISTRADO", "verificar@vetericano.com")
                    )
                )
            }
        }
    }

    @Test
    fun loginUsuarioInactivoOSinVerificar_noGuardaSesion() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"non_field_errors":["Esta cuenta ha sido desactivada."]}"""
            )
        )

        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.editTextText))
                .perform(scrollTo(), replaceText("inactivo@vetericano.com"), closeSoftKeyboard())
            onView(withId(R.id.edtPassword))
                .perform(scrollTo(), replaceText("ClaveSegura1"), closeSoftKeyboard())
            onView(withId(R.id.btnIniciarSesion)).perform(scrollTo(), click())

            AuthUiTestHelper.esperarHasta { server.requestCount >= 1 }
            assertTrue(SharedPreferencesManager(appContext).getAccessToken().isEmpty())
        }
    }
}
