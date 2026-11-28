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
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.bienvenida
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
class PasswordRecoveryUiTest {

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
        Intents.intending(hasComponent(bienvenida::class.java.name))
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
    fun recuperacionCompleta_prerellenaCorreo_validaCodigo_yPermiteLoginConNuevaClave() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Si el correo está registrado, recibirás un código."}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"mensaje":"Contraseña actualizada exitosamente."}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Ana",
                  "email": "ana.recuperar@vetericano.com",
                  "id_usuario": 22,
                  "nombre": "Ana",
                  "apellido": "Ruiz",
                  "telefono": "3001234567",
                  "id_rol": 4,
                  "tokens": {"refresh": "ref-rec", "access": "acc-rec"}
                }
                """.trimIndent()
            )
        )

        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.editTextText))
                .perform(scrollTo(), replaceText("ana.recuperar@vetericano.com"), closeSoftKeyboard())
            onView(withId(R.id.txtRecoverPassword)).perform(scrollTo(), click())

            // El correo escrito en login debe quedar pre-llenado en el modal de recuperación
            onView(withId(R.id.edtCorreo))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
                .check(matches(withText("ana.recuperar@vetericano.com")))

            // Paso 1: Solicitar código
            onView(withId(R.id.btnEnviarCodigo)).inRoot(isDialog()).perform(click())
            AuthUiTestHelper.esperarHasta { server.requestCount >= 1 }

            // Paso 2: Validar código + nueva contraseña
            onView(withId(R.id.edtCodigo))
                .inRoot(isDialog())
                .perform(replaceText("654321"), closeSoftKeyboard())
            onView(withId(R.id.edtNuevaContrasena))
                .inRoot(isDialog())
                .perform(replaceText("NuevaClave2026"), closeSoftKeyboard())
            onView(withId(R.id.btnCambiarContrasena)).inRoot(isDialog()).perform(click())

            AuthUiTestHelper.esperarAsercion {
                onView(withId(R.id.edtPassword))
                    .perform(scrollTo(), replaceText("NuevaClave2026"), closeSoftKeyboard())
            }
            onView(withId(R.id.btnIniciarSesion)).perform(scrollTo(), click())

            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-rec"
            }
            assertEquals("acc-rec", SharedPreferencesManager(appContext).getAccessToken())
            AuthUiTestHelper.esperarAsercion {
                Intents.intended(hasComponent(bienvenida::class.java.name))
            }
        }
    }
}
