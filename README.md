# 小助手 X60 Voice Assistant

本 Repository 目前只完成 **M0：專案骨架與裝置探測**。M0 的前景服務不建立 `AudioRecord`、不錄音，也不包含 KWS、ASR、LLM 或 TTS 播放。

## 專案基準

- Package：`com.jajafu.x60assistant`
- Kotlin + Jetpack Compose Material 3
- `minSdk 26`、`targetSdk 35`、`compileSdk 35`
- 單一 `app` module
- 所有依賴與 plugin 版本由 `gradle/libs.versions.toml` 管理

## 本機建置

需求：JDK 17 以上及 Android SDK Platform 35。

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

已執行的實機結果見 [`docs/test-reports/m0-2026-09-22-vivo-x60.md`](docs/test-reports/m0-2026-09-22-vivo-x60.md)。

## vivo X60 USB 安裝

1. 在 X60 開啟「開發人員選項」與「USB 偵錯」，以資料線連接電腦。
2. 手機出現 RSA 指紋提示時選擇允許。
3. 確認 ADB 只列出預期裝置：

   ```powershell
   adb devices -l
   ```

4. 安裝或覆蓋安裝 Debug APK：

   ```powershell
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

5. 啟動 App：

   ```powershell
   adb shell am start -n com.jajafu.x60assistant/.MainActivity
   ```

## M0 真機驗收

1. 首頁應顯示 Android 版本、API Level、Build / OriginOS 顯示版本、型號、ABI、總／可用記憶體、TTS 引擎及電池最佳化狀態。
2. 按「啟動 M0 服務」，依序允許麥克風與通知權限；通知內容應明確顯示「診斷模式；未錄音」。
3. 從通知按「停止服務」，確認通知消失且回到 App 後狀態為「已停止」。
4. 拒絕任一權限，再按「開啟 App 權限設定」手動恢復；回到 App 後重新整理並再次啟動。
5. 按「開啟系統電池最佳化」，在標準設定頁將 App 設為不最佳化；返回後狀態應顯示「已忽略」。
6. 依 OriginOS 版本手動確認自啟動、允許後台高耗電、允許通知、允許麥克風及最近任務加鎖。App 不會嘗試寫死或繞過 vivo 私有設定頁。
7. 執行以下檢查，確認 M0 沒有開啟錄音：

   ```powershell
   adb shell dumpsys audio | Select-String com.jajafu.x60assistant
   adb logcat -d | Select-String "AudioRecord|com.jajafu.x60assistant"
   ```

8. 執行停止服務與強制停止，確認 App 不會自行復活：

   ```powershell
   adb shell am force-stop com.jajafu.x60assistant
   ```

> 真機結果必須記錄裝置型號、Android/API、OriginOS Build、App version、APK SHA256 與測試時間。沒有完成操作就不能標記通過。

## 長期接電提醒

vivo X60 為舊裝置，長期插電前應保持通風並定期檢查外殼變形、電池膨脹及異常溫度。App 的診斷狀態不能取代實體安全檢查。

## M0 已知邊界

- 前景服務宣告為 `microphone`，用來提前驗證 Android 權限與啟動限制，但 M0 不使用麥克風。
- TTS 診斷只列出已安裝的引擎服務，不初始化 TTS、查詢 voice 或播放聲音。
- 不包含開機接收器；未來也不得從 `BOOT_COMPLETED` 直接啟動 microphone FGS。
- OriginOS 私有保活狀態無穩定標準 API，需依手機實際版本人工驗收。
