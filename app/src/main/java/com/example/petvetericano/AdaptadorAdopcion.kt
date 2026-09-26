package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ItemAnimalCompaniaBinding

class AdaptadorAdopcion(
    private var listaAnimales: List<AnimalCompania>,
    private val onItemClick: (AnimalCompania) -> Unit
) : RecyclerView.Adapter<AdaptadorAdopcion.AnimalViewHolder>() {

    inner class AnimalViewHolder(val binding: ItemAnimalCompaniaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val binding = ItemAnimalCompaniaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val animal = listaAnimales[position]

        with(holder.binding) {
            tvId.text = animal.idFicha
            tvNombre.text = "Nombre: ${animal.nombre}"
            tvRaza.text = "Raza: ${animal.raza}"
            tvDescripcion.text = "Descripción: ${animal.descripcion}"

            Glide.with(holder.itemView.context)
                .load(animal.urlImagen)
                .placeholder(R.drawable.pastor_gato)
                .error(R.drawable.pastor_gato)
                .centerCrop()
                .into(imgAnimal)

            if (!animal.urlYoutube.isNullOrEmpty()) {
                btnVerVideo.visibility = View.VISIBLE
                btnVerVideo.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(animal.urlYoutube))
                    holder.itemView.context.startActivity(intent)
                }
            } else {
                btnVerVideo.visibility = View.GONE
            }

            root.setOnClickListener {
                onItemClick(animal)
            }
        }
    }

    override fun getItemCount(): Int = listaAnimales.size

    // Reemplaza la lista cuando llegan los datos desde la API
    fun actualizarLista(nuevaLista: List<AnimalCompania>) {
        this.listaAnimales = nuevaLista
        notifyDataSetChanged()
    }
}