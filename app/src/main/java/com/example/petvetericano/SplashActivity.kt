package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivitySplashBinding
import com.example.petvetericano.network.RetrofitClient

class SplashActivity : AppCompatActivity() {

    private var binding: ActivitySplashBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var navegacionIniciada = false
    private var pausadoDuranteSplash = false
    private val navegacionRunnable = Runnable { navegarADestino(conTransicion = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isDarkMode = aplicarModoOscuro()
        super.onCreate(savedInstanceState)

        // Si la app ya estaba abierta en segundo plano y el usuario toca el launcher, retoma la tarea actual
        if (!isTaskRoot) {
            finish()
            return
        }

        val barStyle = if (isDarkMode) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )

        val animatorScale = runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)

        if (animatorScale <= 0f) {
            navegarADestino(conTransicion = false)
            return
        }

        val inflated = ActivitySplashBinding.inflate(layoutInflater)
        binding = inflated
        setContentView(inflated.root)

        val paddingIzq = inflated.splashRoot.paddingLeft
        val paddingSup = inflated.splashRoot.paddingTop
        val paddingDer = inflated.splashRoot.paddingRight
        val paddingInf = inflated.splashRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(inflated.splashRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                paddingIzq + bars.left,
                paddingSup + bars.top,
                paddingDer + bars.right,
                paddingInf + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(inflated.splashRoot)

        iniciarAnimacionEntrada(inflated, animatorScale)
    }

    override fun onResume() {
        super.onResume()
        if (pausadoDuranteSplash && !navegacionIniciada) {
            pausadoDuranteSplash = false
            navegarADestino(conTransicion = true)
        }
    }

    override fun onStop() {
        super.onStop()
        cancelarPendientes()
        if (!navegacionIniciada && !isFinishing) {
            pausadoDuranteSplash = true
        }
    }

    override fun onDestroy() {
        cancelarPendientes()
        binding = null
        super.onDestroy()
    }

    private fun iniciarAnimacionEntrada(
        inflated: ActivitySplashBinding,
        animatorScale: Float
    ) {
        (inflated.imgSplashEmblem.drawable as? Animatable)?.start()

        val offsetWordmarkPx = 8f * resources.displayMetrics.density
        inflated.tvSplashBrandName.alpha = 0f
        inflated.tvSplashBrandName.translationY = offsetWordmarkPx
        inflated.tvSplashBrandName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(WORDMARK_START_DELAY_MS)
            .setDuration(WORDMARK_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val delayMs = (SPLASH_TOTAL_DURATION_MS * animatorScale)
            .toLong()
            .coerceIn(180L, 2500L)
        mainHandler.postDelayed(navegacionRunnable, delayMs)
    }

    private fun cancelarPendientes() {
        mainHandler.removeCallbacks(navegacionRunnable)
        binding?.tvSplashBrandName?.animate()?.cancel()
        (binding?.imgSplashEmblem?.drawable as? Animatable)?.let { anim ->
            if (anim.isRunning) anim.stop()
        }
    }

    private fun navegarADestino(conTransicion: Boolean) {
        if (navegacionIniciada || isFinishing || isDestroyed) return
        navegacionIniciada = true
        mainHandler.removeCallbacks(navegacionRunnable)

        val prefs = SharedPreferencesManager(this)
        val accessToken = prefs.getAccessToken()
        val refreshToken = prefs.getRefreshToken()
        val userId = prefs.getUserId()
        val userRole = prefs.getUserRole()

        val tieneToken = accessToken.isNotEmpty() || refreshToken.isNotEmpty()
        val rolValido = userRole > 0

        val destino = if (tieneToken && userId > 0 && rolValido) {
            if (accessToken.isNotEmpty()) {
                RetrofitClient.authToken = accessToken
            }
            if (userRole == inicio_sesion.ROL_VETERINARIO) {
                Intent(this, dahsboard_veterianrio::class.java)
            } else {
                Intent(this, bienvenida::class.java)
            }
        } else {
            Intent(this, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val options = if (conTransicion) {
            ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.splash_fade_in,
                R.anim.splash_fade_out
            ).toBundle()
        } else {
            ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
        }

        startActivity(destino, options)
        finish()
    }

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

    companion object {
        private const val WORDMARK_START_DELAY_MS = 480L
        private const val WORDMARK_DURATION_MS = 420L
        private const val SPLASH_TOTAL_DURATION_MS = 1000L
    }
}
