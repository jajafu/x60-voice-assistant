package com.jajafu.x60assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jajafu.x60assistant.diagnostics.DeviceDiagnostics
import com.jajafu.x60assistant.diagnostics.DeviceDiagnosticsCollector
import com.jajafu.x60assistant.service.ServiceStartPolicy
import com.jajafu.x60assistant.service.ServiceStateRepository
import com.jajafu.x60assistant.service.VoiceForegroundService
import com.jajafu.x60assistant.ui.diagnostics.DeviceDiagnosticsScreen
import com.jajafu.x60assistant.ui.theme.X60AssistantTheme

class MainActivity : ComponentActivity() {
    private lateinit var diagnosticsCollector: DeviceDiagnosticsCollector
    private var diagnostics by mutableStateOf(emptyDiagnostics())
    private var errorMessage by mutableStateOf<String?>(null)
    private var startAfterPermissionGrant = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshDiagnostics()
        if (granted && startAfterPermissionGrant) {
            startAfterPermissionGrant = false
            startM0Service()
        } else if (!granted) {
            startAfterPermissionGrant = false
            errorMessage = getString(R.string.notification_permission_denied)
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshDiagnostics()
        if (!granted) {
            startAfterPermissionGrant = false
            errorMessage = getString(R.string.microphone_permission_denied)
            return@registerForActivityResult
        }

        if (ServiceStartPolicy.requiresNotificationPermission(Build.VERSION.SDK_INT) &&
            !hasPermission(POST_NOTIFICATIONS_PERMISSION)
        ) {
            notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
        } else if (startAfterPermissionGrant) {
            startAfterPermissionGrant = false
            startM0Service()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticsCollector = DeviceDiagnosticsCollector(applicationContext)
        refreshDiagnostics()

        setContent {
            val serviceRunning by ServiceStateRepository.running.collectAsStateWithLifecycle()
            X60AssistantTheme {
                DeviceDiagnosticsScreen(
                    diagnostics = diagnostics,
                    serviceRunning = serviceRunning,
                    errorMessage = errorMessage,
                    onPrimaryAction = {
                        if (serviceRunning) stopM0Service() else requestPermissionsAndStart()
                    },
                    onOpenAppSettings = ::openAppSettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onRefresh = ::refreshDiagnostics,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::diagnosticsCollector.isInitialized) refreshDiagnostics()
    }

    private fun requestPermissionsAndStart() {
        errorMessage = null
        startAfterPermissionGrant = true
        when {
            !hasPermission(Manifest.permission.RECORD_AUDIO) ->
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

            ServiceStartPolicy.requiresNotificationPermission(Build.VERSION.SDK_INT) &&
                !hasPermission(POST_NOTIFICATIONS_PERMISSION) ->
                notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)

            else -> {
                startAfterPermissionGrant = false
                startM0Service()
            }
        }
    }

    private fun startM0Service() {
        refreshDiagnostics()
        if (!ServiceStartPolicy.canStart(
                diagnostics.microphonePermissionGranted,
                diagnostics.notificationPermissionGranted,
            )
        ) {
            errorMessage = getString(R.string.permissions_required)
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, VoiceForegroundService::class.java),
            )
        } catch (exception: SecurityException) {
            errorMessage = getString(R.string.service_start_security_error)
        } catch (exception: IllegalStateException) {
            errorMessage = getString(R.string.service_start_background_error)
        }
    }

    private fun stopM0Service() {
        val stopIntent = Intent(this, VoiceForegroundService::class.java)
        stopService(stopIntent)
    }

    private fun refreshDiagnostics() {
        diagnostics = diagnosticsCollector.collect()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:$packageName".toUri(),
        )
        startActivity(intent)
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    companion object {
        private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        private fun emptyDiagnostics() = DeviceDiagnostics(
            androidVersion = "—",
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = "—",
            manufacturerAndModel = "—",
            supportedAbis = "—",
            totalMemory = "—",
            availableMemory = "—",
            ttsEngines = emptyList(),
            microphonePermissionGranted = false,
            notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
            ignoringBatteryOptimizations = false,
        )
    }
}
