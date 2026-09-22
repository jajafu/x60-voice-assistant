# 疑難排解

## 無法啟動

- 必須從可見 Activity 按啟動，並允許麥克風及通知。Android 13 不允許 BootReceiver 或一般背景工作取得 while-in-use 麥克風。
- 若顯示 `ASR_MODEL_INVALID`，重新執行 `prepare-m2-model-packs.ps1 -Pack small -InstallToDevice`；SHA 不符時 App 會刻意拒絕載入。
- vivo 請人工允許自啟動、背景高耗電，並在最近任務鎖定 App。Android 電池最佳化白名單不是 OEM 保活保證。

## 喚醒或辨識不準

- 確認 Audio Source 是 `VOICE_RECOGNITION`、Audio Error／Dropped Frame 為 0，並在約一公尺正常音量測試。
- `VAD_NO_SPEECH`：在提示音結束後 5 秒內開始；`VAD_SEGMENT_TOO_SHORT`：至少持續 250 ms；`VAD_MAX_DURATION`：單句預設 20 秒。
- 不要用合成語音結果取代真人召回率或辨識驗收。

## LLM 連線

- Base URL 必須是 HTTPS；Endpoint 通常是 `/chat/completions`。程式會避免重複 `/v1` 及 endpoint。
- 401/403：更新 Key；404：檢查 Model 與 Endpoint；429：等待 Provider 限流解除；Timeout：檢查網路或提高 Read Timeout。
- Key 只能在手機 App 輸入。不要貼到 issue、聊天、ADB、Logcat 或 `local.properties`。

## 沒有聲音

- 先按「測試 TTS」。App 優先 zh-TW 離線 voice、其次 zh-CN；可在系統 TTS 設定安裝語音資料。
- Audio Focus 被其他 App 長期占用時，TTS 會停止；回答文字仍顯示於主畫面。

## Boot 後沒有錄音

這是預期行為。Boot 通知只開啟 App，使用者仍需明確按「啟動語音助理」。
