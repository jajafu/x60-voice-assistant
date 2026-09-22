# Decision Log

## M0（2026-09-22）

- 固定 `targetSdk 35`，採單一 `app` module 與 Version Catalog。
- 前景服務在 M0 即宣告 `microphone` 類型，以驗證正式架構的權限與啟動限制；服務本身不建立任何音訊物件。
- 麥克風服務只由可見 Activity 中的使用者操作啟動，且使用 `START_NOT_STICKY`。
- API 33+ 將通知權限視為產品啟動條件，確保常駐服務通知可由使用者看見與停止。
- 電池最佳化只使用標準狀態 API 與標準設定入口，不依賴 vivo 私有 Component。
- M0 不實作 BootReceiver；開機提醒延後到規格排定的 M4，且永不由開機廣播直接啟動 microphone FGS。

## M1（2026-09-22）

- `VoiceForegroundService` 是 AudioCaptureEngine、sherpa KWS、提示音與 WakeLock 的生命週期總擁有者；`AudioRecord` 只能由 `AudioCaptureEngine` 建立及釋放。
- 固定 16000 Hz、mono、PCM16、20 ms frame；先用 `VOICE_RECOGNITION`，只有初始化或開始錄音失敗才完整 release 後改用 `MIC`。
- Audio Thread 設為 `THREAD_PRIORITY_URGENT_AUDIO`，只 blocking read、時間戳、enqueue 與原子計數；sherpa 在獨立執行緒消費固定 50-frame queue，滿時丟最舊 frame。
- M1 初始策略由 Service 全程持有 non-reference-counted `PARTIAL_WAKE_LOCK`；後續需以完整 8 小時測試比較無全程 WakeLock 的結果。
- 提示音採 `ToneGenerator.TONE_PROP_ACK`，不初始化或呼叫 TTS。
- sherpa-onnx runtime 鎖定 `1.13.8`；官方 AAR 由固定 release URL 解析，並限定 APK 只含 `arm64-v8a`。
- KWS 模型固定為 `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` 的 chunk-16 INT8 encoder／joiner組合。模型不提交 Git，由 provisioning script 驗證 archive 與逐檔 SHA256 後放入 ignored assets。
- 上游模型未提供模型專屬授權；只進行本機個人評估，禁止把模型授權誤寫為 sherpa runtime 的 Apache-2.0，禁止再散布模型或含模型 APK。
- sherpa 公開 `KeywordSpotterResult` 不提供 confidence；診斷頁 Score 顯示 configured keyword boosting score 1.5 並明確標示不是 confidence，threshold 固定 0.25。
- Ring Log 只存在記憶體，固定 200 筆，欄位去除換行並限制 160 字元；不記 PCM、tokens、硬體識別碼或任意 exception message。
