package com.example.petvetericano.offline

import android.content.Context
import android.content.SharedPreferences
import com.example.petvetericano.models.EspecieItemResponse
import com.example.petvetericano.models.ExamenCatalogoItemResponse
import com.example.petvetericano.models.RazaItemResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestor de caché local para catálogos oficiales (Especies, Razas y Exámenes).
 * Persiste los catálogos en SharedPreferences con formato JSON para que el
 * Acta de Atención pueda diligenciarse, validarse y enviarse sin conexión a internet.
 * Los catálogos son globales (no dependen del veterinario autenticado).
 */
object CatalogosCacheManager {

    private const val PREFS_NAME = "PetVetericanoCatalogosPrefs"
    private const val KEY_ESPECIES = "CACHE_CATALOGO_ESPECIES"
    private const val KEY_RAZAS = "CACHE_CATALOGO_RAZAS"
    private const val KEY_EXAMENES = "CACHE_CATALOGO_EXAMENES"
    private const val KEY_HORA_GUARDADO = "CACHE_HORA_GUARDADO"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun guardarEspecies(
        context: Context,
        especies: List<EspecieItemResponse>
    ) = withContext(Dispatchers.IO) {
        if (especies.isEmpty()) return@withContext
        val json = gson.toJson(especies)
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_ESPECIES, json)
            .putLong(KEY_HORA_GUARDADO, System.currentTimeMillis())
            .apply()
    }

    suspend fun obtenerEspecies(
        context: Context
    ): List<EspecieItemResponse> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_ESPECIES, null)
        if (!json.isNullOrEmpty()) {
            try {
                val tipo = object : TypeToken<List<EspecieItemResponse>>() {}.type
                gson.fromJson<List<EspecieItemResponse>>(json, tipo) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun guardarRazas(
        context: Context,
        razas: List<RazaItemResponse>
    ) = withContext(Dispatchers.IO) {
        if (razas.isEmpty()) return@withContext
        val json = gson.toJson(razas)
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_RAZAS, json)
            .putLong(KEY_HORA_GUARDADO, System.currentTimeMillis())
            .apply()
    }

    suspend fun obtenerRazas(
        context: Context
    ): List<RazaItemResponse> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_RAZAS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val tipo = object : TypeToken<List<RazaItemResponse>>() {}.type
                gson.fromJson<List<RazaItemResponse>>(json, tipo) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun guardarExamenes(
        context: Context,
        examenes: List<ExamenCatalogoItemResponse>
    ) = withContext(Dispatchers.IO) {
        if (examenes.isEmpty()) return@withContext
        val json = gson.toJson(examenes)
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_EXAMENES, json)
            .putLong(KEY_HORA_GUARDADO, System.currentTimeMillis())
            .apply()
    }

    suspend fun obtenerExamenes(
        context: Context
    ): List<ExamenCatalogoItemResponse> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_EXAMENES, null)
        if (!json.isNullOrEmpty()) {
            try {
                val tipo = object : TypeToken<List<ExamenCatalogoItemResponse>>() {}.type
                gson.fromJson<List<ExamenCatalogoItemResponse>>(json, tipo) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun obtenerHoraGuardado(
        context: Context
    ): Long = withContext(Dispatchers.IO) {
        getPrefs(context).getLong(KEY_HORA_GUARDADO, 0L)
    }

    fun limpiarCache(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
