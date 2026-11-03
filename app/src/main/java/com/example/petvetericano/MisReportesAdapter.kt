package com.example.petvetericano

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemMiReporteBinding
import com.example.petvetericano.offline.EstadoReporteLocal
import com.example.petvetericano.offline.ReporteLocalEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MisReportesAdapter : ListAdapter<ReporteLocalEntity, MisReportesAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMiReporteBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMiReporteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReporteLocalEntity) {
            val context = itemView.context

            val tipo = item.tipoNombre?.trim().takeIf { !it.isNullOrBlank() }
                ?: context.getString(R.string.reporte_offline_tipo_predeterminado)
            binding.tvItemTipo.text = tipo

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.tvItemFecha.text = sdf.format(Date(item.creadoEn))

            val (textoEstadoRes, colorBgRes, colorTextRes) = when (item.estado) {
                EstadoReporteLocal.PENDIENTE,
                EstadoReporteLocal.ENVIANDO,
                EstadoReporteLocal.ERROR_REINTENTABLE -> {
                    Triple(
                        R.string.reporte_offline_mis_reportes_estado_pendiente,
                        R.color.badge_proceso_bg,
                        R.color.badge_proceso_text
                    )
                }
                EstadoReporteLocal.ENVIADO -> {
                    Triple(
                        R.string.reporte_offline_mis_reportes_estado_enviado,
                        R.color.badge_alta_bg,
                        R.color.badge_alta_text
                    )
                }
                EstadoReporteLocal.REQUIERE_ATENCION -> {
                    Triple(
                        R.string.reporte_offline_mis_reportes_estado_atencion,
                        R.color.badge_urgente_bg,
                        R.color.badge_urgente_text
                    )
                }
            }

            binding.tvItemEstado.text = context.getString(textoEstadoRes)
            binding.tvItemEstado.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, colorBgRes)
            )
            binding.tvItemEstado.setTextColor(ContextCompat.getColor(context, colorTextRes))

            val radicado = item.numeroRadicado?.trim().orEmpty()
            if (item.estado == EstadoReporteLocal.ENVIADO && radicado.isNotEmpty()) {
                binding.tvItemRadicado.visibility = View.VISIBLE
                binding.tvItemRadicado.text = context.getString(
                    R.string.reporte_offline_mis_reportes_radicado,
                    radicado
                )
            } else {
                binding.tvItemRadicado.visibility = View.GONE
            }

            if (item.estado == EstadoReporteLocal.REQUIERE_ATENCION) {
                binding.tvItemDetalle.visibility = View.VISIBLE
                binding.tvItemDetalle.text = context.getString(
                    R.string.reporte_offline_requiere_atencion_mensaje
                )
            } else {
                binding.tvItemDetalle.visibility = View.GONE
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReporteLocalEntity>() {
        override fun areItemsTheSame(oldItem: ReporteLocalEntity, newItem: ReporteLocalEntity): Boolean {
            return oldItem.idLocal == newItem.idLocal
        }

        override fun areContentsTheSame(oldItem: ReporteLocalEntity, newItem: ReporteLocalEntity): Boolean {
            return oldItem == newItem
        }
    }
}
