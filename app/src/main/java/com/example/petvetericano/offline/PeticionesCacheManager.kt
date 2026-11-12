package com.example.petvetericano.offline

import android.content.Context
import android.content.SharedPreferences
import com.example.petvetericano.models.PeticionListResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestor de caché local para peticiones asignadas al veterinario.
 * Persiste la lista obtenida de listarPeticiones en SharedPreferences con formato JSON
 * separado por ID de veterinario para garantizar aislamiento entre cuentas.
 */
object PeticionesCacheManager {

    private const val PREFS_PREFIX = "PetVetericanoPeticionesPrefs_"
    private const val KEY_LISTA_PETICIONES = "CACHE_LISTA_PETICIONES"
    private const val KEY_HORA_GUARDADO = "CACHE_HORA_GUARDADO"

    private val gson = Gson()

    private fun getPrefs(context: Context, idVeterinario: Int): SharedPreferences {
        val suffix = if (idVeterinario > 0) idVeterinario.toString() else "default"
        return context.applicationContext.getSharedPreferences("$PREFS_PREFIX$suffix", Context.MODE_PRIVATE)
    }

    suspend fun guardar(
        context: Context,
        idVeterinario: Int,
        peticiones: List<PeticionListResponse>
    ) = withContext(Dispatchers.IO) {
        if (idVeterinario <= 0 || peticiones.isEmpty()) return@withContext
        val previas = obtenerTodas(context, idVeterinario).associateBy { it.id_peticion }
        val repo = ActaLocalRepository.getInstance(context)
        val consolidadas = peticiones.map { pet ->
            val previa = previas[pet.id_peticion]
            val tieneActaLocal = repo.tieneActaConfirmadaLocal(pet.id_peticion, idVeterinario)
            val tieneActaConsolidado = (pet.tiene_acta == true) ||
                (previa?.tiene_acta == true) ||
                tieneActaLocal
            val idSegConsolidado = pet.id_seguimiento ?: previa?.id_seguimiento
            if (tieneActaConsolidado != pet.tiene_acta || idSegConsolidado != pet.id_seguimiento) {
                pet.copy(
                    tiene_acta = tieneActaConsolidado,
                    id_seguimiento = idSegConsolidado
                )
            } else {
                pet
            }
        }
        val json = gson.toJson(consolidadas)
        val prefs = getPrefs(context, idVeterinario)
        prefs.edit()
            .putString(KEY_LISTA_PETICIONES, json)
            .putLong(KEY_HORA_GUARDADO, System.currentTimeMillis())
            .apply()
    }

    suspend fun marcarTieneActa(
        context: Context,
        idVeterinario: Int,
        idPeticion: Int,
        idSeguimiento: Int? = null
    ) = withContext(Dispatchers.IO) {
        if (idVeterinario <= 0 || idPeticion <= 0) return@withContext
        val todas = obtenerTodas(context, idVeterinario)
        if (todas.isEmpty()) return@withContext
        var modificado = false
        val actualizadas = todas.map { pet ->
            if (pet.id_peticion == idPeticion) {
                modificado = true
                pet.copy(
                    tiene_acta = true,
                    id_seguimiento = idSeguimiento ?: pet.id_seguimiento
                )
            } else {
                pet
            }
        }
        if (modificado) {
            val json = gson.toJson(actualizadas)
            getPrefs(context, idVeterinario).edit()
                .putString(KEY_LISTA_PETICIONES, json)
                .apply()
        }
    }

    suspend fun obtenerPorId(
        context: Context,
        idVeterinario: Int,
        idPeticion: Int
    ): PeticionListResponse? = withContext(Dispatchers.IO) {
        if (idVeterinario <= 0 || idPeticion <= 0) return@withContext null
        val todas = obtenerTodas(context, idVeterinario)
        todas.firstOrNull { it.id_peticion == idPeticion }
    }

    suspend fun obtenerTodas(
        context: Context,
        idVeterinario: Int
    ): List<PeticionListResponse> = withContext(Dispatchers.IO) {
        if (idVeterinario <= 0) return@withContext emptyList()
        val prefs = getPrefs(context, idVeterinario)
        val json = prefs.getString(KEY_LISTA_PETICIONES, null)
        if (!json.isNullOrEmpty()) {
            try {
                val tipo = object : TypeToken<List<PeticionListResponse>>() {}.type
                gson.fromJson<List<PeticionListResponse>>(json, tipo) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun obtenerHoraGuardado(
        context: Context,
        idVeterinario: Int
    ): Long = withContext(Dispatchers.IO) {
        if (idVeterinario <= 0) return@withContext 0L
        getPrefs(context, idVeterinario).getLong(KEY_HORA_GUARDADO, 0L)
    }

    fun limpiarCacheVeterinario(context: Context, idVeterinario: Int) {
        if (idVeterinario <= 0) return
        getPrefs(context, idVeterinario).edit().clear().apply()
    }
}
