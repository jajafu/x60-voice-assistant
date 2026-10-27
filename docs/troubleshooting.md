# 疑難排解

## 無法啟動

- 必須從可見 Activity 按啟動，並允許麥克風及通知。Android 13 不允許 BootReceiver 或一般背景工作取得 while-in-use 麥克風。
- 1.0.16 起語音辨識只使用手機系統服務：若啟動停在 `ASR_INIT_FAILED`（`system-asr-unavailable:*`），表示系統語音辨識不存在或明確不支援中文。檢查系統語音服務（如 Google 語音服務）是否已啟用／可連網，裝置不支援時本 App 無替代路徑。
- 系統辨識路徑沒有本機 VAD 端點：等待語音與單句上限沿用 VAD 設定（預設等 5 秒、單句 20 秒），逾時視為沒聽到並回到待喚醒。若系統辨識常「沒反應」，檢查系統語音服務（如 Google 語音服務）是否可用／可連網。
- 部分廠牌（如 vivo）請人工允許自啟動、背景高耗電，並在最近任務鎖定 App。Android 電池最佳化白名單不是 OEM 保活保證。

## 喚醒或辨識不準

- 確認 Audio Source 是 `VOICE_RECOGNITION`、Audio Error／Dropped Frame 為 0，並在約一公尺正常音量測試。
- 自訂喚醒詞限 2–8 個常用中文字，建議 3–5 個清楚音節。特殊讀音或多音字若辨識不穩，改用較單純的詞並重新啟動服務。
- 喚醒回覆播完即可說指令；收音持續運作，回覆播完時會清空音訊佇列。若在回覆尚未播完時就說話，開頭仍可能被清掉。
- `VAD_NO_SPEECH`：在喚醒回覆結束後 5 秒內開始；`VAD_SEGMENT_TOO_SHORT`：至少持續 250 ms；`VAD_MAX_DURATION`：單句預設 20 秒。
- 不要用合成語音結果取代真人召回率或辨識驗收。

## 如何打斷回答

- 朗讀時可直接再說「小助手」中斷回答；聽到「我在」後重新提問。若回答本身說出或近似喚醒詞，可能自行觸發；也可從常駐通知按「閉嘴」取消回答。
- 停止後狀態會回到等待喚醒，再說設定的喚醒詞即可重新提問。
- 正常回答結束後若畫面顯示「等待追問」，可在 Follow Up 秒數內直接說下一句，不必再次說喚醒詞。

## LLM 連線

- Base URL 必須是 HTTPS；Endpoint 通常是 `/chat/completions`。程式會避免重複 `/v1` 及 endpoint。
- 401/403：更新 Key；404：檢查 Model 與 Endpoint；429：等待 Provider 限流解除；Timeout：檢查網路或提高 Read Timeout。
- Key 只能在手機 App 輸入。不要貼到 issue、聊天、ADB、Logcat 或 `local.properties`。

## 沒有聲音

- 先按「測試 TTS」。App 優先 zh-TW 離線 voice、其次 zh-CN；可在系統 TTS 設定安裝語音資料。
- Audio Focus 被其他 App 長期占用時，TTS 會停止；回答文字仍顯示於主畫面。

## Boot 後沒有錄音

這是預期行為。Boot 通知只開啟 App，使用者仍需明確按「啟動語音助理」。
