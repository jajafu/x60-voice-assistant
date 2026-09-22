package com.jajafu.x60assistant.diagnostics

import java.util.Locale

data class DeviceDiagnostics(
    val androidVersion: String,
    val apiLevel: Int,
    val buildDisplay: String,
    val manufacturerAndModel: String,
    val supportedAbis: String,
    val totalMemory: String,
    val availableMemory: String,
    val ttsEngines: List<String>,
    val microphonePermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

object DeviceDiagnosticsFormatter {
    private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0

    fun formatMemory(bytes: Long): String =
        String.format(Locale.ROOT, "%.2f GiB", bytes / BYTES_PER_GIB)
}
