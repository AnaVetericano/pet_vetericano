package com.example.petvetericano

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.databinding.ItemMedicamentoMaletinBinding
import com.example.petvetericano.models.MedicamentoMaletin

class MedicamentoAdapter(
    private var medicamentos: List<MedicamentoMaletin>,
    private val onItemClick: (MedicamentoMaletin) -> Unit
) : RecyclerView.Adapter<MedicamentoAdapter.MedicamentoViewHolder>() {

    fun updateList(newList: List<MedicamentoMaletin>) {
        this.medicamentos = newList
        notifyDataSetChanged()
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

            // Stock disponible y vencido en M3
            val dispInt = item.saldoDisponibleInt
            val vencInt = item.saldoVencido
            val formatStock = if (vencInt > 0) {
                "$dispInt disp. ($vencInt vencidas)"
            } else if (item.cantidadInicial > 0) {
                "$dispInt / ${item.cantidadInicial.toInt()} ${item.unidadMedida}"
            } else {
                "$dispInt ${item.unidadMedida}"
            }
            binding.tvStockCantidad.text = formatStock

            val ratio = if (item.cantidadInicial > 0) (item.cantidadDisponible / item.cantidadInicial).toFloat() else 1f
            val percent = (ratio * 100).toInt().coerceIn(0, 100)
            binding.progressBarStock.progress = percent

            // Dinamismo de color según el estado semántico M3 de existencias
            when {
                dispInt <= 0 -> {
                    val red = Color.parseColor("#EF4444")
                    binding.progressBarStock.setIndicatorColor(red)
                    binding.tvStockCantidad.setTextColor(red)
                }
                dispInt < 5 -> {
                    val amber = Color.parseColor("#D97706")
                    binding.progressBarStock.setIndicatorColor(amber)
                    binding.tvStockCantidad.setTextColor(amber)
                }
                else -> {
                    val green = Color.parseColor("#10B981")
                    binding.progressBarStock.setIndicatorColor(green)
                    binding.tvStockCantidad.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.text_primary))
                }
            }

            // Estilo visual del badge según categoría médica
            configurarBadgeCategoria(item.categoria)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun configurarBadgeCategoria(categoria: String) {
            val (bgColor, textColor) = when {
                categoria.contains("Analgésico", ignoreCase = true) || categoria.contains("AINE", ignoreCase = true) -> {
                    Pair(Color.parseColor("#EFF6FF"), Color.parseColor("#1D4ED8")) // Azul M3
                }
                categoria.contains("Antibiótico", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#B45309")) // Ámbar M3
                }
                categoria.contains("Corticoide", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEE2E2"), Color.parseColor("#B91C1C")) // Rojo suave M3
                }
                categoria.contains("Antiparasitario", ignoreCase = true) -> {
                    Pair(Color.parseColor("#ECFDF5"), Color.parseColor("#047857")) // Verde esmeralda M3
                }
                categoria.contains("Fluido", ignoreCase = true) -> {
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
