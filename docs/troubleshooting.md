# 疑難排解

## 無法啟動

- 必須從可見 Activity 按啟動，並允許麥克風及通知。Android 13 不允許 BootReceiver 或一般背景工作取得 while-in-use 麥克風。
- 若顯示 `ASR_MODEL_INVALID`，確認 App「設定 → 本機語音辨識」選用的模型是否顯示「已下載並驗證」；可按該模型的下載或重新下載按鈕。也可執行 `prepare-m2-model-packs.ps1 -Pack standard|moonshine|xasr -InstallToDevice -Serial <serial>`。SHA 不符時 App 會刻意拒絕載入。
- 模型下載鎖屏後仍可繼續；允許通知時會顯示進度。網路中斷會等待連線並自動續傳；若顯示「下載暫停」，回到「設定 → 本機語音辨識」按「繼續下載」。已下載的部分保存在 App 私有儲存空間；清除 App 資料或解除安裝會失去該進度。若伺服器不支援 Range，App 會從頭下載並重新校驗。
- vivo 請人工允許自啟動、背景高耗電，並在最近任務鎖定 App。Android 電池最佳化白名單不是 OEM 保活保證。

## 喚醒或辨識不準

- 確認 Audio Source 是 `VOICE_RECOGNITION`、Audio Error／Dropped Frame 為 0，並在約一公尺正常音量測試。
- 自訂喚醒詞限 2–8 個常用中文字，建議 3–5 個清楚音節。特殊讀音或多音字若辨識不穩，改用較單純的詞並重新啟動服務。
- 喚醒回覆播完即可說指令；收音持續運作，回覆播完時會清空音訊佇列。若在回覆尚未播完時就說話，開頭仍可能被清掉。
- `VAD_NO_SPEECH`：在喚醒回覆結束後 5 秒內開始；`VAD_SEGMENT_TOO_SHORT`：至少持續 250 ms；`VAD_MAX_DURATION`：單句預設 20 秒。
- 不要用合成語音結果取代真人召回率或辨識驗收。

## 如何打斷回答

- App 在朗讀時按「停止回答」，或從常駐通知按「閉嘴」；目前朗讀期間會停止麥克風以避免手機喇叭誤觸喚醒，因此不要用喚醒詞直接插話。
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
