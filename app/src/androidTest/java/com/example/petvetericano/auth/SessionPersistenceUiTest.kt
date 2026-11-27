package com.example.petvetericano.auth

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.petvetericano.MainActivity
import com.example.petvetericano.R
import com.example.petvetericano.SharedPreferencesManager
import com.example.petvetericano.bienvenida
import com.example.petvetericano.dahsboard_veterianrio
import com.example.petvetericano.inicio_sesion
import com.example.petvetericano.network.RetrofitClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPersistenceUiTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null
        Intents.init()
        Intents.intending(hasComponent(dahsboard_veterianrio::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        Intents.intending(hasComponent(bienvenida::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        SharedPreferencesManager(appContext).clearSession()
        RetrofitClient.authToken = null
    }

    @Test
    fun reinicioApp_conSesionVeterinarioActiva_redirigeADashboardProfesional() {
        val prefs = SharedPreferencesManager(appContext)
        prefs.saveAccessToken("acc-vet-persistente")
        prefs.saveRefreshToken("ref-vet-persistente")
        prefs.saveUserId(42)
        prefs.saveUserRole(inicio_sesion.ROL_VETERINARIO)

        ActivityScenario.launch(MainActivity::class.java).use {
            Intents.intended(hasComponent(dahsboard_veterianrio::class.java.name))
        }
    }

    @Test
    fun reinicioApp_soloConRefreshTokenCiudadano_redirigeABienvenida() {
        val prefs = SharedPreferencesManager(appContext)
        prefs.saveAccessToken("")
        prefs.saveRefreshToken("ref-ciudadano-valido")
        prefs.saveUserId(15)
        prefs.saveUserRole(4)

        ActivityScenario.launch(MainActivity::class.java).use {
            Intents.intended(hasComponent(bienvenida::class.java.name))
        }
    }

    @Test
    fun reinicioApp_sinSesionActiva_muestraPantallaDeBienvenida() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnLoginWelcome)).check(matches(isDisplayed()))
        }
    }
}
