package com.example.petvetericano.dynamicform

import org.junit.Assert.*
import org.junit.Test

class DynamicFormEngineTest {

    @Test
    fun testFormDefinitionRegistryHasAllSectionsAndFields() {
        val def = ActaFormDefinitionRegistry.getActaAtencionDefinition()

        assertEquals("acta_atencion_campo", def.id)
        assertEquals(1, def.version)
        assertTrue(def.sections.size >= 5)

        val allFields = def.getAllFields()
        assertTrue(allFields.size >= 40)

        assertNotNull(def.findField("numero_radicado"))
        assertNotNull(def.findField("propietario_nombre"))
        assertNotNull(def.findField("nombre_paciente"))
        assertNotNull(def.findField("frecuencia_cardiaca"))
        assertNotNull(def.findField("anamnesis_descripcion_queja"))
        assertNotNull(def.findField("tratamiento_realizado"))
        assertNotNull(def.findField("requiere_rescate"))
        assertNotNull(def.findField("sw_respiratorio_tos"))
        assertNotNull(def.findField("sw_digestivo_vomito"))
    }

    @Test
    fun testConditionalRulesQuienReportaOtro() {
        val def = ActaFormDefinitionRegistry.getActaAtencionDefinition()

        // 1. Con Propietario -> quien_reporta_otro debe ser falso (oculto)
        val valuesPropietario = mapOf<String, Any?>("quien_reporta" to "Propietario / Tenedor")
        val eval1 = ConditionalRulesEngine.evaluateForm(def, valuesPropietario)
        assertEquals(false, eval1.fieldVisibility["quien_reporta_otro"])

        // 2. Con Otro -> quien_reporta_otro debe ser verdadero (visible)
        val valuesOtro = mapOf<String, Any?>("quien_reporta" to "Otro")
        val eval2 = ConditionalRulesEngine.evaluateForm(def, valuesOtro)
        assertEquals(true, eval2.fieldVisibility["quien_reporta_otro"])
    }

    @Test
    fun testConditionalRulesRescateCbaYTestigos() {
        val def = ActaFormDefinitionRegistry.getActaAtencionDefinition()

        // 1. Sin rescate -> sección de rescate oculta, testigos ocultos
        val sinRescate = mapOf<String, Any?>("requiere_rescate" to false)
        val eval1 = ConditionalRulesEngine.evaluateForm(def, sinRescate)
        assertEquals(false, eval1.sectionVisibility["sec_rescate_cba"])
        assertEquals(false, eval1.fieldVisibility["motivo_rescate"])
        assertEquals(false, eval1.fieldVisibility["testigo1_nombre"])

        // 2. Con rescate y con testigo
        val conRescateConTestigo = mapOf<String, Any?>(
            "requiere_rescate" to true,
            "chip_sin_testigo" to false
        )
        val eval2 = ConditionalRulesEngine.evaluateForm(def, conRescateConTestigo)
        assertEquals(true, eval2.sectionVisibility["sec_rescate_cba"])
        assertEquals(true, eval2.fieldVisibility["motivo_rescate"])
        assertEquals(true, eval2.fieldVisibility["testigo1_nombre"])

        // 3. Con rescate pero con chip_sin_testigo = true -> testigos ocultos
        val conRescateSinTestigo = mapOf<String, Any?>(
            "requiere_rescate" to true,
            "chip_sin_testigo" to true
        )
        val eval3 = ConditionalRulesEngine.evaluateForm(def, conRescateSinTestigo)
        assertEquals(true, eval3.sectionVisibility["sec_rescate_cba"])
        assertEquals(true, eval3.fieldVisibility["motivo_rescate"])
        assertEquals(false, eval3.fieldVisibility["testigo1_nombre"])
    }

    @Test
    fun testDynamicFormStateValuesAndSerialization() {
        val state = DynamicFormState()

        state.setValue("numero_radicado", "RAD-2026-999")
        state.setValue("propietario_nombre", "María Morales")
        state.setValue("requiere_rescate", true)
        state.setValue("peso_paciente", 14.5)

        assertEquals("RAD-2026-999", state.getString("numero_radicado"))
        assertEquals("María Morales", state.getString("propietario_nombre"))
        assertEquals(true, state.getBoolean("requiere_rescate"))
        assertEquals(14.5, state.getDouble("peso_paciente") ?: 0.0, 0.01)

        // Serializar a JSON
        val json = state.toJson()
        assertTrue(json.contains("RAD-2026-999"))
        assertTrue(json.contains("María Morales"))

        // Restaurar en nuevo estado
        val nuevoState = DynamicFormState()
        nuevoState.restoreFromJson(json)

        assertEquals("RAD-2026-999", nuevoState.getString("numero_radicado"))
        assertEquals("María Morales", nuevoState.getString("propietario_nombre"))
        assertEquals(true, nuevoState.getBoolean("requiere_rescate"))
    }

    @Test
    fun testVoiceExtractionApplication() {
        val state = DynamicFormState()

        val voiceFields = mapOf<String, Any?>(
            "nombre_paciente" to "Simba",
            "paciente_especie" to "Felino",
            "anamnesis_descripcion_queja" to "Gatito con herida en miembro anterior",
            "sw_musculo_herida_abierta" to true
        )
        val confianzas = mapOf(
            "nombre_paciente" to 0.95f,
            "paciente_especie" to 0.98f,
            "anamnesis_descripcion_queja" to 0.92f,
            "sw_musculo_herida_abierta" to 0.99f
        )

        val actualizados = state.applyVoiceExtraction(voiceFields, confianzas, overwriteExisting = true)
        assertEquals(4, actualizados)

        assertEquals("Simba", state.getString("nombre_paciente"))
        assertEquals("Felino", state.getString("paciente_especie"))
        assertTrue(state.getBoolean("sw_musculo_herida_abierta"))

        val fieldVal = state.getFieldValue("nombre_paciente")
        assertNotNull(fieldVal)
        assertEquals("voice", fieldVal?.source)
        assertEquals(0.95f, fieldVal?.confidence ?: 0f, 0.01f)
    }

    @Test
    fun testValidationIgnoresHiddenFields() {
        val state = DynamicFormState()

        // Dejar quien_reporta en Propietario (quien_reporta_otro oculto)
        state.setValue("quien_reporta", "Propietario / Tenedor")

        // Paso 1: Faltan varios campos obligatorios
        val errores = state.validateRequiredFields(step = 1)
        assertTrue(errores.isNotEmpty())

        // Asegurarse de que quien_reporta_otro no figure como error porque está oculto
        val errorOtro = errores.firstOrNull { it.contains("quien_reporta_otro") || it.contains("Especifique") }
        assertNull(errorOtro)
    }
}

