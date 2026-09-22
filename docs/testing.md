# M1–M5 Testing

## 自動化

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\scripts\prepare-m2-model-packs.ps1 -Pack small
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Unit tests 覆蓋權限啟動策略、bounded Audio/TTS queue、狀態機、VAD endpoint/timeout、模型 ID/SHA、URL 正規化、SSE/`[DONE]`、HTTP retry、MockWebServer、AES-GCM round trip、secret redaction、Markdown/SpeechSegmenter、Follow Up monotonic timeout、Audio recovery threshold、Boot reminder policy 與安全診斷匯出。

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

## M2–M5 完整流程

1. 啟動前確認沒有錄音；在 Activity 按啟動後確認唯一 mono/16 kHz/PCM16 recorder。
2. 說「小助手」，提示音後說一整句，確認 `LISTENING → TRANSCRIBING → ROUTING`、VAD endpoint、ASR partial/final、RTF 與 final latency。
3. 在 App 內填私人 Provider 設定並主動測試連線；401/403、404、429、5xx、timeout、SSE 中斷以 MockWebServer 自動化，真實服務只記實際結果。
4. 驗證回答按完整句朗讀、Markdown 不朗讀、TTS 期間沒有 AudioRecord、取消／閉嘴會取消 HTTP 並清 queue。
5. TTS 後確認 `FOLLOW_UP` 10 秒可直接追問，逾時回 `IDLE_KWS`，前後 session 不重疊。
6. 從 App 與鎖屏通知測暫停、恢復、取消、閉嘴、停止；每次停止確認 recorder、WakeLock、HTTP、TTS 與通知釋放。
7. Reboot 後只應出現提醒，Service/recorder 不存在；開啟 Activity 後仍不能自動錄音。
8. 強制停止後等待至少 10 分鐘，確認 service、通知、recorder 都不復活。

正式門檻仍包括：真人 20 句至少 18 句保留語意、30 次連續無 OOM/重啟、RTF median &lt; 0.6、final P95 &lt; 3 秒，以及接電／未接電／安靜環境各 8 小時與建議 24 小時穩定性。短測、合成音、MockWebServer 都不得標成正式驗收通過。
