package com.example.petvetericano

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemEventoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

            // Evento de clic corregido para la ventana emergente
            binding.btnDetalles.setOnClickListener {
                MaterialAlertDialogBuilder(binding.root.context)
                    .setTitle(evento.titulo)
                    .setMessage("Tipo: ${evento.tipo}\nFecha: ${evento.fecha}\nHora: ${evento.hora}\nLugar: ${evento.lugar}")
                    .setPositiveButton("Cerrar") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
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
    override fun getItemCount(): Int {
        return listaEventos.size
    }
}