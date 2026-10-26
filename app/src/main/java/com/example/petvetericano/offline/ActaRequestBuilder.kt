package com.example.petvetericano.offline

import com.example.petvetericano.models.AnimalCardState
import com.example.petvetericano.models.AnimalRapidoRequest
import com.example.petvetericano.models.ExamenSeleccionadoActa
import com.example.petvetericano.models.FuncionarioActaRequest
import com.example.petvetericano.models.LugarAtencionActa
import com.example.petvetericano.models.MedicamentoAplicadoActa
import com.example.petvetericano.models.SeguimientoPeticionVisitaRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Reconstruye el objeto SeguimientoPeticionVisitaRequest a partir de los datos almacenados
 * en ActaLocalEntity con total paridad respecto a submitActa() de ActaAtencionActivity.
 */
object ActaRequestBuilder {

    private fun siNo(valor: Boolean): String = if (valor) "SÍ" else "NO"

    /**
     * Construye el request listo para el backend de creación de seguimiento de visita.
     */
    fun build(
        entity: ActaLocalEntity,
        fotosUrls: List<String>,
        videosUrls: List<String>,
        sincronizacionOffline: Boolean
    ): SeguimientoPeticionVisitaRequest {
        val gson = Gson()

        // 1. Deserializar tarjetas de animales y construir listaAnimales
        val typeAnimales = object : TypeToken<List<AnimalCardState>>() {}.type
        val animalCardList: List<AnimalCardState> = try {
            if (!entity.animalesJson.isNullOrEmpty()) {
                gson.fromJson(entity.animalesJson, typeAnimales) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        val listaAnimales = mutableListOf<AnimalRapidoRequest>()
        for (card in animalCardList) {
            val nombre = card.nombre.trim().ifEmpty { "Sin nombre" }
            val especieRaw = card.especie.trim()
            val especie = if (especieRaw.equals("Seleccionar", ignoreCase = true)) "" else especieRaw
            val razaRaw = card.raza.trim()
            val raza = if (razaRaw.equals("Seleccionar", ignoreCase = true)) "" else razaRaw.ifEmpty { "Mestizo" }
            val sexoRaw = card.sexo.trim()
            val sexo = if (sexoRaw.equals("Seleccionar", ignoreCase = true)) null else sexoRaw.ifEmpty { null }
            val colorRaw = card.color.trim()
            val color = if (colorRaw.equals("Seleccionar", ignoreCase = true)) "" else colorRaw

            val edad = card.edadTexto.trim()
            val peso = card.peso.trim().toDoubleOrNull()
            val descExtra = card.descripcion.trim()
            val esterilizado = card.esterilizado.trim().equals("Si", ignoreCase = true)
            val notasLimpias = descExtra.ifEmpty { null }

            listaAnimales.add(
                AnimalRapidoRequest(
                    nombre = nombre,
                    especie = especie.ifEmpty { null },
                    nombreEspecie = especie.ifEmpty { null },
                    raza = raza.ifEmpty { null },
                    nombreRaza = raza.ifEmpty { null },
                    idRaza = card.idRaza,
                    sexo = sexo,
                    color = color.ifEmpty { null },
                    peso = peso,
                    edad = edad.ifEmpty { null },
                    esterilizado = esterilizado,
                    caracteristicas = notasLimpias
                )
            )
        }

        // Datos del paciente principal (Modo A en Django)
        val primerAnimal = animalCardList.firstOrNull()
        val nombrePaciente = primerAnimal?.nombre?.trim()?.ifEmpty { null }
        val especiePrimerRaw = primerAnimal?.especie?.trim()
        val pacienteEspecie = if (especiePrimerRaw.isNullOrEmpty() || especiePrimerRaw.equals("Seleccionar", ignoreCase = true)) null else especiePrimerRaw
        val sexoPrimerRaw = primerAnimal?.sexo?.trim()
        val pacienteSexo = if (sexoPrimerRaw.isNullOrEmpty() || sexoPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else sexoPrimerRaw
        val colorPrimerRaw = primerAnimal?.color?.trim()
        val pacienteColor = if (colorPrimerRaw.isNullOrEmpty() || colorPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else colorPrimerRaw
        val razaPrimerRaw = primerAnimal?.raza?.trim()
        val pacienteRaza = if (razaPrimerRaw.isNullOrEmpty() || razaPrimerRaw.equals("Seleccionar", ignoreCase = true)) null else razaPrimerRaw
        val pacienteEdad = primerAnimal?.edadTexto?.trim()?.ifEmpty { null }
        val pesoPaciente = primerAnimal?.peso?.trim()?.toDoubleOrNull()
        val esterilizadoStr = primerAnimal?.esterilizado?.trim()
        val esterilizacionPaciente = when {
            esterilizadoStr.equals("Si", ignoreCase = true) -> true
            esterilizadoStr.equals("No", ignoreCase = true) -> false
            else -> null
        }
        val descripcionPaciente = primerAnimal?.descripcion?.trim()?.ifEmpty { null }

        // Lugar de atención
        val lugarAtencion = LugarAtencionActa(
            direccion = entity.lugarAtencion.trim(),
            latitud = if (entity.latitudVisita != 0.0) entity.latitudVisita else 2.4419,
            longitud = if (entity.longitudVisita != 0.0) entity.longitudVisita else -76.6063
        )

        // Funcionario y notificador
        val funcionarioNombre = entity.funcionarioNombre.trim().ifEmpty { "Médico Veterinario" }
        val funcionarioCargoBase = entity.funcionarioCargo.trim().ifEmpty { "Médico Veterinario Zootecnista" }
        val funcionarioMatricula = entity.funcionarioMatricula?.trim()?.ifEmpty { null }
        val notificadorIdentificacion = entity.funcionarioIdentificacion?.trim()?.ifEmpty { null }

        val funcionarioCargo = if (!funcionarioMatricula.isNullOrEmpty() && !funcionarioCargoBase.contains("M.P.", ignoreCase = true)) {
            "$funcionarioCargoBase (M.P. $funcionarioMatricula)"
        } else {
            funcionarioCargoBase
        }

        val listaFuncionarios = listOf(
            FuncionarioActaRequest(
                idUsuario = entity.idVeterinario,
                nombre = funcionarioNombre,
                cargo = funcionarioCargo,
                documentoIdentidad = notificadorIdentificacion,
                matriculaProfesional = funcionarioMatricula,
                tarjetaProfesional = funcionarioMatricula,
                esExterno = false,
                esPrincipal = true
            )
        )

        val quienReporta = entity.quienReporta.trim()
        val textoQuienOtro = entity.quienReportaOtro?.trim().orEmpty()
        val quienReportaOtro = when {
            quienReporta.equals("Otro", ignoreCase = true) -> textoQuienOtro.ifEmpty { "Otro" }
            textoQuienOtro.isNotEmpty() -> textoQuienOtro
            else -> null
        }

        val emailPropietario = entity.propietarioEmail?.trim()?.ifEmpty { null }
        val plazoDias = entity.plazoDias
        val observacion = entity.observacion?.trim()?.ifEmpty { null }

        val fechaAtencion = entity.fechaAtencion.trim()
        val radicado = entity.nroRadicadoAtencion.trim()

        val firmaNotificador = entity.firmaNotificadorBase64
        val firmaNotificado = entity.firmaNotificadoBase64
        val desparasitacion = entity.desparasitacion

        // Constantes vitales del Paso 2
        val fc = entity.frecuenciaCardiaca.trim()
        val fr = entity.frecuenciaRespiratoria.trim()
        val mucosas = entity.mucosas.trim()

        val signosVitales = buildString {
            if (fc.isNotEmpty()) append("FC: ${fc} lpm. ")
            if (fr.isNotEmpty()) append("FR: ${fr} rpm. ")
            if (mucosas.isNotEmpty()) append("Mucosas: $mucosas. ")
        }.trim()

        // Pruebas complementarias y consolidación de exámenes del Paso 3
        val typeExamen = object : TypeToken<List<ExamenSeleccionadoActa>>() {}.type
        val examenesDeserializados: List<ExamenSeleccionadoActa> = try {
            if (!entity.examenesJson.isNullOrEmpty()) {
                gson.fromJson(entity.examenesJson, typeExamen) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        val listaFinalExamenes = examenesDeserializados.toMutableList()
        val tipoPrueba = entity.tipoPrueba.trim()
        val pruebaDetalle = entity.pruebasComplementarias.trim()
        val resultadoPruebasCampos = entity.resultadoPruebas?.trim()?.ifEmpty { null }

        val examenEnCampos = when {
            pruebaDetalle.isNotEmpty() -> pruebaDetalle
            tipoPrueba.isNotEmpty() && !tipoPrueba.contains("Ninguna", ignoreCase = true) -> tipoPrueba
            else -> null
        }

        if (examenEnCampos != null && listaFinalExamenes.none { it.nombreExamen.equals(examenEnCampos, ignoreCase = true) }) {
            listaFinalExamenes.add(
                ExamenSeleccionadoActa(
                    nombreExamen = examenEnCampos,
                    resultado = resultadoPruebasCampos ?: "Pendiente"
                )
            )
        }

        val pruebaFinal = when {
            listaFinalExamenes.isNotEmpty() -> listaFinalExamenes.joinToString(" | ") { it.nombreExamen }
            else -> null
        }
        val resultadoPruebas = when {
            listaFinalExamenes.isNotEmpty() -> listaFinalExamenes.joinToString(" | ") { "${it.nombreExamen}: ${it.resultado}" }
            else -> null
        }

        val resumenExamenes = if (listaFinalExamenes.isNotEmpty()) {
            "[Exámenes clínicos (${listaFinalExamenes.size}): " + listaFinalExamenes.joinToString("; ") { "${it.nombreExamen} (${it.resultado})" } + "]"
        } else ""

        val anamnesisBase = entity.anamnesis.trim()
        val anamnesisConSignos = buildString {
            append(anamnesisBase)
            if (signosVitales.isNotEmpty()) {
                append(" [Examen clínico: $signosVitales]")
            }
            if (resumenExamenes.isNotEmpty()) {
                append(" $resumenExamenes")
            }
        }.trim()

        // [MÓDULO DE RESCATE Y REubicación ANIMAL ACTIVO]
        val anamnesisFinal = if (entity.requiereRescate) {
            val causalRescate = entity.motivoRescate.trim()
            val personaAtiende = entity.personaAtiendeRescatista.trim()
            val personaAtiendeCedula = entity.personaAtiendeCedula.trim()
            val personaAtiendeTel = entity.personaAtiendeTelefono.trim()
            val barrioRescate = entity.barrioRescate.trim()
            val testigo1Nombre = entity.testigo1Nombre.trim()
            val testigo1Cedula = entity.testigo1Cedula.trim()
            val testigo1Tel = entity.testigo1Telefono.trim()
            val testigo2Nombre = entity.testigo2Nombre.trim()
            val testigo2Cedula = entity.testigo2Cedula.trim()
            val testigo2Tel = entity.testigo2Telefono.trim()
            val obsRescate = entity.observacionesRescate.trim()
            val vetReceptor = entity.veterinarioReceptor.trim()
            val fechaRecep = entity.fechaRecepcion.trim()
            val horaRecep = entity.horaRecepcion.trim()
            val obsRecep = entity.observacionesRecepcion.trim()

            val anexoRescate = buildString {
                appendLine()
                appendLine()
                appendLine("=======================================================")
                appendLine("ANEXO: ACTA DE RESCATE Y TRASLADO AL CBA")
                appendLine("(Formato Oficial Alcaldía de Popayán F-GS-C01-03-33, Versión 02)")
                appendLine("=======================================================")
                if (causalRescate.isNotEmpty()) appendLine("• Causal de rescate: $causalRescate")
                appendLine("• Persona que atiende / entrega: $personaAtiende ${if (personaAtiendeCedula.isNotEmpty()) "- CC: $personaAtiendeCedula" else ""} ${if (personaAtiendeTel.isNotEmpty()) "- Tel: $personaAtiendeTel" else ""}")
                appendLine("• Barrio de rescate: $barrioRescate")
                appendLine("• Testigo 1 (Certifica traslado): $testigo1Nombre - CC: $testigo1Cedula ${if (testigo1Tel.isNotEmpty()) "- Tel: $testigo1Tel" else ""}")
                if (testigo2Nombre.isNotEmpty()) {
                    appendLine("• Testigo 2: $testigo2Nombre ${if (testigo2Cedula.isNotEmpty()) "- CC: $testigo2Cedula" else ""} ${if (testigo2Tel.isNotEmpty()) "- Tel: $testigo2Tel" else ""}")
                }
                appendLine()
                appendLine("--- EVALUACIÓN CLÍNICA POR SISTEMAS (SÍ / NO) ---")
                appendLine("1. Sistema Respiratorio:")
                appendLine("   - Presenta moco: ${siNo(entity.swRespiratorioMoco)}")
                appendLine("   - Se le dificulta respirar: ${siNo(entity.swRespiratorioDificultad)}")
                appendLine("   - Tos: ${siNo(entity.swRespiratorioTos)}")
                appendLine("   - Lagaña o lagrimeo: ${siNo(entity.swRespiratorioLaganeo)}")
                appendLine("2. Sistema Digestivo:")
                appendLine("   - Presenta vómito: ${siNo(entity.swDigestivoVomito)}")
                appendLine("   - Presenta diarrea: ${siNo(entity.swDigestivoDiarrea)}")
                appendLine("   - Flaco o desnutrido: ${siNo(entity.swDigestivoDesnutrido)}")
                appendLine("   - Deshidratado: ${siNo(entity.swDigestivoDeshidratado)}")
                appendLine("3. Sistema Músculo Esquelético:")
                appendLine("   - Caída de las patas de atrás: ${siNo(entity.swMusculoCaidaPatas)}")
                appendLine("   - Cojo: ${siNo(entity.swMusculoCojo)}")
                appendLine("   - Presenta herida abierta: ${siNo(entity.swMusculoHeridaAbierta)}")
                appendLine("   - Observa moscos o gusanos: ${siNo(entity.swMusculoGusanos)}")
                appendLine("   - Postrado: ${siNo(entity.swMusculoPostrado)}")
                appendLine("4. Sistema Dermatológico:")
                appendLine("   - Presenta peladuras: ${siNo(entity.swDermatoPeladuras)}")
                appendLine("   - Heridas con sangre: ${siNo(entity.swDermatoHeridasSangre)}")
                appendLine("   - Pulgas o garrapatas: ${siNo(entity.swDermatoPulgas)}")
                appendLine("5. Sistema Genitourinario:")
                appendLine("   - Presenta masa o secreción: ${siNo(entity.swGenitoMasaSecrecion)}")
                appendLine("   - Se encuentra gestante: ${siNo(entity.swGenitoGestante)}")
                appendLine("   - Se encuentra en celo: ${siNo(entity.swGenitoCelo)}")
                appendLine()
                appendLine("--- OBSERVACIONES DEL RESCATE ---")
                appendLine(obsRescate)
                if (vetReceptor.isNotEmpty() || fechaRecep.isNotEmpty() || obsRecep.isNotEmpty()) {
                    appendLine()
                    appendLine("--- RECEPCIÓN EN CLÍNICA / CBA ---")
                    if (vetReceptor.isNotEmpty()) appendLine("• Veterinario receptor: $vetReceptor")
                    if (fechaRecep.isNotEmpty() || horaRecep.isNotEmpty()) appendLine("• Fecha/Hora de recepción: $fechaRecep $horaRecep".trim())
                    if (obsRecep.isNotEmpty()) appendLine("• Observaciones recepción: $obsRecep")
                }
            }.trim()

            "$anamnesisConSignos\n$anexoRescate"
        } else {
            anamnesisConSignos
        }

        val compromisosBase = entity.compromisos.trim()
        val compromisosFinal = if (entity.requiereRescate) {
            if (compromisosBase.isNotEmpty()) {
                "$compromisosBase | Traslado y custodia del paciente al Centro de Bienestar Animal (CBA)."
            } else {
                "Traslado y custodia del paciente al Centro de Bienestar Animal (CBA) bajo formato oficial Popayán F-GS-C01-03-33."
            }
        } else {
            compromisosBase
        }

        val fundamentoLegalBase = entity.fundamentoLegal.trim().ifEmpty { "Ley 1774 de 2016" }
        val fundamentoLegalFinal = if (entity.requiereRescate) {
            "$fundamentoLegalBase / Formato de Rescate F-GS-C01-03-33"
        } else {
            fundamentoLegalBase
        }

        val notificadoNombre = entity.notificadoNombre?.trim()?.ifEmpty { null } ?: run {
            if (entity.requiereRescate) {
                entity.testigo1Nombre.trim().ifEmpty { null }
            } else {
                entity.propietarioNombre.trim().ifEmpty { null }
            }
        }
        val notificadoId = entity.notificadoIdentificacion?.trim()?.ifEmpty { null } ?: run {
            if (entity.requiereRescate) {
                entity.testigo1Cedula.trim().ifEmpty { null }
            } else {
                entity.propietarioCedula.trim().ifEmpty { null }
            }
        }

        // Medicamentos usados
        val typeMed = object : TypeToken<List<MedicamentoAplicadoActa>>() {}.type
        val medicamentosDeserializados: List<MedicamentoAplicadoActa> = try {
            if (!entity.medicamentosJson.isNullOrEmpty()) {
                gson.fromJson(entity.medicamentosJson, typeMed) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        val medicamentosUsados = medicamentosDeserializados.map { it.toMedicamentoUsadoRequest() }.ifEmpty { null }

        return SeguimientoPeticionVisitaRequest(
            idPeticion = entity.idPeticion,
            idVeterinario = entity.idVeterinario,
            numeroRadicado = radicado,
            fechaAtencion = fechaAtencion,
            propietarioNombre = entity.propietarioNombre.trim(),
            propietarioCedula = entity.propietarioCedula.trim(),
            propietarioTelefono = entity.propietarioTelefono.trim(),
            propietarioEmail = emailPropietario,
            propietarioBarrio = entity.propietarioBarrio.trim(),
            propietarioDireccion = entity.propietarioDireccion.trim(),
            quienReporta = quienReporta,
            quienReportaOtro = quienReportaOtro,
            solicitudAtencionPor = entity.solicitudAtencionPor.trim(),
            lugarAtencion = lugarAtencion,
            nombrePaciente = nombrePaciente,
            pacienteEspecie = pacienteEspecie,
            pacienteSexo = pacienteSexo,
            pacienteColor = pacienteColor,
            pacienteRaza = pacienteRaza,
            pacienteEdad = pacienteEdad,
            pesoPaciente = pesoPaciente,
            esterilizacionPaciente = esterilizacionPaciente,
            descripcionPaciente = descripcionPaciente,
            animales = listaAnimales,
            nroAnimalesAtendidos = listaAnimales.size,
            anamnesisDescripcionQueja = anamnesisFinal,
            tratamientoRealizado = entity.tratamientoRealizado.trim(),
            desparasitacion = desparasitacion,
            pruebasComplementarias = pruebaFinal,
            resultadoPruebas = resultadoPruebas,
            compromisos = compromisosFinal,
            fundamentoLegal = fundamentoLegalFinal,
            plazoDiasCumplimiento = plazoDias,
            nombreFuncionario = funcionarioNombre,
            funcionarioCargo = funcionarioCargo,
            funcionarios = listaFuncionarios,
            notificadoNombre = notificadoNombre,
            notificacionesIdentificacion = notificadoId,
            fechaNotificacion = if (notificadoNombre != null) fechaAtencion else null,
            notificadorNombre = funcionarioNombre,
            notificadorIdentificacion = notificadorIdentificacion,
            notificadorCargo = funcionarioCargo,
            notificadorMatricula = funcionarioMatricula,
            matriculaProfesional = funcionarioMatricula,
            tarjetaProfesional = funcionarioMatricula,
            firmaNotificador = firmaNotificador,
            firmaNotificado = firmaNotificado,
            observacion = observacion,
            fotos = fotosUrls,
            videos = videosUrls,
            medicamentosUsados = medicamentosUsados,
            sincronizacionOffline = sincronizacionOffline
        )
    }
}
