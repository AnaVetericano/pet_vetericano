package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.petvetericano.databinding.ActivityBienvenidaBinding
import com.example.petvetericano.models.VoluntariadoEventos
import com.example.petvetericano.network.RetrofitClient
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class bienvenida : AppCompatActivity() {

    private lateinit var binding: ActivityBienvenidaBinding

    private val eventosCarouselAdapter by lazy {
        EventosCarouselAdapter(emptyList()) { evento -> confirmarPostulacionCarrusel(evento) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBienvenidaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Usamos la misma clase que en el login para leer el token
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken()

        if (token.isNotEmpty()) {
            RetrofitClient.authToken = token
            cargarDashboard()
            configurarEventos()
            configurarCarrusel()
        } else {
            // Si no hay token, redirigimos al login
            val intent = Intent(this, inicio_sesion::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Al regresar de editar perfil, refrescamos los datos para mostrar el nombre actualizado
        val prefs = SharedPreferencesManager(this)
        val token = prefs.getAccessToken()
        if (token.isNotEmpty()) {
            RetrofitClient.authToken = token
            cargarDashboard()
            cargarEventosParaCarrusel()
        }
    }

    private fun configurarEventos() {

        binding.cardReportarPeticion.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }
        binding.cardAdopcion.setOnClickListener {
            val intent = Intent(this, Adopcion::class.java)
            startActivity(intent)
        }
        binding.cardVoluntariado.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
        }

        binding.cardNavPrincipal.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.ivNavFavoritos.setOnClickListener {
            val intent = Intent(this, Eventos::class.java)
            startActivity(intent)
            Toast.makeText(this, "Jornadas y eventos", Toast.LENGTH_SHORT).show()
        }

        binding.ivNavPerfil.setOnClickListener {
            val intent = Intent(this, editar_perfil::class.java)
            startActivity(intent)
        }
    }

    private fun cargarDashboard() {
        val prefs = SharedPreferencesManager(this)
        // Carga inmediata del nombre guardado en caché local para que no parpadee
        val nombreLocal = prefs.getUserName()
        if (nombreLocal.isNotEmpty()) {
            binding.tvSaludo.text = "Hola, $nombreLocal"
        }
        binding.tvRolCiudadano.text = "👤 Rol: Ciudadano / Peticionario"

        lifecycleScope.launch {
            try {
                // Llamada limpia sin parámetros, el AuthInterceptor inyecta el token automáticamente
                val response = RetrofitClient.apiService.obtenerDashboard()

                if (response.isSuccessful) {
                    val datos = response.body()

                    if (datos != null) {
                        binding.tvMensajeBienvenida.text = "¡Gracias por ayudar!"

                        val soloNombre = datos.nombre.trim()
                        val soloApellido = datos.apellido.trim()

                        // Mostramos exactamente el nombre del usuario en el saludo
                        val saludo = if (soloApellido.isNotEmpty()) "$soloNombre $soloApellido" else soloNombre
                        if (saludo.isNotEmpty()) {
                            binding.tvSaludo.text = "Hola, $saludo"
                        }
                        binding.tvRolCiudadano.text = "👤 Rol: Ciudadano / Peticionario"
                        // Guardamos nombre y apellido POR SEPARADO (fix: antes quedaba
                        // "nombre apellido" todo junto en USER_NAME y el perfil salia descuadrado).
                        if (soloNombre.isNotEmpty()) {
                            prefs.saveUserData(
                                name = soloNombre,
                                email = datos.email,
                                phone = datos.telefono?.takeIf { it.isNotBlank() } ?: prefs.getUserPhone()
                            )
                        }
                        if (soloApellido.isNotEmpty()) {
                            prefs.saveUserLastName(soloApellido)
                        }
                        prefs.saveProfileImagePath(datos.fotoPerfil ?: "")
                    }
                } else {
                    Toast.makeText(this@bienvenida, "Error al cargar el dashboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@bienvenida, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- CARRUSEL DE EVENTOS ----------------

    private fun configurarCarrusel() {
        binding.eventosViewPager.adapter = eventosCarouselAdapter
        binding.eventosViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                actualizarPuntos(position)
            }
        })
    }

    private fun cargarEventosParaCarrusel() {
        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.obtenerEventos(
                    "Bearer ${SharedPreferencesManager(this@bienvenida).getAccessToken()}"
                )
                val eventos: List<VoluntariadoEventos>? = respuesta.body()
                if (respuesta.isSuccessful && !eventos.isNullOrEmpty()) {
                    eventosCarouselAdapter.actualizarLista(eventos)
                    crearPuntos(eventos.size)
                    binding.tvTituloEventos.visibility = View.VISIBLE
                    binding.eventosCarouselContainer.visibility = View.VISIBLE
                } else {
                    binding.tvTituloEventos.visibility = View.GONE
                    binding.eventosCarouselContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                binding.tvTituloEventos.visibility = View.GONE
                binding.eventosCarouselContainer.visibility = View.GONE
            }
        }
    }

    private fun crearPuntos(cantidad: Int) {
        binding.dotsContainer.removeAllViews()
        val size = (8 * resources.displayMetrics.density).toInt()
        val margin = (3 * resources.displayMetrics.density).toInt()
        for (i in 0 until cantidad) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
            }
            binding.dotsContainer.addView(dot)
        }
        actualizarPuntos(binding.eventosViewPager.currentItem)
    }

    private fun actualizarPuntos(posicion: Int) {
        for (i in 0 until binding.dotsContainer.childCount) {
            binding.dotsContainer.getChildAt(i).setBackgroundResource(
                if (i == posicion) R.drawable.circle_blue else R.drawable.circle_gray
            )
        }
    }

    private fun confirmarPostulacionCarrusel(evento: VoluntariadoEventos) {
        val titulo = evento.titulo.replace("\"", "").trim()
        AlertDialog.Builder(this)
            .setTitle("¿Confirmar postulación?")
            .setMessage("Te postularás a \"$titulo\" con los datos de tu cuenta. El equipo te contactará.")
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .setPositiveButton("Confirmar") { d, _ ->
                d.dismiss()
                postularseDesdeCarrusel(evento)
            }
            .show()
    }

    private fun postularseDesdeCarrusel(evento: VoluntariadoEventos) {
        val token = SharedPreferencesManager(this).getAccessToken()
        if (token.isEmpty()) {
            Toast.makeText(this, "Debes iniciar sesión para postularte.", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val respuesta = RetrofitClient.apiService.postularse("Bearer $token", evento.id)
                val msg = when (respuesta.code()) {
                    201 -> respuesta.body()?.mensaje ?: "¡Te has postulado exitosamente!"
                    400 -> "Ya te encuentras postulado a esta jornada."
                    401 -> "Sesión expirada. Inicia sesión nuevamente."
                    else -> "Error al postularse (${respuesta.code()})."
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@bienvenida, "Error de conexión. Revisa tu internet.", Toast.LENGTH_LONG).show()
            }
        }
    }
}