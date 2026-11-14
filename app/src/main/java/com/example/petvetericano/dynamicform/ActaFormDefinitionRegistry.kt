package com.example.petvetericano.dynamicform

/**
 * Registro centralizado y fuente de verdad de las definiciones de formularios dinámicos en Android.
 * Provee la definición canónica del Acta de Atención en Campo (versión 1) con soporte offline total.
 */
object ActaFormDefinitionRegistry {

    const val FORM_ID_ACTA_ATENCION = "acta_atencion_campo"
    const val FORM_VERSION_ACTA_ATENCION = 1

    /**
     * Retorna la definición canónica completa del Acta de Atención en Campo.
     */
    fun getActaAtencionDefinition(): DynamicFormDefinition {
        return DynamicFormDefinition(
            id = FORM_ID_ACTA_ATENCION,
            version = FORM_VERSION_ACTA_ATENCION,
            title = "Acta de Atención y Valoración en Campo",
            description = "Formulario para registro de visitas veterinarias, valoración clínica, rescates y cierres legales.",
            sections = listOf(
                buildSeccionDatosGenerales(),
                buildSeccionPacientesClinica(),
                buildSeccionPruebasCierre(),
                buildSeccionRescateCba(),
                buildSeccionEvaluacionSistemas(),
                buildSeccionLeyesFundamento()
            )
        )
    }

    private fun buildSeccionDatosGenerales(): FormSectionDefinition {
        return FormSectionDefinition(
            id = "sec_datos_generales",
            title = "Paso 1: Datos Generales y Ubicación",
            step = 1,
            description = "Información administrativa de la visita, solicitante y propietario.",
            fields = listOf(
                FormFieldDefinition(
                    id = "numero_radicado",
                    label = "Número de Radicado",
                    hint = "Ej. VIS-2024-001",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Código de radicado asignado a la petición."
                ),
                FormFieldDefinition(
                    id = "fecha_atencion",
                    label = "Fecha de Atención",
                    hint = "AAAA-MM-DD",
                    type = FieldType.DATE,
                    step = 1,
                    required = true,
                    description = "Fecha de la visita."
                ),
                FormFieldDefinition(
                    id = "solicitud_atencion_por",
                    label = "Solicitud de Atención Por",
                    type = FieldType.SELECT,
                    step = 1,
                    required = true,
                    options = listOf(
                        SelectOption("Queja o Denuncia Ciudadana", "Queja o Denuncia Ciudadana"),
                        SelectOption("Oficio de Autoridad Competente", "Oficio de Autoridad Competente"),
                        SelectOption("Ronda de Inspección Rutinaria", "Ronda de Inspección Rutinaria"),
                        SelectOption("Solicitud Institucional", "Solicitud Institucional"),
                        SelectOption("Emergencia en Vía Pública", "Emergencia en Vía Pública"),
                        SelectOption("Otro", "Otro")
                    ),
                    description = "Canal o motivo de inicio de la visita."
                ),
                FormFieldDefinition(
                    id = "quien_reporta",
                    label = "¿Quién Reporta?",
                    type = FieldType.SELECT,
                    step = 1,
                    required = true,
                    options = listOf(
                        SelectOption("Propietario / Tenedor", "Propietario / Tenedor"),
                        SelectOption("Vecino o Ciudadano Afectado", "Vecino o Ciudadano Afectado"),
                        SelectOption("Policía Ambiental", "Policía Ambiental"),
                        SelectOption("Veeduría Animalista", "Veeduría Animalista"),
                        SelectOption("Comunitaria / JAC", "Comunitaria / JAC"),
                        SelectOption("Otro", "Otro")
                    ),
                    description = "Persona o entidad que dio aviso."
                ),
                FormFieldDefinition(
                    id = "quien_reporta_otro",
                    label = "Especifique Quién Reporta",
                    type = FieldType.TEXT,
                    step = 1,
                    required = false,
                    visibilityRule = ConditionalRule.simple("quien_reporta", ComparisonType.EQUALS, "Otro"),
                    description = "Detalle adicional si quién reporta es 'Otro'."
                ),
                FormFieldDefinition(
                    id = "propietario_nombre",
                    label = "Nombre del Propietario / Tenedor",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Nombre completo del responsable del animal."
                ),
                FormFieldDefinition(
                    id = "propietario_cedula",
                    label = "Cédula / Documento de Identidad",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Número de identificación del propietario."
                ),
                FormFieldDefinition(
                    id = "propietario_telefono",
                    label = "Teléfono de Contacto",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Número telefónico o celular."
                ),
                FormFieldDefinition(
                    id = "propietario_email",
                    label = "Correo Electrónico",
                    type = FieldType.TEXT,
                    step = 1,
                    required = false,
                    description = "Email de contacto."
                ),
                FormFieldDefinition(
                    id = "propietario_barrio",
                    label = "Barrio / Vereda",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Barrio, comuna o vereda."
                ),
                FormFieldDefinition(
                    id = "propietario_direccion",
                    label = "Dirección del Predio",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Dirección exacta del predio."
                ),
                FormFieldDefinition(
                    id = "lugar_atencion",
                    label = "Lugar de Atención",
                    type = FieldType.TEXT,
                    step = 1,
                    required = true,
                    description = "Descripción general del sitio de atención."
                )
            )
        )
    }

