package com.example.petvetericano

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.petvetericano.adapters.PetitionAdapter
import com.example.petvetericano.databinding.ActivityPetitionsListBinding
import com.example.petvetericano.models.Petition
import com.example.petvetericano.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PetitionsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPetitionsListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPetitionsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerPeticiones.layoutManager = LinearLayoutManager(this)

        setupBottomNavigation()
        fetchPeticiones()
    }

    private fun fetchPeticiones() {
        val sharedPreferences = getSharedPreferences("PetVetericanoPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("access_token", "") ?: ""

        if (token.isEmpty()) {
            Toast.makeText(this, "Debe iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.listarPeticiones("Bearer $token")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val listaOriginal = response.body()!!
                        val petitionList = listaOriginal.map {
                            Petition(
                                code = it.numero_radicado ?: "Sin Radicado",
                                title = it.tipo ?: "Desconocido",
                                location = it.ubicacion_direccion ?: "No especificada",
                                time = it.fecha ?: "",
                                status = it.estado ?: "Pendiente",
                                priorityType = it.id_peticion // Usamos priorityType como el ID real
                            )
                        }
                        
                        binding.recyclerPeticiones.adapter = PetitionAdapter(petitionList) { petition ->
                            val intent = Intent(this@PetitionsListActivity, DetallePeticion::class.java).apply {
                                putExtra("PETICION_ID", petition.priorityType)
                            }
                            startActivity(intent)
                        }
                    } else {
                        Toast.makeText(this@PetitionsListActivity, "Error al cargar peticiones", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PetitionsListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.ivNavInicio.setOnClickListener {
            val intent = Intent(this, bienvenida::class.java)
            startActivity(intent)
            finish()
        }
        binding.cardNavPrincipal.setOnClickListener { }
        binding.ivNavFavoritos.setOnClickListener { }
        binding.ivNavPerfil.setOnClickListener { }
    }
}