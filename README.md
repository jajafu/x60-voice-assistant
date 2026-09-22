# 小助手 X60 Voice Assistant

Android 13／vivo X60 的本機喚醒語音助理 release candidate。使用者必須在可見 Activity 明確啟動 microphone foreground service；之後流程為：

`小助手 → 提示音 → VAD → 本機 ASR → OpenAI-compatible SSE → 完整句 TTS → Follow Up`

## 範圍與安全

- 單一 `AudioCaptureEngine` 擁有唯一 `AudioRecord`：16000 Hz、mono、PCM16、20 ms。
- KWS、VAD、ASR 使用 sherpa-onnx `1.13.8`；PCM 只存在 50-frame 有界記憶體 queue，永不落盤或上傳。
- API Key 由 Android Keystore 的不可匯出 AES-256-GCM key 加密；DataStore 只放非敏感設定。
- Release 僅允許 HTTPS；Debug HTTP 只白名單 `localhost`、`127.0.0.1`、`10.0.2.2`。
- Transcript、回答與最近 6 組對話預設只在 process 記憶體。
- `BOOT_COMPLETED` 只顯示提醒，不會啟動 microphone FGS；強制停止後不復活。

## 準備模型

模型 binary 不進 Git，也不得在授權未釐清前隨 APK 散布：

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\scripts\prepare-m2-model-packs.ps1 -Pack small -InstallToDevice -Serial 9654983303001DD
```

完整來源、大小與 SHA256 見 [docs/model-packs.md](docs/model-packs.md)，兩個 ASR 候選比較見 [docs/asr-benchmark.md](docs/asr-benchmark.md)。

## 建置

需求：JDK 17、Android SDK 35、arm64-v8a。

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。Release variant 未配置 production signing，因此只能視為未正式簽章的候選產物。

## 手機設定

1. 在 App 的 LLM Provider 區輸入 Base URL、API Key、Model 與 Endpoint（預設 `/chat/completions`）。
2. API Key 只能直接輸入手機 App；不要貼到聊天、終端或 source。
3. 按「測試連線」。成功後設定才標示可用。
4. 視需要調整 VAD、Follow Up、TTS 語速與音高，按「測試 TTS」。
5. 在可見畫面按「啟動語音助理」。設定變更需先停止服務。
6. 依畫面指引設定 vivo 自啟動、背景高耗電、最近任務加鎖；Android 電池白名單不等同 OEM 保活保證。

## 驗證與限制

自動化、ADB 證據及正式驗收門檻見 [docs/testing.md](docs/testing.md)。M2 短測使用 Windows 合成語音，不能等同真人 20 句或 30 次壓力驗收。沒有使用者在 App 內提供的私人 API Key 時，真實 Provider 測試必須保持「待驗收」；MockWebServer 只證明協定處理。

另見：[隱私](docs/privacy.md)、[疑難排解](docs/troubleshooting.md)、[已知限制](docs/known-issues.md)、[決策紀錄](docs/decision-log.md)、[第三方聲明](THIRD_PARTY_NOTICES.md)。
