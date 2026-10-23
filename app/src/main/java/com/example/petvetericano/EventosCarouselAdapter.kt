package com.example.petvetericano

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ItemCarouselEventoBinding
import com.example.petvetericano.models.VoluntariadoEventos

class EventosCarouselAdapter(
    private var eventos: List<VoluntariadoEventos>,
    private val onPostularseClick: (VoluntariadoEventos) -> Unit
) : RecyclerView.Adapter<EventosCarouselAdapter.CarouselViewHolder>() {

    inner class CarouselViewHolder(
        val binding: ItemCarouselEventoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun vincular(evento: VoluntariadoEventos) {
            val tituloLimpio = evento.titulo.replace("\"", "").trim()
            val fechaLimpia = evento.fecha.replace("\"", "").trim()

            binding.carouselTitulo.text = tituloLimpio
            binding.carouselFecha.text = "📅 $fechaLimpia"

            Glide.with(itemView.context)
                .load(evento.imagen)
                .placeholder(R.drawable.pastor_gato)
                .error(R.drawable.pastor_gato)
                .centerCrop()
                .into(binding.carouselImgEvento)

            binding.carouselBtnPostularse.setOnClickListener {
                onPostularseClick(evento)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
        val binding = ItemCarouselEventoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CarouselViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
        holder.vincular(eventos[position])
    }

    override fun getItemCount(): Int = eventos.size

    fun actualizarLista(nuevaLista: List<VoluntariadoEventos>) {
        this.eventos = nuevaLista
        notifyDataSetChanged()
    }
}
