package com.example.petvetericano.notificaciones

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemNotificacionBinding
import com.example.petvetericano.models.NotificacionItem
import com.example.petvetericano.models.NotificacionesMapper

/** Lista de la bandeja de notificaciones (ítem unificado §5). */
class NotificacionesAdapter(
    private val alTocar: (NotificacionItem) -> Unit
) : ListAdapter<NotificacionItem, NotificacionesAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificacionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemNotificacionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificacionItem) {
            val contexto = binding.root.context
            val noLeida = !item.leida

            binding.tvTituloNotificacion.text =
                item.titulo?.takeIf { it.isNotBlank() } ?: NotificacionesMapper.TITULO_POR_DEFECTO
            binding.tvTituloNotificacion.setTypeface(null, if (noLeida) Typeface.BOLD else Typeface.NORMAL)

            val mensaje = item.mensaje?.trim().orEmpty()
            binding.tvMensajeNotificacion.text = mensaje
            binding.tvMensajeNotificacion.visibility = if (mensaje.isEmpty()) View.GONE else View.VISIBLE

            val modulo = NotificacionesMapper.etiquetaModulo(item.modulo)
            val fecha = NotificacionesMapper.formatearFecha(item.fechaCreacion)
            binding.tvMetaNotificacion.text = listOf(modulo, fecha).filter { it.isNotEmpty() }.joinToString(" • ")

            binding.viewIndicadorNoLeida.visibility = if (noLeida) View.VISIBLE else View.INVISIBLE
            binding.tvBadgeUrgente.visibility = if (NotificacionesMapper.esUrgente(item)) View.VISIBLE else View.GONE

            binding.cardNotificacion.strokeColor = ContextCompat.getColor(
                contexto,
                if (noLeida) R.color.primary else R.color.card_stroke
            )
            binding.cardNotificacion.setCardBackgroundColor(
                ContextCompat.getColor(contexto, if (noLeida) R.color.primary_soft else R.color.card_background)
            )
            binding.cardNotificacion.contentDescription = contexto.getString(
                if (noLeida) R.string.notif_cd_item_no_leida else R.string.notif_cd_item_leida,
                binding.tvTituloNotificacion.text
            )

            binding.cardNotificacion.setOnClickListener { alTocar(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<NotificacionItem>() {
        override fun areItemsTheSame(oldItem: NotificacionItem, newItem: NotificacionItem): Boolean =
            oldItem.clave == newItem.clave

        override fun areContentsTheSame(oldItem: NotificacionItem, newItem: NotificacionItem): Boolean =
            oldItem == newItem
    }
}
