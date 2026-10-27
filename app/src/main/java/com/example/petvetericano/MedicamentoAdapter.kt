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

            // Dinamismo semántico oficial de colores (Semáforo Híbrido CBA)
            when (item.nivelSemaforo) {
                NivelSemaforoStock.CRITICO -> {
                    val red = Color.parseColor("#DC2626")
                    binding.progressBarStock.setIndicatorColor(red)
                    binding.tvStockCantidad.setTextColor(red)
                    binding.tvStockCantidad.text = "⚠️ Crítico: $dispInt ${item.unidadMedida} restante"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "🔴 CRÍTICO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    binding.tvSemaforoBadge.setTextColor(Color.parseColor("#B91C1C"))
                }
                NivelSemaforoStock.REORDEN -> {
                    val amber = Color.parseColor("#D97706")
                    binding.progressBarStock.setIndicatorColor(amber)
                    binding.tvStockCantidad.setTextColor(amber)
                    val baseRef = item.cuotaAsignada ?: item.cantidadInicial.toInt()
                    binding.tvStockCantidad.text = "Stock bajo: $dispInt / $baseRef ${item.unidadMedida}"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "🟠 REORDEN"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    binding.tvSemaforoBadge.setTextColor(Color.parseColor("#B45309"))
                }
                NivelSemaforoStock.OPTIMO -> {
                    val green = Color.parseColor("#059669")
                    binding.progressBarStock.setIndicatorColor(green)
                    binding.tvStockCantidad.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_primary))
                    val baseRef = item.cuotaAsignada ?: item.cantidadInicial.toInt()
                    binding.tvStockCantidad.text = if (vencInt > 0) {
                        "$dispInt disp. ($vencInt vencidas)"
                    } else {
                        "$dispInt / $baseRef ${item.unidadMedida}"
                    }

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "🟢 ÓPTIMO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
                    binding.tvSemaforoBadge.setTextColor(Color.parseColor("#065F46"))
                }
                NivelSemaforoStock.AGOTADO -> {
                    val red = Color.parseColor("#EF4444")
                    binding.progressBarStock.setIndicatorColor(red)
                    binding.tvStockCantidad.setTextColor(red)
                    binding.tvStockCantidad.text = "Agotado (0 ${item.unidadMedida})"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "⚪ AGOTADO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
                    binding.tvSemaforoBadge.setTextColor(Color.parseColor("#6B7280"))
                }
                NivelSemaforoStock.VENCIDO -> {
                    val gray = Color.parseColor("#9CA3AF")
                    binding.progressBarStock.setIndicatorColor(gray)
                    binding.tvStockCantidad.setTextColor(gray)
                    binding.tvStockCantidad.text = "Vencido / No apto"

                    binding.tvSemaforoBadge.visibility = View.VISIBLE
                    binding.tvSemaforoBadge.text = "❌ VENCIDO"
                    binding.tvSemaforoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    binding.tvSemaforoBadge.setTextColor(Color.parseColor("#991B1B"))
                }
            }

            // Configurar texto de cuota de referencia y unidades faltantes
            val cuota = item.cuotaAsignada
            if (cuota != null) {
                val faltantes = item.unidadesFaltantesParaCuota
                if (faltantes > 0) {
                    binding.tvCuotaReferencia.text = "Cuota asignada: $cuota ${item.unidadMedida} • Faltan: $faltantes para completar"
                    binding.tvCuotaReferencia.setTextColor(Color.parseColor("#D97706"))
                } else {
                    binding.tvCuotaReferencia.text = "Cuota asignada: $cuota ${item.unidadMedida} • Cuota al 100%"
                    binding.tvCuotaReferencia.setTextColor(Color.parseColor("#059669"))
                }
                binding.tvCuotaReferencia.visibility = View.VISIBLE
            } else {
                binding.tvCuotaReferencia.visibility = View.GONE
            }

            // Estilo visual del badge según categoría médica
            configurarBadgeCategoria(item.categoria)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun configurarBadgeCategoria(categoria: String) {
            val (bgColor, textColor) = when {
                categoria.contains("Analg", ignoreCase = true) || categoria.contains("AINE", ignoreCase = true) -> {
                    Pair(Color.parseColor("#EFF6FF"), Color.parseColor("#1D4ED8")) // Azul M3
                }
                categoria.contains("Antibi", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#B45309")) // Ámbar M3
                }
                categoria.contains("Anest", ignoreCase = true) -> {
                    Pair(Color.parseColor("#F3E8FF"), Color.parseColor("#7E22CE")) // Púrpura M3
                }
                categoria.contains("Cortico", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEE2E2"), Color.parseColor("#B91C1C")) // Rojo suave M3
                }
                categoria.contains("Antiparasit", ignoreCase = true) -> {
                    Pair(Color.parseColor("#ECFDF5"), Color.parseColor("#047857")) // Verde esmeralda M3
                }
                categoria.contains("Fluid", ignoreCase = true) -> {
                    Pair(Color.parseColor("#F0FDFA"), Color.parseColor("#0F766E")) // Teal M3
                }
                else -> {
                    Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#475569")) // Gris neutro M3
                }
            }
            binding.tvCategoriaBadge.backgroundTintList = ColorStateList.valueOf(bgColor)
            binding.tvCategoriaBadge.setTextColor(textColor)
        }
    }
}
