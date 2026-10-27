package com.example.petvetericano.offline

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemActaColaBinding

class ActaColaAdapter(
    private val onReenviar: (ActaColaItem) -> Unit,
    private val onRevisar: (ActaColaItem) -> Unit
) : ListAdapter<ActaColaItem, ActaColaAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemActaColaBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemActaColaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ActaColaItem) {
            val context = itemView.context

            val radicado = item.nroRadicadoAtencion.trim()
            binding.tvItemRadicado.text = if (radicado.isNotEmpty()) {
                context.getString(R.string.acta_offline_cola_radicado, radicado)
            } else {
                context.getString(R.string.acta_offline_cola_radicado_sin)
            }

            val (textoEstadoRes, esAlerta) = when (item.estado) {
                EstadoSincronizacionActa.PENDIENTE_ENVIO -> {
                    R.string.acta_offline_cola_estado_pendiente to false
                }
                EstadoSincronizacionActa.ENVIANDO -> {
                    R.string.acta_offline_cola_estado_enviando to false
                }
                EstadoSincronizacionActa.ERROR_REINTENTABLE -> {
                    R.string.acta_offline_cola_estado_error to false
                }
                EstadoSincronizacionActa.REQUIERE_ATENCION -> {
                    if (item.ultimoCodigoHttp == 401) {
                        R.string.acta_offline_cola_estado_sesion to true
                    } else {
                        R.string.acta_offline_cola_estado_revision to true
                    }
                }
                else -> {
                    R.string.acta_offline_cola_estado_pendiente to false
                }
            }

            binding.tvItemEstado.text = context.getString(textoEstadoRes)

            val colorBgRes = if (esAlerta) R.color.badge_urgente_bg else R.color.badge_proceso_bg
            val colorTextRes = if (esAlerta) R.color.badge_urgente_text else R.color.badge_proceso_text

            binding.tvItemEstado.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, colorBgRes)
            )
            binding.tvItemEstado.setTextColor(ContextCompat.getColor(context, colorTextRes))

            val errorTexto = item.ultimoError?.trim().orEmpty()
            val debeMostrarDetalle = item.estado == EstadoSincronizacionActa.REQUIERE_ATENCION &&
                    item.ultimoCodigoHttp != 401 &&
                    errorTexto.isNotEmpty()

            if (debeMostrarDetalle) {
                binding.tvItemDetalle.visibility = View.VISIBLE
                binding.tvItemDetalle.text = context.getString(
                    R.string.acta_offline_cola_detalle_error,
                    errorTexto
                )
            } else {
                binding.tvItemDetalle.visibility = View.GONE
            }

            val esReenvio = when (item.estado) {
                EstadoSincronizacionActa.PENDIENTE_ENVIO,
                EstadoSincronizacionActa.ENVIANDO,
                EstadoSincronizacionActa.ERROR_REINTENTABLE -> true
                EstadoSincronizacionActa.REQUIERE_ATENCION -> item.ultimoCodigoHttp == 401
                else -> true
            }

            if (esReenvio) {
                binding.btnItemAccion.text = context.getString(R.string.acta_offline_cola_reenviar)
                binding.btnItemAccion.setOnClickListener { onReenviar(item) }
            } else {
                binding.btnItemAccion.text = context.getString(R.string.acta_offline_cola_revisar)
                binding.btnItemAccion.setOnClickListener { onRevisar(item) }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ActaColaItem>() {
        override fun areItemsTheSame(oldItem: ActaColaItem, newItem: ActaColaItem): Boolean {
            return oldItem.idLocal == newItem.idLocal
        }

        override fun areContentsTheSame(oldItem: ActaColaItem, newItem: ActaColaItem): Boolean {
            return oldItem == newItem
        }
    }
}
