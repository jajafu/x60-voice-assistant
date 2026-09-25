package com.jajafu.x60assistant.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jajafu.x60assistant.MainActivity
import com.jajafu.x60assistant.asr.AsrModelPack
import com.jajafu.x60assistant.asr.AsrModelPacks
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** A foreground, persistent download; WorkManager restarts it after process or network loss. */
class AsrModelDownloadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val pack = AsrModelPacks.byId(inputData.getString(KEY_MODEL_ID) ?: "")
            ?: return Result.failure(workDataOf(KEY_ERROR to "未知模型"))
        createNotificationChannel()
        setForeground(foregroundInfo(pack, "準備下載", 0))
        var lastNotificationPercent = -1
        var lastNotificationPhase = ""
        return try {
            AsrModelDownloader(applicationContext).download(pack) { progress ->
                setProgress(workDataOf(KEY_PHASE to progress.phase, KEY_PERCENT to (progress.percent ?: -1)))
                val percent = progress.percent ?: -1
                if (progress.phase != lastNotificationPhase || percent / 5 != lastNotificationPercent / 5) {
                    setForeground(foregroundInfo(pack, progress.phase, percent))
                    lastNotificationPhase = progress.phase
                    lastNotificationPercent = percent
                }
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            val permanentHttpError = error is HttpDownloadException &&
                error.statusCode in 400..499 && error.statusCode !in listOf(408, 429)
            if (permanentHttpError || runAttemptCount >= MAX_RETRIES) {
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: "下載失敗")))
            } else {
                val savedPercent = (AsrModelDownloader.partialBytes(applicationContext, pack)
                    .coerceAtMost(pack.archiveBytes) * 100 / pack.archiveBytes).toInt()
                setProgress(workDataOf(
                    KEY_PHASE to "網路中斷，等待自動續傳",
                    KEY_PERCENT to savedPercent,
                ))
                Result.retry()
            }
        }
    }

    private fun foregroundInfo(pack: AsrModelPack, phase: String, percent: Int): ForegroundInfo {
        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下載 ${pack.label}")
            .setContentText(if (percent >= 0) "$phase $percent%" else phase)
            .setContentIntent(intent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()
        val notificationId = NOTIFICATION_BASE + AsrModelPacks.all.indexOf(pack)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "模型下載", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PHASE = "phase"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "asr-model-downloads"
        private const val NOTIFICATION_BASE = 3200
        private const val MAX_RETRIES = 8

        fun uniqueWorkName(pack: AsrModelPack): String = "asr-model-download-${pack.id}"

        fun enqueue(context: Context, pack: AsrModelPack) {
            val request = OneTimeWorkRequestBuilder<AsrModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to pack.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(pack),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
