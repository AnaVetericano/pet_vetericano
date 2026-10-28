package com.example.petvetericano.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Estados del ciclo de vida de un reporte ciudadano local.
 */
enum class EstadoReporteLocal {
    PENDIENTE,
    ENVIANDO,
    ERROR_REINTENTABLE,
    REQUIERE_ATENCION,
    ENVIADO
}

/**
 * Representa una evidencia multimedia local y su correspondiente URL en Cloudinary una vez subida.
 */
data class EvidenciaReporteItem(
    val path: String,
    val esVideo: Boolean,
    val urlRemota: String? = null
)

/**
 * Entidad que modela un registro de la tabla [reportes_locales].
 */
data class ReporteLocalEntity(
    val idLocal: Long = 0L,
    val uuid: String = "",
    val idUsuario: Int,
    val idTipo: Int,
    val tipoNombre: String? = null,
    val descripcion: String,
    val latitud: String,
    val longitud: String,
    val direccion: String? = null,
    val evidenciasJson: String? = null,
    val estado: EstadoReporteLocal = EstadoReporteLocal.PENDIENTE,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis(),
    val enviandoDesde: Long? = null,
    val postIniciadoEn: Long? = null,
    val reintentos: Int = 0,
    val codigoError: Int? = null,
    val idPeticionRemota: Int? = null,
    val numeroRadicado: String? = null,
    val enviadoEn: Long? = null
)

/**
 * Parser bidireccional seguro para la columna evidencias_json.
 */
object ReporteEvidenciasParser {
    private const val TAG = "ReporteEvidenciasParser"

    fun parsear(json: String?): List<EvidenciaReporteItem> {
        if (json.isNullOrBlank()) return emptyList()
        val resultado = mutableListOf<EvidenciaReporteItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.optString("path", "")
                val esVideo = obj.optBoolean("esVideo", false)
                val urlRemota = if (obj.has("urlRemota") && !obj.isNull("urlRemota")) {
                    obj.optString("urlRemota").takeIf { it.isNotBlank() }
                } else {
                    null
                }
                if (path.isNotBlank()) {
                    resultado.add(EvidenciaReporteItem(path, esVideo, urlRemota))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando evidencias JSON: ${e.message}")
        }
        return resultado
    }