    private fun buildSeccionPacientesClinica(): FormSectionDefinition {
        return FormSectionDefinition(
            id = "sec_pacientes_clinica",
            title = "Paso 2: Pacientes y Evaluación Clínica",
            step = 2,
            description = "Identificación del animal, examen clínico y tratamiento.",
            fields = listOf(
                FormFieldDefinition(
                    id = "nombre_paciente",
                    label = "Nombre del Paciente",
                    type = FieldType.TEXT,
                    step = 2,
                    required = true,
                    description = "Nombre del animal atendido."
                ),
                FormFieldDefinition(
                    id = "paciente_especie",
                    label = "Especie",
                    type = FieldType.SELECT,
                    step = 2,
                    required = true,
                    options = listOf(
                        SelectOption("Canino", "Canino"),
                        SelectOption("Felino", "Felino"),
                        SelectOption("Equino", "Equino"),
                        SelectOption("Bovino", "Bovino"),
                        SelectOption("Porcino", "Porcino"),
                        SelectOption("Otro", "Otro")
                    ),
                    description = "Especie del animal."
                ),
                FormFieldDefinition(
                    id = "paciente_raza",
                    label = "Raza",
                    type = FieldType.TEXT,
                    step = 2,
                    required = false,
                    description = "Raza del animal."
                ),
                FormFieldDefinition(
                    id = "paciente_sexo",
                    label = "Sexo",
                    type = FieldType.SELECT,
                    step = 2,
                    required = true,
                    options = listOf(
                        SelectOption("Macho", "Macho"),
                        SelectOption("Hembra", "Hembra"),
                        SelectOption("Indeterminado", "Indeterminado")
                    ),
                    description = "Sexo del paciente."
                ),
                FormFieldDefinition(
                    id = "paciente_color",
                    label = "Color / Pelaje",
                    type = FieldType.TEXT,
                    step = 2,
                    required = false,
                    description = "Color del animal."
                ),
                FormFieldDefinition(
                    id = "paciente_edad",
                    label = "Edad Aproximada",
                    type = FieldType.TEXT,
                    step = 2,
                    required = false,
                    description = "Edad estimada."
                ),
                FormFieldDefinition(
                    id = "peso_paciente",
                    label = "Peso (kg)",
                    type = FieldType.DECIMAL,
                    step = 2,
                    required = false,
                    description = "Peso en kilogramos."
                ),
                FormFieldDefinition(
                    id = "esterilizacion_paciente",
                    label = "Esterilizado",
                    type = FieldType.BOOLEAN,
                    step = 2,
                    required = false,
                    description = "Indica si está esterilizado."
                ),
                FormFieldDefinition(
                    id = "frecuencia_cardiaca",
                    label = "Frecuencia Cardíaca (lpm)",
                    type = FieldType.TEXT,
                    step = 2,
                    required = false,
                    description = "Latidos por minuto."
                ),
                FormFieldDefinition(
                    id = "frecuencia_respiratoria",
                    label = "Frecuencia Respiratoria (rpm)",
                    type = FieldType.TEXT,
                    step = 2,
                    required = false,
                    description = "Respiraciones por minuto."
                ),
                FormFieldDefinition(
                    id = "mucosas",
                    label = "Estado de Mucosas",
                    type = FieldType.SELECT,
                    step = 2,
                    required = false,
                    options = listOf(
                        SelectOption("Rosadas", "Rosadas"),
                        SelectOption("Pálidas", "Pálidas"),
                        SelectOption("Ictéricas", "Ictéricas"),
                        SelectOption("Cianóticas", "Cianóticas"),
                        SelectOption("Congestivas", "Congestivas")
                    ),
                    description = "Coloración de mucosas."
                ),
                FormFieldDefinition(
                    id = "anamnesis_descripcion_queja",
                    label = "Anamnesis / Motivo de Consulta",
                    type = FieldType.TEXT_LONG,
                    step = 2,
                    required = true,
                    description = "Historia clínica y motivo de la atención."
                ),
                FormFieldDefinition(
                    id = "tratamiento_realizado",
                    label = "Tratamiento Realizado",
                    type = FieldType.TEXT_LONG,
                    step = 2,
                    required = true,
                    description = "Tratamiento o procedimiento ejecutado."
                ),
                FormFieldDefinition(
                    id = "desparasitacion",
                    label = "Desparasitación",
                    type = FieldType.BOOLEAN,
                    step = 2,
                    required = false,
                    description = "Indica si se administró desparasitante."
                )
            )
        )
    }

