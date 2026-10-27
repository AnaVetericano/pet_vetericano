package com.example.petvetericano.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.petvetericano.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Resultado de la operación de forzar el reenvío de un acta local.
 */
enum class ResultadoReenvio {
    ENCOLADA,
    YA_EN_PROCESO,
    REQUIERE_REVISION,
    NO_ENCONTRADA
}

object ActaSyncScheduler {

    private const val TAG = "ActaSyncScheduler"
    const val TAG_ACTA_SYNC = "acta_sync"
    const val WORK_NAME_PREFIX = "acta_sync_"

    fun enqueueOneTimeSync(context: Context, idLocal: Long) {
        val appContext = context.applicationContext
        val inputData = workDataOf(ActaSyncWorker.KEY_ID_LOCAL to idLocal)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<ActaSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag(TAG_ACTA_SYNC)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "$WORK_NAME_PREFIX$idLocal",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    fun enqueuePendingForCurrentUser(context: Context) {
        val appContext = context.applicationContext
        val prefs = SharedPreferencesManager(appContext)
        val idVet = prefs.getUserId()
        if (idVet == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repositorio = ActaLocalRepository.getInstance(appContext)
                val pendientes = repositorio.obtenerPendientes(idVet)
                for (acta in pendientes) {
                    enqueueOneTimeSync(appContext, acta.idLocal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al encolar actas pendientes del usuario: ${e.message}", e)
            }
        }
    }
    /**
     * Construye una solicitud OneTimeWorkRequest para sincronización con la misma configuración base:
     * restricción de red CONNECTED, backoff exponencial desde 30 segundos y tag TAG_ACTA_SYNC.
     */
    private fun crearSyncRequest(idLocal: Long): OneTimeWorkRequest {
        val inputData = workDataOf(ActaSyncWorker.KEY_ID_LOCAL to idLocal)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<ActaSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag(TAG_ACTA_SYNC)
            .build()
    }

    /**
     * Consulta con seguridad los WorkInfo para el trabajo único especificado.
     * Si ocurre alguna excepción al resolver el ListenableFuture, registra advertencia y retorna lista vacía.
     */
    private fun obtenerWorkInfos(workManager: WorkManager, idLocal: Long): List<WorkInfo> {
        return try {
            workManager.getWorkInfosForUniqueWork("$WORK_NAME_PREFIX$idLocal").get()
        } catch (e: Exception) {
            Log.w(TAG, "Error al consultar WorkInfo para idLocal=$idLocal: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fuerza el reenvío inmediato de un acta local específica en segundo plano.
     *
     * IMPORTANTE: Método bloqueante; debe invocarse desde Dispatchers.IO.
     *
     * @param context Contexto de la aplicación.
     * @param idLocal Identificador local del acta en SQLite.
     * @param idVeterinario Identificador del veterinario que realiza la operación.
     * @return [ResultadoReenvio] indicando si fue encolada, si ya estaba corriendo,
     *         si requiere revisión manual o si no fue encontrada.
     */
    fun reenviarAhora(context: Context, idLocal: Long, idVeterinario: Int): ResultadoReenvio {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        // a) Si el trabajo único tiene algún elemento en estado RUNNING: devuelve YA_EN_PROCESO sin tocar la fila
        val workInfos = obtenerWorkInfos(workManager, idLocal)
        if (workInfos.any { it.state == WorkInfo.State.RUNNING }) {
            return ResultadoReenvio.YA_EN_PROCESO
        }

        // b) Llama a reiniciarParaReenvio; si devuelve false, mira si la fila existe
        val repositorio = ActaLocalRepository.getInstance(appContext)
        val reiniciado = repositorio.reiniciarParaReenvio(idLocal, idVeterinario)
        if (!reiniciado) {
            val fila = repositorio.obtenerPorIdLocal(idLocal)
            return if (fila != null && fila.idVeterinario == idVeterinario) {
                ResultadoReenvio.REQUIERE_REVISION
            } else {
                ResultadoReenvio.NO_ENCONTRADA
            }
        }

        // c) Si devuelve true: encola el mismo trabajo con ExistingWorkPolicy.REPLACE y devuelve ENCOLADA
        val syncRequest = crearSyncRequest(idLocal)
        workManager.enqueueUniqueWork(
            "$WORK_NAME_PREFIX$idLocal",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        return ResultadoReenvio.ENCOLADA
    }

    /**
     * Reenvía todas las actas en cola del veterinario actual.
     *
     * IMPORTANTE: Método bloqueante; debe invocarse desde Dispatchers.IO.
     *
     * @return Cantidad de actas cuyo reenvío resultó en [ResultadoReenvio.ENCOLADA].
     */
    fun reenviarTodas(context: Context, idVeterinario: Int): Int {
        val appContext = context.applicationContext
        val repositorio = ActaLocalRepository.getInstance(appContext)
        val enCola = repositorio.obtenerEnCola(idVeterinario)
        var encoladas = 0
        for (item in enCola) {
            val resultado = reenviarAhora(appContext, item.idLocal, idVeterinario)
            if (resultado == ResultadoReenvio.ENCOLADA) {
                encoladas++
            }
        }
        return encoladas
    }

    /**
     * Reactiva actas que llevan más tiempo del umbral en PENDIENTE_ENVIO o ERROR_REINTENTABLE
     * (por ejemplo, actas con trabajo perdido de WorkManager o esperando un backoff prolongado).
     *
     * Encola en WorkManager con ExistingWorkPolicy.REPLACE sin retraso inicial, SIN reiniciar
     * el contador de reintentos en SQLite (preservando el tope de 10 reintentos de seguridad).
     *
     * IMPORTANTE: Método bloqueante; debe invocarse desde Dispatchers.IO.
     *
     * @param umbralMs Tiempo en milisegundos desde actualizado_en para considerar un acta obsoleta (default 5 min).
     * @return Cantidad de actas reactivadas.
     */
    fun reactivarObsoletas(context: Context, idVeterinario: Int, umbralMs: Long = 300_000L): Int {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val repositorio = ActaLocalRepository.getInstance(appContext)
        val ahoraMs = System.currentTimeMillis()
        val obsoletas = repositorio.obtenerObsoletas(idVeterinario, ahoraMs, umbralMs)

        var reactivadas = 0
        for (idLocal in obsoletas) {
            val workInfos = obtenerWorkInfos(workManager, idLocal)
            if (workInfos.any { it.state == WorkInfo.State.RUNNING }) {
                continue
            }

            val syncRequest = crearSyncRequest(idLocal)
            workManager.enqueueUniqueWork(
                "$WORK_NAME_PREFIX$idLocal",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            reactivadas++
        }
        return reactivadas
    }
}
