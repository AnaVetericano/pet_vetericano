package com.example.petvetericano

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ItemAnimalCompaniaBinding

class AdaptadorVoluntariado(
    private val listaActividades: List<ActividadVoluntariado>,
    private val onItemClick: (ActividadVoluntariado) -> Unit
) : RecyclerView.Adapter<AdaptadorVoluntariado.VoluntariadoViewHolder>() {

    inner class VoluntariadoViewHolder(val binding: ItemAnimalCompaniaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoluntariadoViewHolder {
        val binding = ItemAnimalCompaniaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VoluntariadoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoluntariadoViewHolder, position: Int) {
        val actividad = listaActividades[position]

        with(holder.binding) {
            // 1. Ocultar todos los campos de texto para que solo quede la imagen
            tvId.visibility = View.GONE
            tvNombre.visibility = View.GONE
            tvRaza.visibility = View.GONE
            tvDescripcion.visibility = View.GONE
            btnVerVideo.visibility = View.GONE

            // 2. Configurar la imagen para que ocupe el ancho y alto deseado
            val density = holder.itemView.context.resources.displayMetrics.density

            // Si usas scroll horizontal y quieres la imagen centrada:
            imgAnimal.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            imgAnimal.layoutParams.height = (450 * density).toInt() // Ajusta la altura si deseas más grande

            // 3. Cargar la imagen ajustada
            Glide.with(holder.itemView.context)
                .load(actividad.urlImagen)
                .fitCenter() // Muestra la afiche/imagen completa sin recortarla
                .into(imgAnimal)

            // 4. Clic en la tarjeta abre el mensaje del CBA
            root.setOnClickListener {
                onItemClick(actividad)
            }
        }
    }

    override fun getItemCount(): Int = listaActividades.size
}