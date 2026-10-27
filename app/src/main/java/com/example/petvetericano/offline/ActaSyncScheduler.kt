package com.example.petvetericano.offline

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.petvetericano.SharedPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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
}
