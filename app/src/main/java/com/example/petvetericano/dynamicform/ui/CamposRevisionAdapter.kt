package com.example.petvetericano.dynamicform.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petvetericano.R
import com.example.petvetericano.databinding.ItemCampoExtraidoRevisionBinding
import java.io.Serializable
import kotlin.math.roundToInt

data class CampoRevisionItem(
    val fieldId: String,
    val label: String,
    val sectionTitle: String = "",
    val showSectionHeader: Boolean = false,
    var valueStr: String,
    val originalVoiceStr: String = valueStr,
    val existingValueStr: String? = null,
    val hasConflict: Boolean = false,
    var isAccepted: Boolean = !hasConflict,
    val confidence: Float? = null,
    val isBoolean: Boolean = false
) : Serializable

/**
 * Adaptador para visualización agrupada, revisión, descarte individual y edición humana
 * de los campos extraídos por IA (Gemini).
 */
class CamposRevisionAdapter(
    private val items: MutableList<CampoRevisionItem>,
    private val onSelectionChanged: (acceptedCount: Int) -> Unit = {}
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

        if (item.showSectionHeader && item.sectionTitle.isNotBlank()) {
            holder.binding.tvSeccionHeader.visibility = View.VISIBLE
            holder.binding.tvSeccionHeader.text = item.sectionTitle
        } else {
            holder.binding.tvSeccionHeader.visibility = View.GONE
        }

        holder.binding.tvNombreCampo.text = item.label

        val conf = item.confidence
        val confPorcentaje = conf?.let { (it * 100f).roundToInt().coerceIn(0, 100) }
        val isLowConfidence = conf != null && conf < 0.70f

        when {
            confPorcentaje != null && confPorcentaje >= 85 -> {
                holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.radix_grass_bg)
                )
                holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.radix_grass_border)
                holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.radix_grass_text))
                holder.binding.tvBadgeConfianza.text =
                    context.getString(R.string.reporte_ux_dictado_confianza_alta, confPorcentaje)
            }
            confPorcentaje != null && !isLowConfidence -> {
                holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.primary_soft)
                )
                holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.primary_light)
                holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.binding.tvBadgeConfianza.text =
                    context.getString(R.string.reporte_ux_dictado_confianza_media, confPorcentaje)
            }
            confPorcentaje != null -> {
                holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.radix_amber_bg)
                )
                holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.radix_amber_border)
                holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.radix_amber_text))
                holder.binding.tvBadgeConfianza.text =
                    context.getString(R.string.reporte_ux_dictado_confianza_baja, confPorcentaje)
            }
            else -> {
                holder.binding.cardBadgeConfianza.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.chip_bg_neutral)
                )
                holder.binding.cardBadgeConfianza.strokeColor = ContextCompat.getColor(context, R.color.chip_stroke_neutral)
                holder.binding.tvBadgeConfianza.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                holder.binding.tvBadgeConfianza.text =
                    context.getString(R.string.reporte_ux_dictado_confianza_detectado)
            }
        }

        if (item.hasConflict && !item.existingValueStr.isNullOrBlank()) {
            holder.binding.tvConflictoValor.visibility = View.VISIBLE
            holder.binding.tvConflictoValor.text = context.getString(
                R.string.reporte_ux_dictado_conflicto_label,
                item.existingValueStr,
                item.valueStr
            )
            holder.binding.cbAplicarCampo.text =
                context.getString(R.string.reporte_ux_dictado_checkbox_sobrescribir)
        } else {
            holder.binding.tvConflictoValor.visibility = View.GONE
            holder.binding.cbAplicarCampo.text =
                context.getString(R.string.reporte_ux_dictado_checkbox_aplicar)
        }

        val strokeRes = when {
            item.hasConflict || isLowConfidence -> R.color.radix_amber_border
            item.isAccepted -> R.color.card_stroke
            else -> R.color.chip_stroke_neutral
        }
        holder.binding.cardCampoRevision.strokeColor = ContextCompat.getColor(context, strokeRes)
        holder.binding.cardCampoRevision.alpha = if (item.isAccepted) 1.0f else 0.7f

        holder.textWatcher?.let { holder.binding.etValorCampo.removeTextChangedListener(it) }
        if (holder.binding.etValorCampo.text?.toString() != item.valueStr) {
            holder.binding.etValorCampo.setText(item.valueStr)
        }
        holder.binding.etValorCampo.isEnabled = item.isAccepted

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                item.valueStr = s?.toString() ?: ""
                if (item.hasConflict && !item.existingValueStr.isNullOrBlank()) {
                    holder.binding.tvConflictoValor.text = context.getString(
                        R.string.reporte_ux_dictado_conflicto_label,
                        item.existingValueStr,
                        item.valueStr
                    )
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.textWatcher = watcher
        holder.binding.etValorCampo.addTextChangedListener(watcher)

        holder.binding.cbAplicarCampo.setOnCheckedChangeListener(null)
        holder.binding.cbAplicarCampo.isChecked = item.isAccepted
        holder.binding.cbAplicarCampo.setOnCheckedChangeListener { _, isChecked ->
            item.isAccepted = isChecked
            holder.binding.etValorCampo.isEnabled = isChecked
            holder.binding.cardCampoRevision.alpha = if (isChecked) 1.0f else 0.7f
            onSelectionChanged(getAcceptedCount())
        }
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<CampoRevisionItem> = items

    fun getAcceptedCount(): Int = items.count { it.isAccepted }
}

