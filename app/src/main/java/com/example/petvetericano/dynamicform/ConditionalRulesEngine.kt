package com.example.petvetericano.dynamicform

/**
 * Resultado consolidado de la evaluación de reglas condicionales.
 */
data class FormVisibilityEvaluation(
    val fieldVisibility: Map<String, Boolean>,
    val sectionVisibility: Map<String, Boolean>
)

/**
 * Motor de reglas condicionales para formularios dinámicos.
 * Evalúa expresiones lógicas complejas (AND/OR, comparaciones de tipos heterogéneos)
 * garantizando ausencia de ciclos infinitos y determinando con precisión qué secciones
 * y campos deben mostrarse u ocultarse en la interfaz.
 */
object ConditionalRulesEngine {

    /**
     * Evalúa la visibilidad de todas las secciones y campos de un formulario según sus valores actuales.
     */
    fun evaluateForm(
        definition: DynamicFormDefinition,
        values: Map<String, Any?>
    ): FormVisibilityEvaluation {
        val fieldVis = mutableMapOf<String, Boolean>()
        val secVis = mutableMapOf<String, Boolean>()
        val visited = mutableSetOf<String>()

        // 1. Evaluar visibilidad de cada sección
        for (section in definition.sections) {
            val isSecVisible = if (section.visibilityRule != null) {
                evaluateRule(section.visibilityRule, values, visited)
            } else {
                true
            }
            secVis[section.id] = isSecVisible

            // 2. Evaluar campos dentro de la sección
            for (field in section.fields) {
                val isFieldRuleTrue = if (field.visibilityRule != null) {
                    evaluateRule(field.visibilityRule, values, visited)
                } else {
                    true
                }
                // Un campo es visible solo si su sección es visible Y su regla se cumple
                fieldVis[field.id] = isSecVisible && isFieldRuleTrue
            }
        }

        return FormVisibilityEvaluation(
            fieldVisibility = fieldVis,
            sectionVisibility = secVis
        )
    }

    /**
     * Evalúa una regla condicional compuesta (AND u OR) contra el mapa de valores.
     */
    fun evaluateRule(
        rule: ConditionalRule?,
        values: Map<String, Any?>,
        visitedFields: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (rule == null || rule.conditions.isEmpty()) return true

        return when (rule.operator) {
            LogicalOperator.AND -> {
                rule.conditions.all { evaluateCondition(it, values, visitedFields) }
            }
            LogicalOperator.OR -> {
                rule.conditions.any { evaluateCondition(it, values, visitedFields) }
            }
        }
    }

    /**
     * Evalúa una condición individual sobre el valor de un campo específico.
     */
    fun evaluateCondition(
        condition: FieldCondition,
        values: Map<String, Any?>,
        visitedFields: MutableSet<String> = mutableSetOf()
    ): Boolean {
        val targetId = condition.targetFieldId

        // Protección contra ciclos de dependencias
        if (visitedFields.contains(targetId)) {
            // Ciclo detectado, cortar recursión de manera segura
            return true
        }

        val actualRawValue = values[targetId]

        return when (condition.comparison) {
            ComparisonType.IS_EMPTY -> {
                actualRawValue == null || actualRawValue.toString().trim().isEmpty()
            }
            ComparisonType.IS_NOT_EMPTY -> {
                actualRawValue != null && actualRawValue.toString().trim().isNotEmpty()
            }
            ComparisonType.IS_TRUE -> {
                coerceToBoolean(actualRawValue) == true
            }
            ComparisonType.IS_FALSE -> {
                coerceToBoolean(actualRawValue) == false
            }
            ComparisonType.EQUALS -> {
                areValuesEqual(actualRawValue, condition.expectedValue)
            }
            ComparisonType.NOT_EQUALS -> {
                !areValuesEqual(actualRawValue, condition.expectedValue)
            }
            ComparisonType.CONTAINS -> {
                val str = actualRawValue?.toString() ?: ""
                val expectedStr = condition.expectedValue?.toString() ?: ""
                str.contains(expectedStr, ignoreCase = true)
            }
            ComparisonType.GREATER_THAN -> {
                val numActual = coerceToDouble(actualRawValue)
                val numExpected = coerceToDouble(condition.expectedValue)
                if (numActual != null && numExpected != null) {
                    numActual > numExpected
                } else false
            }
            ComparisonType.LESS_THAN -> {
                val numActual = coerceToDouble(actualRawValue)
                val numExpected = coerceToDouble(condition.expectedValue)
                if (numActual != null && numExpected != null) {
                    numActual < numExpected
                } else false
            }
        }
    }

    private fun areValuesEqual(v1: Any?, v2: Any?): Boolean {
        if (v1 == null && v2 == null) return true
        if (v1 == null || v2 == null) return false

        // Comparación de booleanos
        val b1 = coerceToBoolean(v1)
        val b2 = coerceToBoolean(v2)
        if (b1 != null && b2 != null) {
            return b1 == b2
        }

        // Comparación de números
        val n1 = coerceToDouble(v1)
        val n2 = coerceToDouble(v2)
        if (n1 != null && n2 != null) {
            return n1 == n2
        }

        // Comparación de cadenas ignorando mayúsculas y espacios marginales
        return v1.toString().trim().equals(v2.toString().trim(), ignoreCase = true)
    }

    private fun coerceToBoolean(value: Any?): Boolean? {
        return when (value) {
            null -> null
            is Boolean -> value
            is Number -> value.toInt() == 1
            is String -> {
                val s = value.trim().lowercase()
                when (s) {
                    "true", "1", "si", "sí", "yes", "s" -> true
                    "false", "0", "no", "n" -> false
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun coerceToDouble(value: Any?): Double? {
        return when (value) {
            null -> null
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
}

