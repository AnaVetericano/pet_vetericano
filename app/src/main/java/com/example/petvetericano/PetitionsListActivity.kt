package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.adapters.PetitionAdapter
import com.example.petvetericano.databinding.ActivityPetitionsListBinding
import com.example.petvetericano.models.Petition

class PetitionsListActivity : AppCompatActivity() {

    // Declaramos la variable del binding
    private lateinit var binding: ActivityPetitionsListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializamos el binding inflando el layout
        binding = ActivityPetitionsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Configuración del RecyclerView usando binding.recyclerPeticiones
        binding.recyclerPeticiones.layoutManager = LinearLayoutManager(this)

        // Lista de ejemplo basada en tu diseño de Figma
        val samplePetitions = listOf(
            Petition("#INC-2026-000123", "Canino-Animal herido o enfermo", "Barrio el poblado, com. 14", "08:15", "Urgente", 1),
            Petition("#INC-2026-000124", "Felino- Herida abierta", "Barrio San Fernando, com.9", "09:02", "Asignada", 2),
            Petition("#INC-2026-000125", "Ave- Ala lesionada", "Barrio las granjas, com. 5", "07:30", "En proceso", 3)
        )

        binding.recyclerPeticiones.adapter = PetitionAdapter(samplePetitions) { petition ->
            // Acción al hacer clic en una petición (Ver detalle)
        }

        // 2. Configuración de la barra de navegación inferior con binding
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        // Accedemos directamente a los elementos gracias a View Binding
        binding.ivNavInicio.setOnClickListener {
            val intent = Intent(this, bienvenida::class.java)
            startActivity(intent)
            finish()
        }

        binding.cardNavPrincipal.setOnClickListener {
            // Lógica para el botón central de reportar
        }

        binding.ivNavFavoritos.setOnClickListener {
            // Lógica para eventos / favoritos
        }

        binding.ivNavPerfil.setOnClickListener {
            // Lógica para el perfil
        }
    }
}