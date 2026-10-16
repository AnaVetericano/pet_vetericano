package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityReporteEnviadoBinding
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class reporte_enviado : AppCompatActivity() {

    private lateinit var binding: ActivityReporteEnviadoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReporteEnviadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtener los datos guardados del usuario
        val prefs = SharedPreferencesManager(this)

        val nombreUsuario = prefs.getUserName()

        // Mostrar el nombre
        binding.textView4.text = "Gracias, $nombreUsuario"

        // Mostrar el número de radicado real e incremental (ej. PET-2026-00081)
        val numeroRadicado = intent.getStringExtra("NUMERO_RADICADO")
        val idPeticion = intent.getIntExtra("ID_PETICION", -1)

        val radicadoFinal = when {
            !numeroRadicado.isNullOrBlank() -> numeroRadicado
            idPeticion > 0 -> String.format(Locale.US, "PET-2026-%05d", idPeticion)
            else -> null
        }

        if (radicadoFinal != null) {
            binding.textView9.text = radicadoFinal
        } else {
            cargarUltimoRadicadoReal(prefs)
        }

        binding.contcba.setOnClickListener {
            realizarLlamada("3218905214")
        }

        binding.regremen.setOnClickListener {
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
    }

    private fun cargarUltimoRadicadoReal(prefs: SharedPreferencesManager) {
        val token = prefs.getAccessToken().ifEmpty { RetrofitClient.authToken ?: "" }
        if (token.isEmpty()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                if (response.isSuccessful) {
                    val ultimaPeticion = response.body()?.firstOrNull()
                    if (ultimaPeticion != null) {
                        val radicado = ultimaPeticion.numero_radicado?.takeIf { it.isNotBlank() }
                            ?: String.format(Locale.US, "PET-2026-%05d", ultimaPeticion.id_peticion)
                        withContext(Dispatchers.Main) {
                            binding.textView9.text = radicado
                        }
                    }
                }
            } catch (_: Exception) {
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