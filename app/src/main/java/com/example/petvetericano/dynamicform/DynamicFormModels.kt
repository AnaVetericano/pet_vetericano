package com.example.petvetericano.dynamicform

import java.io.Serializable

/**
 * Tipos de campos soportados por el motor de formularios dinámicos.
 */
enum class FieldType {
    TEXT,
    TEXT_LONG,
    INTEGER,
    DECIMAL,
    DATE,
    TIME,
    DATETIME,
    BOOLEAN,
    CHECKBOX,
    SWITCH,
    RADIO,
    SELECT,
    MULTI_SELECT,
    OBSERVATIONS
}

/**
 * Operadores lógicos para evaluar múltiples condiciones.
 */
enum class LogicalOperator {
    AND,
    OR
}

/**
 * Tipos de comparación para reglas condicionales de visibilidad y habilitación.
 */
enum class ComparisonType {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    IS_EMPTY,
    IS_NOT_EMPTY,
    IS_TRUE,
    IS_FALSE,
    GREATER_THAN,
    LESS_THAN
}

/**
 * Condición individual aplicada sobre el valor de un campo específico.
 */
data class FieldCondition(
    val targetFieldId: String,
    val comparison: ComparisonType,
    val expectedValue: Any? = null
) : Serializable

/**
 * Regla condicional compuesta por una o varias condiciones combinadas con AND u OR.
 */
data class ConditionalRule(
    val operator: LogicalOperator = LogicalOperator.AND,
    val conditions: List<FieldCondition>
) : Serializable {
    companion object {
        fun simple(targetFieldId: String, comparison: ComparisonType, expectedValue: Any? = null): ConditionalRule {
            return ConditionalRule(
                operator = LogicalOperator.AND,
                conditions = listOf(FieldCondition(targetFieldId, comparison, expectedValue))
            )
        }
    }
}

/**
 * Opción de selección para campos tipo SELECT, RADIO o MULTI_SELECT.
 */
data class SelectOption(
    val value: String,
    val label: String
) : Serializable

/**
 * Definición declarativa de un campo individual dentro de una sección del formulario.
 */
data class FormFieldDefinition(
    val id: String,
    val label: String,
    val hint: String? = null,
    val type: FieldType = FieldType.TEXT,
    val step: Int = 1,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val options: List<SelectOption>? = null,
    val validationRegex: String? = null,
    val validationErrorMsg: String? = null,
    val visibilityRule: ConditionalRule? = null,
    val voiceExtractable: Boolean = true,
    val voiceExtractionKey: String = id,
    val description: String? = null
) : Serializable

/**
 * Sección de un formulario que agrupa campos temáticos y puede tener reglas de visibilidad.
 */
data class FormSectionDefinition(
    val id: String,
    val title: String,
    val step: Int,
    val description: String? = null,
    val visibilityRule: ConditionalRule? = null,
    val fields: List<FormFieldDefinition>
) : Serializable

/**
 * Definición raíz versionada de un formulario dinámico.
 */
data class DynamicFormDefinition(
    val id: String,
    val version: Int,
    val title: String,
    val description: String? = null,
    val sections: List<FormSectionDefinition>
) : Serializable {

    fun findField(fieldId: String): FormFieldDefinition? {
        for (section in sections) {
            for (field in section.fields) {
                if (field.id == fieldId) return field
            }
        }
        return null
    }

    fun getAllFields(): List<FormFieldDefinition> {
        return sections.flatMap { it.fields }
    }
}

/**
 * Valor en tiempo de ejecución de un campo dentro del formulario.
 */
data class DynamicFieldValue(
    val fieldId: String,
    var value: Any? = null,
    var confidence: Float? = null,
    var source: String = "user", // "user", "voice", "default"
    var modifiedByUser: Boolean = false
) : Serializable {

    fun asString(): String {
        return when (val v = value) {
            null -> ""
            is String -> v
            is Boolean -> if (v) "true" else "false"
            else -> v.toString()
        }
    }

    fun asBoolean(): Boolean {
        return when (val v = value) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true) || v.equals("si", ignoreCase = true) || v.equals("1")
            is Number -> v.toInt() == 1
            else -> false
        }
    }

    fun asDouble(): Double? {
        return when (val v = value) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    fun asInt(): Int? {
        return when (val v = value) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }
}

