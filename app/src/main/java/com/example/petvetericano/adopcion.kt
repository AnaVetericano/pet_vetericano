package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityAdopcionBinding
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.launch

class Adopcion : AppCompatActivity() {

    private lateinit var binding: ActivityAdopcionBinding
    private lateinit var adapter: AdaptadorAdopcion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdopcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AdaptadorAdopcion(emptyList()) { animalSeleccionado ->
            mostrarDialogoAdopcion(animalSeleccionado)
        }

        binding.recyclerViewAnimales.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAnimales.adapter = adapter

        binding.btnAtras.setOnClickListener {
            finish()
        }

        cargarAnimalesAdopcion()
    }

    private fun cargarAnimalesAdopcion() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.obtenerAnimalesAdopcion()

                if (response.isSuccessful) {
                    val animalesRemotos = response.body()

                    if (!animalesRemotos.isNullOrEmpty()) {
                        val listaMapeada = animalesRemotos
                            .filter { it.disponible }
                            .map { animal ->
                                AnimalCompania(
                                    idFicha = "#ADOP-${animal.id.toString().padStart(4, '0')}",
                                    nombre = animal.nombre,
                                    raza = if (!animal.raza.isNullOrBlank()) animal.raza else "Mestizo",
                                    descripcion = animal.descripcion ?: "Sin descripción",
                                    urlImagen = animal.imagen,
                                    urlYoutube = null
                                )
                            }
                        adapter.actualizarLista(listaMapeada)
                    } else {
                        adapter.actualizarLista(emptyList())
                        Toast.makeText(this@Adopcion, "No hay animales disponibles para adopción", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("API_ADOPCION", "Error HTTP: ${response.code()}")
                    Toast.makeText(this@Adopcion, "Error al cargar adopciones (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("API_ADOPCION", "Fallo al conectar con el servidor", e)
                Toast.makeText(this@Adopcion, "Error de conexión al cargar adopciones", Toast.LENGTH_SHORT).show()
            }
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