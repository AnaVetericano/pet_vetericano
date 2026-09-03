
package com.example.petvetericano
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.databinding.ActivityEventosBinding

class Eventos : AppCompatActivity() {

    private lateinit var binding: ActivityEventosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Inicializar Binding
        binding = ActivityEventosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lista de eventos
        val listaEventos = listOf(

            Evento(
                titulo = "Jornada de adopción",
                tipo = "Adopción",
                fecha = "30 de agosto",
                hora = "9:00 AM",
                lugar = "Parque principal",
                imagen =R.drawable.pastor_gato
            ),

            Evento(
                titulo = "Jornada de vacunación",
                tipo = "Vacunación",
                fecha = "5 de septiembre",
                hora = "10:00 AM",
                lugar = "Centro veterinario",
                imagen =R.drawable.chanchita
            ),

            Evento(
                titulo = "Campaña de desparasitación",
                tipo = "Desparasitación",
                fecha = "12 de septiembre",
                hora = "8:00 AM",
                lugar = "Plaza central",
                imagen=R.drawable.chanchita
            )
        )

        // Configurar RecyclerView
        binding.recyclerEventos.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerEventos.adapter =
            EventoAdapter(listaEventos)
    }
}