    private fun buildSeccionPruebasCierre(): FormSectionDefinition {
        return FormSectionDefinition(
            id = "sec_pruebas_cierre",
            title = "Paso 3: Pruebas, Compromisos y Cierre",
            step = 3,
            description = "Pruebas paraclínicas, compromisos y resultado de la visita.",
            fields = listOf(
                FormFieldDefinition(
                    id = "tipo_prueba",
                    label = "Tipo de Prueba",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Prueba rápida realizada."
                ),
                FormFieldDefinition(
                    id = "pruebas_complementarias",
                    label = "Pruebas Complementarias",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Exámenes complementarios."
                ),
                FormFieldDefinition(
                    id = "resultado_pruebas",
                    label = "Resultado de Pruebas",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Resultado del test o examen."
                ),
                FormFieldDefinition(
                    id = "compromisos",
                    label = "Compromisos Adquiridos",
                    type = FieldType.TEXT_LONG,
                    step = 3,
                    required = false,
                    description = "Obligaciones fijadas al propietario."
                ),
                FormFieldDefinition(
                    id = "plazo_dias_cumplimiento",
                    label = "Plazo en Días",
                    type = FieldType.INTEGER,
                    step = 3,
                    required = false,
                    visibilityRule = ConditionalRule.simple("compromisos", ComparisonType.IS_NOT_EMPTY),
                    description = "Días de plazo para cumplir los compromisos."
                ),
                FormFieldDefinition(
                    id = "fundamento_legal",
                    label = "Fundamento Legal",
                    type = FieldType.TEXT,
                    step = 3,
                    defaultValue = "Ley 1774 de 2016",
                    description = "Leyes y normas aplicadas."
                ),
                FormFieldDefinition(
                    id = "estado_cierre",
                    label = "Estado de Cierre",
                    type = FieldType.SELECT,
                    step = 3,
                    defaultValue = "En tratamiento",
                    options = listOf(
                        SelectOption("En tratamiento", "En tratamiento"),
                        SelectOption("Caso Cerrado - Favorable", "Caso Cerrado - Favorable"),
                        SelectOption("Remitido a CBA Popayán", "Remitido a CBA Popayán"),
                        SelectOption("Traslado por Protección Animal", "Traslado por Protección Animal"),
                        SelectOption("En Seguimiento Posterior", "En Seguimiento Posterior")
                    ),
                    description = "Estado final del acta."
                ),
                FormFieldDefinition(
                    id = "observacion",
                    label = "Observaciones Finales",
                    type = FieldType.TEXT_LONG,
                    step = 3,
                    required = false,
                    description = "Observaciones generales de cierre."
                ),
                FormFieldDefinition(
                    id = "funcionario_nombre",
                    label = "Nombre del Funcionario / Veterinario",
                    type = FieldType.TEXT,
                    step = 3,
                    required = true,
                    description = "Nombre del profesional actuante."
                ),
                FormFieldDefinition(
                    id = "funcionario_cargo",
                    label = "Cargo del Funcionario",
                    type = FieldType.TEXT,
                    step = 3,
                    required = true,
                    description = "Cargo institucional."
                ),
                FormFieldDefinition(
                    id = "funcionario_matricula",
                    label = "Matrícula Profesional (M.P.)",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Número de matrícula COMVEZCOL."
                ),
                FormFieldDefinition(
                    id = "funcionario_identificacion",
                    label = "Identificación del Funcionario",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Cédula del veterinario."
                ),
                FormFieldDefinition(
                    id = "notificado_nombre",
                    label = "Nombre del Notificado",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Persona que firma la notificación del acta."
                ),
                FormFieldDefinition(
                    id = "notificado_identificacion",
                    label = "Cédula del Notificado",
                    type = FieldType.TEXT,
                    step = 3,
                    required = false,
                    description = "Cédula de quien se notifica."
                )
            )
        )
    }

