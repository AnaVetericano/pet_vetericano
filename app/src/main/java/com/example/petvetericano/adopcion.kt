package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityAdopcionBinding

class Adopcion : AppCompatActivity() {

    private lateinit var binding: ActivityAdopcionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdopcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sustituye tus URLs reales aquí:
        val listaAnimales = listOf(
            AnimalCompania(
                idFicha = "#INC-2026-000123",
                nombre = "Nena",
                raza = "Mestiza / Ancianita",
                descripcion = "Es ciega y sordita. Perrita adulta ancianita, rescatada de un refugio al lado del río Cauca. Aún guarda la esperanza de encontrar un hogar.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/v1787888763/Nena.jpg",
                urlYoutube = "https://www.youtube.com/shorts/1kObwSpAXSw"
            ),
            AnimalCompania(
                idFicha = "#INC-2026-000124",
                nombre = "Crispeta",
                raza = "Mestiza (Silla de ruedas)",
                descripcion = "Edad: 7 años aprox. Sufrió un accidente de tránsito en la variante sur, lo que la dejó en silla de ruedas y perdió un ojo. Sigue esperando un hogar.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/v1787888476/Crispeta.jpg",
                urlYoutube = "https://www.youtube.com/shorts/c0KFiiSaDg4"
            ),
            AnimalCompania(
                idFicha = "#INC-2026-000125",
                nombre = "Coco",
                raza = "Mestizo",
                descripcion = "Perrito que poco a poco ha ido perdiendo la visión. Ha superado sus temores y se encuentra listo para recibir mucho amor en un hogar.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/f_auto,q_auto/Coco",
                urlYoutube = "https://www.youtube.com/shorts/ffV1BlJq_Rw"
            ),
            AnimalCompania(
                idFicha = "#INC-2026-000126",
                nombre = "Vampira",
                raza = "Mestiza / Ancianita",
                descripcion = "Perrita muy adulta, rescatada del refugio cerca al río Cauca. Ya no cuenta con dientes, pero aún espera un hogar que le brinde amor.",
                urlImagen = "https://res.cloudinary.com/le5sk8qf/image/upload/v1787888749/Vampira.jpg",
                urlYoutube = "..."
            )
        )

        binding.recyclerViewAnimales.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAnimales.adapter = AdaptadorAdopcion(listaAnimales) { animalSeleccionado ->
            mostrarDialogoAdopcion(animalSeleccionado)
        }

        binding.btnAtras.setOnClickListener {
            finish()
        }
    }

    private fun mostrarDialogoAdopcion(animal: AnimalCompania) {
        val mensaje = "Gracias por salvar con amor, su solicitud será atendida bajo el número ${animal.idFicha}."

        AlertDialog.Builder(this)
            .setTitle("¡Adopción en proceso!")
            .setMessage(mensaje)
            .setCancelable(true)
            .setPositiveButton("Contactar al CBA") { dialog, _ ->
                val numeroWhatsApp = "573012489098"
                val url = "https://api.whatsapp.com/send?phone=$numeroWhatsApp&text=Hola,%20quiero%20consultar%20la%20solicitud%20${animal.idFicha}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}