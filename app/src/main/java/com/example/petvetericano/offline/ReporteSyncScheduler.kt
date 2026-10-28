package com.example.petvetericano.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.petvetericano.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Planificador y despachador de tareas WorkManager para la sincronización de reportes ciudadanos offline.
 */
object ReporteSyncScheduler {

    private const val TAG = "ReporteSyncScheduler"
    const val TAG_REPORTE_SYNC = "reporte_sync"
    const val WORK_NAME_PREFIX = "reporte_sync_"

    /**
     * Encola un trabajo único en WorkManager con clave única "reporte_sync_<idLocal>",
     * política ExistingWorkPolicy.KEEP, restricción de red CONNECTED y backoff exponencial.
     */
    fun encolar(context: Context, idLocal: Long) {
        val appContext = context.applicationContext
        val workRequest = crearSyncRequest(idLocal)

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "$WORK_NAME_PREFIX$idLocal",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Encola todas las peticiones ciudadanas pendientes de sincronización para el usuario actualmente autenticado.
     */
    fun encolarPendientesDelUsuario(context: Context) {
        val appContext = context.applicationContext
        val prefs = SharedPreferencesManager(appContext)
        val idUsuario = prefs.getUserId()
        if (idUsuario <= 0) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repositorio = ReporteLocalRepository.getInstance(appContext)
                val pendientes = repositorio.obtenerPendientesPorUsuario(idUsuario)
                for (reporte in pendientes) {
                    encolar(appContext, reporte.idLocal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al encolar reportes pendientes del usuario: ${e.message}", e)
            }
        }
    }

    /**
     * Construye una solicitud OneTimeWorkRequest para sincronización con la configuración requerida.
     */
    private fun crearSyncRequest(idLocal: Long): OneTimeWorkRequest {
        val inputData = workDataOf(ReporteSyncWorker.KEY_ID_LOCAL to idLocal)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<ReporteSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag(TAG_REPORTE_SYNC)
            .build()
    }
}

