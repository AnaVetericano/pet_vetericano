package com.example.petvetericano.offline

import com.example.petvetericano.*
import com.example.petvetericano.databinding.ActivityActaAtencionBinding
import com.example.petvetericano.models.AnimalCardState
import com.example.petvetericano.models.ExamenSeleccionadoActa
import com.example.petvetericano.models.MedicamentoAplicadoActa
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Mapeador bidireccional entre la interfaz visual (ActivityActaAtencionBinding) y ActaLocalEntity.
 *
 * Responsabilidades:
 * - [capturar]: extrae los valores actuales de los campos simples (EditText, AutoCompleteTextView,
 *   Switch, firmas con toBase64, paso y coordenadas) y devuelve un [ActaLocalEntity] actualizado,
 *   preservando el estado y metadatos de sincronización de la entidad base.
 * - [restaurar]: inyecta en las vistas los valores persistidos en [ActaLocalEntity], usando
 *   setText(texto, false) en AutoCompleteTextView para no activar filtrado y restoreFromBase64 en firmas.
 * - Métodos auxiliares de serialización/deserialización JSON para listas dinámicas (animales,
 *   medicamentos, exámenes, evidencias).
 */
object ActaFormSnapshotMapper {

    /**
     * Captura el estado de todos los campos simples del formulario en una nueva instancia de [ActaLocalEntity].
     *
     * @param binding Binding principal de [ActaAtencionActivity] que expone todos los componentes visuales.
     * @param baseEntity Entidad base de la cual se preservan metadatos (idLocal, estado, idSeguimientoRemoto, etc.).
     * @param pasoActual Paso actual del asistente (1, 2 o 3).
     * @param latitudVisita Latitud GPS registrada para la visita.
     * @param longitudVisita Longitud GPS registrada para la visita.
     * @param animalesJson JSON de tarjetas de pacientes (manejado dinámicamente en Tarea 3c).
     * @param medicamentosJson JSON de medicamentos aplicados (manejado dinámicamente en Tarea 3c).
     * @param examenesJson JSON de exámenes complementarios (manejado dinámicamente en Tarea 3c).
     * @param evidenciasJson JSON de evidencias multimedia (manejado dinámicamente en Tarea 3c).
     * @param incluirFirmas Si es true, serializa las firmas digitales a Base64 desde [SignatureView.toBase64].
     *                      Si es false, conserva [baseEntity.firmaNotificadorBase64] y [baseEntity.firmaNotificadoBase64]
     *                      sin llamar a [toBase64] (usar false en guardados con debounce y true en onPause, cambio de paso y envío).
     * @return [ActaLocalEntity] actualizada con los valores leídos de la interfaz.
     */
    fun capturar(
        binding: ActivityActaAtencionBinding,
        baseEntity: ActaLocalEntity,
        pasoActual: Int = baseEntity.pasoActual,
        latitudVisita: Double = baseEntity.latitudVisita,
        longitudVisita: Double = baseEntity.longitudVisita,
        animalesJson: String? = baseEntity.animalesJson,
        medicamentosJson: String? = baseEntity.medicamentosJson,
        examenesJson: String? = baseEntity.examenesJson,
        evidenciasJson: String? = baseEntity.evidenciasJson,
        incluirFirmas: Boolean = true
    ): ActaLocalEntity {
        val firmaNotificador = if (incluirFirmas) {
            binding.signatureNotificador.toBase64()
        } else {
            baseEntity.firmaNotificadorBase64
        }

        val firmaNotificado = if (incluirFirmas) {
            binding.signatureNotificado.toBase64()
        } else {
            baseEntity.firmaNotificadoBase64
        }

        return baseEntity.copy(
            pasoActual = pasoActual,
            actualizadoEn = System.currentTimeMillis(),
            latitudVisita = latitudVisita,
            longitudVisita = longitudVisita,

            // Paso 1: Datos Generales
            nroRadicadoAtencion = binding.etRadicado.text?.toString()?.trim() ?: "",
            fechaAtencion = binding.etFechaAtencion.text?.toString()?.trim() ?: "",
            propietarioNombre = binding.etPropietarioNombre.text?.toString()?.trim() ?: "",
            propietarioCedula = binding.etPropietarioCedula.text?.toString()?.trim() ?: "",
            propietarioTelefono = binding.etPropietarioTelefono.text?.toString()?.trim() ?: "",
            propietarioEmail = binding.etPropietarioEmail.text?.toString()?.trim()?.ifEmpty { null },
            propietarioBarrio = binding.etPropietarioBarrio.text?.toString()?.trim() ?: "",
            propietarioDireccion = binding.etPropietarioDireccion.text?.toString()?.trim() ?: "",
            quienReporta = binding.autoCompleteQuienReporta.text?.toString()?.trim() ?: "",
            quienReportaOtro = binding.etQuienReportaOtro.text?.toString()?.trim()?.ifEmpty { null },
            solicitudAtencionPor = binding.etSolicitudAtencion.text?.toString()?.trim() ?: "",
            lugarAtencion = binding.etLugarAtencion.text?.toString()?.trim() ?: "",

            // Paso 2: Pacientes y Clínica
            animalesJson = animalesJson,
            desparasitacion = binding.swDesparasitacion.isChecked,
            frecuenciaCardiaca = binding.etFrecuenciaCardiaca.text?.toString()?.trim() ?: "",
            frecuenciaRespiratoria = binding.etFrecuenciaRespiratoria.text?.toString()?.trim() ?: "",
            mucosas = binding.etMucosas.text?.toString()?.trim() ?: "",
            anamnesis = binding.etAnamnesis.text?.toString()?.trim() ?: "",
            tratamientoRealizado = binding.etTratamiento.text?.toString()?.trim() ?: "",
            medicamentosJson = medicamentosJson,
            evidenciasJson = evidenciasJson,

            // Paso 3: Pruebas, Compromisos, Funcionarios y Notificados
            tipoPrueba = binding.autoCompleteTipoPrueba.text?.toString()?.trim() ?: "",
            pruebasComplementarias = binding.etPruebasComplementarias.text?.toString()?.trim() ?: "",
            resultadoPruebas = binding.etResultadoPruebas.text?.toString()?.trim()?.ifEmpty { null },
            examenesJson = examenesJson,
            compromisos = binding.etCompromisos.text?.toString()?.trim() ?: "",
            fundamentoLegal = binding.etFundamentoLegal.text?.toString()?.trim()?.ifEmpty { "Ley 1774 de 2016" } ?: "Ley 1774 de 2016",
            plazoDias = binding.etPlazoDias.text?.toString()?.trim()?.toIntOrNull(),
            estadoCierre = binding.autoCompleteEstadoCierre.text?.toString()?.trim()?.ifEmpty { "En tratamiento" } ?: "En tratamiento",
            observacion = binding.etObservaciones.text?.toString()?.trim()?.ifEmpty { null },
            funcionarioNombre = binding.etFuncionarioNombre.text?.toString()?.trim() ?: "",
            funcionarioCargo = binding.etFuncionarioCargo.text?.toString()?.trim() ?: "",
            funcionarioMatricula = binding.etFuncionarioMatricula.text?.toString()?.trim()?.ifEmpty { null },
            funcionarioIdentificacion = binding.etFuncionarioIdentificacion.text?.toString()?.trim()?.ifEmpty { null },
            notificadoNombre = binding.etNotificadoNombre.text?.toString()?.trim()?.ifEmpty { null },
            notificadoIdentificacion = binding.etNotificadoIdentificacion.text?.toString()?.trim()?.ifEmpty { null },
            firmaNotificadorBase64 = firmaNotificador,
            firmaNotificadoBase64 = firmaNotificado,

            // Paso 3: Módulo de Rescate CBA
            requiereRescate = binding.swRequiereRescate.isChecked,
            motivoRescate = binding.autoCompleteMotivoRescate.text?.toString()?.trim() ?: "",
            personaAtiendeRescatista = binding.etPersonaAtiendeRescatista.text?.toString()?.trim() ?: "",
            personaAtiendeCedula = binding.etPersonaAtiendeCedula.text?.toString()?.trim() ?: "",
            personaAtiendeTelefono = binding.etPersonaAtiendeTelefono.text?.toString()?.trim() ?: "",
            barrioRescate = binding.etBarrioRescate.text?.toString()?.trim() ?: "",
            testigo1Nombre = binding.etTestigo1Nombre.text?.toString()?.trim() ?: "",
            testigo1Cedula = binding.etTestigo1Cedula.text?.toString()?.trim() ?: "",
            testigo1Telefono = binding.etTestigo1Telefono.text?.toString()?.trim() ?: "",
            testigo2Nombre = binding.etTestigo2Nombre.text?.toString()?.trim() ?: "",
            testigo2Cedula = binding.etTestigo2Cedula.text?.toString()?.trim() ?: "",
            testigo2Telefono = binding.etTestigo2Telefono.text?.toString()?.trim() ?: "",
            observacionesRescate = binding.etObservacionesRescate.text?.toString()?.trim() ?: "",
            veterinarioReceptor = binding.etVeterinarioReceptor.text?.toString()?.trim() ?: "",
            fechaRecepcion = binding.etFechaRecepcion.text?.toString()?.trim() ?: "",
            horaRecepcion = binding.etHoraRecepcion.text?.toString()?.trim() ?: "",
            observacionesRecepcion = binding.etObservacionesRecepcion.text?.toString()?.trim() ?: "",

            // Paso 3: 19 Switches de Evaluación Clínica por Sistemas
            swRespiratorioMoco = binding.swRespiratorioMoco.isChecked,
            swRespiratorioDificultad = binding.swRespiratorioDificultad.isChecked,
            swRespiratorioTos = binding.swRespiratorioTos.isChecked,
            swRespiratorioLaganeo = binding.swRespiratorioLaganeo.isChecked,
            swDigestivoVomito = binding.swDigestivoVomito.isChecked,
            swDigestivoDiarrea = binding.swDigestivoDiarrea.isChecked,
            swDigestivoDesnutrido = binding.swDigestivoDesnutrido.isChecked,
            swDigestivoDeshidratado = binding.swDigestivoDeshidratado.isChecked,
            swMusculoCaidaPatas = binding.swMusculoCaidaPatas.isChecked,
            swMusculoCojo = binding.swMusculoCojo.isChecked,
            swMusculoHeridaAbierta = binding.swMusculoHeridaAbierta.isChecked,
            swMusculoGusanos = binding.swMusculoGusanos.isChecked,
            swMusculoPostrado = binding.swMusculoPostrado.isChecked,
            swDermatoPeladuras = binding.swDermatoPeladuras.isChecked,
            swDermatoHeridasSangre = binding.swDermatoHeridasSangre.isChecked,
            swDermatoPulgas = binding.swDermatoPulgas.isChecked,
            swGenitoMasaSecrecion = binding.swGenitoMasaSecrecion.isChecked,
            swGenitoGestante = binding.swGenitoGestante.isChecked,
            swGenitoCelo = binding.swGenitoCelo.isChecked
        )
    }

