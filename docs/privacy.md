# M1 Privacy

- 麥克風只能由可見 Activity 中的使用者按下啟動按鈕後開啟。
- App 沒有 BootReceiver，不從開機、Alarm、Accessibility、Overlay 或背景自動建立 microphone FGS。
- `AudioRecord` 的 PCM16 frame 只進入記憶體中的 50-frame 有界 queue；消費後即可回收，不寫入檔案、資料庫、Logcat 或診斷記錄。
- M1 Manifest 不宣告 `INTERNET`，沒有音訊、keyword 或診斷上傳路徑。
- 提示音是固定 Tone，不使用 TTS，也不包含使用者語音內容。
- Ring Log 最多 200 筆、只存在 process 記憶體；欄位會移除換行並截斷為 160 字元。它只記狀態、固定錯誤碼、計數與安全原因，不記 PCM、完整 tokens、序號或 Android ID。
- 模型完整性檢查只讀 APK assets 並計算 SHA256，不傳送任何資料。
- App 強制停止後不自行復活。正常停止、錯誤與 Service 銷毀路徑會 release AudioRecord、WakeLock、sherpa handle、提示音與 queue。
