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

        // 1. Inicializamos ViewBinding
        binding = ActivityAdopcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Lista de datos de prueba
        val listaAnimales = listOf(
            AnimalCompania(
                idFicha = "#INC-2025-000123",
                nombre = "Pepito",
                raza = "Chancolang",
                descripcion = "Tierna, juguetona, no le gusta convivir con más animales."
            ),
            AnimalCompania(
                idFicha = "#INC-2025-000124",
                nombre = "Chita",
                raza = "Labrador",
                descripcion = "Le encanta el agua y es muy juguetona."
            ),
            AnimalCompania(
                idFicha = "#INC-2025-000125",
                nombre = "Beto",
                raza = "Criollo",
                descripcion = "Muy tranquilo y obediente."
            )
        )

        // 3. Conectamos los datos con la vista
        binding.recyclerViewAnimales.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAnimales.adapter = AdaptadorAdopcion(listaAnimales) { animalSeleccionado ->
            mostrarDialogoAdopcion(animalSeleccionado)
        }

        // 4. Acción para el botón de regresar
        binding.btnAtras.setOnClickListener {
            finish()
        }
    }

    // Ventana emergente que redirige a WhatsApp al presionar "Contactar al CBA"
    private fun mostrarDialogoAdopcion(animal: AnimalCompania) {
        val mensaje = "Gracias por salvar con amor, su solicitud será atendida bajo el número ${animal.idFicha}."

        AlertDialog.Builder(this)
            .setTitle("¡Adopción en proceso!")
            .setMessage(mensaje)
            .setCancelable(true)
            .setPositiveButton("Contactar al CBA") { dialog, _ ->
                // Reemplaza los 000000000 por el número de teléfono real del CBA
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