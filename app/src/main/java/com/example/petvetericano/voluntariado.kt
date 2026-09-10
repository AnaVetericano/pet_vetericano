package com.example.petvetericano

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityVoluntariadoBinding

class voluntariado : AppCompatActivity() {

    private lateinit var binding: ActivityVoluntariadoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoluntariadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtras.setOnClickListener {
            finish()
        }

        val listaVoluntariados = listOf(
            ActividadVoluntariado(
                idActividad = "VOL-2026-001",
                titulo = "Jornada de Vacunación",
                fecha = "29 de agosto, 2026",
                descripcion = "Apoyo en la logística del evento, registro de asistentes y cuidado de los animales durante la jornada.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/v1787890273/Vacunacion.jpg"
            ),
            ActividadVoluntariado(
                idActividad = "VOL-2026-002",
                titulo = "Paseo y Socialización Canina",
                fecha = "29 de agosto, 2026",
                descripcion = "Acompañamiento a los perritos del albergue en actividades al aire libre y caminatas recreativas.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/v1787890263/Voluntariado.jpg"
            )
        )

        binding.recyclerViewVoluntariado.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        binding.recyclerViewVoluntariado.adapter = AdaptadorVoluntariado(
            listaActividades = listaVoluntariados,
            onItemClick = {
                mostrarMensajeCBA()
            }
        )
    }

    private fun mostrarMensajeCBA() {
        AlertDialog.Builder(this)
            .setTitle("Información de Voluntariado")
            .setMessage("El CBA se pondrá en contacto con usted. Gracias.")
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}