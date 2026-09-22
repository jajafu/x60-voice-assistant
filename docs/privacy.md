# Privacy

- 麥克風只能由可見 Activity 中的使用者按下啟動按鈕後開啟。
- BootReceiver 只顯示「點擊以恢復」提醒；不從開機、Alarm、Accessibility、Overlay 或背景自動建立 microphone FGS。
- `AudioRecord` 的 PCM16 frame 只進入記憶體中的 50-frame 有界 queue；消費後即可回收，不寫入檔案、資料庫、Logcat 或診斷記錄。
- 只有本機 ASR 的文字會送到使用者自行設定的 OpenAI-compatible HTTPS Provider；PCM、keyword audio 與診斷不會上傳。
- API Key 使用 Android Keystore 不可匯出 AES-GCM key 加密；App Private Storage 只含 version、alias、IV 與 ciphertext。
- Transcript、回答及最近對話不寫入 DataStore；停止 process 即消失。設定頁可清除 Key、設定與目前記憶體診斷。
- Ring Log 最多 200 筆、只存在 process 記憶體；欄位會移除換行並截斷為 160 字元。它只記狀態、固定錯誤碼、計數與安全原因，不記 PCM、完整 tokens、序號或 Android ID。
- 模型完整性檢查只讀 APK assets 並計算 SHA256，不傳送任何資料。
- 安全診斷匯出不含 Key、Authorization、完整私人 URL、PCM、逐字稿或回答，只含 host、長度、hash、狀態、計數與錯誤碼。
- App 強制停止後不自行復活。正常停止、錯誤與 Service 銷毀路徑會 release AudioRecord、WakeLock、sherpa handle、HTTP/SSE、TTS、Audio Focus 與所有 queue。
