package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityAdopcionBinding
import com.example.petvetericano.models.AdopcionAnimalResponse
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class Adopcion : AppCompatActivity() {

    companion object {
        internal const val DESCRIPCION_DEFAULT_CBA =
            "Paciente recuperado satisfactoriamente en el Centro de Bienestar Animal (CBA), fuera de peligro y apto para adopción responsable."

        /**
         * Normaliza la URL o Data URI de la imagen del animal (alineado con `normalizarImagen` en la Web):
         * soporta URLs absolutas (Cloudinary/HTTPS), Data URIs (`data:image/...`) y rutas relativas (`/media/...`).
         */
        internal fun normalizarUrlImagenAnimal(
            rawImagen: String?,
            apiBaseUrl: String = RetrofitClient.BASE_URL
        ): String? {
            val u = rawImagen?.trim().orEmpty()
            if (u.isEmpty() || u.equals("null", ignoreCase = true) || u.equals("undefined", ignoreCase = true)) {
                return null
            }
            if (u.startsWith("http://", ignoreCase = true) ||
                u.startsWith("https://", ignoreCase = true) ||
                u.startsWith("data:image", ignoreCase = true)
            ) {
                return u
            }

            val hostBase = apiBaseUrl
                .trim()
                .trimEnd('/')
                .removeSuffix("/api")
                .trimEnd('/')
            if (hostBase.isEmpty()) return u

            return if (u.startsWith("/")) {
                "$hostBase$u"
            } else {
                "$hostBase/media/$u"
            }
        }

        /**
         * Filtra los animales dados de alta / disponibles en `/api/adopciones/` y los transforma al modelo UI.
         */
        internal fun mapearAnimalesDisponibles(
            remotos: List<AdopcionAnimalResponse>,
            apiBaseUrl: String = RetrofitClient.BASE_URL
        ): List<AnimalCompania> {
            return remotos
                .filter { it.esDisponible }
                .sortedByDescending { it.id }
                .map { animal ->
                    val nombreLimpio = animal.nombre?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Animal de compañía CBA"
                    val razaLimpia = animal.raza?.trim()?.takeIf {
                        it.isNotEmpty() && !it.equals("Desconocida", ignoreCase = true)
                    } ?: "Mestizo"
                    val descripcionLimpia = animal.descripcion?.trim()?.takeIf { it.isNotEmpty() }
                        ?: DESCRIPCION_DEFAULT_CBA

                    AnimalCompania(
                        idFicha = "#ADOP-${animal.id.coerceAtLeast(0).toString().padStart(4, '0')}",
                        nombre = nombreLimpio,
                        raza = razaLimpia,
                        descripcion = descripcionLimpia,
                        urlImagen = normalizarUrlImagenAnimal(animal.imagen, apiBaseUrl),
                        urlYoutube = null
                    )
                }
        }
    }

    private lateinit var binding: ActivityAdopcionBinding
    private lateinit var adapter: AdaptadorAdopcion
    private var primeraCargaEnCurso = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdopcionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarVistas()
        cargarAdopciones(desdeSwipe = false)
    }

    override fun onResume() {
        super.onResume()
        if (!primeraCargaEnCurso) {
            cargarAdopciones(desdeSwipe = false, silencioso = true)
        }
    }

    private fun configurarVistas() {
        adapter = AdaptadorAdopcion(emptyList()) { animalSeleccionado ->
            mostrarDialogoAdopcion(animalSeleccionado)
        }

        binding.recyclerViewAnimales.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAnimales.adapter = adapter

        binding.swipeRefreshAdopciones.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.blue)
        )
        binding.swipeRefreshAdopciones.setOnRefreshListener {
            cargarAdopciones(desdeSwipe = true)
        }

        binding.btnAtras.setOnClickListener {
            finish()
        }
    }

    private fun cargarAdopciones(desdeSwipe: Boolean = false, silencioso: Boolean = false) {
        if (!desdeSwipe && !silencioso && adapter.itemCount == 0) {
            binding.progressBarAdopciones.visibility = View.VISIBLE
        }
        if (!silencioso) {
            binding.tvSinAdopciones.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.obtenerAnimalesAdopcion()

                if (response.isSuccessful) {
                    val animalesRemotos = response.body().orEmpty()
                    val disponibles = mapearAnimalesDisponibles(animalesRemotos)

                    adapter.actualizarLista(disponibles)
                    binding.tvSinAdopciones.visibility =
                        if (disponibles.isEmpty()) View.VISIBLE else View.GONE
                } else if (!silencioso) {
                    Log.e("API_ADOPCIONES", "Error HTTP: ${response.code()}")
                    Toast.makeText(
                        this@Adopcion,
                        "Error al cargar adopciones (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (adapter.itemCount == 0) {
                        binding.tvSinAdopciones.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("API_ADOPCIONES", "Fallo al conectar", e)
                if (!silencioso) {
                    Toast.makeText(
                        this@Adopcion,
                        "Error de conexión. Revisa tu internet.",
                        Toast.LENGTH_LONG
                    ).show()
                    if (adapter.itemCount == 0) {
                        binding.tvSinAdopciones.visibility = View.VISIBLE
                    }
                }
            } finally {
                primeraCargaEnCurso = false
                binding.progressBarAdopciones.visibility = View.GONE
                binding.swipeRefreshAdopciones.isRefreshing = false
            }
        }
    }

    private fun mostrarDialogoAdopcion(animal: AnimalCompania) {
        val detalle = "Nombre: ${animal.nombre}\n" +
            "Raza: ${animal.raza}\n" +
            "Ficha: ${animal.idFicha}\n\n" +
            animal.descripcion

        AlertDialog.Builder(this)
            .setTitle("Quiero adoptar a ${animal.nombre}")
            .setMessage(detalle)
            .setCancelable(true)
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Quiero adoptar") { dialog, _ ->
                dialog.dismiss()
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
