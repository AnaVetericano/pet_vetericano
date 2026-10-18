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
            binding.tvConcentracionPresentacion.text = "${item.concentracion} • ${item.presentacion}"
            binding.tvCategoriaBadge.text = item.categoria
            binding.tvLote.text = "Lote: ${item.lote}"
            binding.tvVencimiento.text = "Vence: ${item.fechaVencimiento}"
            binding.tvVia.text = "Vía: ${item.viaRecomendada}"

            val formatStock = if (item.cantidadDisponible % 1.0 == 0.0) {
                "${item.cantidadDisponible.toInt()} / ${item.cantidadInicial.toInt()} ${item.unidadMedida}"
            } else {
                "${item.cantidadDisponible} / ${item.cantidadInicial.toInt()} ${item.unidadMedida}"
            }
            binding.tvStockCantidad.text = formatStock

            val ratio = if (item.cantidadInicial > 0) (item.cantidadDisponible / item.cantidadInicial).toFloat() else 0f
            val percent = (ratio * 100).toInt().coerceIn(0, 100)
            binding.progressBarStock.progress = percent

            // Dinamismo de color según el porcentaje de stock disponible
            when {
                ratio <= 0f -> {
                    val red = Color.parseColor("#EF4444")
                    binding.progressBarStock.setIndicatorColor(red)
                    binding.tvStockCantidad.setTextColor(red)
                }
                ratio <= 0.25f -> {
                    val orange = Color.parseColor("#F59E0B")
                    binding.progressBarStock.setIndicatorColor(orange)
                    binding.tvStockCantidad.setTextColor(orange)
                }
                else -> {
                    val green = Color.parseColor("#10B981")
                    binding.progressBarStock.setIndicatorColor(green)
                    binding.tvStockCantidad.setTextColor(Color.parseColor("#1E293B"))
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
                    Pair(Color.parseColor("#EFF6FF"), Color.parseColor("#1D4ED8")) // Azul
                }
                categoria.contains("Antibiótico", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEF3C7"), Color.parseColor("#B45309")) // Ámbar
                }
                categoria.contains("Corticoide", ignoreCase = true) -> {
                    Pair(Color.parseColor("#FEE2E2"), Color.parseColor("#B91C1C")) // Rojo suave
                }
                categoria.contains("Antiparasitario", ignoreCase = true) -> {
                    Pair(Color.parseColor("#ECFDF5"), Color.parseColor("#047857")) // Verde esmeralda
                }
                categoria.contains("Fluido", ignoreCase = true) -> {
                    Pair(Color.parseColor("#F0FDFA"), Color.parseColor("#0F766E")) // Teal
                }
                else -> {
                    Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#475569")) // Gris pizarra
                }
            }
            binding.tvCategoriaBadge.backgroundTintList = ColorStateList.valueOf(bgColor)
            binding.tvCategoriaBadge.setTextColor(textColor)
        }
    }
}
