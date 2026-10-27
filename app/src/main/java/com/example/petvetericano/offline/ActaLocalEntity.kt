package com.example.petvetericano.offline

/**
 * Estados posibles del ciclo de vida y sincronización del Acta de Atención en campo.
 */
enum class EstadoSincronizacionActa {
    BORRADOR,
    PENDIENTE_ENVIO,
    ENVIANDO,
    ERROR_REINTENTABLE,
    REQUIERE_ATENCION,
    ENVIADA
}

/**
 * Representación de un ítem de evidencia multimedia (foto o video) adjunto al acta.
 * Se serializa dentro del campo evidencias_json.
 */
data class EvidenciaLocalItem(
    val localPath: String,
    val esVideo: Boolean,
    val etiqueta: String = "",
    val remoteUrl: String? = null
)

/**
 * Entidad local que modela el registro completo del Acta de Seres Sintientes en SQLite.
 * Mapea 1:1 los campos de la interfaz de usuario en ActaAtencionActivity y los metadatos de sincronización.
 */
data class ActaLocalEntity(
    val idLocal: Long = 0L,
    val idPeticion: Int,
    val idVeterinario: Int,
    val estadoSincronizacion: EstadoSincronizacionActa = EstadoSincronizacionActa.BORRADOR,
    val enviandoDesde: Long? = null,
    val idSeguimientoRemoto: Int? = null,
    val sincronizacionOffline: Boolean = false,
    val reintentos: Int = 0,
    val ultimoCodigoHttp: Int? = null,
    val ultimoError: String? = null,
    val pasoActual: Int = 1,
    val actualizadoEn: Long = System.currentTimeMillis(),
    val nroRadicadoAtencion: String = "",

    // Paso 1: Datos Generales
    val fechaAtencion: String = "",
    val propietarioNombre: String = "",
    val propietarioCedula: String = "",
    val propietarioTelefono: String = "",
    val propietarioEmail: String? = null,
    val propietarioBarrio: String = "",
    val propietarioDireccion: String = "",
    val quienReporta: String = "",
    val quienReportaOtro: String? = null,
    val solicitudAtencionPor: String = "",
    val lugarAtencion: String = "",
    val latitudVisita: Double = 0.0,
    val longitudVisita: Double = 0.0,

    // Paso 2: Pacientes y Clínica
    val animalesJson: String? = null,
    val desparasitacion: Boolean = false,
    val frecuenciaCardiaca: String = "",
    val frecuenciaRespiratoria: String = "",
    val mucosas: String = "",
    val anamnesis: String = "",
    val tratamientoRealizado: String = "",
    val medicamentosJson: String? = null,
    val evidenciasJson: String? = null,

    // Paso 3: Pruebas, Compromisos, Cierre, Funcionarios, Notificados, Firmas
    val tipoPrueba: String = "",
    val pruebasComplementarias: String = "",
    val resultadoPruebas: String? = null,
    val examenesJson: String? = null,
    val compromisos: String = "",
    val fundamentoLegal: String = "Ley 1774 de 2016",
    val plazoDias: Int? = null,
    val estadoCierre: String = "En tratamiento",
    val observacion: String? = null,
    val funcionarioNombre: String = "",
    val funcionarioCargo: String = "",
    val funcionarioMatricula: String? = null,
    val funcionarioIdentificacion: String? = null,
    val notificadoNombre: String? = null,
    val notificadoIdentificacion: String? = null,
    val firmaNotificadorBase64: String? = null,
    val firmaNotificadoBase64: String? = null,

    // Paso 3: Módulo de Rescate CBA (Formato Popayán F-GS-C01-03-33)
    val requiereRescate: Boolean = false,
    val motivoRescate: String = "",
    val personaAtiendeRescatista: String = "",
    val personaAtiendeCedula: String = "",
    val personaAtiendeTelefono: String = "",
    val barrioRescate: String = "",
    val testigo1Nombre: String = "",
    val testigo1Cedula: String = "",
    val testigo1Telefono: String = "",
    val testigo2Nombre: String = "",
    val testigo2Cedula: String = "",
    val testigo2Telefono: String = "",
    val observacionesRescate: String = "",
    val veterinarioReceptor: String = "",
    val fechaRecepcion: String = "",
    val horaRecepcion: String = "",
    val observacionesRecepcion: String = "",

    // Paso 3: Evaluación Clínica por Sistemas (19 switches)
    val swRespiratorioMoco: Boolean = false,
    val swRespiratorioDificultad: Boolean = false,
    val swRespiratorioTos: Boolean = false,
    val swRespiratorioLaganeo: Boolean = false,
    val swDigestivoVomito: Boolean = false,
    val swDigestivoDiarrea: Boolean = false,
    val swDigestivoDesnutrido: Boolean = false,
    val swDigestivoDeshidratado: Boolean = false,
    val swMusculoCaidaPatas: Boolean = false,
    val swMusculoCojo: Boolean = false,
    val swMusculoHeridaAbierta: Boolean = false,
    val swMusculoGusanos: Boolean = false,
    val swMusculoPostrado: Boolean = false,
    val swDermatoPeladuras: Boolean = false,
    val swDermatoHeridasSangre: Boolean = false,
    val swDermatoPulgas: Boolean = false,
    val swGenitoMasaSecrecion: Boolean = false,
    val swGenitoGestante: Boolean = false,
    val swGenitoCelo: Boolean = false
)
