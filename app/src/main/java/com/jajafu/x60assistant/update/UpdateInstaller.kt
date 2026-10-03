package com.jajafu.x60assistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 透過 FileProvider 叫起系統安裝程式。
 * 非系統 App 無法靜默安裝，一律需要使用者在系統畫面按確認。
 */
object UpdateInstaller {
    private const val PROVIDER_SUFFIX = ".update_provider"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** minSdk 26 起可直接呼叫，不需版本分支。 */
    fun canInstallUnknownApps(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownAppSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, apkFile)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
