package com.example.petvetericano.models

import com.google.gson.annotations.SerializedName

/**
 * Modelos de datos para el Acta de Atención en Campo (Seguimiento Peticiones Visita).
 * Se adapta 100% a los serializers y modelos de Django:
 * - SeguimientoPeticionesVisitaSerializer
 * - AnimalRapidoSerializer
 * - UbicacionEditableSerializer
 * - SeguimientoVisitaFuncionariosSerializer
 */

data class SeguimientoPeticionVisitaRequest(
    @SerializedName("id_peticion")
    val idPeticion: Int,

    @SerializedName("id_veterinario")
    val idVeterinario: Int,

    @SerializedName("numero_radicado")
    val numeroRadicado: String,

    @SerializedName("fecha_atencion")
    val fechaAtencion: String,

    @SerializedName("propietario_nombre")
    val propietarioNombre: String,

    @SerializedName("propietario_cedula")
    val propietarioCedula: String,

    @SerializedName("propietario_telefono")
    val propietarioTelefono: String,

    @SerializedName("propietario_email")
    val propietarioEmail: String? = null,

    @SerializedName("propietario_barrio")
    val propietarioBarrio: String,

    @SerializedName("propietario_direccion")
    val propietarioDireccion: String,

    @SerializedName("quien_reporta")
    val quienReporta: String,

    @SerializedName("quien_reporta_otro")
    val quienReportaOtro: String? = null,

    @SerializedName("solicitud_atencion_por")
    val solicitudAtencionPor: String,

    @SerializedName("lugar_atencion")
    val lugarAtencion: LugarAtencionActa,

    // Modo A / Campos directos del paciente en Django
    @SerializedName("nombre_paciente")
    val nombrePaciente: String? = null,

    @SerializedName("paciente_especie")
    val pacienteEspecie: String? = null,

    @SerializedName("paciente_sexo")
    val pacienteSexo: String? = null,

    @SerializedName("paciente_color")
    val pacienteColor: String? = null,

    @SerializedName("paciente_raza")
    val pacienteRaza: String? = null,

    @SerializedName("paciente_edad")
    val pacienteEdad: String? = null,

    @SerializedName("peso_paciente")
    val pesoPaciente: Double? = null,

    @SerializedName("esterilizacion_paciente")
    val esterilizacionPaciente: Boolean? = null,

    @SerializedName("descripcion_paciente")
    val descripcionPaciente: String? = null,

    // Modo B / Lista de animales
    @SerializedName("animales")
    val animales: List<AnimalRapidoRequest>,

    @SerializedName("nro_animales_atendidos")
    val nroAnimalesAtendidos: Int? = null,

    @SerializedName("anamnesis_descripcion_queja")
    val anamnesisDescripcionQueja: String,

    @SerializedName("tratamiento_realizado")
    val tratamientoRealizado: String,

    @SerializedName("desparasitacion")
    val desparasitacion: Boolean = false,

    @SerializedName("pruebas_complementarias")
    val pruebasComplementarias: String? = null,

    @SerializedName("resultado_pruebas")
    val resultadoPruebas: String? = null,

    @SerializedName("compromisos")
    val compromisos: String? = null,

    @SerializedName("fundamento_legal")
    val fundamentoLegal: String? = "Ley 1774 de 2016",

    @SerializedName("plazo_dias_cumplimiento")
    val plazoDiasCumplimiento: Int? = null,

    // Funcionarios directos y lista
    @SerializedName("nombre_funcionario")
    val nombreFuncionario: String? = null,

    @SerializedName("funcionario_cargo")
    val funcionarioCargo: String? = null,

    @SerializedName("funcionarios")
    val funcionarios: List<FuncionarioActaRequest>? = null,

    // Notificación
    @SerializedName("notificado_nombre")
    val notificadoNombre: String? = null,

    @SerializedName("notificaciones_identificacion")
    val notificacionesIdentificacion: String? = null,

    @SerializedName("fecha_notificacion")
    val fechaNotificacion: String? = null,

    // Notificador
    @SerializedName("notificador_nombre")
    val notificadorNombre: String? = null,

    @SerializedName("notificador_identificacion")
    val notificadorIdentificacion: String? = null,

    @SerializedName("notificador_cargo")
    val notificadorCargo: String? = null,

    // Firmas Digitales
    @SerializedName("firma_notificador")
    val firmaNotificador: String? = null,

    @SerializedName("firma_notificado")
    val firmaNotificado: String? = null,

    @SerializedName("observacion")
    val observacion: String? = null
)

data class LugarAtencionActa(
    @SerializedName("direccion")
    val direccion: String,

    @SerializedName("latitud")
    val latitud: Double = 0.0,

    @SerializedName("longitud")
    val longitud: Double = 0.0
)

data class AnimalRapidoRequest(
    @SerializedName("id_animal")
    val idAnimal: Int? = null,

    @SerializedName("nombre")
    val nombre: String = "Sin nombre",

    @SerializedName("id_raza")
    val idRaza: Int? = null,

    @SerializedName("sexo")
    val sexo: String? = null,

    @SerializedName("color")
    val color: String? = null,

    @SerializedName("peso")
    val peso: Double? = null,

    @SerializedName("esterilizado")
    val esterilizado: Boolean = false,

    @SerializedName("caracteristicas")
    val caracteristicas: String? = null,

    @SerializedName("fecha_nacimiento")
    val fechaNacimiento: String? = null
)

data class FuncionarioActaRequest(
    @SerializedName("id_usuario")
    val idUsuario: Int? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("cargo")
    val cargo: String? = null,

    @SerializedName("es_externo")
    val esExterno: Boolean = false,

    @SerializedName("es_principal")
    val esPrincipal: Boolean = false,

    @SerializedName("institucion")
    val institucion: String? = null,

    @SerializedName("documento_identidad")
    val documentoIdentidad: String? = null
)

