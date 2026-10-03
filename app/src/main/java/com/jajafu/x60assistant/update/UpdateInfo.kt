package com.jajafu.x60assistant.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 資產 `update.json` 的內容，由手動 CI workflow 產生。
 * App 只信任 HTTPS 且 host 在允許清單的 apkUrl（見 [UpdateUrls]），
 * 且下載後一律以 sha256 驗證才交付安裝。
 */
@Serializable
data class UpdateInfo(
    @SerialName("versionCode")
    val versionCode: Int,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("apkUrl")
    val apkUrl: String,
    @SerialName("sha256")
    val sha256: String,
    @SerialName("notes")
    val notes: String = "",
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}
