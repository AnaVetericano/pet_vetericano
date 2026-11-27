package com.example.petvetericano

import android.content.Intent
import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petvetericano.databinding.ActivityMainBinding
import com.example.petvetericano.network.RetrofitClient

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var introAnimPlayed = false
    private var avdNativeCallback: Animatable2.AnimationCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar el modo oscuro ANTES de inflar cualquier vista
        aplicarModoOscuro()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        introAnimPlayed = savedInstanceState?.getBoolean(KEY_INTRO_ANIM_PLAYED, false) ?: false

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

        iniciarAnimacionMarcaVetericano()

        binding.btnLoginWelcome.setOnClickListener { login() }
        binding.btnSignUpWelcome.setOnClickListener { signUp() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_INTRO_ANIM_PLAYED, introAnimPlayed)
    }

    override fun onStop() {
        super.onStop()
        if (::binding.isInitialized && introAnimPlayed) {
            mostrarMarcaEstadoFinal()
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            limpiarCallbacksAvd()
            binding.tvVetericanoBrandName.animate().cancel()
        }
        super.onDestroy()
    }

    private fun iniciarAnimacionMarcaVetericano() {
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)

        if (introAnimPlayed || animatorScale <= 0f) {
            introAnimPlayed = true
            mostrarMarcaEstadoFinal()
            return
        }

        introAnimPlayed = true

        // 480 ms - 900 ms: Entrada sincronizada del nombre "VETERICANO" (alpha 0 -> 1, 8dp -> 0dp)
        val offsetWordmarkPx = 8f * resources.displayMetrics.density
        binding.tvVetericanoBrandName.alpha = 0f
        binding.tvVetericanoBrandName.translationY = offsetWordmarkPx
        binding.tvVetericanoBrandName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(WORDMARK_START_DELAY_MS)
            .setDuration(WORDMARK_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!isFinishing && !isDestroyed) {
                    binding.tvVetericanoBrandName.alpha = 1f
                    binding.tvVetericanoBrandName.translationY = 0f
                }
            }
            .start()

        // 0 ms - 880 ms: AnimatedVectorDrawable nativo del emblema
        when (val drawable = binding.imgVetericanoLogotip.drawable) {
            is AnimatedVectorDrawable -> {
                val callback = object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(d: Drawable?) {
                        if (!isFinishing && !isDestroyed) {
                            binding.imgVetericanoLogotip.setImageResource(
                                R.drawable.ic_logo_vetericano_emblem_final
                            )
                        }
                    }
                }
                avdNativeCallback = callback
                drawable.registerAnimationCallback(callback)
                drawable.start()
            }
            is Animatable -> drawable.start()
            else -> binding.imgVetericanoLogotip.setImageResource(
                R.drawable.ic_logo_vetericano_emblem_final
            )
        }
    }

    private fun mostrarMarcaEstadoFinal() {
        limpiarCallbacksAvd()
        (binding.imgVetericanoLogotip.drawable as? Animatable)?.let { anim ->
            if (anim.isRunning) anim.stop()
        }
        binding.imgVetericanoLogotip.setImageResource(R.drawable.ic_logo_vetericano_emblem_final)
        binding.tvVetericanoBrandName.animate().cancel()
        binding.tvVetericanoBrandName.alpha = 1f
        binding.tvVetericanoBrandName.translationY = 0f
    }

    private fun limpiarCallbacksAvd() {
        val current = binding.imgVetericanoLogotip.drawable
        avdNativeCallback?.let { cb ->
            if (current is AnimatedVectorDrawable) {
                current.unregisterAnimationCallback(cb)
            }
        }
        avdNativeCallback = null
    }

    // Lee la preferencia guardada y aplica el tema en toda la app
    private fun aplicarModoOscuro() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("isDarkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
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
        private const val KEY_INTRO_ANIM_PLAYED = "intro_anim_played"
        private const val WORDMARK_START_DELAY_MS = 480L
        private const val WORDMARK_DURATION_MS = 420L
    }
}
