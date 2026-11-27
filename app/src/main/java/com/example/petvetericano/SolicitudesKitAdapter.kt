package com.example.petvetericano

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemSolicitudKitBinding
import com.example.petvetericano.models.SolicitudKitResponse

class SolicitudesKitAdapter(
    private var solicitudes: List<SolicitudKitResponse>,
    private val onCancelarClick: (SolicitudKitResponse) -> Unit
) : RecyclerView.Adapter<SolicitudesKitAdapter.SolicitudViewHolder>() {

    fun updateList(newList: List<SolicitudKitResponse>) {
        this.solicitudes = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SolicitudViewHolder {
        val binding = ItemSolicitudKitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SolicitudViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SolicitudViewHolder, position: Int) {
        holder.bind(solicitudes[position])
    }

    override fun getItemCount(): Int = solicitudes.size

    inner class SolicitudViewHolder(private val binding: ItemSolicitudKitBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SolicitudKitResponse) {
            val esOffline = item.estado.equals("EN_COLA_OFFLINE", ignoreCase = true)

            val tipoStr = if (item.tipoSolicitud.equals("REPOSICION", ignoreCase = true)) {
                if (item.esReposicionTotal) "Reposición Total Automática" else "Reposición Específica"
            } else {
                "Devolución de Sobrantes"
            }
            binding.tvTipoSolicitud.text = tipoStr

            val fechaStr = item.fechaSolicitud?.substringBefore("T") ?: "Fecha pendiente"
            binding.tvFechaSolicitud.text = if (esOffline) {
                "$fechaStr • Guardado en teléfono (esperando red)"
            } else {
                "$fechaStr • Solicitud #${item.idSolicitud}"
            }

            // Estado y Badge M3
            binding.tvEstadoBadge.text = if (esOffline) "EN COLA OFFLINE" else item.estado.uppercase()
            configurarBadgeEstado(item.estado)

            // Motivo
            binding.tvMotivoSolicitud.text = "Motivo: ${item.motivoSolicitud ?: "Sin motivo especificado"}"

            // Renglones de medicamentos
            if (item.esReposicionTotal) {
                binding.tvDetalleItems.text = "• Reposición automática de todos los faltantes del kit"
            } else if (item.detalles.isNotEmpty()) {
                val detalleTxt = item.detalles.joinToString("\n") { det ->
                    val nom = det.nombreMedicamento ?: "Medicamento #${det.idMedicamento}"
                    val aprobTxt = if (det.cantidadAprobada != null) " (Aprobado: ${det.cantidadAprobada})" else ""
                    "• $nom: ${det.cantidadSolicitada} uds$aprobTxt"
                }
                binding.tvDetalleItems.text = detalleTxt
            } else {
                binding.tvDetalleItems.text = "• Sin ítems especificados"
            }

            // Observaciones del gestor si existen
            if (!item.observacionesGestor.isNullOrBlank()) {
                binding.tvObservacionesGestor.visibility = View.VISIBLE
                binding.tvObservacionesGestor.text = if (esOffline) item.observacionesGestor else "Respuesta del Gestor: ${item.observacionesGestor}"
            } else {
                binding.tvObservacionesGestor.visibility = View.GONE
            }

            // Botón de cancelación / retirar de la cola offline
            if (esOffline) {
                binding.btnCancelarSolicitud.visibility = View.VISIBLE
                binding.btnCancelarSolicitud.text = "Quitar de la cola"
                binding.btnCancelarSolicitud.setOnClickListener {
                    onCancelarClick(item)
                }
            } else if (item.estado.equals("PENDIENTE", ignoreCase = true)) {
                binding.btnCancelarSolicitud.visibility = View.VISIBLE
                binding.btnCancelarSolicitud.text = "Cancelar Solicitud"
                binding.btnCancelarSolicitud.setOnClickListener {
                    onCancelarClick(item)
                }
            } else {
                binding.btnCancelarSolicitud.visibility = View.GONE
            }
        }

        private fun configurarBadgeEstado(estado: String) {
            val ctx = binding.root.context
            val (bgColorRes, textColorRes) = when (estado.uppercase()) {
                "EN_COLA_OFFLINE" -> R.color.badge_transferido_bg to R.color.badge_transferido_text
                "PENDIENTE" -> R.color.badge_proceso_bg to R.color.badge_proceso_text
                "APROBADA" -> R.color.badge_alta_bg to R.color.badge_alta_text
                "PARCIAL" -> R.color.badge_asignada_bg to R.color.badge_asignada_text
                "RECHAZADA" -> R.color.badge_urgente_bg to R.color.badge_urgente_text
                "CANCELADA" -> R.color.chip_bg_neutral to R.color.text_secondary
                else -> R.color.chip_bg_neutral to R.color.text_secondary
            }
            binding.tvEstadoBadge.backgroundTintList = ContextCompat.getColorStateList(ctx, bgColorRes)
            binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(ctx, textColorRes))
        }
    }
}
