package com.example.petvetericano.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper para la base de datos local de actas de atención y rescate en campo.
 * Implementa patrón singleton thread-safe con el applicationContext y WAL habilitado.
 */
class ActaLocalDbHelper private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_ACTAS (
                $COL_ID_LOCAL INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ID_PETICION INTEGER NOT NULL,
                $COL_ID_VETERINARIO INTEGER NOT NULL,
                $COL_ESTADO_SINCRONIZACION TEXT NOT NULL,
                $COL_ENVIANDO_DESDE INTEGER,
                $COL_ID_SEGUIMIENTO_REMOTO INTEGER,
                $COL_SINCRONIZACION_OFFLINE INTEGER NOT NULL DEFAULT 0,
                $COL_REINTENTOS INTEGER NOT NULL DEFAULT 0,
                $COL_ULTIMO_CODIGO_HTTP INTEGER,
                $COL_ULTIMO_ERROR TEXT,
                $COL_PASO_ACTUAL INTEGER NOT NULL DEFAULT 1,
                $COL_ACTUALIZADO_EN INTEGER NOT NULL,
                $COL_NRO_RADICADO_ATENCION TEXT,
                
                $COL_FECHA_ATENCION TEXT,
                $COL_PROPIETARIO_NOMBRE TEXT,
                $COL_PROPIETARIO_CEDULA TEXT,
                $COL_PROPIETARIO_TELEFONO TEXT,
                $COL_PROPIETARIO_EMAIL TEXT,
                $COL_PROPIETARIO_BARRIO TEXT,
                $COL_PROPIETARIO_DIRECCION TEXT,
                $COL_QUIEN_REPORTA TEXT,
                $COL_QUIEN_REPORTA_OTRO TEXT,
                $COL_SOLICITUD_ATENCION_POR TEXT,
                $COL_LUGAR_ATENCION TEXT,
                $COL_LATITUD_VISITA REAL,
                $COL_LONGITUD_VISITA REAL,
                
                $COL_ANIMALES_JSON TEXT,
                $COL_DESPARASITACION INTEGER NOT NULL DEFAULT 0,
                $COL_FRECUENCIA_CARDIACA TEXT,
                $COL_FRECUENCIA_RESPIRATORIA TEXT,
                $COL_MUCOSAS TEXT,
                $COL_ANAMNESIS TEXT,
                $COL_TRATAMIENTO_REALIZADO TEXT,
                $COL_MEDICAMENTOS_JSON TEXT,
                $COL_EVIDENCIAS_JSON TEXT,
                
                $COL_TIPO_PRUEBA TEXT,
                $COL_PRUEBAS_COMPLEMENTARIAS TEXT,
                $COL_RESULTADO_PRUEBAS TEXT,
                $COL_EXAMENES_JSON TEXT,
                $COL_COMPROMISOS TEXT,
                $COL_FUNDAMENTO_LEGAL TEXT,
                $COL_PLAZO_DIAS INTEGER,
                $COL_ESTADO_CIERRE TEXT,
                $COL_OBSERVACION TEXT,
                $COL_FUNCIONARIO_NOMBRE TEXT,
                $COL_FUNCIONARIO_CARGO TEXT,
                $COL_FUNCIONARIO_MATRICULA TEXT,
                $COL_FUNCIONARIO_IDENTIFICACION TEXT,
                $COL_NOTIFICADO_NOMBRE TEXT,
                $COL_NOTIFICADO_IDENTIFICACION TEXT,
                $COL_FIRMA_NOTIFICADOR_BASE64 TEXT,
                $COL_FIRMA_NOTIFICADO_BASE64 TEXT,
                
                $COL_REQUIERE_RESCATE INTEGER NOT NULL DEFAULT 0,
                $COL_MOTIVO_RESCATE TEXT,
                $COL_PERSONA_ATIENDE_RESCATISTA TEXT,
                $COL_PERSONA_ATIENDE_CEDULA TEXT,
                $COL_PERSONA_ATIENDE_TELEFONO TEXT,
                $COL_BARRIO_RESCATE TEXT,
                $COL_TESTIGO1_NOMBRE TEXT,
                $COL_TESTIGO1_CEDULA TEXT,
                $COL_TESTIGO1_TELEFONO TEXT,
                $COL_TESTIGO2_NOMBRE TEXT,
                $COL_TESTIGO2_CEDULA TEXT,
                $COL_TESTIGO2_TELEFONO TEXT,
                $COL_OBSERVACIONES_RESCATE TEXT,
                $COL_VETERINARIO_RECEPTOR TEXT,
                $COL_FECHA_RECEPCION TEXT,
                $COL_HORA_RECEPCION TEXT,
                $COL_OBSERVACIONES_RECEPCION TEXT,
                
                $COL_SW_RESPIRATORIO_MOCO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_RESPIRATORIO_DIFICULTAD INTEGER NOT NULL DEFAULT 0,
                $COL_SW_RESPIRATORIO_TOS INTEGER NOT NULL DEFAULT 0,
                $COL_SW_RESPIRATORIO_LAGANEO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DIGESTIVO_VOMITO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DIGESTIVO_DIARREA INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DIGESTIVO_DESNUTRIDO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DIGESTIVO_DESHIDRATADO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_MUSCULO_CAIDA_PATAS INTEGER NOT NULL DEFAULT 0,
                $COL_SW_MUSCULO_COJO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_MUSCULO_HERIDA_ABIERTA INTEGER NOT NULL DEFAULT 0,
                $COL_SW_MUSCULO_GUSANOS INTEGER NOT NULL DEFAULT 0,
                $COL_SW_MUSCULO_POSTRADO INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DERMATO_PELADURAS INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DERMATO_HERIDAS_SANGRE INTEGER NOT NULL DEFAULT 0,
                $COL_SW_DERMATO_PULGAS INTEGER NOT NULL DEFAULT 0,
                $COL_SW_GENITO_MASA_SECRECION INTEGER NOT NULL DEFAULT 0,
                $COL_SW_GENITO_GESTANTE INTEGER NOT NULL DEFAULT 0,
                $COL_SW_GENITO_CELO INTEGER NOT NULL DEFAULT 0,
                
                UNIQUE($COL_ID_PETICION, $COL_ID_VETERINARIO)
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // En futuras versiones incrementales se aplican migraciones con ALTER TABLE
    }

    companion object {
        const val DATABASE_NAME = "petvetericano_actas_offline.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: ActaLocalDbHelper? = null

        fun getInstance(context: Context): ActaLocalDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ActaLocalDbHelper(context.applicationContext).also { instance = it }
            }
        }

        const val TABLE_ACTAS = "actas_locales"

        const val COL_ID_LOCAL = "id_local"
        const val COL_ID_PETICION = "id_peticion"
        const val COL_ID_VETERINARIO = "id_veterinario"
        const val COL_ESTADO_SINCRONIZACION = "estado_sincronizacion"
        const val COL_ENVIANDO_DESDE = "enviando_desde"
        const val COL_ID_SEGUIMIENTO_REMOTO = "id_seguimiento_remoto"
        const val COL_SINCRONIZACION_OFFLINE = "sincronizacion_offline"
        const val COL_REINTENTOS = "reintentos"
        const val COL_ULTIMO_CODIGO_HTTP = "ultimo_codigo_http"
        const val COL_ULTIMO_ERROR = "ultimo_error"
        const val COL_PASO_ACTUAL = "paso_actual"
        const val COL_ACTUALIZADO_EN = "actualizado_en"
        const val COL_NRO_RADICADO_ATENCION = "nro_radicado_atencion"

        // Paso 1: Datos Generales
        const val COL_FECHA_ATENCION = "fecha_atencion"
        const val COL_PROPIETARIO_NOMBRE = "propietario_nombre"
        const val COL_PROPIETARIO_CEDULA = "propietario_cedula"
        const val COL_PROPIETARIO_TELEFONO = "propietario_telefono"
        const val COL_PROPIETARIO_EMAIL = "propietario_email"
        const val COL_PROPIETARIO_BARRIO = "propietario_barrio"
        const val COL_PROPIETARIO_DIRECCION = "propietario_direccion"
        const val COL_QUIEN_REPORTA = "quien_reporta"
        const val COL_QUIEN_REPORTA_OTRO = "quien_reporta_otro"
        const val COL_SOLICITUD_ATENCION_POR = "solicitud_atencion_por"
        const val COL_LUGAR_ATENCION = "lugar_atencion"
        const val COL_LATITUD_VISITA = "latitud_visita"
        const val COL_LONGITUD_VISITA = "longitud_visita"

        // Paso 2: Pacientes y Clínica
        const val COL_ANIMALES_JSON = "animales_json"
        const val COL_DESPARASITACION = "desparasitacion"
        const val COL_FRECUENCIA_CARDIACA = "frecuencia_cardiaca"
        const val COL_FRECUENCIA_RESPIRATORIA = "frecuencia_respiratoria"
        const val COL_MUCOSAS = "mucosas"
        const val COL_ANAMNESIS = "anamnesis"
        const val COL_TRATAMIENTO_REALIZADO = "tratamiento_realizado"
        const val COL_MEDICAMENTOS_JSON = "medicamentos_json"
        const val COL_EVIDENCIAS_JSON = "evidencias_json"

        // Paso 3: Pruebas, Compromisos, Cierre, Funcionarios, Notificados, Firmas
        const val COL_TIPO_PRUEBA = "tipo_prueba"
        const val COL_PRUEBAS_COMPLEMENTARIAS = "pruebas_complementarias"
        const val COL_RESULTADO_PRUEBAS = "resultado_pruebas"
        const val COL_EXAMENES_JSON = "examenes_json"
        const val COL_COMPROMISOS = "compromisos"
        const val COL_FUNDAMENTO_LEGAL = "fundamento_legal"
        const val COL_PLAZO_DIAS = "plazo_dias"
        const val COL_ESTADO_CIERRE = "estado_cierre"
        const val COL_OBSERVACION = "observacion"
        const val COL_FUNCIONARIO_NOMBRE = "funcionario_nombre"
        const val COL_FUNCIONARIO_CARGO = "funcionario_cargo"
        const val COL_FUNCIONARIO_MATRICULA = "funcionario_matricula"
        const val COL_FUNCIONARIO_IDENTIFICACION = "funcionario_identificacion"
        const val COL_NOTIFICADO_NOMBRE = "notificado_nombre"
        const val COL_NOTIFICADO_IDENTIFICACION = "notificado_identificacion"
        const val COL_FIRMA_NOTIFICADOR_BASE64 = "firma_notificador_base64"
        const val COL_FIRMA_NOTIFICADO_BASE64 = "firma_notificado_base64"

        // Rescate CBA
        const val COL_REQUIERE_RESCATE = "requiere_rescate"
        const val COL_MOTIVO_RESCATE = "motivo_rescate"
        const val COL_PERSONA_ATIENDE_RESCATISTA = "persona_atiende_rescatista"
        const val COL_PERSONA_ATIENDE_CEDULA = "persona_atiende_cedula"
        const val COL_PERSONA_ATIENDE_TELEFONO = "persona_atiende_telefono"
        const val COL_BARRIO_RESCATE = "barrio_rescate"
        const val COL_TESTIGO1_NOMBRE = "testigo1_nombre"
        const val COL_TESTIGO1_CEDULA = "testigo1_cedula"
        const val COL_TESTIGO1_TELEFONO = "testigo1_telefono"
        const val COL_TESTIGO2_NOMBRE = "testigo2_nombre"
        const val COL_TESTIGO2_CEDULA = "testigo2_cedula"
        const val COL_TESTIGO2_TELEFONO = "testigo2_telefono"
        const val COL_OBSERVACIONES_RESCATE = "observaciones_rescate"
        const val COL_VETERINARIO_RECEPTOR = "veterinario_receptor"
        const val COL_FECHA_RECEPCION = "fecha_recepcion"
        const val COL_HORA_RECEPCION = "hora_recepcion"
        const val COL_OBSERVACIONES_RECEPCION = "observaciones_recepcion"

        // 19 Switches clínicos por sistemas
        const val COL_SW_RESPIRATORIO_MOCO = "sw_respiratorio_moco"
        const val COL_SW_RESPIRATORIO_DIFICULTAD = "sw_respiratorio_dificultad"
        const val COL_SW_RESPIRATORIO_TOS = "sw_respiratorio_tos"
        const val COL_SW_RESPIRATORIO_LAGANEO = "sw_respiratorio_laganeo"
        const val COL_SW_DIGESTIVO_VOMITO = "sw_digestivo_vomito"
        const val COL_SW_DIGESTIVO_DIARREA = "sw_digestivo_diarrea"
        const val COL_SW_DIGESTIVO_DESNUTRIDO = "sw_digestivo_desnutrido"
        const val COL_SW_DIGESTIVO_DESHIDRATADO = "sw_digestivo_deshidratado"
        const val COL_SW_MUSCULO_CAIDA_PATAS = "sw_musculo_caida_patas"
        const val COL_SW_MUSCULO_COJO = "sw_musculo_cojo"
        const val COL_SW_MUSCULO_HERIDA_ABIERTA = "sw_musculo_herida_abierta"
        const val COL_SW_MUSCULO_GUSANOS = "sw_musculo_gusanos"
        const val COL_SW_MUSCULO_POSTRADO = "sw_musculo_postrado"
        const val COL_SW_DERMATO_PELADURAS = "sw_dermato_peladuras"
        const val COL_SW_DERMATO_HERIDAS_SANGRE = "sw_dermato_heridas_sangre"
        const val COL_SW_DERMATO_PULGAS = "sw_dermato_pulgas"
        const val COL_SW_GENITO_MASA_SECRECION = "sw_genito_masa_secrecion"
        const val COL_SW_GENITO_GESTANTE = "sw_genito_gestante"
        const val COL_SW_GENITO_CELO = "sw_genito_celo"

        fun toContentValues(entity: ActaLocalEntity): ContentValues {
            return ContentValues().apply {
                if (entity.idLocal > 0L) {
                    put(COL_ID_LOCAL, entity.idLocal)
                }
                put(COL_ID_PETICION, entity.idPeticion)
                put(COL_ID_VETERINARIO, entity.idVeterinario)
                put(COL_ESTADO_SINCRONIZACION, entity.estadoSincronizacion.name)
                if (entity.enviandoDesde != null) put(COL_ENVIANDO_DESDE, entity.enviandoDesde) else putNull(COL_ENVIANDO_DESDE)
                if (entity.idSeguimientoRemoto != null) put(COL_ID_SEGUIMIENTO_REMOTO, entity.idSeguimientoRemoto) else putNull(COL_ID_SEGUIMIENTO_REMOTO)
                put(COL_SINCRONIZACION_OFFLINE, if (entity.sincronizacionOffline) 1 else 0)
                put(COL_REINTENTOS, entity.reintentos)
                if (entity.ultimoCodigoHttp != null) put(COL_ULTIMO_CODIGO_HTTP, entity.ultimoCodigoHttp) else putNull(COL_ULTIMO_CODIGO_HTTP)
                if (entity.ultimoError != null) put(COL_ULTIMO_ERROR, entity.ultimoError) else putNull(COL_ULTIMO_ERROR)
                put(COL_PASO_ACTUAL, entity.pasoActual)
                put(COL_ACTUALIZADO_EN, entity.actualizadoEn)
                put(COL_NRO_RADICADO_ATENCION, entity.nroRadicadoAtencion)

                // Paso 1
                put(COL_FECHA_ATENCION, entity.fechaAtencion)
                put(COL_PROPIETARIO_NOMBRE, entity.propietarioNombre)
                put(COL_PROPIETARIO_CEDULA, entity.propietarioCedula)
                put(COL_PROPIETARIO_TELEFONO, entity.propietarioTelefono)
                if (entity.propietarioEmail != null) put(COL_PROPIETARIO_EMAIL, entity.propietarioEmail) else putNull(COL_PROPIETARIO_EMAIL)
                put(COL_PROPIETARIO_BARRIO, entity.propietarioBarrio)
                put(COL_PROPIETARIO_DIRECCION, entity.propietarioDireccion)
                put(COL_QUIEN_REPORTA, entity.quienReporta)
                if (entity.quienReportaOtro != null) put(COL_QUIEN_REPORTA_OTRO, entity.quienReportaOtro) else putNull(COL_QUIEN_REPORTA_OTRO)
                put(COL_SOLICITUD_ATENCION_POR, entity.solicitudAtencionPor)
                put(COL_LUGAR_ATENCION, entity.lugarAtencion)
                put(COL_LATITUD_VISITA, entity.latitudVisita)
                put(COL_LONGITUD_VISITA, entity.longitudVisita)

                // Paso 2
                if (entity.animalesJson != null) put(COL_ANIMALES_JSON, entity.animalesJson) else putNull(COL_ANIMALES_JSON)
                put(COL_DESPARASITACION, if (entity.desparasitacion) 1 else 0)
                put(COL_FRECUENCIA_CARDIACA, entity.frecuenciaCardiaca)
                put(COL_FRECUENCIA_RESPIRATORIA, entity.frecuenciaRespiratoria)
                put(COL_MUCOSAS, entity.mucosas)
                put(COL_ANAMNESIS, entity.anamnesis)
                put(COL_TRATAMIENTO_REALIZADO, entity.tratamientoRealizado)
                if (entity.medicamentosJson != null) put(COL_MEDICAMENTOS_JSON, entity.medicamentosJson) else putNull(COL_MEDICAMENTOS_JSON)
                if (entity.evidenciasJson != null) put(COL_EVIDENCIAS_JSON, entity.evidenciasJson) else putNull(COL_EVIDENCIAS_JSON)

                // Paso 3
                put(COL_TIPO_PRUEBA, entity.tipoPrueba)
                put(COL_PRUEBAS_COMPLEMENTARIAS, entity.pruebasComplementarias)
                if (entity.resultadoPruebas != null) put(COL_RESULTADO_PRUEBAS, entity.resultadoPruebas) else putNull(COL_RESULTADO_PRUEBAS)
                if (entity.examenesJson != null) put(COL_EXAMENES_JSON, entity.examenesJson) else putNull(COL_EXAMENES_JSON)
                put(COL_COMPROMISOS, entity.compromisos)
                put(COL_FUNDAMENTO_LEGAL, entity.fundamentoLegal)
                if (entity.plazoDias != null) put(COL_PLAZO_DIAS, entity.plazoDias) else putNull(COL_PLAZO_DIAS)
                put(COL_ESTADO_CIERRE, entity.estadoCierre)
                if (entity.observacion != null) put(COL_OBSERVACION, entity.observacion) else putNull(COL_OBSERVACION)
                put(COL_FUNCIONARIO_NOMBRE, entity.funcionarioNombre)
                put(COL_FUNCIONARIO_CARGO, entity.funcionarioCargo)
                if (entity.funcionarioMatricula != null) put(COL_FUNCIONARIO_MATRICULA, entity.funcionarioMatricula) else putNull(COL_FUNCIONARIO_MATRICULA)
                if (entity.funcionarioIdentificacion != null) put(COL_FUNCIONARIO_IDENTIFICACION, entity.funcionarioIdentificacion) else putNull(COL_FUNCIONARIO_IDENTIFICACION)
                if (entity.notificadoNombre != null) put(COL_NOTIFICADO_NOMBRE, entity.notificadoNombre) else putNull(COL_NOTIFICADO_NOMBRE)
                if (entity.notificadoIdentificacion != null) put(COL_NOTIFICADO_IDENTIFICACION, entity.notificadoIdentificacion) else putNull(COL_NOTIFICADO_IDENTIFICACION)
                if (entity.firmaNotificadorBase64 != null) put(COL_FIRMA_NOTIFICADOR_BASE64, entity.firmaNotificadorBase64) else putNull(COL_FIRMA_NOTIFICADOR_BASE64)
                if (entity.firmaNotificadoBase64 != null) put(COL_FIRMA_NOTIFICADO_BASE64, entity.firmaNotificadoBase64) else putNull(COL_FIRMA_NOTIFICADO_BASE64)

                // Rescate CBA
                put(COL_REQUIERE_RESCATE, if (entity.requiereRescate) 1 else 0)
                put(COL_MOTIVO_RESCATE, entity.motivoRescate)
                put(COL_PERSONA_ATIENDE_RESCATISTA, entity.personaAtiendeRescatista)
                put(COL_PERSONA_ATIENDE_CEDULA, entity.personaAtiendeCedula)
                put(COL_PERSONA_ATIENDE_TELEFONO, entity.personaAtiendeTelefono)
                put(COL_BARRIO_RESCATE, entity.barrioRescate)
                put(COL_TESTIGO1_NOMBRE, entity.testigo1Nombre)
                put(COL_TESTIGO1_CEDULA, entity.testigo1Cedula)
                put(COL_TESTIGO1_TELEFONO, entity.testigo1Telefono)
                put(COL_TESTIGO2_NOMBRE, entity.testigo2Nombre)
                put(COL_TESTIGO2_CEDULA, entity.testigo2Cedula)
                put(COL_TESTIGO2_TELEFONO, entity.testigo2Telefono)
                put(COL_OBSERVACIONES_RESCATE, entity.observacionesRescate)
                put(COL_VETERINARIO_RECEPTOR, entity.veterinarioReceptor)
                put(COL_FECHA_RECEPCION, entity.fechaRecepcion)
                put(COL_HORA_RECEPCION, entity.horaRecepcion)
                put(COL_OBSERVACIONES_RECEPCION, entity.observacionesRecepcion)

                // 19 Switches
                put(COL_SW_RESPIRATORIO_MOCO, if (entity.swRespiratorioMoco) 1 else 0)
                put(COL_SW_RESPIRATORIO_DIFICULTAD, if (entity.swRespiratorioDificultad) 1 else 0)
                put(COL_SW_RESPIRATORIO_TOS, if (entity.swRespiratorioTos) 1 else 0)
                put(COL_SW_RESPIRATORIO_LAGANEO, if (entity.swRespiratorioLaganeo) 1 else 0)
                put(COL_SW_DIGESTIVO_VOMITO, if (entity.swDigestivoVomito) 1 else 0)
                put(COL_SW_DIGESTIVO_DIARREA, if (entity.swDigestivoDiarrea) 1 else 0)
                put(COL_SW_DIGESTIVO_DESNUTRIDO, if (entity.swDigestivoDesnutrido) 1 else 0)
                put(COL_SW_DIGESTIVO_DESHIDRATADO, if (entity.swDigestivoDeshidratado) 1 else 0)
                put(COL_SW_MUSCULO_CAIDA_PATAS, if (entity.swMusculoCaidaPatas) 1 else 0)
                put(COL_SW_MUSCULO_COJO, if (entity.swMusculoCojo) 1 else 0)
                put(COL_SW_MUSCULO_HERIDA_ABIERTA, if (entity.swMusculoHeridaAbierta) 1 else 0)
                put(COL_SW_MUSCULO_GUSANOS, if (entity.swMusculoGusanos) 1 else 0)
                put(COL_SW_MUSCULO_POSTRADO, if (entity.swMusculoPostrado) 1 else 0)
                put(COL_SW_DERMATO_PELADURAS, if (entity.swDermatoPeladuras) 1 else 0)
                put(COL_SW_DERMATO_HERIDAS_SANGRE, if (entity.swDermatoHeridasSangre) 1 else 0)
                put(COL_SW_DERMATO_PULGAS, if (entity.swDermatoPulgas) 1 else 0)
                put(COL_SW_GENITO_MASA_SECRECION, if (entity.swGenitoMasaSecrecion) 1 else 0)
                put(COL_SW_GENITO_GESTANTE, if (entity.swGenitoGestante) 1 else 0)
                put(COL_SW_GENITO_CELO, if (entity.swGenitoCelo) 1 else 0)
            }
        }

        fun fromCursor(cursor: Cursor): ActaLocalEntity {
            val estadoStr = cursor.getStringOrDefault(COL_ESTADO_SINCRONIZACION, EstadoSincronizacionActa.BORRADOR.name)
            val estado = try {
                EstadoSincronizacionActa.valueOf(estadoStr)
            } catch (_: Exception) {
                EstadoSincronizacionActa.BORRADOR
            }

            return ActaLocalEntity(
                idLocal = cursor.getLongOrDefault(COL_ID_LOCAL, 0L),
                idPeticion = cursor.getIntOrDefault(COL_ID_PETICION, 0),
                idVeterinario = cursor.getIntOrDefault(COL_ID_VETERINARIO, 0),
                estadoSincronizacion = estado,
                enviandoDesde = cursor.getLongOrNull(COL_ENVIANDO_DESDE),
                idSeguimientoRemoto = cursor.getIntOrNull(COL_ID_SEGUIMIENTO_REMOTO),
                sincronizacionOffline = cursor.getBooleanOrDefault(COL_SINCRONIZACION_OFFLINE, false),
                reintentos = cursor.getIntOrDefault(COL_REINTENTOS, 0),
                ultimoCodigoHttp = cursor.getIntOrNull(COL_ULTIMO_CODIGO_HTTP),
                ultimoError = cursor.getStringOrNull(COL_ULTIMO_ERROR),
                pasoActual = cursor.getIntOrDefault(COL_PASO_ACTUAL, 1),
                actualizadoEn = cursor.getLongOrDefault(COL_ACTUALIZADO_EN, System.currentTimeMillis()),
                nroRadicadoAtencion = cursor.getStringOrDefault(COL_NRO_RADICADO_ATENCION, ""),

                // Paso 1
                fechaAtencion = cursor.getStringOrDefault(COL_FECHA_ATENCION, ""),
                propietarioNombre = cursor.getStringOrDefault(COL_PROPIETARIO_NOMBRE, ""),
                propietarioCedula = cursor.getStringOrDefault(COL_PROPIETARIO_CEDULA, ""),
                propietarioTelefono = cursor.getStringOrDefault(COL_PROPIETARIO_TELEFONO, ""),
                propietarioEmail = cursor.getStringOrNull(COL_PROPIETARIO_EMAIL),
                propietarioBarrio = cursor.getStringOrDefault(COL_PROPIETARIO_BARRIO, ""),
                propietarioDireccion = cursor.getStringOrDefault(COL_PROPIETARIO_DIRECCION, ""),
                quienReporta = cursor.getStringOrDefault(COL_QUIEN_REPORTA, ""),
                quienReportaOtro = cursor.getStringOrNull(COL_QUIEN_REPORTA_OTRO),
                solicitudAtencionPor = cursor.getStringOrDefault(COL_SOLICITUD_ATENCION_POR, ""),
                lugarAtencion = cursor.getStringOrDefault(COL_LUGAR_ATENCION, ""),
                latitudVisita = cursor.getDoubleOrDefault(COL_LATITUD_VISITA, 0.0),
                longitudVisita = cursor.getDoubleOrDefault(COL_LONGITUD_VISITA, 0.0),

                // Paso 2
                animalesJson = cursor.getStringOrNull(COL_ANIMALES_JSON),
                desparasitacion = cursor.getBooleanOrDefault(COL_DESPARASITACION, false),
                frecuenciaCardiaca = cursor.getStringOrDefault(COL_FRECUENCIA_CARDIACA, ""),
                frecuenciaRespiratoria = cursor.getStringOrDefault(COL_FRECUENCIA_RESPIRATORIA, ""),
                mucosas = cursor.getStringOrDefault(COL_MUCOSAS, ""),
                anamnesis = cursor.getStringOrDefault(COL_ANAMNESIS, ""),
                tratamientoRealizado = cursor.getStringOrDefault(COL_TRATAMIENTO_REALIZADO, ""),
                medicamentosJson = cursor.getStringOrNull(COL_MEDICAMENTOS_JSON),
                evidenciasJson = cursor.getStringOrNull(COL_EVIDENCIAS_JSON),

                // Paso 3
                tipoPrueba = cursor.getStringOrDefault(COL_TIPO_PRUEBA, ""),
                pruebasComplementarias = cursor.getStringOrDefault(COL_PRUEBAS_COMPLEMENTARIAS, ""),
                resultadoPruebas = cursor.getStringOrNull(COL_RESULTADO_PRUEBAS),
                examenesJson = cursor.getStringOrNull(COL_EXAMENES_JSON),
                compromisos = cursor.getStringOrDefault(COL_COMPROMISOS, ""),
                fundamentoLegal = cursor.getStringOrDefault(COL_FUNDAMENTO_LEGAL, "Ley 1774 de 2016"),
                plazoDias = cursor.getIntOrNull(COL_PLAZO_DIAS),
                estadoCierre = cursor.getStringOrDefault(COL_ESTADO_CIERRE, "En tratamiento"),
                observacion = cursor.getStringOrNull(COL_OBSERVACION),
                funcionarioNombre = cursor.getStringOrDefault(COL_FUNCIONARIO_NOMBRE, ""),
                funcionarioCargo = cursor.getStringOrDefault(COL_FUNCIONARIO_CARGO, ""),
                funcionarioMatricula = cursor.getStringOrNull(COL_FUNCIONARIO_MATRICULA),
                funcionarioIdentificacion = cursor.getStringOrNull(COL_FUNCIONARIO_IDENTIFICACION),
                notificadoNombre = cursor.getStringOrNull(COL_NOTIFICADO_NOMBRE),
                notificadoIdentificacion = cursor.getStringOrNull(COL_NOTIFICADO_IDENTIFICACION),
                firmaNotificadorBase64 = cursor.getStringOrNull(COL_FIRMA_NOTIFICADOR_BASE64),
                firmaNotificadoBase64 = cursor.getStringOrNull(COL_FIRMA_NOTIFICADO_BASE64),

                // Rescate CBA
                requiereRescate = cursor.getBooleanOrDefault(COL_REQUIERE_RESCATE, false),
                motivoRescate = cursor.getStringOrDefault(COL_MOTIVO_RESCATE, ""),
                personaAtiendeRescatista = cursor.getStringOrDefault(COL_PERSONA_ATIENDE_RESCATISTA, ""),
                personaAtiendeCedula = cursor.getStringOrDefault(COL_PERSONA_ATIENDE_CEDULA, ""),
                personaAtiendeTelefono = cursor.getStringOrDefault(COL_PERSONA_ATIENDE_TELEFONO, ""),
                barrioRescate = cursor.getStringOrDefault(COL_BARRIO_RESCATE, ""),
                testigo1Nombre = cursor.getStringOrDefault(COL_TESTIGO1_NOMBRE, ""),
                testigo1Cedula = cursor.getStringOrDefault(COL_TESTIGO1_CEDULA, ""),
                testigo1Telefono = cursor.getStringOrDefault(COL_TESTIGO1_TELEFONO, ""),
                testigo2Nombre = cursor.getStringOrDefault(COL_TESTIGO2_NOMBRE, ""),
                testigo2Cedula = cursor.getStringOrDefault(COL_TESTIGO2_CEDULA, ""),
                testigo2Telefono = cursor.getStringOrDefault(COL_TESTIGO2_TELEFONO, ""),
                observacionesRescate = cursor.getStringOrDefault(COL_OBSERVACIONES_RESCATE, ""),
                veterinarioReceptor = cursor.getStringOrDefault(COL_VETERINARIO_RECEPTOR, ""),
                fechaRecepcion = cursor.getStringOrDefault(COL_FECHA_RECEPCION, ""),
                horaRecepcion = cursor.getStringOrDefault(COL_HORA_RECEPCION, ""),
                observacionesRecepcion = cursor.getStringOrDefault(COL_OBSERVACIONES_RECEPCION, ""),

                // 19 Switches
                swRespiratorioMoco = cursor.getBooleanOrDefault(COL_SW_RESPIRATORIO_MOCO, false),
                swRespiratorioDificultad = cursor.getBooleanOrDefault(COL_SW_RESPIRATORIO_DIFICULTAD, false),
                swRespiratorioTos = cursor.getBooleanOrDefault(COL_SW_RESPIRATORIO_TOS, false),
                swRespiratorioLaganeo = cursor.getBooleanOrDefault(COL_SW_RESPIRATORIO_LAGANEO, false),
                swDigestivoVomito = cursor.getBooleanOrDefault(COL_SW_DIGESTIVO_VOMITO, false),
                swDigestivoDiarrea = cursor.getBooleanOrDefault(COL_SW_DIGESTIVO_DIARREA, false),
                swDigestivoDesnutrido = cursor.getBooleanOrDefault(COL_SW_DIGESTIVO_DESNUTRIDO, false),
                swDigestivoDeshidratado = cursor.getBooleanOrDefault(COL_SW_DIGESTIVO_DESHIDRATADO, false),
                swMusculoCaidaPatas = cursor.getBooleanOrDefault(COL_SW_MUSCULO_CAIDA_PATAS, false),
                swMusculoCojo = cursor.getBooleanOrDefault(COL_SW_MUSCULO_COJO, false),
                swMusculoHeridaAbierta = cursor.getBooleanOrDefault(COL_SW_MUSCULO_HERIDA_ABIERTA, false),
                swMusculoGusanos = cursor.getBooleanOrDefault(COL_SW_MUSCULO_GUSANOS, false),
                swMusculoPostrado = cursor.getBooleanOrDefault(COL_SW_MUSCULO_POSTRADO, false),
                swDermatoPeladuras = cursor.getBooleanOrDefault(COL_SW_DERMATO_PELADURAS, false),
                swDermatoHeridasSangre = cursor.getBooleanOrDefault(COL_SW_DERMATO_HERIDAS_SANGRE, false),
                swDermatoPulgas = cursor.getBooleanOrDefault(COL_SW_DERMATO_PULGAS, false),
                swGenitoMasaSecrecion = cursor.getBooleanOrDefault(COL_SW_GENITO_MASA_SECRECION, false),
                swGenitoGestante = cursor.getBooleanOrDefault(COL_SW_GENITO_GESTANTE, false),
                swGenitoCelo = cursor.getBooleanOrDefault(COL_SW_GENITO_CELO, false)
            )
        }

        private fun Cursor.getStringOrNull(col: String): String? {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getString(idx) else null
        }

        private fun Cursor.getStringOrDefault(col: String, default: String = ""): String {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getString(idx) else default
        }

        private fun Cursor.getIntOrNull(col: String): Int? {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getInt(idx) else null
        }

        private fun Cursor.getIntOrDefault(col: String, default: Int = 0): Int {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
        }

        private fun Cursor.getLongOrNull(col: String): Long? {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
        }

        private fun Cursor.getLongOrDefault(col: String, default: Long = 0L): Long {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
        }

        private fun Cursor.getDoubleOrDefault(col: String, default: Double = 0.0): Double {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getDouble(idx) else default
        }

        private fun Cursor.getBooleanOrDefault(col: String, default: Boolean = false): Boolean {
            val idx = getColumnIndex(col)
            return if (idx >= 0 && !isNull(idx)) getInt(idx) == 1 else default
        }
    }
}