    /**
     * Escribe en los campos visuales de [binding] los valores correspondientes de [entity].
     *
     * Orden de restauración y efectos secundarios de listeners:
     * 1. Se restauran los campos de texto del Paso 1 y Paso 2.
     * 2. Para [binding.autoCompleteQuienReporta] se usa [setText(valor, false)] para no disparar el dropdown.
     *    Su listener [addTextChangedListener] actualiza automáticamente la visibilidad de [binding.tilQuienReportaOtro].
     * 3. Se asigna [binding.swDesparasitacion.isChecked], cuyo listener [setOnCheckedChangeListener]
     *    actualiza automáticamente la etiqueta de estado textual y color.
     * 4. En el Paso 3, se asigna PRIMERO [binding.swRequiereRescate.isChecked] para que su listener active
     *    la visibilidad de los contenedores y actualice el label; posteriormente se vuelcan los campos específicos
     *    de rescate para asegurar que los datos persistidos en la entidad sobrescriban cualquier sugerencia automática.
     * 5. Se asignan los 19 switches clínicos (cuyos listeners actualizarán sus labels a "SÍ"/"NO").
     * 6. Se restauran las firmas digitales en los [SignatureView] mediante [restoreFromBase64].
     *
     * @param binding Binding de [ActaAtencionActivity].
     * @param entity Entidad local con los datos a restaurar.
     */
    fun restaurar(
        binding: ActivityActaAtencionBinding,
        entity: ActaLocalEntity
    ) {
        // ----------------------------------------------------
        // 1. Paso 1: Datos Generales
        // ----------------------------------------------------
        binding.etRadicado.setText(entity.nroRadicadoAtencion)
        binding.etFechaAtencion.setText(entity.fechaAtencion)
        binding.etSolicitudAtencion.setText(entity.solicitudAtencionPor, false)
        binding.autoCompleteQuienReporta.setText(entity.quienReporta, false)
        binding.etQuienReportaOtro.setText(entity.quienReportaOtro ?: "")
        binding.etPropietarioNombre.setText(entity.propietarioNombre)
        binding.etPropietarioCedula.setText(entity.propietarioCedula)
        binding.etPropietarioTelefono.setText(entity.propietarioTelefono)
        binding.etPropietarioEmail.setText(entity.propietarioEmail ?: "")
        binding.etPropietarioBarrio.setText(entity.propietarioBarrio)
        binding.etPropietarioDireccion.setText(entity.propietarioDireccion)
        binding.etLugarAtencion.setText(entity.lugarAtencion)

        // ----------------------------------------------------
        // 2. Paso 2: Pacientes y Clínica (Constantes Vitales)
        // ----------------------------------------------------
        binding.swDesparasitacion.isChecked = entity.desparasitacion
        binding.etFrecuenciaCardiaca.setText(entity.frecuenciaCardiaca)
        binding.etFrecuenciaRespiratoria.setText(entity.frecuenciaRespiratoria)
        binding.etMucosas.setText(entity.mucosas, false)
        binding.etAnamnesis.setText(entity.anamnesis)
        binding.etTratamiento.setText(entity.tratamientoRealizado)

        // ----------------------------------------------------
        // 3. Paso 3: Pruebas, Compromisos, Funcionarios y Cierre
        // ----------------------------------------------------
        binding.autoCompleteTipoPrueba.setText(entity.tipoPrueba, false)
        binding.etPruebasComplementarias.setText(entity.pruebasComplementarias)
        binding.etResultadoPruebas.setText(entity.resultadoPruebas ?: "")
        binding.etCompromisos.setText(entity.compromisos)
        binding.etFundamentoLegal.setText(entity.fundamentoLegal)
        binding.etPlazoDias.setText(entity.plazoDias?.toString() ?: "")
        binding.autoCompleteEstadoCierre.setText(entity.estadoCierre, false)
        binding.etObservaciones.setText(entity.observacion ?: "")
        binding.etFuncionarioNombre.setText(entity.funcionarioNombre)
        binding.etFuncionarioCargo.setText(entity.funcionarioCargo)
        binding.etFuncionarioMatricula.setText(entity.funcionarioMatricula ?: "")
        binding.etFuncionarioIdentificacion.setText(entity.funcionarioIdentificacion ?: "")
        binding.etNotificadoNombre.setText(entity.notificadoNombre ?: "")
        binding.etNotificadoIdentificacion.setText(entity.notificadoIdentificacion ?: "")

        // ----------------------------------------------------
        // 4. Paso 3: Módulo de Rescate CBA
        // Activamos primero el switch para que sus listeners configuren visibilidad
        // y luego restauramos los valores explícitos para no ser pisados por la autocompleción.
        // ----------------------------------------------------
        binding.swRequiereRescate.isChecked = entity.requiereRescate
        binding.autoCompleteMotivoRescate.setText(entity.motivoRescate, false)
        binding.etPersonaAtiendeRescatista.setText(entity.personaAtiendeRescatista)
        binding.etPersonaAtiendeCedula.setText(entity.personaAtiendeCedula)
        binding.etPersonaAtiendeTelefono.setText(entity.personaAtiendeTelefono)
        binding.etBarrioRescate.setText(entity.barrioRescate)
        binding.etTestigo1Nombre.setText(entity.testigo1Nombre)
        binding.etTestigo1Cedula.setText(entity.testigo1Cedula)
        binding.etTestigo1Telefono.setText(entity.testigo1Telefono)
        binding.etTestigo2Nombre.setText(entity.testigo2Nombre)
        binding.etTestigo2Cedula.setText(entity.testigo2Cedula)
        binding.etTestigo2Telefono.setText(entity.testigo2Telefono)
        binding.etObservacionesRescate.setText(entity.observacionesRescate)
        binding.etVeterinarioReceptor.setText(entity.veterinarioReceptor)
        binding.etFechaRecepcion.setText(entity.fechaRecepcion)
        binding.etHoraRecepcion.setText(entity.horaRecepcion)
        binding.etObservacionesRecepcion.setText(entity.observacionesRecepcion)

        // ----------------------------------------------------
        // 5. Paso 3: 19 Switches de Evaluación Clínica por Sistemas
        // ----------------------------------------------------
        binding.swRespiratorioMoco.isChecked = entity.swRespiratorioMoco
        binding.swRespiratorioDificultad.isChecked = entity.swRespiratorioDificultad
        binding.swRespiratorioTos.isChecked = entity.swRespiratorioTos
        binding.swRespiratorioLaganeo.isChecked = entity.swRespiratorioLaganeo
        binding.swDigestivoVomito.isChecked = entity.swDigestivoVomito
        binding.swDigestivoDiarrea.isChecked = entity.swDigestivoDiarrea
        binding.swDigestivoDesnutrido.isChecked = entity.swDigestivoDesnutrido
        binding.swDigestivoDeshidratado.isChecked = entity.swDigestivoDeshidratado
        binding.swMusculoCaidaPatas.isChecked = entity.swMusculoCaidaPatas
        binding.swMusculoCojo.isChecked = entity.swMusculoCojo
        binding.swMusculoHeridaAbierta.isChecked = entity.swMusculoHeridaAbierta
        binding.swMusculoGusanos.isChecked = entity.swMusculoGusanos
        binding.swMusculoPostrado.isChecked = entity.swMusculoPostrado
        binding.swDermatoPeladuras.isChecked = entity.swDermatoPeladuras
        binding.swDermatoHeridasSangre.isChecked = entity.swDermatoHeridasSangre
        binding.swDermatoPulgas.isChecked = entity.swDermatoPulgas
        binding.swGenitoMasaSecrecion.isChecked = entity.swGenitoMasaSecrecion
        binding.swGenitoGestante.isChecked = entity.swGenitoGestante
        binding.swGenitoCelo.isChecked = entity.swGenitoCelo

        // ----------------------------------------------------
        // 6. Firmas Digitales
        // ----------------------------------------------------
        binding.signatureNotificador.restoreFromBase64(entity.firmaNotificadorBase64)
        binding.signatureNotificado.restoreFromBase64(entity.firmaNotificadoBase64)
    }

