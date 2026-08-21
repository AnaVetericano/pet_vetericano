package com.example.petvetericano

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemAnimalCompaniaBinding

class AdaptadorAdopcion(
    private val listaAnimales: List<AnimalCompania>,
    private val onItemClick: (AnimalCompania) -> Unit
) : RecyclerView.Adapter<AdaptadorAdopcion.AnimalViewHolder>() {

    class AnimalViewHolder(val binding: ItemAnimalCompaniaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val binding = ItemAnimalCompaniaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AnimalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = listaAnimales[position]

        holder.binding.tvId.text = animal.idFicha
        holder.binding.tvNombre.text = "Nombre: ${animal.nombre}"
        holder.binding.tvRaza.text = "Raza: ${animal.raza}"
        holder.binding.tvDescripcion.text = "Descripción: ${animal.descripcion}"

        // Detecta el clic en la tarjeta entera y reenvía el objeto 'animal'
        holder.binding.root.setOnClickListener {
            onItemClick(animal)
        }
    }

    override fun getItemCount(): Int = listaAnimales.size
}