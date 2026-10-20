package com.example.petvetericano

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            val tipoStr = if (item.tipoSolicitud.equals("REPOSICION", ignoreCase = true)) {
                if (item.esReposicionTotal) "Reposición Total Automática" else "Reposición Específica"
            } else {
                "Devolución de Sobrantes"
            }
            binding.tvTipoSolicitud.text = tipoStr

            val fechaStr = item.fechaSolicitud?.substringBefore("T") ?: "Fecha pendiente"
            binding.tvFechaSolicitud.text = "$fechaStr • Solicitud #${item.idSolicitud}"

            // Estado y Badge M3
            binding.tvEstadoBadge.text = item.estado.uppercase()
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
                binding.tvObservacionesGestor.text = "Respuesta del Gestor: ${item.observacionesGestor}"
            } else {
                binding.tvObservacionesGestor.visibility = View.GONE
            }

            // Botón de cancelación (únicamente permitido por backend en estado PENDIENTE)
            if (item.estado.equals("PENDIENTE", ignoreCase = true)) {
                binding.btnCancelarSolicitud.visibility = View.VISIBLE
                binding.btnCancelarSolicitud.setOnClickListener {
                    onCancelarClick(item)
                }
            } else {
                binding.btnCancelarSolicitud.visibility = View.GONE
            }
        }

        private fun configurarBadgeEstado(estado: String) {
            val (bgColor, textColor) = when (estado.uppercase()) {
                "PENDIENTE" -> Pair(Color.parseColor("#FFF4D0"), Color.parseColor("#D49F00")) // Ámbar
                "APROBADA" -> Pair(Color.parseColor("#D1FAE5"), Color.parseColor("#047857"))  // Verde
                "PARCIAL" -> Pair(Color.parseColor("#EEF0FF"), Color.parseColor("#4141A5"))   // Azul
                "RECHAZADA" -> Pair(Color.parseColor("#FEE2E2"), Color.parseColor("#EF4444")) // Rojo
                "CANCELADA" -> Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#64748B")) // Gris
                else -> Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"))
            }
            binding.tvEstadoBadge.backgroundTintList = ColorStateList.valueOf(bgColor)
            binding.tvEstadoBadge.setTextColor(textColor)
        }
    }
}
