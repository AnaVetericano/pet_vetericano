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

    @SerializedName("funcionarios")
    val funcionarios: List<FuncionarioActaRequest>? = null,

    @SerializedName("notificado_nombre")
    val notificadoNombre: String? = null,

    @SerializedName("notificaciones_identificacion")
    val notificacionesIdentificacion: String? = null,

    @SerializedName("fecha_notificacion")
    val fechaNotificacion: String? = null,

    @SerializedName("notificador_nombre")
    val notificadorNombre: String? = null,

    @SerializedName("notificador_identificacion")
    val notificadorIdentificacion: String? = null,

    @SerializedName("notificador_cargo")
    val notificadorCargo: String? = null,

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
