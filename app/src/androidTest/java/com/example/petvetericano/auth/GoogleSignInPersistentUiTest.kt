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
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.GoogleAuthHelper
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.dahsboard_veterianrio
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleSignInPersistentUiTest {

    private lateinit var server: MockWebServer
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        SharedPreferencesManager(appContext).clearSession()
        GoogleLinkedSessionVault(appContext).limpiarTodo()
        RetrofitClient.authToken = null

        server = MockWebServer()
        server.start()
        RetrofitClient.configurarParaPruebas(
            baseUrl = server.url("/api/").toString(),
            deshabilitarRespaldoLocal = true
        )
        Intents.init()
        Intents.intending(hasComponent(dahsboard_veterianrio::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        GoogleAuthHelper.restaurarConfiguracion()
        RetrofitClient.restaurarConfiguracion()
        SharedPreferencesManager(appContext).clearSession()
        GoogleLinkedSessionVault(appContext).limpiarTodo()
        server.shutdown()
    }

    @Test
    fun googleSignInOAuth_primerInicio_logout_segundoInicio_noPideContrasena() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImpkcXVpbnRlcm9AdW5pY2F1Y2EuZWR1LmNvIn0.sigval"
        var passwordPrompts = 0
        GoogleAuthHelper.configurarParaPruebas(
            proveedorToken = {
                GoogleAuthHelper.ResultadoObtenerToken.Exito(
                    GoogleAuthHelper.CredencialGoogleExtraida(
                        idToken = jwt,
                        email = "jdquintero@unicauca.edu.co",
                        nombre = "Juan",
                        apellido = "Quintero"
                    )
                )
            },
            onDialogoPasswordMostrado = { passwordPrompts++ }
        )

        val loginBody1 = """
            {
              "mensaje": "Bienvenido, Juan Quintero",
              "email": "jdquintero@unicauca.edu.co",
              "id_usuario": 42,
              "nombre": "Juan",
              "apellido": "Quintero",
              "telefono": "3123456789",
              "matricula_profesional": "MV-9001",
              "id_rol": 3,
              "es_nuevo_usuario": true,
              "tokens": {"refresh": "ref-google-1", "access": "acc-google-1"}
            }
        """.trimIndent()
        val loginBody2 = loginBody1.replace("\"es_nuevo_usuario\": true", "\"es_nuevo_usuario\": false")
            .replace("acc-google-1", "acc-google-2")
            .replace("ref-google-1", "ref-google-2")

        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody2))

        // 1er inicio con Google
        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-google-1"
            }
        }
        assertEquals(0, passwordPrompts)

        // Simula cerrar sesión desde Perfil Profesional
        runBlocking { GoogleAuthHelper.cerrarSesionGoogle(appContext) }
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null
        assertTrue(SharedPreferencesManager(appContext).getAccessToken().isEmpty())

        // 2do inicio con Google tras logout: no debe pedir contraseña
        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-google-2"
            }
        }
        assertEquals(0, passwordPrompts)
        AuthUiTestHelper.esperarAsercion {
            Intents.intended(hasComponent(dahsboard_veterianrio::class.java.name), Intents.times(2))
        }
    }

    @Test
    fun respaldoGoogleMD3_primeraVerificacionUnaVez_trasLogoutNoVuelveAPedirContrasena() {
        var passwordPrompts = 0
        GoogleAuthHelper.configurarParaPruebas(
            proveedorToken = {
                GoogleAuthHelper.ResultadoObtenerToken.Error(
                    mensajeUsuario = "Fallo simulado OAuth [28444]",
                    usarRespaldoSelectorMD3 = true
                )
            },
            selectorCuentaMd3 = {
                GoogleAuthHelper.CredencialGoogleExtraida(
                    idToken = "",
                    email = "jdquintero@unicauca.edu.co",
                    nombre = "Juan",
                    apellido = "Quintero"
                )
            },
            onDialogoPasswordMostrado = { passwordPrompts++ }
        )

        // 1er login: POST /api/usuarios/login/ tras ingresar contraseña una sola vez
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Juan Quintero",
                  "email": "jdquintero@unicauca.edu.co",
                  "id_usuario": 42,
                  "nombre": "Juan",
                  "apellido": "Quintero",
                  "telefono": "3123456789",
                  "matricula_profesional": "MV-9001",
                  "id_rol": 3,
                  "tokens": {"refresh": "ref-vinculado-1", "access": "acc-vinculado-1"}
                }
                """.trimIndent()
            )
        )
        // 2do login tras logout: POST /api/token/refresh/ automático sin pedir contraseña
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access":"acc-vinculado-2","refresh":"ref-vinculado-2"}"""
            )
        )

        // 1er inicio: solicita contraseña una sola vez y guarda vínculo cifrado en Android Keystore
        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            onView(withId(R.id.edtPasswordGoogleDialog)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.edtPasswordGoogleDialog))
                .inRoot(isDialog())
                .perform(replaceText("ClaveSegura123"), closeSoftKeyboard())
            onView(withId(R.id.btnConfirmarDialogGoogle)).inRoot(isDialog()).perform(click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-vinculado-1"
            }
        }
        assertEquals(1, passwordPrompts)

        // Cierre de sesión desde Perfil Profesional
        runBlocking { GoogleAuthHelper.cerrarSesionGoogle(appContext) }
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null

        // 2do inicio con la misma cuenta: NO debe abrir el modal de contraseña
        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-vinculado-2"
            }
        }
        assertEquals("No debe volver a pedir contraseña en el segundo inicio", 1, passwordPrompts)
        assertEquals("ref-vinculado-2", SharedPreferencesManager(appContext).getRefreshToken())
        AuthUiTestHelper.esperarAsercion {
            Intents.intended(hasComponent(dahsboard_veterianrio::class.java.name), Intents.times(2))
        }
    }
}