    // =========================================================================
    // FUNCIONES AUXILIARES DE SERIALIZACIÓN JSON PARA LISTAS DINÁMICAS
    // =========================================================================

    /**
     * Serializa la lista de tarjetas de animales (pacientes) a formato JSON.
     *
     * @param animales Lista de tarjetas diligenciadas en la vista de atención.
     * @param resolverIdRaza Función opcional para resolver el ID numérico de la raza a partir
     * de la especie y raza seleccionadas. En la Tarea 3c, [ActaAtencionActivity] le pasará
     * la búsqueda contra `listaRazasBackend` (tal como lo hace en `submitActa`, líneas ~2208-2217):
     * ```kotlin
     * val resolverIdRaza: (String, String) -> Int? = { especie, raza ->
     *     val razaObj = listaRazasBackend.firstOrNull {
     *         it.nombre.equals(raza, ignoreCase = true) &&
     *         (it.nombreEspecie.isNullOrEmpty() || it.nombreEspecie.equals(especie, ignoreCase = true))
     *     } ?: listaRazasBackend.firstOrNull {
     *         it.nombre.equals(raza, ignoreCase = true)
     *     } ?: listaRazasBackend.firstOrNull {
     *         it.nombreEspecie?.equals(especie, ignoreCase = true) == true
     *     }
     *     razaObj?.idRaza
     * }
     * ```
     * El ID resuelto se asigna a [AnimalCardState.idRaza] en cada tarjeta antes de serializar a JSON.
     */
    fun serializarAnimales(
        animales: List<AnimalCardState>,
        resolverIdRaza: ((especie: String, raza: String) -> Int?)? = null
    ): String {
        val gson = Gson()
        val listaConRaza = if (resolverIdRaza != null) {
            animales.map { card ->
                val idResuelto = resolverIdRaza(card.especie, card.raza) ?: card.idRaza
                card.copy(idRaza = idResuelto)
            }
        } else {
            animales
        }
        return gson.toJson(listaConRaza)
    }

