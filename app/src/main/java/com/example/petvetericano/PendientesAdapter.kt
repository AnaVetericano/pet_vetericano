package com.example.petvetericano

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemPendienteBinding
import com.example.petvetericano.models.PeticionPendiente
import com.example.petvetericano.models.TipoEstado

class PendientesAdapter(
    private val pendientes: List<PeticionPendiente>,
    private val onItemClick: ((PeticionPendiente) -> Unit)? = null
) : RecyclerView.Adapter<PendientesAdapter.PendienteViewHolder>() {

    inner class PendienteViewHolder(val binding: ItemPendienteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(peticion: PeticionPendiente) {
            binding.tvTituloPaciente.text = "${peticion.titulo} — ${peticion.paciente}"
            binding.tvEstadoBadge.text = peticion.estado

            val context = binding.root.context

            // Asignar colores según el estado (Urgente, Proceso, Asignada, Transferida)
            val (bgColor, textColor) = when (peticion.tipoEstado) {
                TipoEstado.URGENTE -> Pair(R.color.badge_urgente_bg, R.color.badge_urgente_text)
                TipoEstado.EN_PROCESO -> Pair(R.color.badge_proceso_bg, R.color.badge_proceso_text)
                TipoEstado.ASIGNADA -> Pair(R.color.badge_asignada_bg, R.color.badge_asignada_text)
                TipoEstado.TRANSFERIDA -> Pair(R.color.badge_transferido_bg, R.color.badge_transferido_text)
            }

            // Aplicar colores
            binding.tvEstadoBadge.backgroundTintList = ContextCompat.getColorStateList(context, bgColor)
            binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(context, textColor))
            binding.vwIndicadorColor.backgroundTintList = ContextCompat.getColorStateList(context, textColor)

            binding.root.setOnClickListener {
                onItemClick?.invoke(peticion)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendienteViewHolder {
        val binding = ItemPendienteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PendienteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PendienteViewHolder, position: Int) {
        holder.bind(pendientes[position])
    }

    override fun getItemCount(): Int = pendientes.size
}