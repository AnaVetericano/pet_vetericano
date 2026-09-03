package com.example.petvetericano

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemEventoBinding

class EventoAdapter(
    private val listaEventos: List<Evento>
) : RecyclerView.Adapter<EventoAdapter.EventoViewHolder>() {

    // Binding de cada tarjeta
    class EventoViewHolder(
        val binding: ItemEventoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun vincular(evento: Evento) {

            binding.imgEvento.setImageResource(evento.imagen)

            binding.txtTipo.text = evento.tipo

            binding.txtTitulo.text = evento.titulo

            binding.txtFecha.text = "📅 ${evento.fecha}"

            binding.txtHora.text = "🕐 ${evento.hora}"

            binding.txtLugar.text = "📍 ${evento.lugar}"

            binding.btnDetalles.setOnClickListener {
                // Más adelante abriremos los detalles del evento
            }
        }
    }

    // Crear la tarjeta
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): EventoViewHolder {

        val binding = ItemEventoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return EventoViewHolder(binding)
    }

    // Mostrar los datos
    override fun onBindViewHolder(
        holder: EventoViewHolder,
        position: Int
    ) {

        val evento = listaEventos[position]

        holder.vincular(evento)
    }

    // Cantidad de eventos
    override fun getItemCount(): Int {
        return listaEventos.size
    }
}