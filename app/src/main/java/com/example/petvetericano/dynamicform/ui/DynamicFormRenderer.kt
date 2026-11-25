package com.example.petvetericano.dynamicform.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import com.example.petvetericano.R
import com.example.petvetericano.dynamicform.*
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Renderizador dinámico de controles visuales en Android (XML + Material Design 3).
 * Genera programáticamente campos configurables basados en FormFieldDefinition,
 * gestionando sus escuchadores, tipos de entrada, valores y visibilidad condicional.
 */
class DynamicFormRenderer(
    private val context: Context,
    private val state: DynamicFormState
) {

    private val viewsMap = mutableMapOf<String, View>()

    /**
     * Renderiza una lista de campos dentro de un contenedor padre LinearLayout.
     */
    fun renderFields(
        container: LinearLayout,
        fields: List<FormFieldDefinition>
    ) {
        container.removeAllViews()
        viewsMap.clear()

        for (field in fields) {
            val fieldView = createFieldView(field)
            if (fieldView != null) {
                viewsMap[field.id] = fieldView
                container.addView(fieldView)
            }
        }

        updateVisibility()
    }

    /**
     * Crea la vista adecuada según el tipo de campo especificado.
     */
    fun createFieldView(field: FormFieldDefinition): View? {
        return when (field.type) {
            FieldType.TEXT, FieldType.TEXT_LONG, FieldType.OBSERVATIONS -> {
                createTextInput(field, isLong = (field.type != FieldType.TEXT))
            }
            FieldType.INTEGER -> {
                createNumericInput(field, isDecimal = false)
            }
            FieldType.DECIMAL -> {
                createNumericInput(field, isDecimal = true)
            }
            FieldType.DATE, FieldType.TIME, FieldType.DATETIME -> {
                createTextInput(field, isLong = false)
            }
            FieldType.SELECT -> {
                createSelectDropdown(field)
            }
            FieldType.BOOLEAN, FieldType.SWITCH, FieldType.CHECKBOX -> {
                createSwitchInput(field)
            }
            FieldType.RADIO, FieldType.MULTI_SELECT -> {
                createSelectDropdown(field)
            }
        }
    }

    private fun createTextInput(field: FormFieldDefinition, isLong: Boolean): View {
        val r = dpToPx(12).toFloat()
        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
            hint = field.label
            setBoxCornerRadii(r, r, r, r)
        }

        val et = TextInputEditText(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (isLong) {
                minLines = 3
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                maxLines = 1
                inputType = InputType.TYPE_CLASS_TEXT
            }
            setText(state.getString(field.id))

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    state.setValue(field.id, s?.toString()?.trim() ?: "")
                    updateVisibility()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        til.addView(et)
        return til
    }

    private fun createNumericInput(field: FormFieldDefinition, isDecimal: Boolean): View {
        val r = dpToPx(12).toFloat()
        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
            hint = field.label
            setBoxCornerRadii(r, r, r, r)
        }

        val et = TextInputEditText(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            maxLines = 1
            inputType = if (isDecimal) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_NUMBER
            }
            setText(state.getString(field.id))

            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val str = s?.toString()?.trim() ?: ""
                    val value: Any? = if (isDecimal) str.toDoubleOrNull() else str.toIntOrNull()
                    state.setValue(field.id, value ?: str)
                    updateVisibility()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        til.addView(et)
        return til
    }

    private fun createSelectDropdown(field: FormFieldDefinition): View {
        val r = dpToPx(12).toFloat()
        val til = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
            hint = field.label
            setBoxCornerRadii(r, r, r, r)
        }

        val actv = MaterialAutoCompleteTextView(til.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            inputType = InputType.TYPE_NULL
            val opList = field.options?.map { it.label } ?: emptyList()
            val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, opList)
            setAdapter(adapter)

            val current = state.getString(field.id)
            if (current.isNotBlank()) {
                setText(current, false)
            }

            setOnItemClickListener { _, _, position, _ ->
                val selected = opList.getOrNull(position) ?: ""
                state.setValue(field.id, selected)
                updateVisibility()
            }
        }

        til.addView(actv)
        return til
    }

    private fun createSwitchInput(field: FormFieldDefinition): View {
        return MaterialSwitch(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(4)
            }
            text = field.label
            isChecked = state.getBoolean(field.id)

            setOnCheckedChangeListener { _, isChecked ->
                state.setValue(field.id, isChecked)
                updateVisibility()
            }
        }
    }

    fun updateVisibility() {
        val eval = state.reevaluateVisibility()
        for ((fieldId, view) in viewsMap) {
            val isVis = eval.fieldVisibility[fieldId] ?: true
            view.visibility = if (isVis) View.VISIBLE else View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