data class FuncionarioItemResponse(
    @SerializedName("id_usuario")
    val idUsuario: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("apellido")
    val apellido: String,

    @SerializedName("rol")
    val rol: String,

    @SerializedName("id_rol")
    val idRol: Int,

    @SerializedName("email")
    val email: String,

    @SerializedName("identificacion")
    val identificacion: String?
)

data class CrearActaResponse(
    @SerializedName("mensaje")
    val mensaje: String?,

    @SerializedName("id_seguimiento")
    val idSeguimiento: Int?
)

// ==========================================
// MODELOS PARA PASO 2: PACIENTES Y CLÍNICA
// ==========================================

data class AnimalRequest(
    @SerializedName("nombre")
    val nombre: String = "Sin nombre",

    @SerializedName("id_raza")
    val idRaza: Int? = null,

    @SerializedName("sexo")
    val sexo: String? = null,

    @SerializedName("color")
    val color: String? = null,

    @SerializedName("peso")
    val peso: Double? = null,

    @SerializedName("esterilizado")
    val esterilizado: Boolean = false,

    @SerializedName("caracteristicas")
    val caracteristicas: String? = null,

    @SerializedName("fecha_nacimiento")
    val fechaNacimiento: String? = null,

    @SerializedName("activo")
    val activo: Boolean = true,

    @SerializedName("fecha_ingreso")
    val fechaIngreso: String? = null
)

data class AnimalResponse(
    @SerializedName("id_animal")
    val idAnimal: Int,

    @SerializedName("nombre")
    val nombre: String?,

    @SerializedName("id_raza")
    val idRaza: Int?,

    @SerializedName("raza_nombre")
    val razaNombre: String?,

    @SerializedName("sexo")
    val sexo: String?,

    @SerializedName("color")
    val color: String?,

    @SerializedName("peso")
    val peso: Double?,

    @SerializedName("esterilizado")
    val esterilizado: Boolean?,

    @SerializedName("caracteristicas")
    val caracteristicas: String?,

    @SerializedName("activo")
    val activo: Boolean?
)

data class ConsultaClinicaRequest(
    @SerializedName("id_historia")
    val idHistoria: Int? = null,

    @SerializedName("prioridad")
    val prioridad: String? = "Media",

    @SerializedName("motivo_consulta")
    val motivoConsulta: String? = null,

    @SerializedName("anamnesis")
    val anamnesis: String? = null,

    @SerializedName("hallazgos_examen")
    val hallazgosExamen: String? = null,

    @SerializedName("frecuencia_respiratoria")
    val frecuenciaRespiratoria: Int? = null,

    @SerializedName("frecuencia_cardiaca")
    val frecuenciaCardiaca: Int? = null,

    @SerializedName("temperatura")
    val temperatura: Double? = null,

    @SerializedName("pulso")
    val pulso: String? = null,

    @SerializedName("tllc")
    val tllc: String? = null,

    @SerializedName("ganglios_linfaticos")
    val gangliosLinfaticos: String? = null,

    @SerializedName("mucosas")
    val mucosas: String? = null,

    @SerializedName("actitud_temperamento")
    val actitudTemperamento: String? = null,

    @SerializedName("observaciones_examen")
    val observacionesExamen: String? = null,

    @SerializedName("materiales_utilizados")
    val materialesUtilizados: String? = null,

    @SerializedName("resultado_estado")
    val resultadoEstado: String? = null,

    @SerializedName("ingresa_cba")
    val ingresaCba: Boolean = false
)

data class ConsultaClinicaResponse(
    @SerializedName("id_consulta")
    val idConsulta: Int,

    @SerializedName("fecha_hora")
    val fechaHora: String?,

    @SerializedName("motivo_consulta")
    val motivoConsulta: String?,

    @SerializedName("anamnesis")
    val anamnesis: String?
)

data class TratamientoClinicoRequest(
    @SerializedName("id_consulta")
    val idConsulta: Int,

    @SerializedName("descripcion")
    val descripcion: String,

    @SerializedName("producto_base")
    val productoBase: String? = null,

    @SerializedName("dosis_basica")
    val dosisBasica: String? = null,

    @SerializedName("presentacion")
    val presentacion: String? = null,

    @SerializedName("via_administracion")
    val viaAdministracion: String? = null,

    @SerializedName("frecuencia_duracion")
    val frecuenciaDuracion: String? = null,

    @SerializedName("materiales_utilizados")
    val materialesUtilizados: String? = null
)

data class TratamientoClinicoResponse(
    @SerializedName("id_tratamiento")
    val idTratamiento: Int,

    @SerializedName("descripcion")
    val descripcion: String
)

// ==========================================
// MODELOS PARA PASO 3: PRUEBAS Y CIERRE
// ==========================================

data class ExamenClinicoRequest(
    @SerializedName("id_consulta")
    val idConsulta: Int? = null,

    @SerializedName("nombre_tipo_examen")
    val nombreTipoExamen: String? = null,

    @SerializedName("solicitado")
    val solicitado: Boolean = true,

    @SerializedName("descripcion_hallazgos")
    val descripcionHallazgos: String? = null,

    @SerializedName("resultado")
    val resultado: String? = null,

    @SerializedName("ruta_archivo_resultado")
    val rutaArchivoResultado: String? = null,

    @SerializedName("estado")
    val estado: String = "activo"
)

data class ExamenClinicoResponse(
    @SerializedName("id_examen")
    val idExamen: Int,

    @SerializedName("nombre_tipo_examen")
    val nombreTipoExamen: String?,

    @SerializedName("resultado")
    val resultado: String?,

    @SerializedName("estado")
    val estado: String?
)

