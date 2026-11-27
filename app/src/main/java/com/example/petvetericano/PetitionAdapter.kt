package com.example.petvetericano.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.models.Petition

class PetitionAdapter(
    private val petitionList: List<Petition>,
    private val onItemClick: (Petition) -> Unit
) : RecyclerView.Adapter<PetitionAdapter.PetitionViewHolder>() {

    class PetitionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCode: TextView = itemView.findViewById(R.id.tvPetitionCode)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
        val tvTitle: TextView = itemView.findViewById(R.id.tvPetitionTitle)
        val tvLocation: TextView = itemView.findViewById(R.id.tvPetitionLocation)
        val tvTime: TextView = itemView.findViewById(R.id.tvAssignedTime)
        val tvViewDetail: TextView = itemView.findViewById(R.id.tvViewDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetitionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_petition, parent, false)
        return PetitionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PetitionViewHolder, position: Int) {
        val petition = petitionList[position]

        holder.tvCode.text = petition.code
        holder.tvTitle.text = petition.title
        holder.tvLocation.text = petition.location
        holder.tvTime.text = "Asignada: ${petition.time}"
        holder.tvStatusBadge.text = petition.status

        // Colores semánticos en el Badge según el estado
        val ctx = holder.itemView.context
        val statusLower = petition.status.lowercase()
        val (bgColorRes, textColorRes) = when {
            statusLower.contains("urgente") -> {
                R.color.badge_urgente_bg to R.color.badge_urgente_text
            }
            statusLower.contains("proceso") || statusLower.contains("tratamiento") -> {
                R.color.badge_proceso_bg to R.color.badge_proceso_text
            }
            statusLower.contains("atendida") || statusLower.contains("finaliz") -> {
                R.color.badge_alta_bg to R.color.badge_alta_text
            }
            else -> {
                R.color.badge_asignada_bg to R.color.badge_asignada_text
            }
        }
        holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_rounded_badge)
        holder.tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(ctx, bgColorRes)
        holder.tvStatusBadge.setTextColor(ContextCompat.getColor(ctx, textColorRes))

        // Click en toda la tarjeta Y en "Ver detalle"
        holder.itemView.setOnClickListener { onItemClick(petition) }
        holder.tvViewDetail.setOnClickListener { onItemClick(petition) }
    }

    override fun getItemCount(): Int = petitionList.size
}