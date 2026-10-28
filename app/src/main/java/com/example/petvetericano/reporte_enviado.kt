package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.petvetericano.databinding.ActivityReporteEnviadoBinding

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

        binding.textView5.text = getString(R.string.reporte_offline_enviado_mensaje)
        binding.textView6.visibility = View.GONE
        binding.cardReporteNumber.visibility = View.GONE

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

    private fun realizarLlamada(numeroTelefono: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$numeroTelefono")
        }
        startActivity(intent)
    }
}