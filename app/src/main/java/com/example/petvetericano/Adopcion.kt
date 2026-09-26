package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityAdopcionBinding
import com.example.petvetericano.models.AdopcionAnimalResponse
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class Adopcion : AppCompatActivity() {

    private lateinit var binding: ActivityAdopcionBinding
    private lateinit var adaptador: AdaptadorAdopcion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdopcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarVistas()
        cargarAdopciones()
    }

    private fun configurarVistas() {
        adaptador = AdaptadorAdopcion(emptyList<AnimalCompania>()) { animalSeleccionado ->
            mostrarDialogoAdopcion(animalSeleccionado)
        }

        binding.recyclerViewAnimales.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAnimales.adapter = adaptador

        binding.btnAtras.setOnClickListener {
            finish()
        }
    }

    private fun cargarAdopciones() {
        binding.progressBarAdopciones.visibility = View.VISIBLE
        binding.tvSinAdopciones.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.obtenerAdopciones()

                if (respuesta.isSuccessful) {
                    val adopciones: List<AdopcionAnimalResponse> = respuesta.body().orEmpty()

                    val disponibles = adopciones
                        .filter { it.disponible }
                        .map { adopcion ->
                            AnimalCompania(
                                idFicha = "#INC-2026-" + adopcion.id.toString().padStart(6, '0'),
                                nombre = adopcion.nombre,
                                raza = adopcion.raza,
                                descripcion = adopcion.descripcion,
                                urlImagen = adopcion.imagen?.takeIf { it.isNotBlank() },
                                urlYoutube = null
                            )
                        }

                    adaptador.actualizarLista(disponibles)
                    binding.tvSinAdopciones.visibility =
                        if (disponibles.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    Log.e("API_ADOPCIONES", "Error ${respuesta.code()}: ${respuesta.errorBody()?.string()}")
                    Toast.makeText(
                        this@Adopcion,
                        "No se pudieron cargar las adopciones (Error ${respuesta.code()}).",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.tvSinAdopciones.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("API_ADOPCIONES", "Excepción al consultar adopciones: ${e.message}", e)
                Toast.makeText(
                    this@Adopcion,
                    "Error de conexión. Revisa tu internet.",
                    Toast.LENGTH_LONG
                ).show()
                binding.tvSinAdopciones.visibility = View.VISIBLE
            } finally {
                binding.progressBarAdopciones.visibility = View.GONE
            }
        }
    }

    private fun mostrarDialogoAdopcion(animal: AnimalCompania) {
        // Paso 1 M3 (igual que eventos): dialogo informativo, no ejecuta la accion.
        // Solo visual: no hay API de adopcion para enviar, asi que no se guarda nada.
        val detalle = "Nombre: ${animal.nombre}\n" +
            "Raza: ${animal.raza}\n" +
            "Ficha: ${animal.idFicha}\n\n" +
            "${animal.descripcion}"

        AlertDialog.Builder(this)
            .setTitle("Quiero adoptar a ${animal.nombre}")
            .setMessage(detalle)
            .setCancelable(true)
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Quiero adoptar") { dialog, _ ->
                dialog.dismiss()
                // Paso 2 M3: confirmacion antes de la accion con consecuencia
                mostrarConfirmacionAdopcion(animal)
            }
            .show()
    }

    private fun mostrarConfirmacionAdopcion(animal: AnimalCompania) {
        AlertDialog.Builder(this)
            .setTitle("¿Confirmar adopción?")
            .setMessage(
                "Vas a iniciar el proceso de adopción de \"${animal.nombre}\" " +
                    "(${animal.idFicha}) con los datos de tu cuenta. " +
                    "El equipo del CBA te contactará."
            )
            .setCancelable(true)
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Confirmar") { dialog, _ ->
                dialog.dismiss()
                confirmarAdopcion(animal)
            }
            .show()
    }

    private fun confirmarAdopcion(animal: AnimalCompania) {
        // Solo visual / local: no existe endpoint de adopcion, asi que no se envia nada a la BD.
        // Se muestra la confirmacion con el numero de ficha y la opcion de contactar al CBA.
        val mensaje = "Gracias por salvar con amor, su solicitud será atendida bajo el número ${animal.idFicha}."

        Snackbar.make(
            binding.root,
            "¡Solicitud lista! ${animal.idFicha}: ${animal.nombre}",
            Snackbar.LENGTH_LONG
        ).show()

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