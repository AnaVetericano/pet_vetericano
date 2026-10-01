package com.example.petvetericano.adapters // 1. Paquete correcto según tu estructura

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.models.Petition // 2. Importación correcta de tu modelo



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
        holder.tvTime.text = "Asignada: ${petition.time}" // Corregido el warning de concatenación
        holder.tvStatusBadge.text = petition.status

        // Regla de Negocio: Colores semánticos en el Badge
        when (petition.status.lowercase()) {
            "urgente" -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#EF4444")) // Rojo semántico
            }
            "asignada" -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#F1C63C")) // Amarillo semántico
            }
            "en proceso" -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#10B981")) // Verde semántico
            }
        }

        holder.tvViewDetail.setOnClickListener {
            onItemClick(petition)
        }
    }

    override fun getItemCount(): Int = petitionList.size
}