package com.example.petvetericano.offline

import android.content.ContentValues
import android.content.Context

/**
 * Repositorio de acceso a datos para las actas guardadas localmente en SQLite.
 * Garantiza que todas las operaciones de escritura se ejecuten dentro de transacciones atómicas
 * gestionadas mediante beginTransaction() y setTransactionSuccessful().
 */
class ActaLocalRepository(private val dbHelper: ActaLocalDbHelper) {

    constructor(context: Context) : this(ActaLocalDbHelper.getInstance(context))

    companion object {
        @Volatile
        private var instance: ActaLocalRepository? = null

        fun getInstance(context: Context): ActaLocalRepository {
            return instance ?: synchronized(this) {
                instance ?: ActaLocalRepository(ActaLocalDbHelper.getInstance(context)).also { instance = it }
            }
        }
    }

    /**
     * Obtiene el acta guardada para una petición y un veterinario específico.
     */
    fun obtenerPorPeticionYVeterinario(idPeticion: Int, idVeterinario: Int): ActaLocalEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ActaLocalDbHelper.TABLE_ACTAS,
            null,
            "${ActaLocalDbHelper.COL_ID_PETICION} = ? AND ${ActaLocalDbHelper.COL_ID_VETERINARIO} = ?",
            arrayOf(idPeticion.toString(), idVeterinario.toString()),
            null,
            null,
            null,
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) {
                ActaLocalDbHelper.fromCursor(it)
            } else {
                null
            }
        }
    }

    /**
     * Obtiene un acta específica por su clave primaria local (id_local).
     */
    fun obtenerPorIdLocal(idLocal: Long): ActaLocalEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ActaLocalDbHelper.TABLE_ACTAS,
            null,
            "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
            arrayOf(idLocal.toString()),
            null,
            null,
            null,
            "1"
        )
        return cursor.use {
            if (it.moveToFirst()) {
                ActaLocalDbHelper.fromCursor(it)
            } else {
                null
            }
        }
    }

    /**
     * Guarda o actualiza un borrador localmente en la base de datos.
     * Si la fila ya existe para la petición y veterinario, se actualiza conservando el id_local.
     * Retorna el idLocal resultante.
     */
    fun guardarBorrador(entity: ActaLocalEntity): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            var existingId = if (entity.idLocal > 0L) entity.idLocal else null
            if (existingId == null) {
                val cursor = db.query(
                    ActaLocalDbHelper.TABLE_ACTAS,
                    arrayOf(ActaLocalDbHelper.COL_ID_LOCAL),
                    "${ActaLocalDbHelper.COL_ID_PETICION} = ? AND ${ActaLocalDbHelper.COL_ID_VETERINARIO} = ?",
                    arrayOf(entity.idPeticion.toString(), entity.idVeterinario.toString()),
                    null,
                    null,
                    null,
                    "1"
                )
                cursor.use {
                    if (it.moveToFirst()) {
                        existingId = it.getLong(0)
                    }
                }
            }

            val cv = ActaLocalDbHelper.toContentValues(
                entity.copy(
                    idLocal = existingId ?: 0L,
                    actualizadoEn = System.currentTimeMillis()
                )
            )
            cv.remove(ActaLocalDbHelper.COL_ID_LOCAL)

            val finalId: Long
            if (existingId != null && existingId > 0L) {
                db.update(
                    ActaLocalDbHelper.TABLE_ACTAS,
                    cv,
                    "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                    arrayOf(existingId.toString())
                )
                finalId = existingId
            } else {
                finalId = db.insert(ActaLocalDbHelper.TABLE_ACTAS, null, cv)
            }
            db.setTransactionSuccessful()
            finalId
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Elimina el registro local por su id_local.
     */
    fun eliminar(idLocal: Long): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val filas = db.delete(
                ActaLocalDbHelper.TABLE_ACTAS,
                "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Intenta adquirir un lease exclusivo de envío pasando el estado a ENVIANDO.
     * Solo tiene éxito si el estado actual es PENDIENTE_ENVIO, ERROR_REINTENTABLE,
     * o ENVIANDO cuyo lease haya expirado (enviando_desde es nulo o superó leaseTimeoutMs).
     */
    fun intentarAdquirirLeaseEnvio(
        idLocal: Long,
        ahoraMs: Long = System.currentTimeMillis(),
        leaseTimeoutMs: Long = 600_000L
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val limiteVencimiento = ahoraMs - leaseTimeoutMs
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION, EstadoSincronizacionActa.ENVIANDO.name)
                put(ActaLocalDbHelper.COL_ENVIANDO_DESDE, ahoraMs)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, ahoraMs)
            }
            val whereClause = """
                ${ActaLocalDbHelper.COL_ID_LOCAL} = ? AND (
                    ${ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION} IN (?, ?) OR (
                        ${ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION} = ? AND (
                            ${ActaLocalDbHelper.COL_ENVIANDO_DESDE} IS NULL OR 
                            ${ActaLocalDbHelper.COL_ENVIANDO_DESDE} <= ?
                        )
                    )
                )
            """.trimIndent()
            val whereArgs = arrayOf(
                idLocal.toString(),
                EstadoSincronizacionActa.PENDIENTE_ENVIO.name,
                EstadoSincronizacionActa.ERROR_REINTENTABLE.name,
                EstadoSincronizacionActa.ENVIANDO.name,
                limiteVencimiento.toString()
            )
            val filasAfectadas = db.update(ActaLocalDbHelper.TABLE_ACTAS, cv, whereClause, whereArgs)
            val adquirido = filasAfectadas > 0
            if (adquirido) {
                db.setTransactionSuccessful()
            }
            adquirido
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Recupera todos los leases vencidos en estado ENVIANDO devolviéndolos a ERROR_REINTENTABLE.
     */
    fun recuperarLeasesVencidos(
        ahoraMs: Long = System.currentTimeMillis(),
        leaseTimeoutMs: Long = 600_000L
    ): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val limiteVencimiento = ahoraMs - leaseTimeoutMs
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION, EstadoSincronizacionActa.ERROR_REINTENTABLE.name)
                putNull(ActaLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, ahoraMs)
            }
            val whereClause = """
                ${ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION} = ? AND (
                    ${ActaLocalDbHelper.COL_ENVIANDO_DESDE} IS NULL OR 
                    ${ActaLocalDbHelper.COL_ENVIANDO_DESDE} <= ?
                )
            """.trimIndent()
            val whereArgs = arrayOf(
                EstadoSincronizacionActa.ENVIANDO.name,
                limiteVencimiento.toString()
            )
            val recuperados = db.update(ActaLocalDbHelper.TABLE_ACTAS, cv, whereClause, whereArgs)
            db.setTransactionSuccessful()
            recuperados
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Actualiza el JSON de evidencias con las rutas locales y las URLs de Cloudinary obtenidas.
     */
    fun actualizarEvidenciasJson(idLocal: Long, evidenciasJson: String): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_EVIDENCIAS_JSON, evidenciasJson)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ActaLocalDbHelper.TABLE_ACTAS,
                cv,
                "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Guarda el ID remoto del seguimiento asignado por el backend al crear el acta.
     */
    fun guardarIdSeguimientoRemoto(idLocal: Long, idSeguimiento: Int): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_ID_SEGUIMIENTO_REMOTO, idSeguimiento)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ActaLocalDbHelper.TABLE_ACTAS,
                cv,
                "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Actualiza el estado de sincronización, código HTTP y último mensaje de error.
     */
    fun marcarEstado(
        idLocal: Long,
        estado: EstadoSincronizacionActa,
        codigoHttp: Int? = null,
        error: String? = null
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION, estado.name)
                if (codigoHttp != null) {
                    put(ActaLocalDbHelper.COL_ULTIMO_CODIGO_HTTP, codigoHttp)
                } else {
                    putNull(ActaLocalDbHelper.COL_ULTIMO_CODIGO_HTTP)
                }
                if (error != null) {
                    put(ActaLocalDbHelper.COL_ULTIMO_ERROR, error)
                } else {
                    putNull(ActaLocalDbHelper.COL_ULTIMO_ERROR)
                }
                if (estado != EstadoSincronizacionActa.ENVIANDO) {
                    putNull(ActaLocalDbHelper.COL_ENVIANDO_DESDE)
                }
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ActaLocalDbHelper.TABLE_ACTAS,
                cv,
                "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Incrementa en 1 el contador de reintentos del acta.
     * Retorna el nuevo número de reintentos.
     */
    fun incrementarReintentos(idLocal: Long): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            var nuevoReintentos = 1
            val cursor = db.rawQuery(
                "SELECT ${ActaLocalDbHelper.COL_REINTENTOS} FROM ${ActaLocalDbHelper.TABLE_ACTAS} WHERE ${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    nuevoReintentos = it.getInt(0) + 1
                }
            }
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_REINTENTOS, nuevoReintentos)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            db.update(
                ActaLocalDbHelper.TABLE_ACTAS,
                cv,
                "${ActaLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            nuevoReintentos
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Restablece las actas que fallaron por sesión expirada (REQUIERE_ATENCION con 401)
     * a PENDIENTE_ENVIO tras un nuevo inicio de sesión.
     */
    fun resetAuthErrorsToPending(idVeterinario: Int): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION, EstadoSincronizacionActa.PENDIENTE_ENVIO.name)
                putNull(ActaLocalDbHelper.COL_ULTIMO_CODIGO_HTTP)
                putNull(ActaLocalDbHelper.COL_ULTIMO_ERROR)
                putNull(ActaLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ActaLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ActaLocalDbHelper.TABLE_ACTAS,
                cv,
                "${ActaLocalDbHelper.COL_ID_VETERINARIO} = ? AND ${ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION} = ? AND ${ActaLocalDbHelper.COL_ULTIMO_CODIGO_HTTP} = 401",
                arrayOf(idVeterinario.toString(), EstadoSincronizacionActa.REQUIERE_ATENCION.name)
            )
            db.setTransactionSuccessful()
            filas
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retorna todas las actas pendientes de sincronización para un veterinario
     * (estados PENDIENTE_ENVIO, ERROR_REINTENTABLE y ENVIANDO), ordenadas por antigüedad.
     */
    fun obtenerPendientes(idVeterinario: Int): List<ActaLocalEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ActaLocalDbHelper.TABLE_ACTAS,
            null,
            "${ActaLocalDbHelper.COL_ID_VETERINARIO} = ? AND ${ActaLocalDbHelper.COL_ESTADO_SINCRONIZACION} IN (?, ?, ?)",
            arrayOf(
                idVeterinario.toString(),
                EstadoSincronizacionActa.PENDIENTE_ENVIO.name,
                EstadoSincronizacionActa.ERROR_REINTENTABLE.name,
                EstadoSincronizacionActa.ENVIANDO.name
            ),
            null,
            null,
            "${ActaLocalDbHelper.COL_ACTUALIZADO_EN} ASC"
        )
        val lista = mutableListOf<ActaLocalEntity>()
        cursor.use {
            while (it.moveToNext()) {
                lista.add(ActaLocalDbHelper.fromCursor(it))
            }
        }
        return lista
    }
}
