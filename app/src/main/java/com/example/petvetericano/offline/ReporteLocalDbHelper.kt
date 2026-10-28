package com.example.petvetericano.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLiteOpenHelper para la base de datos local de reportes ciudadanos offline.
 * Nombre del archivo: petvetericano_reportes_offline.db (aislado de la base de datos de actas).
 * Implementa patrón singleton thread-safe con el applicationContext y WAL activado.
 */
class ReporteLocalDbHelper private constructor(context: Context) : SQLiteOpenHelper(
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
        val sql = """
            CREATE TABLE $TABLE_REPORTES (
                $COL_ID_LOCAL INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_UUID TEXT UNIQUE NOT NULL,
                $COL_ID_USUARIO INTEGER NOT NULL,
                $COL_ID_TIPO INTEGER NOT NULL,
                $COL_TIPO_NOMBRE TEXT,
                $COL_DESCRIPCION TEXT NOT NULL,
                $COL_LATITUD TEXT NOT NULL,
                $COL_LONGITUD TEXT NOT NULL,
                $COL_DIRECCION TEXT,
                $COL_EVIDENCIAS_JSON TEXT,
                $COL_ESTADO TEXT NOT NULL,
                $COL_CREADO_EN INTEGER,
                $COL_ACTUALIZADO_EN INTEGER,
                $COL_ENVIANDO_DESDE INTEGER,
                $COL_POST_INICIADO_EN INTEGER,
                $COL_REINTENTOS INTEGER DEFAULT 0,
                $COL_CODIGO_ERROR INTEGER,
                $COL_ID_PETICION_REMOTA INTEGER,
                $COL_NUMERO_RADICADO TEXT,
                $COL_ENVIADO_EN INTEGER
            )
        """.trimIndent()
        db.execSQL(sql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reportes_usuario_estado ON $TABLE_REPORTES($COL_ID_USUARIO, $COL_ESTADO)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // En futuras versiones incrementales se aplican migraciones con ALTER TABLE
    }

    companion object {
        const val DATABASE_NAME = "petvetericano_reportes_offline.db"
        const val DATABASE_VERSION = 1

        const val TABLE_REPORTES = "reportes_locales"

        const val COL_ID_LOCAL = "id_local"
        const val COL_UUID = "uuid"
        const val COL_ID_USUARIO = "id_usuario"
        const val COL_ID_TIPO = "id_tipo"
        const val COL_TIPO_NOMBRE = "tipo_nombre"
        const val COL_DESCRIPCION = "descripcion"
        const val COL_LATITUD = "latitud"
        const val COL_LONGITUD = "longitud"
        const val COL_DIRECCION = "direccion"
        const val COL_EVIDENCIAS_JSON = "evidencias_json"
        const val COL_ESTADO = "estado"
        const val COL_CREADO_EN = "creado_en"
        const val COL_ACTUALIZADO_EN = "actualizado_en"
        const val COL_ENVIANDO_DESDE = "enviando_desde"
        const val COL_POST_INICIADO_EN = "post_iniciado_en"
        const val COL_REINTENTOS = "reintentos"
        const val COL_CODIGO_ERROR = "codigo_error"
        const val COL_ID_PETICION_REMOTA = "id_peticion_remota"
        const val COL_NUMERO_RADICADO = "numero_radicado"
        const val COL_ENVIADO_EN = "enviado_en"

        @Volatile
        private var instance: ReporteLocalDbHelper? = null

        fun getInstance(context: Context): ReporteLocalDbHelper {
            return instance ?: synchronized(this) {
                instance ?: ReporteLocalDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

