package com.example.petvetericano.dynamicform

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

/**
 * Gestor de estado reactivo y centralizado para un formulario dinámico.
 * Mantiene los valores de los campos, sus niveles de confianza (en caso de dictado por voz),
 * evalúa reglas condicionales de visibilidad y valida campos obligatorios visibles.
 */
class DynamicFormState(
    val definition: DynamicFormDefinition = ActaFormDefinitionRegistry.getActaAtencionDefinition()
) : Serializable {

    private val values = mutableMapOf<String, DynamicFieldValue>()
    private var cachedVisibility: FormVisibilityEvaluation? = null

    @Transient
    private val listeners = mutableListOf<(fieldId: String, value: Any?) -> Unit>()

    init {
        // Inicializar campos con sus valores por defecto
        for (field in definition.getAllFields()) {
            values[field.id] = DynamicFieldValue(
                fieldId = field.id,
                value = field.defaultValue,
                source = if (field.defaultValue != null) "default" else "user"
            )
        }
        reevaluateVisibility()
    }

    fun addChangeListener(listener: (fieldId: String, value: Any?) -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: (fieldId: String, value: Any?) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Actualiza el valor de un campo y dispara la reevaluación de reglas condicionales.
     */
    fun setValue(
        fieldId: String,
        value: Any?,
        source: String = "user",
        confidence: Float? = null,
        notifyListeners: Boolean = true
    ) {
        val existing = values[fieldId]
        if (existing != null) {
            existing.value = value
            existing.source = source
            if (confidence != null) existing.confidence = confidence
            if (source == "user") existing.modifiedByUser = true
        } else {
            values[fieldId] = DynamicFieldValue(
                fieldId = fieldId,
                value = value,
                confidence = confidence,
                source = source,
                modifiedByUser = (source == "user")
            )
        }

        reevaluateVisibility()

        if (notifyListeners) {
            for (listener in listeners) {
                try {
                    listener(fieldId, value)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun getValue(fieldId: String): Any? = values[fieldId]?.value

    fun getString(fieldId: String): String = values[fieldId]?.asString() ?: ""

    fun getBoolean(fieldId: String): Boolean = values[fieldId]?.asBoolean() ?: false

    fun getDouble(fieldId: String): Double? = values[fieldId]?.asDouble()

    fun getInt(fieldId: String): Int? = values[fieldId]?.asInt()

    fun getFieldValue(fieldId: String): DynamicFieldValue? = values[fieldId]

    fun getAllValuesMap(): Map<String, Any?> {
        return values.mapValues { it.value.value }
    }

    /**
     * Reevalúa la visibilidad de todas las secciones y campos según los valores actuales.
     */
    fun reevaluateVisibility(): FormVisibilityEvaluation {
        val eval = ConditionalRulesEngine.evaluateForm(definition, getAllValuesMap())
        cachedVisibility = eval
        return eval
    }

    fun isFieldVisible(fieldId: String): Boolean {
        val eval = cachedVisibility ?: reevaluateVisibility()
        return eval.fieldVisibility[fieldId] ?: true
    }

    fun isSectionVisible(sectionId: String): Boolean {
        val eval = cachedVisibility ?: reevaluateVisibility()
        return eval.sectionVisibility[sectionId] ?: true
    }

    /**
     * Valida que los campos obligatorios VISIBLES contengan valor.
     * Los campos ocultos por reglas condicionales son ignorados conforme a la política de negocio.
     */
    fun validateRequiredFields(step: Int? = null): List<String> {
        val errors = mutableListOf<String>()
        val eval = cachedVisibility ?: reevaluateVisibility()

        for (section in definition.sections) {
            if (step != null && section.step != step) continue
            if (eval.sectionVisibility[section.id] == false) continue

            for (field in section.fields) {
                if (step != null && field.step != step) continue
                if (eval.fieldVisibility[field.id] == false) continue

                if (field.required) {
                    val raw = values[field.id]?.value
                    val isEmpty = when (raw) {
                        null -> true
                        is String -> raw.trim().isEmpty()
                        is List<*> -> raw.isEmpty()
                        else -> false
                    }
                    if (isEmpty) {
                        errors.add("El campo '${field.label}' es obligatorio.")
                    }
                }
            }
        }

        return errors
    }

    /**
     * Aplica la extracción estructurada devuelta por la IA (Gemini).
     */
    fun applyVoiceExtraction(
        extractedFields: Map<String, Any?>,
        confidences: Map<String, Float?> = emptyMap(),
        overwriteExisting: Boolean = false
    ): Int {
        var camposActualizados = 0

        for ((fieldId, newVal) in extractedFields) {
            if (newVal == null) continue

            val currentVal = values[fieldId]?.value
            val isCurrentEmpty = when (currentVal) {
                null -> true
                is String -> currentVal.trim().isEmpty()
                is Boolean -> !currentVal
                else -> false
            }

            // Aplicar si está vacío o si se autoriza sobreescribir
            if (isCurrentEmpty || overwriteExisting) {
                val conf = confidences[fieldId]
                setValue(fieldId, newVal, source = "voice", confidence = conf, notifyListeners = true)
                camposActualizados++
            }
        }

        return camposActualizados
    }

    /**
     * Serializa el estado de los campos en JSON para persistencia local en SQLite.
     */
    fun toJson(): String {
        val gson = Gson()
        val dtoList = values.values.map {
            mapOf(
                "field_id" to it.fieldId,
                "value" to it.value,
                "confidence" to it.confidence,
                "source" to it.source,
                "modified" to it.modifiedByUser
            )
        }
        return gson.toJson(dtoList)
    }

    /**
     * Restaura el estado de los campos desde JSON previamente guardado en SQLite.
     */
    fun restoreFromJson(json: String?) {
        if (json.isNullOrBlank()) return
        try {
            val gson = Gson()
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val dtoList: List<Map<String, Any?>> = gson.fromJson(json, type) ?: return

            for (item in dtoList) {
                val fId = item["field_id"] as? String ?: continue
                val fVal = item["value"]
                val fConf = (item["confidence"] as? Number)?.toFloat()
                val fSrc = item["source"] as? String ?: "user"
                val fMod = item["modified"] as? Boolean ?: false

                val entity = values[fId]
                if (entity != null) {
                    entity.value = fVal
                    entity.confidence = fConf
                    entity.source = fSrc
                    entity.modifiedByUser = fMod
                } else {
                    values[fId] = DynamicFieldValue(
                        fieldId = fId,
                        value = fVal,
                        confidence = fConf,
                        source = fSrc,
                        modifiedByUser = fMod
                    )
                }
            }
            reevaluateVisibility()
        } catch (_: Exception) {
        }
    }
}

