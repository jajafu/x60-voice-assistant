package com.jajafu.x60assistant.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Retained so download jobs queued by older versions stop after an upgrade. */
class AsrModelDownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.failure()
}
