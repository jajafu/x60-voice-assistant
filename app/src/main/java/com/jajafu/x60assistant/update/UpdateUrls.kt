package com.jajafu.x60assistant.update

import java.net.URI

/**
 * 更新連結白名單。Release 只允許 HTTPS，且 apkUrl 的完整性最終由
 * 下載後的 SHA-256 驗證保證（見 [UpdateDownloader]）。
 */
object UpdateUrls {
    val trustedHosts: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    fun requireTrustedHttps(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrElse {
            throw UpdateException(UpdateFailure.INVALID_RESPONSE, "更新連結格式無效")
        }
        val host = uri.host?.lowercase().orEmpty()
        if (uri.scheme?.lowercase() != "https" || host !in trustedHosts || uri.userInfo != null) {
            throw UpdateException(UpdateFailure.INVALID_RESPONSE, "更新連結不在允許範圍")
        }
        return uri.toString()
    }
}
