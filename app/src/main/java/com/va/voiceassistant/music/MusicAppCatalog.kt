package com.va.voiceassistant.music

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class MusicAppOption(
    val appName: String,
    val packageName: String,
    val serviceName: String,
)

/** Lists only apps that expose a browser service the assistant can actually control. */
class MusicAppCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun compatibleApps(): List<MusicAppOption> {
        val discovered = queryBrowserServices().toMutableList()
        musicCabinFallback()?.let { fallback ->
            if (discovered.none { it.packageName == fallback.packageName }) discovered += fallback
        }
        return discovered
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
    }

    private fun queryBrowserServices(): List<MusicAppOption> {
        val intent = Intent(MEDIA_BROWSER_SERVICE_ACTION)
        val services = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        return services.mapNotNull { resolveInfo ->
            val service = resolveInfo.serviceInfo ?: return@mapNotNull null
            if (!service.exported || service.packageName == appContext.packageName) return@mapNotNull null
            val label = service.applicationInfo.loadLabel(packageManager).toString()
            MusicAppOption(label.ifBlank { service.packageName }, service.packageName, service.name)
        }
    }

    private fun musicCabinFallback(): MusicAppOption? {
        val applicationInfo = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    MusicCabinController.DEFAULT_PACKAGE_NAME,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(MusicCabinController.DEFAULT_PACKAGE_NAME, 0)
            }
        }.getOrNull() ?: return null
        val label = applicationInfo.loadLabel(packageManager).toString().ifBlank { "MusicCabin" }
        return MusicAppOption(
            appName = label,
            packageName = MusicCabinController.DEFAULT_PACKAGE_NAME,
            serviceName = MusicCabinController.DEFAULT_SERVICE_NAME,
        )
    }

    companion object {
        const val MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService"
    }
}
