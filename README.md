# 小助手 X60 Voice Assistant

本 Repository 目前完成 **M1：常駐本機喚醒詞診斷**。使用者必須在可見 Activity 按下「啟動常駐 KWS」，App 才會建立 microphone Foreground Service、唯一的 `AudioRecord` 與 sherpa-onnx KWS。偵測到「小助手」只播放短提示音；尚未包含 VAD、ASR、LLM、TTS、Follow Up 或完整問答流程。

## M1 基準

- Package：`com.jajafu.x60assistant`
- App：`0.2.0-m1`（versionCode 2）
- Kotlin + Jetpack Compose Material 3
- `minSdk 26`、`targetSdk 35`、`compileSdk 35`
- ABI：`arm64-v8a`
- Audio：16000 Hz、mono、PCM 16-bit、20 ms frame
- Audio source：`VOICE_RECOGNITION` 優先，建立或啟動失敗才使用 `MIC`
- Runtime：sherpa-onnx `1.13.8`
- Model：`sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20`
- Wake word：`小助手`

## 模型準備

模型 binary 不納入 Git。先執行固定來源與 SHA256 驗證的 provisioning script：

```powershell
.\scripts\prepare-m1-kws-assets.ps1
```

官方 archive 是 32,885,699 bytes，SHA256：

```text
68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6
```

重要：sherpa-onnx runtime 為 Apache-2.0，但上游模型 archive 沒有模型專屬 `LICENSE` 或 `NOTICE`，官方頁面也未明示權重授權。此 script 只供本機個人評估；在授權釐清前不要提交或再散布產生的模型檔。完整查核見 [`docs/model-packs.md`](docs/model-packs.md)。

## 建置

需求：JDK 17、Android SDK Platform 35，以及已準備的 KWS assets。

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

## vivo X60 USB 安裝與啟動

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$serial = "9654983303001DD"

& $adb -s $serial install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -s $serial shell am start -W -n com.jajafu.x60assistant/.MainActivity
```

OriginOS 會顯示側載風險確認；由使用者在手機上確認。不可用背景廣播、開機事件或 ADB 指令代替 App 內的啟動按鈕來開始錄音。

## M1 診斷與驗收

啟動前先確認沒有 Service 或錄音：

```powershell
& $adb -s $serial shell dumpsys activity services com.jajafu.x60assistant
& $adb -s $serial shell cmd appops get com.jajafu.x60assistant RECORD_AUDIO
& $adb -s $serial shell dumpsys audio | Select-String com.jajafu.x60assistant
```

在可見 App 畫面按「啟動常駐 KWS」後，診斷頁會顯示 Audio Frame、Wake Count、configured score、Timestamp、Service Uptime、Audio Error、Dropped Frame、實際 Audio Source、WakeLock、最後錯誤碼與模型識別資料。原始音訊只在記憶體中的有界 queue 流動，不落盤、不上傳。

```powershell
& $adb -s $serial shell dumpsys audio | Select-String 'com.jajafu.x60assistant|VOICE_RECOGNITION' -Context 1,2
& $adb -s $serial shell dumpsys power | Select-String 'com.jajafu.x60assistant:VoiceKws'
```

完整測試矩陣與記錄欄位見 [`docs/testing.md`](docs/testing.md)，目前 X60 實際結果見 [`docs/test-reports/m1-2026-09-22-vivo-x60.md`](docs/test-reports/m1-2026-09-22-vivo-x60.md)。8 小時接電、未接電及安靜環境測試必須真的跑滿 8 小時才能標記通過。

## 安全與範圍

- 不宣告 `INTERNET`，M1 沒有音訊上傳或網路語音功能。
- 不保存 PCM、逐字稿或任何原始音訊。
- 沒有 BootReceiver，不從 `BOOT_COMPLETED` 啟動 microphone FGS。
- 不使用 Root、Accessibility、Overlay 或 Alarm 保活。
- 初始策略由 Service 全程持有 non-reference-counted `PARTIAL_WAKE_LOCK`；所有正常停止、初始化失敗、讀取錯誤與 Service 銷毀路徑都釋放 AudioRecord、sherpa handle、queue、提示音與 WakeLock。
- vivo X60 長期接電前需保持通風，定期檢查電池膨脹、外殼變形與異常溫度。
