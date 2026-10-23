package com.example.petvetericano

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petvetericano.databinding.ItemEventoBinding
import com.example.petvetericano.models.VoluntariadoEventos

class EventoAdapter(
    private var listaEventos: List<VoluntariadoEventos>,
    private val onItemClick: ((VoluntariadoEventos) -> Unit)? = null
) : RecyclerView.Adapter<EventoAdapter.EventoViewHolder>() {

    // Binding de cada tarjeta
    inner class EventoViewHolder(
        val binding: ItemEventoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun vincular(evento: VoluntariadoEventos) {
            // Limpieza de comillas escapadas que pueda devolver la API
            val tituloLimpio = evento.titulo.replace("\"", "").trim()
            val descripcionLimpia = evento.descripcion.replace("\"", "").trim()
            val fechaLimpia = evento.fecha.replace("\"", "").trim()

            binding.txtTipo.text = "VOLUNTARIADO"
            binding.txtTitulo.text = tituloLimpio
            binding.txtDescripcion.text = descripcionLimpia
            binding.txtFecha.text = "📅 $fechaLimpia"

            // Ocultar campos que no vienen de la API para mantener el diseño limpio
            binding.txtHora.visibility = View.GONE
            binding.txtLugar.visibility = View.GONE

            // Cargar imagen remota con Glide
            Glide.with(itemView.context)
                .load(evento.imagen)
                .placeholder(R.drawable.pastor_gato)
                .error(R.drawable.pastor_gato)
                .centerCrop()
                .into(binding.imgEvento)

            // Clic en la tarjeta
            binding.root.setOnClickListener {
                onItemClick?.invoke(evento)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventoViewHolder {
        val binding = ItemEventoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EventoViewHolder(binding)
    }

    // Mostrar los datos
    override fun onBindViewHolder(holder: EventoViewHolder, position: Int) {
        val evento = listaEventos[position]
        holder.vincular(evento)
    }

    // Cantidad de eventos
    override fun getItemCount(): Int = listaEventos.size

    // Método para actualizar la lista de eventos dinámicamente
    fun actualizarLista(nuevaLista: List<VoluntariadoEventos>) {
        this.listaEventos = nuevaLista
        notifyDataSetChanged()
    }
}