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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.GoogleAuthHelper
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.bienvenida
import com.example.petvetericano.dahsboard_veterianrio
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        Intents.intending(hasComponent(bienvenida::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        GoogleAuthHelper.restaurarConfiguracion()
        RetrofitClient.restaurarConfiguracion()
        SharedPreferencesManager(appContext).clearSession()
        server.shutdown()
    }

    @Test
    fun googleSignInEstandar_usuarioCompleto_primerInicio_logout_segundoInicio_sinContrasena() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImpkcXVpbnRlcm9AdW5pY2F1Y2EuZWR1LmNvIn0.sigval"
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
            }
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
              "es_nuevo_usuario": false,
              "tokens": {"refresh": "ref-google-1", "access": "acc-google-1"}
            }
        """.trimIndent()
        val loginBody2 = loginBody1
            .replace("acc-google-1", "acc-google-2")
            .replace("ref-google-1", "ref-google-2")
        val perfilCompleto = """
            {
              "id_usuario": 42,
              "email": "jdquintero@unicauca.edu.co",
              "nombre": "Juan",
              "apellido": "Quintero",
              "identificacion": "1061789123",
              "telefono": "3123456789",
              "matricula_profesional": "MV-9001"
            }
        """.trimIndent()

        // 1er inicio: google-login + GET perfil
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(perfilCompleto))
        // 2do inicio tras logout: google-login + GET perfil
        server.enqueue(MockResponse().setResponseCode(200).setBody(loginBody2))
        server.enqueue(MockResponse().setResponseCode(200).setBody(perfilCompleto))

        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-google-1"
            }
        }

        // Cierre de sesión normal
        runBlocking { GoogleAuthHelper.cerrarSesionGoogle(appContext) }
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null
        assertTrue(SharedPreferencesManager(appContext).getAccessToken().isEmpty())

        // 2do inicio con Google tras logout: entra directo sin pedir contraseña ni modal de datos
        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())
            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-google-2"
            }
        }
        AuthUiTestHelper.esperarAsercion {
            Intents.intended(hasComponent(dahsboard_veterianrio::class.java.name), Intents.times(2))
        }
    }

    @Test
    fun googleSignInEstandar_primerIngresoSinTelefonoNiIdentificacion_exigeCompletarDatosYEntra() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6Im51ZXZvQGdtYWlsLmNvbSJ9.signuevo"
        GoogleAuthHelper.configurarParaPruebas(
            proveedorToken = {
                GoogleAuthHelper.ResultadoObtenerToken.Exito(
                    GoogleAuthHelper.CredencialGoogleExtraida(
                        idToken = jwt,
                        email = "nuevo@gmail.com",
                        nombre = "Laura",
                        apellido = "Gómez"
                    )
                )
            }
        )

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Laura Gómez",
                  "email": "nuevo@gmail.com",
                  "id_usuario": 77,
                  "nombre": "Laura",
                  "apellido": "Gómez",
                  "telefono": null,
                  "id_rol": 4,
                  "es_nuevo_usuario": true,
                  "tokens": {"refresh": "ref-nuevo-1", "access": "acc-nuevo-1"}
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id_usuario": 77,
                  "email": "nuevo@gmail.com",
                  "nombre": "Laura",
                  "apellido": "Gómez",
                  "identificacion": null,
                  "telefono": null
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id_usuario": 77,
                  "email": "nuevo@gmail.com",
                  "nombre": "Laura",
                  "apellido": "Gómez",
                  "identificacion": "1061700800",
                  "telefono": "3104567890"
                }
                """.trimIndent()
            )
        )

        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())

            onView(withId(R.id.edtIdentificacionGoogle)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.edtIdentificacionGoogle))
                .inRoot(isDialog())
                .perform(replaceText("1061700800"), closeSoftKeyboard())
            onView(withId(R.id.edtTelefonoGoogle))
                .inRoot(isDialog())
                .perform(replaceText("3104567890"), closeSoftKeyboard())
            onView(withId(R.id.btnGuardarDatosGoogle)).inRoot(isDialog()).perform(click())

            AuthUiTestHelper.esperarHasta {
                SharedPreferencesManager(appContext).getAccessToken() == "acc-nuevo-1"
            }
        }

        val prefs = SharedPreferencesManager(appContext)
        assertEquals("1061700800", prefs.getUserIdentification())
        assertEquals("3104567890", prefs.getUserPhone())
        AuthUiTestHelper.esperarAsercion {
            Intents.intended(hasComponent(bienvenida::class.java.name), Intents.times(1))
        }
    }

    @Test
    fun googleSignInEstandar_cancelarDialogoCompletarDatos_noPersisteSesion() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJlbWFpbCI6ImNhbmNlbGFkb0BnbWFpbC5jb20ifQ.sigcancel"
        GoogleAuthHelper.configurarParaPruebas(
            proveedorToken = {
                GoogleAuthHelper.ResultadoObtenerToken.Exito(
                    GoogleAuthHelper.CredencialGoogleExtraida(
                        idToken = jwt,
                        email = "cancelado@gmail.com",
                        nombre = "Carlos",
                        apellido = "Ruiz"
                    )
                )
            }
        )

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "mensaje": "Bienvenido, Carlos Ruiz",
                  "email": "cancelado@gmail.com",
                  "id_usuario": 88,
                  "nombre": "Carlos",
                  "apellido": "Ruiz",
                  "telefono": null,
                  "id_rol": 4,
                  "es_nuevo_usuario": true,
                  "tokens": {"refresh": "ref-cancel", "access": "acc-cancel"}
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id_usuario": 88,
                  "email": "cancelado@gmail.com",
                  "nombre": "Carlos",
                  "apellido": "Ruiz",
                  "identificacion": null,
                  "telefono": null
                }
                """.trimIndent()
            )
        )

        ActivityScenario.launch(inicio_sesion::class.java).use {
            onView(withId(R.id.btnGoogleLogin)).perform(scrollTo(), click())

            onView(withId(R.id.edtIdentificacionGoogle)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.btnCancelarCompletarGoogle)).inRoot(isDialog()).perform(click())

            AuthUiTestHelper.esperarHasta {
                RetrofitClient.authToken == null
            }
        }

        assertTrue(SharedPreferencesManager(appContext).getAccessToken().isEmpty())
        assertNull(RetrofitClient.authToken)
    }
}
