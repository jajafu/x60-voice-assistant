# M1 Testing

## 自動化

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Unit tests 覆蓋權限啟動策略、裝置格式、bounded queue 丟最舊行為、dropped count、safe ring log 容量／清理，以及固定模型 ID／SHA256。

## X60 測試前記錄

每輪都記錄 App version、Git commit、APK SHA256、model ID／archive SHA256、Android、OriginOS Build、充電狀態、電量、電池溫度、thermal status、Wi-Fi、開始／結束時間、Audio Error、Dropped Frame 與 Service Uptime。

## 手動步驟

1. 強制停止 App，確認 Service、AudioRecord 與 WakeLock 不存在。
2. 啟動 Activity 但不要按按鈕；再次確認沒有錄音。
3. 由使用者按「啟動常駐 KWS」。確認通知存在、Audio Frame 持續增加、Audio Source 是 `VOICE_RECOGNITION`（不支援時才允許 `MIC`）、Audio Error／Dropped Frame 初始為 0。
4. 確認系統同時只有一個屬於本 package 的 active AudioRecord，格式為 mono／16000 Hz／PCM16。
5. 說「小助手」並確認 Wake Count、Timestamp 更新且播放固定短音；不能出現 TTS、ASR 文字或網路請求。
6. 從通知停止，確認 AudioRecord、WakeLock、Service 與通知都消失。
7. 重新啟動後鎖屏／熄屏，先測 30 分鐘，再測接電 8 小時與未接電 8 小時。
8. 一公尺正常音量測 20 次（至少三位說話者），目標至少 18 次；每次間隔超過 2 秒 cooldown。
9. 安靜房間連續 8 小時，目標誤喚醒不超過 1 次；另外記錄電視背景 8 小時，但不先宣稱門檻。

## ADB 證據

所有指令都必須指定實體 X60 serial，避免誤用已連線模擬器：

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$serial = "9654983303001DD"

& $adb -s $serial shell dumpsys activity services com.jajafu.x60assistant
& $adb -s $serial shell cmd appops get com.jajafu.x60assistant RECORD_AUDIO
& $adb -s $serial shell dumpsys audio | Select-String 'com.jajafu.x60assistant|VOICE_RECOGNITION' -Context 1,2
& $adb -s $serial shell dumpsys power | Select-String 'com.jajafu.x60assistant:VoiceKws'
& $adb -s $serial shell dumpsys battery | Select-String 'status:|level:|temperature:'
& $adb -s $serial shell dumpsys thermalservice
```

8 小時測試若中斷、未跑滿或缺少開始／結束證據，結果只能是「待執行」或「未完成」，不能寫成通過。
