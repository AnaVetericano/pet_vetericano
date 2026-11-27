package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.view.animation.AnimationUtils
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityMainBinding
import com.example.petvetericano.network.RetrofitClient

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar el modo oscuro ANTES de inflar cualquier vista
        val isDarkMode = aplicarModoOscuro()

        super.onCreate(savedInstanceState)
        val barStyle = if (isDarkMode) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
        configurarTransicionesEntrada()

        val prefs = SharedPreferencesManager(this)
        val accessToken = prefs.getAccessToken()
        val refreshToken = prefs.getRefreshToken()
        val userId = prefs.getUserId()
        val userRole = prefs.getUserRole()

        val tieneToken = accessToken.isNotEmpty() || refreshToken.isNotEmpty()
        val rolValido = userRole > 0

        if (tieneToken && userId > 0 && rolValido) {
            if (accessToken.isNotEmpty()) {
                RetrofitClient.authToken = accessToken
            }
            val destino = if (userRole == inicio_sesion.ROL_VETERINARIO) {
                Intent(this, dahsboard_veterianrio::class.java)
            } else {
                Intent(this, bienvenida::class.java)
            }
            destino.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(destino)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val paddingIzq = binding.main.paddingLeft
        val paddingSup = binding.main.paddingTop
        val paddingDer = binding.main.paddingRight
        val paddingInf = binding.main.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                paddingIzq + bars.left,
                paddingSup + bars.top,
                paddingDer + bars.right,
                paddingInf + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.main)

        binding.btnLoginWelcome.setOnClickListener { login() }
        binding.btnSignUpWelcome.setOnClickListener { signUp() }
    }

    private fun configurarTransicionesEntrada() {
        val fastOutSlowIn = AnimationUtils.loadInterpolator(
            this,
            android.interpolator.fast_out_slow_in
        )
        window.sharedElementEnterTransition = ChangeBounds().apply {
            duration = SHARED_TRANSITION_DURATION_MS
            interpolator = fastOutSlowIn
        }
        window.enterTransition = Fade().apply {
            duration = WINDOW_FADE_DURATION_MS
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }
        window.allowEnterTransitionOverlap = true
    }

    // Lee la preferencia guardada y aplica el tema en toda la app
    private fun aplicarModoOscuro(): Boolean {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        return isDarkMode
    }

    private fun login() {
        val intent = Intent(this, inicio_sesion::class.java)
        startActivity(intent)
    }

    private fun signUp() {
        val intent = Intent(this, Registro::class.java)
        startActivity(intent)
    }

    companion object {
        private const val SHARED_TRANSITION_DURATION_MS = 320L
        private const val WINDOW_FADE_DURATION_MS = 260L
    }
}
