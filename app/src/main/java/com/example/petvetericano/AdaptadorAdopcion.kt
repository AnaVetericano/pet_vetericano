package com.example.petvetericano

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

            cargarImagenAnimal(imgAnimal, animal.urlImagen)

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

    private fun cargarImagenAnimal(imageView: ImageView, rawUrl: String?) {
        val context = imageView.context
        val valor = rawUrl?.trim().orEmpty()
        if (valor.isEmpty()) {
            try {
                Glide.with(context).clear(imageView)
            } catch (_: Exception) {
            }
            imageView.setImageResource(R.drawable.pastor_gato)
            return
        }

        try {
            if (valor.startsWith("data:image", ignoreCase = true)) {
                val base64Part = valor.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    Glide.with(context)
                        .load(bytes)
                        .placeholder(R.drawable.pastor_gato)
                        .error(R.drawable.pastor_gato)
                        .centerCrop()
                        .into(imageView)
                    return
                }
            }

            Glide.with(context)
                .load(valor)
                .placeholder(R.drawable.pastor_gato)
                .error(R.drawable.pastor_gato)
                .centerCrop()
                .into(imageView)
        } catch (_: Exception) {
            imageView.setImageResource(R.drawable.pastor_gato)
        }
    }

    override fun getItemCount(): Int = listaAnimales.size

    fun actualizarLista(nuevaLista: List<AnimalCompania>) {
        this.listaAnimales = nuevaLista
        notifyDataSetChanged()
    }
}