    private fun buildSeccionRescateCba(): FormSectionDefinition {
        return FormSectionDefinition(
            id = "sec_rescate_cba",
            title = "Módulo de Rescate CBA (Centro de Bienestar Animal)",
            step = 3,
            description = "Formato Popayán F-GS-C01-03-33 para rescates.",
            visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
            fields = listOf(
                FormFieldDefinition(
                    id = "requiere_rescate",
                    label = "¿Requiere Rescate y Traslado al CBA?",
                    type = FieldType.SWITCH,
                    step = 3,
                    defaultValue = false,
                    description = "Habilita el módulo de rescate al CBA."
                ),
                FormFieldDefinition(
                    id = "motivo_rescate",
                    label = "Motivo del Rescate",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Causa del rescate."
                ),
                FormFieldDefinition(
                    id = "persona_atiende_rescatista",
                    label = "Rescatista / Quien Entrega",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Nombre de quien realiza o apoya el rescate."
                ),
                FormFieldDefinition(
                    id = "persona_atiende_cedula",
                    label = "Cédula del Rescatista",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Cédula de la persona que entrega."
                ),
                FormFieldDefinition(
                    id = "persona_atiende_telefono",
                    label = "Teléfono del Rescatista",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Teléfono de contacto del rescatista."
                ),
                FormFieldDefinition(
                    id = "barrio_rescate",
                    label = "Barrio / Sector del Rescate",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Sector donde se produjo el rescate."
                ),
                FormFieldDefinition(
                    id = "chip_sin_testigo",
                    label = "Sin Testigos Presentes",
                    type = FieldType.CHECKBOX,
                    step = 3,
                    defaultValue = false,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE),
                    description = "Marca si no hubo testigos en el rescate."
                ),
                FormFieldDefinition(
                    id = "testigo1_nombre",
                    label = "Nombre del Testigo 1",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule(
                        operator = LogicalOperator.AND,
                        conditions = listOf(
                            FieldCondition("requiere_rescate", ComparisonType.IS_TRUE),
                            FieldCondition("chip_sin_testigo", ComparisonType.IS_FALSE)
                        )
                    ),
                    description = "Testigo ciudadano del rescate."
                ),
                FormFieldDefinition(
                    id = "testigo1_cedula",
                    label = "Cédula del Testigo 1",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule(
                        operator = LogicalOperator.AND,
                        conditions = listOf(
                            FieldCondition("requiere_rescate", ComparisonType.IS_TRUE),
                            FieldCondition("chip_sin_testigo", ComparisonType.IS_FALSE)
                        )
                    )
                ),
                FormFieldDefinition(
                    id = "testigo1_telefono",
                    label = "Teléfono del Testigo 1",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule(
                        operator = LogicalOperator.AND,
                        conditions = listOf(
                            FieldCondition("requiere_rescate", ComparisonType.IS_TRUE),
                            FieldCondition("chip_sin_testigo", ComparisonType.IS_FALSE)
                        )
                    )
                ),
                FormFieldDefinition(
                    id = "observaciones_rescate",
                    label = "Observaciones del Rescate",
                    type = FieldType.TEXT_LONG,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE)
                ),
                FormFieldDefinition(
                    id = "veterinario_receptor",
                    label = "Veterinario Receptor en CBA",
                    type = FieldType.TEXT,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE)
                ),
                FormFieldDefinition(
                    id = "fecha_recepcion",
                    label = "Fecha de Recepción",
                    type = FieldType.DATE,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE)
                ),
                FormFieldDefinition(
                    id = "hora_recepcion",
                    label = "Hora de Recepción",
                    type = FieldType.TIME,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE)
                ),
                FormFieldDefinition(
                    id = "observaciones_recepcion",
                    label = "Observaciones de Recepción en CBA",
                    type = FieldType.TEXT_LONG,
                    step = 3,
                    visibilityRule = ConditionalRule.simple("requiere_rescate", ComparisonType.IS_TRUE)
                )
            )
        )
    }

    private fun buildSeccionEvaluacionSistemas(): FormSectionDefinition {
        val switchesInfo = listOf(
            Triple("sw_respiratorio_moco", "Secreción / Moco Nasal", "Respiratorio"),
            Triple("sw_respiratorio_dificultad", "Dificultad Respiratoria", "Respiratorio"),
            Triple("sw_respiratorio_tos", "Tos Frecuente", "Respiratorio"),
            Triple("sw_respiratorio_laganeo", "Secreción Ocular / Laganeo", "Respiratorio"),
            Triple("sw_digestivo_vomito", "Vómito", "Digestivo"),
            Triple("sw_digestivo_diarrea", "Diarrea", "Digestivo"),
            Triple("sw_digestivo_desnutrido", "Desnutrición / Caquexia", "Digestivo"),
            Triple("sw_digestivo_deshidratado", "Deshidratación", "Digestivo"),
            Triple("sw_musculo_caida_patas", "Debilidad / Caída de Patas", "Músculo-esquelético"),
            Triple("sw_musculo_cojo", "Claudicación / Cojo", "Músculo-esquelético"),
            Triple("sw_musculo_herida_abierta", "Herida Abierta", "Músculo-esquelético"),
            Triple("sw_musculo_gusanos", "Miasis / Gusanos", "Músculo-esquelético"),
            Triple("sw_musculo_postrado", "Postrado / Postración", "Músculo-esquelético"),
            Triple("sw_musculo_sarcoma", "Masa / Sarcoma", "Músculo-esquelético"),
            Triple("sw_dermato_peladuras", "Peladuras / Alopecias", "Dermatológico"),
            Triple("sw_dermato_heridas_sangre", "Heridas Sangrantes", "Dermatológico"),
            Triple("sw_dermato_pulgas", "Hectoparásitos", "Dermatológico"),
            Triple("sw_dermato_carcinoma_cutaneo", "Carcinoma Cutáneo", "Dermatológico"),
            Triple("sw_genito_masa_secrecion", "Masa / Secreción Genital", "Genitourinario"),
            Triple("sw_genito_gestante", "Gestante", "Genitourinario"),
            Triple("sw_genito_celo", "En Celo", "Genitourinario")
        )

        val commentFieldsInfo = listOf(
            Triple("comentario_respiratorio", "Comentario Sistema Respiratorio", "Respiratorio"),
            Triple("comentario_digestivo", "Comentario Sistema Digestivo", "Digestivo"),
            Triple("comentario_musculo", "Comentario Sistema Músculo-Esquelético", "Músculo-esquelético"),
            Triple("comentario_dermato", "Comentario Sistema Dermatológico", "Dermatológico"),
            Triple("comentario_genito", "Comentario Sistema Genitourinario", "Genitourinario")
        )

        val fields = switchesInfo.map { (id, label, desc) ->
            FormFieldDefinition(
                id = id,
                label = label,
                type = FieldType.SWITCH,
                step = 3,
                defaultValue = false,
                description = desc
            )
        } + commentFieldsInfo.map { (id, label, desc) ->
            FormFieldDefinition(
                id = id,
                label = label,
                type = FieldType.TEXT_LONG,
                step = 3,
                required = false,
                description = "Comentario adicional para sistema $desc"
            )
        }

        return FormSectionDefinition(
            id = "sec_evaluacion_sistemas",
            title = "Evaluación Clínica por Sistemas",
            step = 3,
            description = "Hallazgos patológicos en órganos y sistemas.",
            fields = fields
        )
    }

    private fun buildSeccionLeyesFundamento(): FormSectionDefinition {
        val leyes = listOf(
            Pair("sw_ley_84_1989", "Ley 84 de 1989 (Estatuto Nacional de Protección Animal)"),
            Pair("sw_ley_1638_2013", "Ley 1638 de 2013 (Prohibición de Animales Silvestres en Circos)"),
            Pair("sw_ley_1774_2016", "Ley 1774 de 2016 (Seres Sintientes y Tipificación Penal del Maltrato)"),
            Pair("sw_ley_1801_2016", "Ley 1801 de 2016 (Código Nacional de Seguridad y Convivencia)"),
            Pair("sw_ley_2054_2020", "Ley 2054 de 2020 (Atenuación de Maltrato y Centros de Bienestar)"),
            Pair("sw_ley_2385_2024", "Ley 2385 de 2024 (Prohibición Progresiva de Corridas de Toros)")
        )

        val fields = leyes.map { (id, label) ->
            FormFieldDefinition(
                id = id,
                label = label,
                type = FieldType.SWITCH,
                step = 3,
                defaultValue = (id == "sw_ley_1774_2016"),
                description = "Marco jurídico colombiano de protección animal"
            )
        }

        return FormSectionDefinition(
            id = "sec_fundamento_legal_leyes",
            title = "Fundamento Legal (Normativa Aplicable)",
            step = 3,
            description = "Leyes y disposiciones legales colombianas vinculadas.",
            fields = fields
        )
    }
}

