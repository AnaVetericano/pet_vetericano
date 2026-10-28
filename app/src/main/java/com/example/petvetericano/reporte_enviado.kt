package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityReporteEnviadoBinding
import com.example.petvetericano.offline.EstadoReporteLocal
import com.example.petvetericano.offline.ReporteLocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class reporte_enviado : AppCompatActivity() {

    private lateinit var binding: ActivityReporteEnviadoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReporteEnviadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = SharedPreferencesManager(this)
        val nombreUsuario = prefs.getUserName().trim()

        if (nombreUsuario.isNotEmpty()) {
            binding.textView4.text = getString(R.string.reporte_offline_gracias_usuario, nombreUsuario)
        } else {
            binding.textView4.text = getString(R.string.lbl_gracias)
        }

        binding.cardReporteNumber.visibility = View.GONE

        val idLocal = intent.getLongExtra("ID_LOCAL", -1L)
        val hayConexion = intent.getBooleanExtra("HAY_CONEXION", false)

        if (idLocal > 0L) {
            verificarYProcesarReporte(idLocal, hayConexion, prefs)
        } else {
            mostrarEstadoSinConexion()
        }

        binding.contcba.setOnClickListener {
            realizarLlamada("3218905214")
        }

        binding.regremen.setOnClickListener {
            volverAlMenuPrincipal()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                volverAlMenuPrincipal()
            }
        })
    }

    private fun volverAlMenuPrincipal() {
        val prefs = SharedPreferencesManager(this)
        val role = prefs.getUserRole()
        val intent = if (role == inicio_sesion.ROL_VETERINARIO) {
            Intent(this, dahsboard_veterianrio::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(this, bienvenida::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        startActivity(intent)
        finish()
    }

    private fun verificarYProcesarReporte(idLocal: Long, hayConexion: Boolean, prefs: SharedPreferencesManager) {
        lifecycleScope.launch {
            val repo = ReporteLocalRepository.getInstance(this@reporte_enviado)
            val reporte = withContext(Dispatchers.IO) {
                repo.obtenerPorId(idLocal)
            }
            val currentUserId = prefs.getUserId()
            if (reporte == null || reporte.idUsuario != currentUserId) {
                volverAlMenuPrincipal()
                return@launch
            }

            if (hayConexion) {
                iniciarSeguimientoEnvio(idLocal)
            } else {
                mostrarEstadoSinConexion()
            }
        }
    }

    private fun mostrarEstadoSinConexion() {
        binding.progressBarReporte.visibility = View.GONE
        binding.textView5.visibility = View.VISIBLE
        binding.textView5.text = getString(R.string.reporte_offline_enviado_sin_conexion)
        binding.textView6.visibility = View.GONE
        binding.cardReporteNumber.visibility = View.GONE
    }

    private fun mostrarEstadoExito(numeroRadicado: String?) {
        binding.progressBarReporte.visibility = View.GONE
        binding.textView5.visibility = View.VISIBLE
        binding.textView5.text = getString(R.string.lbl_tu_reporte_ha_sido_enviado)
        binding.textView6.visibility = View.VISIBLE
        binding.textView6.text = getString(R.string.lbl_hemos_notificado_a_nuestro_equipo)

        val radicadoLimpio = numeroRadicado?.trim()
        if (!radicadoLimpio.isNullOrBlank()) {
            binding.textView9.text = radicadoLimpio
            binding.cardReporteNumber.visibility = View.VISIBLE
        } else {
            binding.cardReporteNumber.visibility = View.GONE
        }
    }

    private fun mostrarEstadoRequiereAtencion() {
        binding.progressBarReporte.visibility = View.GONE
        binding.textView5.visibility = View.VISIBLE
        binding.textView5.text = getString(R.string.reporte_offline_requiere_atencion_mensaje)
        binding.textView6.visibility = View.GONE
        binding.cardReporteNumber.visibility = View.GONE
    }

    private fun iniciarSeguimientoEnvio(idLocal: Long) {
        binding.progressBarReporte.visibility = View.VISIBLE
        binding.textView5.visibility = View.VISIBLE
        binding.textView5.text = getString(R.string.reporte_offline_enviando_reporte)
        binding.textView6.visibility = View.GONE
        binding.cardReporteNumber.visibility = View.GONE

        lifecycleScope.launch {
            val repo = ReporteLocalRepository.getInstance(this@reporte_enviado)
            val prefs = SharedPreferencesManager(this@reporte_enviado)
            var resuelto = false
            val tiempoInicio = System.currentTimeMillis()
            val tiempoMaximoMs = 90_000L

            while (isActive && !resuelto && (System.currentTimeMillis() - tiempoInicio < tiempoMaximoMs)) {
                val reporte = withContext(Dispatchers.IO) {
                    repo.obtenerPorId(idLocal)
                }

                if (reporte != null) {
                    if (reporte.idUsuario != prefs.getUserId()) {
                        volverAlMenuPrincipal()
                        return@launch
                    }

                    when (reporte.estado) {
                        EstadoReporteLocal.ENVIADO -> {
                            resuelto = true
                            mostrarEstadoExito(reporte.numeroRadicado)
                            break
                        }
                        EstadoReporteLocal.REQUIERE_ATENCION -> {
                            resuelto = true
                            mostrarEstadoRequiereAtencion()
                            break
                        }
                        else -> {
                            // Sigue en proceso
                        }
                    }
                }

                if (!resuelto) {
                    delay(1000L)
                }
            }

            if (!resuelto && isActive) {
                mostrarEstadoSinConexion()
            }
        }
    }

    private fun realizarLlamada(numeroTelefono: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$numeroTelefono")
        }
        startActivity(intent)
    }
}
