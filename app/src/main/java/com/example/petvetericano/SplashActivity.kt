package com.example.petvetericano

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import com.example.petvetericano.databinding.ActivitySplashBinding
import com.example.petvetericano.network.RetrofitClient

class SplashActivity : AppCompatActivity() {

    private var binding: ActivitySplashBinding? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var animatorScale = 1f
    private var primerFrameListo = false
    private var ventanaEnfocada = false
    private var animacionIniciada = false
    private var navegacionIniciada = false
    private var pausadoDuranteSplash = false
    private var avdCallback: Animatable2.AnimationCallback? = null

    private val iniciarAnimacionRunnable = Runnable {
        val inflated = binding ?: return@Runnable
        if (!navegacionIniciada && !pausadoDuranteSplash && !isFinishing && !isDestroyed) {
            ejecutarCoreografia(inflated)
        }
    }
    private val navegacionRunnable = Runnable { navegarADestino(conTransicion = true) }
    private val fallbackRunnable = Runnable { navegarADestino(conTransicion = true) }

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
        configurarTransicionesVentana()

        val scale = runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        animatorScale = scale

        if (scale <= 0f) {
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

        inflated.tvSplashBrandName.alpha = 0f
        inflated.tvSplashBrandName.translationY = 8f * resources.displayMetrics.density

        inflated.splashRoot.doOnPreDraw {
            primerFrameListo = true
            if (hasWindowFocus()) {
                ventanaEnfocada = true
            }
            intentarIniciarAnimacion()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ventanaEnfocada = true
            intentarIniciarAnimacion()
        }
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
        if (navegacionIniciada) {
            if (!isFinishing) {
                finish()
            }
        } else if (!isFinishing) {
            pausadoDuranteSplash = true
        }
    }

    override fun onDestroy() {
        cancelarPendientes()
        binding = null
        super.onDestroy()
    }

    private fun intentarIniciarAnimacion() {
        val inflated = binding ?: return
        if (animacionIniciada || navegacionIniciada || pausadoDuranteSplash) return
        if (!primerFrameListo || !ventanaEnfocada) return

        animacionIniciada = true
        inflated.splashRoot.postOnAnimation(iniciarAnimacionRunnable)
    }

    private fun ejecutarCoreografia(inflated: ActivitySplashBinding) {
        inflated.tvSplashBrandName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(WORDMARK_START_DELAY_MS)
            .setDuration(WORDMARK_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val fallbackMs = (FALLBACK_TIMEOUT_MS * animatorScale)
            .toLong()
            .coerceIn(700L, 3500L)
        mainHandler.postDelayed(fallbackRunnable, fallbackMs)

        when (val drawable = inflated.imgSplashEmblem.drawable) {
            is AnimatedVectorDrawable -> {
                val callback = object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(d: Drawable?) {
                        limpiarCallbackAvd()
                        mainHandler.removeCallbacks(fallbackRunnable)
                        if (!navegacionIniciada && !pausadoDuranteSplash && !isFinishing && !isDestroyed) {
                            val esperaFinalMs = (POST_ANIM_HOLD_MS * animatorScale)
                                .toLong()
                                .coerceIn(80L, 600L)
                            mainHandler.postDelayed(navegacionRunnable, esperaFinalMs)
                        }
                    }
                }
                avdCallback = callback
                drawable.registerAnimationCallback(callback)
                drawable.start()
            }
            is Animatable -> {
                drawable.start()
                val delayFallbackMs = ((CHOREOGRAPHY_DURATION_MS + FINAL_PAUSE_MS) * animatorScale)
                    .toLong()
                    .coerceIn(250L, 2500L)
                mainHandler.postDelayed(navegacionRunnable, delayFallbackMs)
            }
            else -> {
                mainHandler.postDelayed(navegacionRunnable, FINAL_PAUSE_MS)
            }
        }
    }

    private fun cancelarPendientes() {
        binding?.splashRoot?.removeCallbacks(iniciarAnimacionRunnable)
        mainHandler.removeCallbacks(navegacionRunnable)
        mainHandler.removeCallbacks(fallbackRunnable)
        limpiarCallbackAvd()
        binding?.tvSplashBrandName?.animate()?.cancel()
        (binding?.imgSplashEmblem?.drawable as? Animatable)?.let { anim ->
            if (anim.isRunning) anim.stop()
        }
    }

    private fun limpiarCallbackAvd() {
        val current = binding?.imgSplashEmblem?.drawable
        avdCallback?.let { cb ->
            if (current is AnimatedVectorDrawable) {
                current.unregisterAnimationCallback(cb)
            }
        }
        avdCallback = null
    }

    private fun configurarTransicionesVentana() {
        val fastOutSlowIn = AnimationUtils.loadInterpolator(
            this,
            android.R.interpolator.fast_out_slow_in
        )
        window.sharedElementExitTransition = ChangeBounds().apply {
            duration = SHARED_TRANSITION_DURATION_MS
            interpolator = fastOutSlowIn
        }
        window.exitTransition = Fade().apply {
            duration = WINDOW_FADE_DURATION_MS
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }
    }

    private fun navegarADestino(conTransicion: Boolean) {
        if (navegacionIniciada || isFinishing || isDestroyed) return
        navegacionIniciada = true
        mainHandler.removeCallbacks(navegacionRunnable)
        mainHandler.removeCallbacks(fallbackRunnable)
        limpiarCallbackAvd()

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
            return
        }

        val inflated = binding
        if (conTransicion && inflated != null) {
            inflated.tvSplashBrandName.animate().cancel()
            inflated.tvSplashBrandName.alpha = 1f
            inflated.tvSplashBrandName.translationY = 0f
            (inflated.imgSplashEmblem.drawable as? Animatable)?.let { anim ->
                if (anim.isRunning) anim.stop()
            }
            inflated.imgSplashEmblem.setImageResource(R.drawable.ic_logo_vetericano_emblem_final)

            val destino = Intent(this, MainActivity::class.java)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                inflated.layoutSplashBrand,
                BRAND_TRANSITION_NAME
            ).toBundle()
            startActivity(destino, options)
        } else {
            val destino = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val options = ActivityOptionsCompat.makeCustomAnimation(this, 0, 0).toBundle()
            startActivity(destino, options)
            finish()
        }
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
        private const val CHOREOGRAPHY_DURATION_MS = 900L
        private const val FINAL_PAUSE_MS = 250L
        private const val POST_ANIM_HOLD_MS = 270L
        private const val FALLBACK_TIMEOUT_MS = 1450L
        private const val SHARED_TRANSITION_DURATION_MS = 320L
        private const val WINDOW_FADE_DURATION_MS = 220L
        private const val BRAND_TRANSITION_NAME = "vetericano_brand_block"
    }
}
