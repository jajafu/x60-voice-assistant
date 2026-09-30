package com.jajafu.x60assistant.navigation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NavigationActionResult(
    val message: String,
    val mode: NavigationLaunchMode,
)

/**
 * 執行本機導航指令並回報可驗證的結果。
 *
 * 與音樂執行器不同，地圖沒有背景可控的媒體工作階段，因此不需要 pauseForVoice／resumeAfterVoice：
 * 導航是在別的 App 畫面進行，小助手這邊沒有需要暫停的播放。
 */
class NavigationActionExecutor(
    context: Context,
    private val catalog: NavigationAppCatalog,
    private var selectedPackageName: String,
    private var selectedAppName: String,
) {
    private val appContext = context.applicationContext

    fun updateSelection(appName: String, packageName: String) {
        selectedAppName = appName
        selectedPackageName = packageName
    }

    suspend fun execute(command: NavigationCommand): NavigationActionResult = withContext(Dispatchers.Main.immediate) {
        when (command) {
            is NavigationCommand.Navigate -> navigate(command.destination)
        }
    }

    private fun navigate(destination: String): NavigationActionResult {
        val app = catalog.find(selectedPackageName) ?: run {
            val name = selectedAppName.ifBlank { "地圖 App" }
            return NavigationActionResult(
                message = NavigationReplyFormatter.routePreview(NavigationLaunchMode.NOT_FOUND, name, destination),
                mode = NavigationLaunchMode.NOT_FOUND,
            )
        }
        val mode = NavigationAppLauncher(appContext, app.packageName, app.appName)
            .openRoutePreview(app, destination)
        return NavigationActionResult(
            message = NavigationReplyFormatter.routePreview(mode, app.appName, destination),
            mode = mode,
        )
    }
}
