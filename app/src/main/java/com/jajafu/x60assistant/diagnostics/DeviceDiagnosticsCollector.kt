package com.jajafu.x60assistant.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.BatteryManager
import android.content.IntentFilter
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

class DeviceDiagnosticsCollector(private val context: Context) {
    fun collect(): DeviceDiagnostics {
        val memoryInfo = ActivityManager.MemoryInfo().also { info ->
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temperatureTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)

        return DeviceDiagnostics(
            androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { "—" },
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY.orEmpty().ifBlank { "—" },
            manufacturerAndModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "—" },
            supportedAbis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "—" },
            totalMemory = DeviceDiagnosticsFormatter.formatMemory(memoryInfo.totalMem),
            availableMemory = DeviceDiagnosticsFormatter.formatMemory(memoryInfo.availMem),
            ttsEngines = queryTtsEngines(),
            microphonePermissionGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(POST_NOTIFICATIONS_PERMISSION),
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            temperatureCelsius = temperatureTenths?.takeIf { it != Int.MIN_VALUE }?.div(10.0),
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else null,
            exactAlarmPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms(),
            appVersionName = queryAppVersion().first,
            appVersionCode = queryAppVersion().second,
        )
    }

    private fun queryAppVersion(): Pair<String, Long> = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        (info.versionName.orEmpty()) to code
    }.getOrDefault("" to 0L)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun queryTtsEngines(): List<String> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }

        return services.mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val packageName = serviceInfo.packageName
            val label = resolveInfo.loadLabel(context.packageManager).toString().ifBlank { packageName }
            val version = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    ).versionName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(packageName, 0).versionName
                }
            }.getOrNull().orEmpty()
            buildString {
                append(label)
                append(" (")
                append(packageName)
                if (version.isNotBlank()) {
                    append(" · ")
                    append(version)
                }
                append(")")
            }
        }.distinct().sorted()
    }

    companion object {
        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
