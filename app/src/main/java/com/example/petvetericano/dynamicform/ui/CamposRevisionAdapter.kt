package com.example.petvetericano.dynamicform.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemCampoExtraidoRevisionBinding
import com.example.petvetericano.dynamicform.ActaFormDefinitionRegistry
import java.io.Serializable

data class CampoRevisionItem(
    val fieldId: String,
    val label: String,
    var valueStr: String,
    val confidence: Float? = null,
    val isBoolean: Boolean = false
) : Serializable

/**
 * Adaptador para visualización, revisión y corrección humana de los campos extraídos por IA (Gemini).
 */
class CamposRevisionAdapter(
    private val items: MutableList<CampoRevisionItem>
) : RecyclerView.Adapter<CamposRevisionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCampoExtraidoRevisionBinding) : RecyclerView.ViewHolder(binding.root) {
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCampoExtraidoRevisionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.tvNombreCampo.text = item.label

        // Configurar badge de confianza
        val conf = item.confidence
        val confPorcentaje = if (conf != null) (conf * 100).toInt() else null

        if (confPorcentaje != null && confPorcentaje >= 85) {
            holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.radix_grass_bg)
            )
            holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.radix_grass_border)
            holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.radix_grass_text))
            holder.binding.tvBadgeConfianza.text = "$confPorcentaje% certeza"
        } else if (confPorcentaje != null) {
            holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.radix_amber_bg)
            )
            holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.radix_amber_border)
            holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.radix_amber_text))
            holder.binding.tvBadgeConfianza.text = "$confPorcentaje% verificar"
        } else {
            holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.chip_bg_neutral)
            )
            holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.chip_stroke_neutral)
            holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            holder.binding.tvBadgeConfianza.text = "Detectado"
        }

        // Remover watcher anterior para evitar recursión
        holder.textWatcher?.let { holder.binding.etValorCampo.removeTextChangedListener(it) }

        holder.binding.etValorCampo.setText(item.valueStr)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.valueStr = s?.toString() ?: ""
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.textWatcher = watcher
        holder.binding.etValorCampo.addTextChangedListener(watcher)
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<CampoRevisionItem> = items

    fun setItems(nuevos: List<CampoRevisionItem>) {
        items.clear()
        items.addAll(nuevos)
        notifyDataSetChanged()
    }
}

