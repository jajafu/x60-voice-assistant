package com.jajafu.x60assistant.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jajafu.x60assistant.BuildConfig
import com.jajafu.x60assistant.data.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * 每日一次的背景更新檢查。開關在設定（[SettingsRepository] 的 autoUpdateCheck，
 * 預設開啟）；有關閉、有新版、檢查失敗都只記成功，不重試，避免背景耗電。
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = runCatching { SettingsRepository(applicationContext).current() }.getOrNull()
            ?: return Result.success()
        if (!settings.autoUpdateCheck) return Result.success()
        val result = runCatching { UpdateChecker().checkLatest(BuildConfig.VERSION_CODE) }
            .getOrElse { return Result.success() }
        if (result is UpdateCheckResult.Available) {
            UpdateNotifier.showAvailable(applicationContext, result.info)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "app-update-check"

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
