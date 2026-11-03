package com.example.petvetericano

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemMedicamentoMaletinBinding
import com.example.petvetericano.models.MedicamentoMaletin
import com.example.petvetericano.models.NivelSemaforoStock

class MedicamentoAdapter(
    private var medicamentos: List<MedicamentoMaletin>,
    private val onItemClick: (MedicamentoMaletin) -> Unit
) : RecyclerView.Adapter<MedicamentoAdapter.MedicamentoViewHolder>() {

    class MedicamentoDiffCallback(
        private val oldList: List<MedicamentoMaletin>,
        private val newList: List<MedicamentoMaletin>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].idMedicamentoInt == newList[newItemPosition].idMedicamentoInt
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.cantidadDisponible == newItem.cantidadDisponible &&
                    oldItem.cuotaAsignada == newItem.cuotaAsignada &&
                    oldItem.saldoDisponibleInt == newItem.saldoDisponibleInt &&
                    oldItem.saldoVencido == newItem.saldoVencido &&
                    oldItem.nivelSemaforo == newItem.nivelSemaforo &&
                    oldItem.lote == newItem.lote &&
                    oldItem.fechaVencimiento == newItem.fechaVencimiento
        }
    }

    fun updateList(newList: List<MedicamentoMaletin>) {
        val diffCallback = MedicamentoDiffCallback(this.medicamentos, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.medicamentos = newList
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicamentoViewHolder {
        val binding = ItemMedicamentoMaletinBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MedicamentoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicamentoViewHolder, position: Int) {
        holder.bind(medicamentos[position])
    }

    override fun getItemCount(): Int = medicamentos.size

    inner class MedicamentoViewHolder(private val binding: ItemMedicamentoMaletinBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MedicamentoMaletin) {
            binding.tvNombreMedicamento.text = item.nombre
            val detalleSub = if (item.concentracion.isNotEmpty()) {
                "${item.concentracion} • ${item.presentacion}"
            } else {
                item.presentacion
            }
            binding.tvConcentracionPresentacion.text = detalleSub
            binding.tvCategoriaBadge.text = item.categoria
            binding.tvLote.text = item.lote
            binding.tvVencimiento.text = "Vence: ${item.fechaVencimiento.ifEmpty { "Vigente" }}"
            binding.tvVia.text = item.viaRecomendada

            val dispInt = item.saldoDisponibleInt
            val vencInt = item.saldoVencido

            val maxRef = item.cuotaAsignada?.toFloat()?.takeIf { it > 0f }
                ?: item.cantidadInicial.toFloat().takeIf { it > 0f }
                ?: 1f
            val ratio = (item.cantidadDisponible.toFloat() / maxRef).coerceIn(0f, 1f)
            val percent = (ratio * 100).toInt()
            binding.progressBarStock.progress = percent

            // Dinamismo semántico oficial de colores (Material Design 3 - Modo Claro y Oscuro)
            val context = binding.root.context
            when (item.nivelSemaforo) {
                NivelSemaforoStock.CRITICO -> {
                    val colorCritico = ContextCompat.getColor(context, R.color.badge_urgente_text)
                    val bgCritico = ContextCompat.getColor(context, R.color.badge_urgente_bg)
                    binding.progressBarStock.setIndicatorColor(colorCritico)
                    binding.tvStockCantidad.setTextColor(colorCritico)
                    binding.tvStockCantidad.text = "$dispInt ${item.unidadMedida} restantes"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "CRÍTICO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(bgCritico)
                    binding.tvSemaforoBadge.setTextColor(colorCritico)
                }
                NivelSemaforoStock.REORDEN -> {
                    val colorReorden = ContextCompat.getColor(context, R.color.badge_proceso_text)
                    val bgReorden = ContextCompat.getColor(context, R.color.badge_proceso_bg)
                    binding.progressBarStock.setIndicatorColor(colorReorden)
                    binding.tvStockCantidad.setTextColor(colorReorden)
                    val baseRef = item.cuotaAsignada ?: item.cantidadInicial.toInt()
                    binding.tvStockCantidad.text = "Stock bajo: $dispInt / $baseRef ${item.unidadMedida}"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "STOCK BAJO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(bgReorden)
                    binding.tvSemaforoBadge.setTextColor(colorReorden)
                }
                NivelSemaforoStock.OPTIMO -> {
                    val colorOptimo = ContextCompat.getColor(context, R.color.badge_alta_text)
                    val bgOptimo = ContextCompat.getColor(context, R.color.badge_alta_bg)
                    binding.progressBarStock.setIndicatorColor(colorOptimo)
                    binding.tvStockCantidad.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    val baseRef = item.cuotaAsignada ?: item.cantidadInicial.toInt()
                    binding.tvStockCantidad.text = if (vencInt > 0) {
                        "$dispInt disp. ($vencInt vencidas)"
                    } else {
                        "$dispInt / $baseRef ${item.unidadMedida}"
                    }

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "ÓPTIMO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(bgOptimo)
                    binding.tvSemaforoBadge.setTextColor(colorOptimo)
                }
                NivelSemaforoStock.AGOTADO -> {
                    val colorMuted = ContextCompat.getColor(context, R.color.text_muted)
                    val bgMuted = ContextCompat.getColor(context, R.color.chip_bg_neutral)
                    binding.progressBarStock.setIndicatorColor(colorMuted)
                    binding.tvStockCantidad.setTextColor(colorMuted)
                    binding.tvStockCantidad.text = "Agotado (0 ${item.unidadMedida})"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "AGOTADO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(bgMuted)
                    binding.tvSemaforoBadge.setTextColor(colorMuted)
                }
                NivelSemaforoStock.VENCIDO -> {
                    val colorCritico = ContextCompat.getColor(context, R.color.badge_urgente_text)
                    val bgCritico = ContextCompat.getColor(context, R.color.badge_urgente_bg)
                    binding.progressBarStock.setIndicatorColor(colorCritico)
                    binding.tvStockCantidad.setTextColor(colorCritico)
                    binding.tvStockCantidad.text = "Vencido / No apto"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "VENCIDO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(bgCritico)
                    binding.tvSemaforoBadge.setTextColor(colorCritico)
                }
            }

            // Configurar texto de cuota de referencia y unidades faltantes (diseño limpio y armónico)
            val cuota = item.cuotaAsignada
            if (cuota != null) {
                val faltantes = item.unidadesFaltantesParaCuota
                if (faltantes > 0) {
                    binding.tvCuotaReferencia.text = "Cuota asignada: $cuota ${item.unidadMedida} • Faltan: $faltantes"
                    binding.tvCuotaReferencia.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                } else {
                    binding.tvCuotaReferencia.text = "Cuota asignada: $cuota ${item.unidadMedida} • Cuota al 100%"
                    binding.tvCuotaReferencia.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                }
                binding.tvCuotaReferencia.visibility = View.VISIBLE
            } else {
                binding.tvCuotaReferencia.visibility = View.GONE
            }

            // Estilo visual del badge de categoría médica sobrio y agradable a la vista
            configurarBadgeCategoria()

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun configurarBadgeCategoria() {
            val context = binding.root.context
            val bgColor = ContextCompat.getColor(context, R.color.chip_bg_neutral)
            val textColor = ContextCompat.getColor(context, R.color.text_secondary)
            binding.tvCategoriaBadge.backgroundTintList = ColorStateList.valueOf(bgColor)
            binding.tvCategoriaBadge.setTextColor(textColor)
        }
    }
}
