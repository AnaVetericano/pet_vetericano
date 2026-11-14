package com.example.petvetericano.dynamicform

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.example.petvetericano.*
import com.example.petvetericano.databinding.ActivityActaAtencionBinding

/**
 * Vinculador bidireccional entre la interfaz visual (ActivityActaAtencionBinding)
 * y el motor de formulario dinámico (DynamicFormState y ConditionalRulesEngine).
 *
 * Mantiene sincronizados los controles en pantalla con el estado reactivo,
 * aplicando visibilidad condicional sobre campos dependientes (ej. quien_reporta_otro,
 * módulo de rescate CBA, switches clínicos, etc.).
 */
class ActaFormBinder(
    private val binding: ActivityActaAtencionBinding,
    private val state: DynamicFormState
) {

    private var suprimirListeners = false

    fun inicializar() {
        conectarListenersVistas()
        state.addChangeListener { _, _ ->
            if (!suprimirListeners) {
                aplicarVisibilidad()
            }
        }
        aplicarVisibilidad()
    }

    /**
     * Sincroniza los valores actuales de los componentes visuales hacia DynamicFormState.
     */
    fun capturarHaciaEstado() {
        suprimirListeners = true
        try {
            // Paso 1
            state.setValue("numero_radicado", binding.etRadicado.text?.toString()?.trim() ?: "")
            state.setValue("fecha_atencion", binding.etFechaAtencion.text?.toString()?.trim() ?: "")
            state.setValue("solicitud_atencion_por", binding.etSolicitudAtencion.text?.toString()?.trim() ?: "")
            state.setValue("quien_reporta", binding.autoCompleteQuienReporta.text?.toString()?.trim() ?: "")
            state.setValue("quien_reporta_otro", binding.etQuienReportaOtro.text?.toString()?.trim() ?: "")
            state.setValue("propietario_nombre", binding.etPropietarioNombre.text?.toString()?.trim() ?: "")
            state.setValue("propietario_cedula", binding.etPropietarioCedula.text?.toString()?.trim() ?: "")
            state.setValue("propietario_telefono", binding.etPropietarioTelefono.text?.toString()?.trim() ?: "")
            state.setValue("propietario_email", binding.etPropietarioEmail.text?.toString()?.trim() ?: "")
            state.setValue("propietario_barrio", binding.etPropietarioBarrio.text?.toString()?.trim() ?: "")
            state.setValue("propietario_direccion", binding.etPropietarioDireccion.text?.toString()?.trim() ?: "")
            state.setValue("lugar_atencion", binding.etLugarAtencion.text?.toString()?.trim() ?: "")

            // Paso 2
            state.setValue("frecuencia_cardiaca", binding.etFrecuenciaCardiaca.text?.toString()?.trim() ?: "")
            state.setValue("frecuencia_respiratoria", binding.etFrecuenciaRespiratoria.text?.toString()?.trim() ?: "")
            state.setValue("mucosas", binding.etMucosas.text?.toString()?.trim() ?: "")
            state.setValue("anamnesis_descripcion_queja", binding.etAnamnesis.text?.toString()?.trim() ?: "")
            state.setValue("tratamiento_realizado", binding.etTratamiento.text?.toString()?.trim() ?: "")
            state.setValue("desparasitacion", binding.swDesparasitacion.isChecked)

            // Paso 3
            state.setValue("tipo_prueba", binding.autoCompleteTipoPrueba.text?.toString()?.trim() ?: "")
            state.setValue("pruebas_complementarias", binding.etPruebasComplementarias.text?.toString()?.trim() ?: "")
            state.setValue("resultado_pruebas", binding.etResultadoPruebas.text?.toString()?.trim() ?: "")
            state.setValue("compromisos", binding.etCompromisos.text?.toString()?.trim() ?: "")
            state.setValue("plazo_dias_cumplimiento", binding.etPlazoDias.text?.toString()?.trim()?.toIntOrNull())
            state.setValue("fundamento_legal", binding.etFundamentoLegal.text?.toString()?.trim() ?: "")
            state.setValue("estado_cierre", binding.autoCompleteEstadoCierre.text?.toString()?.trim() ?: "")
            state.setValue("observacion", binding.etObservaciones.text?.toString()?.trim() ?: "")
            state.setValue("funcionario_nombre", binding.etFuncionarioNombre.text?.toString()?.trim() ?: "")
            state.setValue("funcionario_cargo", binding.etFuncionarioCargo.text?.toString()?.trim() ?: "")
            state.setValue("funcionario_matricula", binding.etFuncionarioMatricula.text?.toString()?.trim() ?: "")
            state.setValue("funcionario_identificacion", binding.etFuncionarioIdentificacion.text?.toString()?.trim() ?: "")
            state.setValue("notificado_nombre", binding.etNotificadoNombre.text?.toString()?.trim() ?: "")
            state.setValue("notificado_identificacion", binding.etNotificadoIdentificacion.text?.toString()?.trim() ?: "")

            // Módulo de Rescate CBA
            state.setValue("requiere_rescate", binding.swRequiereRescate.isChecked)
            state.setValue("motivo_rescate", binding.autoCompleteMotivoRescate.text?.toString()?.trim() ?: "")
            state.setValue("persona_atiende_rescatista", binding.etPersonaAtiendeRescatista.text?.toString()?.trim() ?: "")
            state.setValue("persona_atiende_cedula", binding.etPersonaAtiendeCedula.text?.toString()?.trim() ?: "")
            state.setValue("persona_atiende_telefono", binding.etPersonaAtiendeTelefono.text?.toString()?.trim() ?: "")
            state.setValue("barrio_rescate", binding.etBarrioRescate.text?.toString()?.trim() ?: "")
            state.setValue("chip_sin_testigo", binding.chipSinTestigo.isChecked)
            state.setValue("testigo1_nombre", binding.etTestigo1Nombre.text?.toString()?.trim() ?: "")
            state.setValue("testigo1_cedula", binding.etTestigo1Cedula.text?.toString()?.trim() ?: "")
            state.setValue("testigo1_telefono", binding.etTestigo1Telefono.text?.toString()?.trim() ?: "")
            state.setValue("observaciones_rescate", binding.etObservacionesRescate.text?.toString()?.trim() ?: "")
            state.setValue("veterinario_receptor", binding.etVeterinarioReceptor.text?.toString()?.trim() ?: "")
            state.setValue("fecha_recepcion", binding.etFechaRecepcion.text?.toString()?.trim() ?: "")
            state.setValue("hora_recepcion", binding.etHoraRecepcion.text?.toString()?.trim() ?: "")
            state.setValue("observaciones_recepcion", binding.etObservacionesRecepcion.text?.toString()?.trim() ?: "")

            // 19 Switches de Evaluación Clínica por Sistemas
            state.setValue("sw_respiratorio_moco", binding.swRespiratorioMoco.isChecked)
            state.setValue("sw_respiratorio_dificultad", binding.swRespiratorioDificultad.isChecked)
            state.setValue("sw_respiratorio_tos", binding.swRespiratorioTos.isChecked)
            state.setValue("sw_respiratorio_laganeo", binding.swRespiratorioLaganeo.isChecked)
            state.setValue("sw_digestivo_vomito", binding.swDigestivoVomito.isChecked)
            state.setValue("sw_digestivo_diarrea", binding.swDigestivoDiarrea.isChecked)
            state.setValue("sw_digestivo_desnutrido", binding.swDigestivoDesnutrido.isChecked)
            state.setValue("sw_digestivo_deshidratado", binding.swDigestivoDeshidratado.isChecked)
            state.setValue("sw_musculo_caida_patas", binding.swMusculoCaidaPatas.isChecked)
            state.setValue("sw_musculo_cojo", binding.swMusculoCojo.isChecked)
            state.setValue("sw_musculo_herida_abierta", binding.swMusculoHeridaAbierta.isChecked)
            state.setValue("sw_musculo_gusanos", binding.swMusculoGusanos.isChecked)
            state.setValue("sw_musculo_postrado", binding.swMusculoPostrado.isChecked)
            state.setValue("sw_musculo_sarcoma", binding.swMusculoSarcoma.isChecked)
            state.setValue("sw_dermato_peladuras", binding.swDermatoPeladuras.isChecked)
            state.setValue("sw_dermato_heridas_sangre", binding.swDermatoHeridasSangre.isChecked)
            state.setValue("sw_dermato_pulgas", binding.swDermatoPulgas.isChecked)
            state.setValue("sw_dermato_carcinoma_cutaneo", binding.swDermatoCarcinomaCutaneo.isChecked)
            state.setValue("sw_genito_masa_secrecion", binding.swGenitoMasaSecrecion.isChecked)
            state.setValue("sw_genito_gestante", binding.swGenitoGestante.isChecked)
            state.setValue("sw_genito_celo", binding.swGenitoCelo.isChecked)
            state.setValue("comentario_respiratorio", binding.etComentarioRespiratorio.text?.toString()?.trim() ?: "")
            state.setValue("comentario_digestivo", binding.etComentarioDigestivo.text?.toString()?.trim() ?: "")
            state.setValue("comentario_musculo", binding.etComentarioMusculo.text?.toString()?.trim() ?: "")
            state.setValue("comentario_dermato", binding.etComentarioDermato.text?.toString()?.trim() ?: "")
            state.setValue("comentario_genito", binding.etComentarioGenito.text?.toString()?.trim() ?: "")

            // Leyes de Fundamento Legal
            val textoLegal = binding.etFundamentoLegal.text?.toString() ?: ""
            state.setValue("sw_ley_84_1989", textoLegal.contains("84 de 1989", ignoreCase = true))
            state.setValue("sw_ley_1638_2013", textoLegal.contains("1638 de 2013", ignoreCase = true))
            state.setValue("sw_ley_1774_2016", textoLegal.contains("1774 de 2016", ignoreCase = true))
            state.setValue("sw_ley_1801_2016", textoLegal.contains("1801 de 2016", ignoreCase = true))
            state.setValue("sw_ley_2054_2020", textoLegal.contains("2054 de 2020", ignoreCase = true))
            state.setValue("sw_ley_2385_2024", textoLegal.contains("2385 de 2024", ignoreCase = true))
        } finally {
            suprimirListeners = false
        }
    }

    /**
     * Inyecta los valores de DynamicFormState hacia los componentes visuales de la pantalla.
     * Útil tras restaurar un borrador de SQLite o al aplicar la extracción estructurada por voz.
     */
    fun inyectarHaciaVistas() {
        suprimirListeners = true
        try {
            // Paso 1
            asignarTexto(binding.etRadicado, state.getString("numero_radicado"))
            asignarTexto(binding.etFechaAtencion, state.getString("fecha_atencion"))
            asignarAutoComplete(binding.etSolicitudAtencion, state.getString("solicitud_atencion_por"))
            asignarAutoComplete(binding.autoCompleteQuienReporta, state.getString("quien_reporta"))
            asignarTexto(binding.etQuienReportaOtro, state.getString("quien_reporta_otro"))
            asignarTexto(binding.etPropietarioNombre, state.getString("propietario_nombre"))
            asignarTexto(binding.etPropietarioCedula, state.getString("propietario_cedula"))
            asignarTexto(binding.etPropietarioTelefono, state.getString("propietario_telefono"))
            asignarTexto(binding.etPropietarioEmail, state.getString("propietario_email"))
            asignarTexto(binding.etPropietarioBarrio, state.getString("propietario_barrio"))
            asignarTexto(binding.etPropietarioDireccion, state.getString("propietario_direccion"))
            asignarTexto(binding.etLugarAtencion, state.getString("lugar_atencion"))

            // Paso 2
            asignarTexto(binding.etFrecuenciaCardiaca, state.getString("frecuencia_cardiaca"))
            asignarTexto(binding.etFrecuenciaRespiratoria, state.getString("frecuencia_respiratoria"))
            asignarAutoComplete(binding.etMucosas, state.getString("mucosas"))
            asignarTexto(binding.etAnamnesis, state.getString("anamnesis_descripcion_queja"))
            asignarTexto(binding.etTratamiento, state.getString("tratamiento_realizado"))
            binding.swDesparasitacion.isChecked = state.getBoolean("desparasitacion")

            // Paso 3
            asignarAutoComplete(binding.autoCompleteTipoPrueba, state.getString("tipo_prueba"))
            asignarTexto(binding.etPruebasComplementarias, state.getString("pruebas_complementarias"))
            asignarTexto(binding.etResultadoPruebas, state.getString("resultado_pruebas"))
            asignarTexto(binding.etCompromisos, state.getString("compromisos"))
            val plazo = state.getInt("plazo_dias_cumplimiento")
            asignarTexto(binding.etPlazoDias, plazo?.toString() ?: "")
            asignarTexto(binding.etFundamentoLegal, state.getString("fundamento_legal"))
            asignarAutoComplete(binding.autoCompleteEstadoCierre, state.getString("estado_cierre"))
            asignarTexto(binding.etObservaciones, state.getString("observacion"))
            asignarTexto(binding.etFuncionarioNombre, state.getString("funcionario_nombre"))
            asignarTexto(binding.etFuncionarioCargo, state.getString("funcionario_cargo"))
            asignarTexto(binding.etFuncionarioMatricula, state.getString("funcionario_matricula"))
            asignarTexto(binding.etFuncionarioIdentificacion, state.getString("funcionario_identificacion"))
            asignarTexto(binding.etNotificadoNombre, state.getString("notificado_nombre"))
            asignarTexto(binding.etNotificadoIdentificacion, state.getString("notificado_identificacion"))

            // Módulo de Rescate CBA
            binding.swRequiereRescate.isChecked = state.getBoolean("requiere_rescate")
            asignarAutoComplete(binding.autoCompleteMotivoRescate, state.getString("motivo_rescate"))
            asignarTexto(binding.etPersonaAtiendeRescatista, state.getString("persona_atiende_rescatista"))
            asignarTexto(binding.etPersonaAtiendeCedula, state.getString("persona_atiende_cedula"))
            asignarTexto(binding.etPersonaAtiendeTelefono, state.getString("persona_atiende_telefono"))
            asignarTexto(binding.etBarrioRescate, state.getString("barrio_rescate"))
            binding.chipSinTestigo.isChecked = state.getBoolean("chip_sin_testigo")
            asignarTexto(binding.etTestigo1Nombre, state.getString("testigo1_nombre"))
            asignarTexto(binding.etTestigo1Cedula, state.getString("testigo1_cedula"))
            asignarTexto(binding.etTestigo1Telefono, state.getString("testigo1_telefono"))
            asignarTexto(binding.etObservacionesRescate, state.getString("observaciones_rescate"))
            asignarTexto(binding.etVeterinarioReceptor, state.getString("veterinario_receptor"))
            asignarTexto(binding.etFechaRecepcion, state.getString("fecha_recepcion"))
            asignarTexto(binding.etHoraRecepcion, state.getString("hora_recepcion"))
            asignarTexto(binding.etObservacionesRecepcion, state.getString("observaciones_recepcion"))

            // Switches Clínicos por Sistemas
            binding.swRespiratorioMoco.isChecked = state.getBoolean("sw_respiratorio_moco")
            binding.swRespiratorioDificultad.isChecked = state.getBoolean("sw_respiratorio_dificultad")
            binding.swRespiratorioTos.isChecked = state.getBoolean("sw_respiratorio_tos")
            binding.swRespiratorioLaganeo.isChecked = state.getBoolean("sw_respiratorio_laganeo")
            binding.swDigestivoVomito.isChecked = state.getBoolean("sw_digestivo_vomito")
            binding.swDigestivoDiarrea.isChecked = state.getBoolean("sw_digestivo_diarrea")
            binding.swDigestivoDesnutrido.isChecked = state.getBoolean("sw_digestivo_desnutrido")
            binding.swDigestivoDeshidratado.isChecked = state.getBoolean("sw_digestivo_deshidratado")
            binding.swMusculoCaidaPatas.isChecked = state.getBoolean("sw_musculo_caida_patas")
            binding.swMusculoCojo.isChecked = state.getBoolean("sw_musculo_cojo")
            binding.swMusculoHeridaAbierta.isChecked = state.getBoolean("sw_musculo_herida_abierta")
            binding.swMusculoGusanos.isChecked = state.getBoolean("sw_musculo_gusanos")
            binding.swMusculoPostrado.isChecked = state.getBoolean("sw_musculo_postrado")
            binding.swMusculoSarcoma.isChecked = state.getBoolean("sw_musculo_sarcoma")
            binding.swDermatoPeladuras.isChecked = state.getBoolean("sw_dermato_peladuras")
            binding.swDermatoHeridasSangre.isChecked = state.getBoolean("sw_dermato_heridas_sangre")
            binding.swDermatoPulgas.isChecked = state.getBoolean("sw_dermato_pulgas")
            binding.swDermatoCarcinomaCutaneo.isChecked = state.getBoolean("sw_dermato_carcinoma_cutaneo")
            binding.swGenitoMasaSecrecion.isChecked = state.getBoolean("sw_genito_masa_secrecion")
            binding.swGenitoGestante.isChecked = state.getBoolean("sw_genito_gestante")
            binding.swGenitoCelo.isChecked = state.getBoolean("sw_genito_celo")
            asignarTexto(binding.etComentarioRespiratorio, state.getString("comentario_respiratorio"))
            asignarTexto(binding.etComentarioDigestivo, state.getString("comentario_digestivo"))
            asignarTexto(binding.etComentarioMusculo, state.getString("comentario_musculo"))
            asignarTexto(binding.etComentarioDermato, state.getString("comentario_dermato"))
            asignarTexto(binding.etComentarioGenito, state.getString("comentario_genito"))
        } finally {
            suprimirListeners = false
        }
        aplicarVisibilidad()
    }

    /**
     * Propaga cambios de controles cuyo listener principal vive en ActaAtencionActivity
     * (sin sobrescribir su OnCheckedChangeListener único).
     */
    fun notificarCambioVista(fieldId: String, value: Any?) {
        if (!suprimirListeners) {
            state.setValue(fieldId, value)
        }
    }

    /**
     * Aplica la visibilidad condicional sobre los componentes XML de la actividad.
     */
    fun aplicarVisibilidad() {
        val eval = state.reevaluateVisibility()

        // 1. Quién reporta -> Otro
        val quienReportaOtroVisible = eval.fieldVisibility["quien_reporta_otro"] ?: false
        binding.tilQuienReportaOtro.visibility = if (quienReportaOtroVisible) View.VISIBLE else View.GONE

        // 2. Módulo de rescate CBA
        val rescateVisible = eval.sectionVisibility["sec_rescate_cba"] ?: false
        binding.layoutModuloRescateIntegrado.visibility = if (rescateVisible) View.VISIBLE else View.GONE
        binding.cardBadgeRescateActivo.visibility = if (rescateVisible) View.VISIBLE else View.GONE

        // 3. Testigos del rescate
        val testigoVisible = eval.fieldVisibility["testigo1_nombre"] ?: false
        val visTestigo = if (testigoVisible) View.VISIBLE else View.GONE
        binding.tilTestigo1Nombre.visibility = visTestigo
        binding.tilTestigo1Cedula.visibility = visTestigo
        binding.tilTestigo1Telefono.visibility = visTestigo

        // 4. Plazo de compromisos
        val plazoVisible = eval.fieldVisibility["plazo_dias_cumplimiento"] ?: false
        binding.tilPlazoDias.visibility = if (plazoVisible) View.VISIBLE else View.GONE
    }

    private fun conectarListenersVistas() {
        // Quién reporta
        binding.autoCompleteQuienReporta.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suprimirListeners) {
                    state.setValue("quien_reporta", s?.toString()?.trim() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Compromisos
        binding.etCompromisos.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suprimirListeners) {
                    state.setValue("compromisos", s?.toString()?.trim() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun asignarTexto(view: android.widget.EditText, valor: String) {
        if (valor.isNotBlank() && view.text?.toString() != valor) {
            view.setText(valor)
        }
    }

    private fun asignarAutoComplete(view: com.google.android.material.textfield.MaterialAutoCompleteTextView, valor: String) {
        if (valor.isNotBlank() && view.text?.toString() != valor) {
            view.setText(valor, false)
        }
    }
}