    /**
     * Deserializa la lista de tarjetas de animales (pacientes) a partir del JSON almacenado.
     */
    fun deserializarAnimales(json: String?): List<AnimalCardState> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AnimalCardState>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa la lista de medicamentos aplicados a formato JSON.
     */
    fun serializarMedicamentos(medicamentos: List<MedicamentoAplicadoActa>): String {
        return Gson().toJson(medicamentos)
    }

    /**
     * Deserializa la lista de medicamentos aplicados a partir del JSON almacenado.
     */
    fun deserializarMedicamentos(json: String?): List<MedicamentoAplicadoActa> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MedicamentoAplicadoActa>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa la lista de exámenes clínicos complementarios a formato JSON.
     */
    fun serializarExamenes(examenes: List<ExamenSeleccionadoActa>): String {
        return Gson().toJson(examenes)
    }

    /**
     * Deserializa la lista de exámenes clínicos complementarios a partir del JSON almacenado.
     */
    fun deserializarExamenes(json: String?): List<ExamenSeleccionadoActa> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<ExamenSeleccionadoActa>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa la lista de evidencias multimedia locales a formato JSON.
     */
    fun serializarEvidencias(evidencias: List<EvidenciaLocalItem>): String {
        return Gson().toJson(evidencias)
    }

    /**
     * Deserializa la lista de evidencias multimedia locales a partir del JSON almacenado.
     */
    fun deserializarEvidencias(json: String?): List<EvidenciaLocalItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<EvidenciaLocalItem>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
