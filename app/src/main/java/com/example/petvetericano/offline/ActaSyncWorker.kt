package com.example.petvetericano.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ActaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ID_LOCAL = "id_local"
    }

    override suspend fun doWork(): Result {
        // implementación en 4a-2
        return Result.success()
    }
}
