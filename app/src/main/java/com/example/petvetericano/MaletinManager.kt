package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences
import com.example.petvetericano.models.ConsumoOfflineItem
import com.example.petvetericano.models.KitItemResponse
import com.example.petvetericano.models.MedicamentoMaletin
import com.example.petvetericano.models.SincronizarOfflineRequest
import com.example.petvetericano.models.SincronizarOfflineResponse
import com.example.petvetericano.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestor del inventario del Maletín de Medicamentos asignado al veterinario (Kit Móvil).
 * Conecta con el backend Django (/api/inventario/kit/), mantiene la caché offline
 * para trabajo en campo sin conectividad y gestiona la cola de consumos pendientes.
 */
class MaletinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("PetVetericanoMaletinPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_MEDICAMENTOS = "MALETIN_MEDICAMENTOS_LIST"
        private const val KEY_ULTIMA_SYNC = "MALETIN_ULTIMA_SINCRONIZACION"
        private const val KEY_CONSUMOS_PENDIENTES = "MALETIN_CONSUMOS_PENDIENTES"

        @Volatile
        private var instance: MaletinManager? = null

        fun getInstance(context: Context): MaletinManager {
            return instance ?: synchronized(this) {
                instance ?: MaletinManager(context).also { instance = it }
            }
        }
    }

    /**
     * Obtiene la lista actual de medicamentos del maletín desde la caché local.
     */
    fun getMedicamentos(): List<MedicamentoMaletin> {
        val json = prefs.getString(KEY_MEDICAMENTOS, null)
        return if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<MedicamentoMaletin>>() {}.type
                gson.fromJson<List<MedicamentoMaletin>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Guarda la lista de medicamentos en caché local.
     */
    fun guardarMedicamentos(lista: List<MedicamentoMaletin>) {
        val json = gson.toJson(lista)
        prefs.edit().putString(KEY_MEDICAMENTOS, json).apply()
    }

    /**
     * Obtiene un medicamento por su ID.
     */
    fun getMedicamento(id: String): MedicamentoMaletin? {
        return getMedicamentos().find { it.id == id }
    }

    /**
     * Indica si ya hay datos de inventario cacheados.
     */
    fun hayDatosSincronizados(): Boolean {
        return getMedicamentos().isNotEmpty()
    }

    /**
     * Obtiene el timestamp de la última sincronización con el servidor.
     */
    fun getUltimoTimestampSync(): Long {
        return prefs.getLong(KEY_ULTIMA_SYNC, 0L)
    }

    /**
     * Sincroniza el inventario completo del maletín desde el backend (/api/inventario/kit/mi-kit/).
     * Si falla la red pero hay datos en caché, preserva la caché local.
     */
    suspend fun sincronizarDesdeServidor(token: String): Result<List<MedicamentoMaletin>> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Token de autenticación vacío"))
        }

        try {
            val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
            val response = RetrofitClient.apiService.obtenerMiKit(authHeader)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val listaTransformada = body.items.map { item ->
                    MedicamentoMaletin.fromKitItem(item)
                }

                guardarMedicamentos(listaTransformada)
                prefs.edit().putLong(KEY_ULTIMA_SYNC, System.currentTimeMillis()).apply()

                Result.success(listaTransformada)
            } else {
                // Si /mi-kit/ falla por algún motivo, intentar con snapshot-movil/
                val snapshotResp = RetrofitClient.apiService.obtenerSnapshotMovil(authHeader)
                if (snapshotResp.isSuccessful && snapshotResp.body() != null) {
                    val snapshot = snapshotResp.body()!!
                    val existentesMap = getMedicamentos().associateBy { it.idMedicamentoInt }

                    val listaDesdeSnapshot = snapshot.stock.map { stockItem ->
                        val anterior = existentesMap[stockItem.idMedicamento]
                        MedicamentoMaletin(
                            id = stockItem.idMedicamento.toString(),
                            nombre = stockItem.nombre,
                            categoria = anterior?.categoria ?: "General",
                            unidadMedida = anterior?.unidadMedida ?: "unidades",
                            cantidadDisponible = stockItem.saldoDisponible.toDouble(),
                            cantidadInicial = maxOf(anterior?.cantidadInicial ?: 0.0, stockItem.saldoDisponible.toDouble()),
                            lote = anterior?.lote ?: "Lote activo",
                            fechaVencimiento = anterior?.fechaVencimiento ?: "Vigente",
                            viaRecomendada = anterior?.viaRecomendada ?: "Clínica",
                            descripcionUso = anterior?.descripcionUso ?: ""
                        )
                    }

                    guardarMedicamentos(listaDesdeSnapshot)
                    prefs.edit().putLong(KEY_ULTIMA_SYNC, snapshot.timestamp * 1000).apply()

                    Result.success(listaDesdeSnapshot)
                } else {
                    val code = response.code()
                    val errorStr = response.errorBody()?.string() ?: "Error $code"
                    Result.failure(Exception("Error al consultar el kit ($code): $errorStr"))
                }
            }
        } catch (e: Exception) {
            val locales = getMedicamentos()
            if (locales.isNotEmpty()) {
                // Modo resiliente: falló la red pero podemos continuar con la caché local
                Result.success(locales)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Descuenta una cantidad de existencias de un medicamento en la memoria local.
     * @return true si se pudo descontar, false si no hay suficiente stock disponible.
     */
    @Synchronized
    fun descontarStock(id: String, cantidad: Double): Boolean {
        if (cantidad <= 0.0) return false
        val lista = getMedicamentos().toMutableList()
        val index = lista.indexOfFirst { it.id == id }
        if (index != -1) {
            val med = lista[index]
            if (med.cantidadDisponible >= cantidad) {
                val nuevaCantidad = (med.cantidadDisponible - cantidad).coerceAtLeast(0.0)
                lista[index] = med.copy(cantidadDisponible = nuevaCantidad)
                guardarMedicamentos(lista)
                return true
            }
        }
        return false
    }

    /**
     * Revierte / devuelve una cantidad descontada (si el veterinario remueve el medicamento del acta).
     */
    @Synchronized
    fun devolverStock(id: String, cantidad: Double) {
        if (cantidad <= 0.0) return
        val lista = getMedicamentos().toMutableList()
        val index = lista.indexOfFirst { it.id == id }
        if (index != -1) {
            val med = lista[index]
            val nuevaCantidad = med.cantidadDisponible + cantidad
            lista[index] = med.copy(cantidadDisponible = nuevaCantidad)
            guardarMedicamentos(lista)
        }
    }

    /**
     * Retorna el número de medicamentos con stock disponible (> 0).
     */
    fun getTotalMedicamentosDisponibles(): Int {
        return getMedicamentos().count { it.cantidadDisponible > 0 }
    }

    /**
     * Retorna el total de referencias en el maletín.
     */
    fun getTotalReferencias(): Int {
        return getMedicamentos().size
    }

    /**
     * Calcula el estado general del maletín para el Dashboard y badges M3.
     */
    fun getEstadoStock(): String {
        val meds = getMedicamentos()
        if (meds.isEmpty()) return "Sin asignación"

        val vencidos = meds.count { it.saldoVencido > 0 }
        val agotados = meds.count { it.cantidadDisponible <= 0 }
        val bajos = meds.count { it.cantidadDisponible > 0 && it.cantidadDisponible < 5 }

        return when {
            vencidos > 0 -> "$vencidos vencidos"
            agotados > 0 -> "$agotados agotados"
            bajos > 0 -> "$bajos stock bajo"
            else -> "Stock disponible"
        }
    }

    // =========================================================================
    // COLA DE CONSUMOS PENDIENTES OFFLINE
    // =========================================================================

    fun getConsumosPendientes(): List<ConsumoOfflineItem> {
        val json = prefs.getString(KEY_CONSUMOS_PENDIENTES, null)
        return if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<ConsumoOfflineItem>>() {}.type
                gson.fromJson<List<ConsumoOfflineItem>>(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun guardarConsumosPendientes(lista: List<ConsumoOfflineItem>) {
        val json = gson.toJson(lista)
        prefs.edit().putString(KEY_CONSUMOS_PENDIENTES, json).apply()
    }

    fun encolarConsumoPendiente(item: ConsumoOfflineItem) {
        val actuales = getConsumosPendientes().toMutableList()
        // Evita duplicados por client_uuid
        if (actuales.none { it.clientUuid == item.clientUuid }) {
            actuales.add(item)
            guardarConsumosPendientes(actuales)
        }
    }

    /**
     * Sincroniza los consumos acumulados offline con el backend (/api/inventario/kit/sincronizar-offline/).
     */
    suspend fun sincronizarConsumosPendientes(token: String): Result<SincronizarOfflineResponse> = withContext(Dispatchers.IO) {
        val pendientes = getConsumosPendientes()
        if (pendientes.isEmpty()) {
            return@withContext Result.success(SincronizarOfflineResponse())
        }

        try {
            val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
            val response = RetrofitClient.apiService.sincronizarOfflineKit(
                token = authHeader,
                request = SincronizarOfflineRequest(consumos = pendientes)
            )

            if (response.isSuccessful && response.body() != null) {
                // 200 OK: todos aplicados
                guardarConsumosPendientes(emptyList())
                Result.success(response.body()!!)
            } else if (response.code() == 409) {
                // 409 CONFLICTO_STOCK_KIT: según la regla oficial del proyecto, los conflictos
                // ya quedan registrados como RECHAZADO_CONFLICTO en el servidor y NO deben reintentarse.
                // Limpiamos la cola y retornamos el detalle para informar al usuario.
                guardarConsumosPendientes(emptyList())
                val errorBodyStr = response.errorBody()?.string() ?: ""
                val conflictResp = try {
                    gson.fromJson(errorBodyStr, SincronizarOfflineResponse::class.java)
                } catch (_: Exception) {
                    SincronizarOfflineResponse(error = "CONFLICTO_STOCK_KIT")
                }
                Result.success(conflictResp)
            } else {
                val code = response.code()
                Result.failure(Exception("Error al sincronizar consumos ($code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
