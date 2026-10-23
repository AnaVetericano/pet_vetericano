package com.example.petvetericano.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        when {
            petition.status.lowercase().contains("urgente") -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#FEF2F2"))
            }
            petition.status.lowercase().contains("proceso") ||
            petition.status.lowercase().contains("tratamiento") -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#ECFDF5"))
            }
            petition.status.lowercase().contains("atendida") ||
            petition.status.lowercase().contains("finaliz") -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#6366F1"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#EEF2FF"))
            }
            else -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFFBEB"))
            }
        }

        // Click en toda la tarjeta Y en "Ver detalle"
        holder.itemView.setOnClickListener { onItemClick(petition) }
        holder.tvViewDetail.setOnClickListener { onItemClick(petition) }
    }

    override fun getItemCount(): Int = petitionList.size
}