    fun serializar(lista: List<EvidenciaReporteItem>): String {
        val array = JSONArray()
        for (item in lista) {
            val obj = JSONObject().apply {
                put("path", item.path)
                put("esVideo", item.esVideo)
                if (!item.urlRemota.isNullOrBlank()) {
                    put("urlRemota", item.urlRemota)
                } else {
                    put("urlRemota", JSONObject.NULL)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }
}

/**
 * Repositorio para la persistencia y ciclo de vida de los reportes ciudadanos en SQLite.
 * Thread-safe, transaccional y singleton por context.
 */
class ReporteLocalRepository private constructor(context: Context) {

    private val dbHelper = ReporteLocalDbHelper.getInstance(context)

    companion object {
        private const val TAG = "ReporteLocalRepo"

        @Volatile
        private var instance: ReporteLocalRepository? = null

        fun getInstance(context: Context): ReporteLocalRepository {
            return instance ?: synchronized(this) {
                instance ?: ReporteLocalRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Inserta un nuevo reporte en estado PENDIENTE. Si no trae UUID, lo genera automáticamente.
     * Retorna el idLocal generado.
     */
    fun insertar(entity: ReporteLocalEntity): Long {
        val uuidFinal = if (entity.uuid.isBlank()) UUID.randomUUID().toString() else entity.uuid
        val ahora = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_UUID, uuidFinal)
                put(ReporteLocalDbHelper.COL_ID_USUARIO, entity.idUsuario)
                put(ReporteLocalDbHelper.COL_ID_TIPO, entity.idTipo)
                put(ReporteLocalDbHelper.COL_TIPO_NOMBRE, entity.tipoNombre)
                put(ReporteLocalDbHelper.COL_DESCRIPCION, entity.descripcion)
                put(ReporteLocalDbHelper.COL_LATITUD, entity.latitud)
                put(ReporteLocalDbHelper.COL_LONGITUD, entity.longitud)
                put(ReporteLocalDbHelper.COL_DIRECCION, entity.direccion)
                put(ReporteLocalDbHelper.COL_EVIDENCIAS_JSON, entity.evidenciasJson)
                put(ReporteLocalDbHelper.COL_ESTADO, entity.estado.name)
                put(ReporteLocalDbHelper.COL_CREADO_EN, if (entity.creadoEn > 0) entity.creadoEn else ahora)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahora)
                if (entity.enviandoDesde != null) put(ReporteLocalDbHelper.COL_ENVIANDO_DESDE, entity.enviandoDesde) else putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                if (entity.postIniciadoEn != null) put(ReporteLocalDbHelper.COL_POST_INICIADO_EN, entity.postIniciadoEn) else putNull(ReporteLocalDbHelper.COL_POST_INICIADO_EN)
                put(ReporteLocalDbHelper.COL_REINTENTOS, entity.reintentos)
                if (entity.codigoError != null) put(ReporteLocalDbHelper.COL_CODIGO_ERROR, entity.codigoError) else putNull(ReporteLocalDbHelper.COL_CODIGO_ERROR)
                if (entity.idPeticionRemota != null) put(ReporteLocalDbHelper.COL_ID_PETICION_REMOTA, entity.idPeticionRemota) else putNull(ReporteLocalDbHelper.COL_ID_PETICION_REMOTA)
                put(ReporteLocalDbHelper.COL_NUMERO_RADICADO, entity.numeroRadicado)
                if (entity.enviadoEn != null) put(ReporteLocalDbHelper.COL_ENVIADO_EN, entity.enviadoEn) else putNull(ReporteLocalDbHelper.COL_ENVIADO_EN)
            }
            val id = db.insertOrThrow(ReporteLocalDbHelper.TABLE_REPORTES, null, cv)
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Obtiene un reporte local por su PK id_local.
     */
    fun obtenerPorId(idLocal: Long): ReporteLocalEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ReporteLocalDbHelper.TABLE_REPORTES,
            null,
            "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
            arrayOf(idLocal.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) mapearEntidad(it) else null
        }
    }

    /**
     * Obtiene un reporte local por su UUID único.
     */
    fun obtenerPorUuid(uuid: String): ReporteLocalEntity? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ReporteLocalDbHelper.TABLE_REPORTES,
            null,
            "${ReporteLocalDbHelper.COL_UUID} = ?",
            arrayOf(uuid),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) mapearEntidad(it) else null
        }
    }

    /**
     * Lista todos los reportes de un usuario ordenados de más reciente a más antiguo.
     */
    fun listarPorUsuario(idUsuario: Int): List<ReporteLocalEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ReporteLocalDbHelper.TABLE_REPORTES,
            null,
            "${ReporteLocalDbHelper.COL_ID_USUARIO} = ?",
            arrayOf(idUsuario.toString()),
            null,
            null,
            "${ReporteLocalDbHelper.COL_CREADO_EN} DESC, ${ReporteLocalDbHelper.COL_ID_LOCAL} DESC"
        )
        val lista = mutableListOf<ReporteLocalEntity>()
        cursor.use {
            while (it.moveToNext()) {
                lista.add(mapearEntidad(it))
            }
        }
        return lista
    }

    /**
     * Lista reportes pendientes de sincronización para un usuario específico (PENDIENTE, ERROR_REINTENTABLE, ENVIANDO).
     */
    fun obtenerPendientesPorUsuario(idUsuario: Int): List<ReporteLocalEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            ReporteLocalDbHelper.TABLE_REPORTES,
            null,
            "${ReporteLocalDbHelper.COL_ID_USUARIO} = ? AND ${ReporteLocalDbHelper.COL_ESTADO} IN (?, ?, ?)",
            arrayOf(
                idUsuario.toString(),
                EstadoReporteLocal.PENDIENTE.name,
                EstadoReporteLocal.ERROR_REINTENTABLE.name,
                EstadoReporteLocal.ENVIANDO.name
            ),
            null,
            null,
            "${ReporteLocalDbHelper.COL_CREADO_EN} ASC"
        )
        val lista = mutableListOf<ReporteLocalEntity>()
        cursor.use {
            while (it.moveToNext()) {
                lista.add(mapearEntidad(it))
            }
        }
        return lista
    }

    /**
     * Actualiza directamente el JSON completo de evidencias.
     */
    fun actualizarEvidenciasJson(idLocal: Long, evidenciasJson: String): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_EVIDENCIAS_JSON, evidenciasJson)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Guarda la urlRemota de una evidencia apenas se sube exitosamente a Cloudinary.
     */
    fun guardarUrlRemotaEvidencia(idLocal: Long, path: String, urlRemota: String): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val entity = obtenerPorId(idLocal) ?: return false
            val evidencias = ReporteEvidenciasParser.parsear(entity.evidenciasJson).toMutableList()
            var modificada = false
            for (i in evidencias.indices) {
                if (evidencias[i].path == path) {
                    evidencias[i] = evidencias[i].copy(urlRemota = urlRemota)
                    modificada = true
                    break
                }
            }
            if (!modificada) return false
            val nuevoJson = ReporteEvidenciasParser.serializar(evidencias)
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_EVIDENCIAS_JSON, nuevoJson)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, System.currentTimeMillis())
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Registra la marca de tiempo de inicio del intento de POST.
     */
    fun marcarPostIniciado(idLocal: Long, ahoraMs: Long = System.currentTimeMillis()): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_POST_INICIADO_EN, ahoraMs)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahoraMs)
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Marca el reporte como ENVIADO tras recibir 2xx o confirmarse en el servidor.
     */
    fun marcarEnviado(
        idLocal: Long,
        idPeticionRemota: Int,
        numeroRadicado: String?,
        enviadoEnMs: Long = System.currentTimeMillis()
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_ESTADO, EstadoReporteLocal.ENVIADO.name)
                put(ReporteLocalDbHelper.COL_ID_PETICION_REMOTA, idPeticionRemota)
                put(ReporteLocalDbHelper.COL_NUMERO_RADICADO, numeroRadicado)
                put(ReporteLocalDbHelper.COL_ENVIADO_EN, enviadoEnMs)
                putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                putNull(ReporteLocalDbHelper.COL_CODIGO_ERROR)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, enviadoEnMs)
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Actualiza el estado a ERROR_REINTENTABLE o REQUIERE_ATENCION, actualizando código de error y reintentos.
     */
    fun marcarError(
        idLocal: Long,
        estado: EstadoReporteLocal,
        codigoError: Int? = null,
        reintentos: Int? = null
    ): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val ahora = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_ESTADO, estado.name)
                if (codigoError != null) {
                    put(ReporteLocalDbHelper.COL_CODIGO_ERROR, codigoError)
                } else {
                    putNull(ReporteLocalDbHelper.COL_CODIGO_ERROR)
                }
                if (reintentos != null) {
                    put(ReporteLocalDbHelper.COL_REINTENTOS, reintentos)
                }
                putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahora)
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Devuelve el reporte al estado PENDIENTE liberando el lease (usado si el usuario logueado no coincide).
     */
    fun devolverAPendiente(idLocal: Long): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val ahora = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_ESTADO, EstadoReporteLocal.PENDIENTE.name)
                putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahora)
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Incrementa el contador de reintentos y marca estado ERROR_REINTENTABLE.
     */
    fun incrementarReintentos(idLocal: Long, codigoError: Int? = null): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            val actual = obtenerPorId(idLocal) ?: return false
            val nuevosReintentos = actual.reintentos + 1
            val ahora = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put(ReporteLocalDbHelper.COL_ESTADO, EstadoReporteLocal.ERROR_REINTENTABLE.name)
                put(ReporteLocalDbHelper.COL_REINTENTOS, nuevosReintentos)
                if (codigoError != null) put(ReporteLocalDbHelper.COL_CODIGO_ERROR, codigoError) else putNull(ReporteLocalDbHelper.COL_CODIGO_ERROR)
                putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahora)
            }
            val filas = db.update(
                ReporteLocalDbHelper.TABLE_REPORTES,
                cv,
                "${ReporteLocalDbHelper.COL_ID_LOCAL} = ?",
                arrayOf(idLocal.toString())
            )
            db.setTransactionSuccessful()
            filas > 0
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Intenta adquirir de manera atómica el lease de envío (cambio a ENVIANDO).
     * Solo tiene éxito si el estado actual es PENDIENTE, ERROR_REINTENTABLE, o ENVIANDO con lease vencido (> 10 min).
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
                put(ReporteLocalDbHelper.COL_ESTADO, EstadoReporteLocal.ENVIANDO.name)
                put(ReporteLocalDbHelper.COL_ENVIANDO_DESDE, ahoraMs)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahoraMs)
            }
            val whereClause = """
                ${ReporteLocalDbHelper.COL_ID_LOCAL} = ? AND (
                    ${ReporteLocalDbHelper.COL_ESTADO} IN (?, ?) OR (
                        ${ReporteLocalDbHelper.COL_ESTADO} = ? AND (
                            ${ReporteLocalDbHelper.COL_ENVIANDO_DESDE} IS NULL OR 
                            ${ReporteLocalDbHelper.COL_ENVIANDO_DESDE} <= ?
                        )
                    )
                )
            """.trimIndent()
            val whereArgs = arrayOf(
                idLocal.toString(),
                EstadoReporteLocal.PENDIENTE.name,
                EstadoReporteLocal.ERROR_REINTENTABLE.name,
                EstadoReporteLocal.ENVIANDO.name,
                limiteVencimiento.toString()
            )
            val filasAfectadas = db.update(ReporteLocalDbHelper.TABLE_REPORTES, cv, whereClause, whereArgs)
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
                put(ReporteLocalDbHelper.COL_ESTADO, EstadoReporteLocal.ERROR_REINTENTABLE.name)
                putNull(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
                put(ReporteLocalDbHelper.COL_ACTUALIZADO_EN, ahoraMs)
            }
            val whereClause = """
                ${ReporteLocalDbHelper.COL_ESTADO} = ? AND (
                    ${ReporteLocalDbHelper.COL_ENVIANDO_DESDE} IS NULL OR 
                    ${ReporteLocalDbHelper.COL_ENVIANDO_DESDE} <= ?
                )
            """.trimIndent()
            val whereArgs = arrayOf(
                EstadoReporteLocal.ENVIANDO.name,
                limiteVencimiento.toString()
            )
            val recuperados = db.update(ReporteLocalDbHelper.TABLE_REPORTES, cv, whereClause, whereArgs)
            db.setTransactionSuccessful()
            recuperados
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Obtiene el conjunto de IDs remotos de peticiones ya vinculados en la base de datos local.
     */
    fun idsRemotosYaEnlazados(): Set<Int> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            true,
            ReporteLocalDbHelper.TABLE_REPORTES,
            arrayOf(ReporteLocalDbHelper.COL_ID_PETICION_REMOTA),
            "${ReporteLocalDbHelper.COL_ID_PETICION_REMOTA} IS NOT NULL",
            null,
            null,
            null,
            null,
            null
        )
        val resultado = mutableSetOf<Int>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getInt(0)
                if (id > 0) {
                    resultado.add(id)
                }
            }
        }
        return resultado
    }

    private fun mapearEntidad(cursor: Cursor): ReporteLocalEntity {
        val idLocal = cursor.getLong(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ID_LOCAL))
        val uuid = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_UUID)) ?: ""
        val idUsuario = cursor.getInt(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ID_USUARIO))
        val idTipo = cursor.getInt(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ID_TIPO))
        val tipoNombre = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_TIPO_NOMBRE))
        val descripcion = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_DESCRIPCION)) ?: ""
        val latitud = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_LATITUD)) ?: ""
        val longitud = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_LONGITUD)) ?: ""
        val direccion = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_DIRECCION))
        val evidenciasJson = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_EVIDENCIAS_JSON))
        val estadoStr = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ESTADO)) ?: EstadoReporteLocal.PENDIENTE.name
        val estado = try {
            EstadoReporteLocal.valueOf(estadoStr)
        } catch (_: Exception) {
            EstadoReporteLocal.PENDIENTE
        }
        val creadoEn = cursor.getLong(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_CREADO_EN))
        val actualizadoEn = cursor.getLong(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ACTUALIZADO_EN))

        val colEnviandoDesde = cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ENVIANDO_DESDE)
        val enviandoDesde = if (cursor.isNull(colEnviandoDesde)) null else cursor.getLong(colEnviandoDesde)

        val colPostIniciadoEn = cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_POST_INICIADO_EN)
        val postIniciadoEn = if (cursor.isNull(colPostIniciadoEn)) null else cursor.getLong(colPostIniciadoEn)

        val reintentos = cursor.getInt(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_REINTENTOS))

        val colCodigoError = cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_CODIGO_ERROR)
        val codigoError = if (cursor.isNull(colCodigoError)) null else cursor.getInt(colCodigoError)

        val colIdRemoto = cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ID_PETICION_REMOTA)
        val idPeticionRemota = if (cursor.isNull(colIdRemoto)) null else cursor.getInt(colIdRemoto)

        val numeroRadicado = cursor.getString(cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_NUMERO_RADICADO))

        val colEnviadoEn = cursor.getColumnIndexOrThrow(ReporteLocalDbHelper.COL_ENVIADO_EN)
        val enviadoEn = if (cursor.isNull(colEnviadoEn)) null else cursor.getLong(colEnviadoEn)

        return ReporteLocalEntity(
            idLocal = idLocal,
            uuid = uuid,
            idUsuario = idUsuario,
            idTipo = idTipo,
            tipoNombre = tipoNombre,
            descripcion = descripcion,
            latitud = latitud,
            longitud = longitud,
            direccion = direccion,
            evidenciasJson = evidenciasJson,
            estado = estado,
            creadoEn = creadoEn,
            actualizadoEn = actualizadoEn,
            enviandoDesde = enviandoDesde,
            postIniciadoEn = postIniciadoEn,
            reintentos = reintentos,
            codigoError = codigoError,
            idPeticionRemota = idPeticionRemota,
            numeroRadicado = numeroRadicado,
            enviadoEn = enviadoEn
        )
    }
}

