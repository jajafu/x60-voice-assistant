# Privacy

- 麥克風只能由可見 Activity 中的使用者按下啟動按鈕後開啟。
- BootReceiver 只顯示「點擊以恢復」提醒；不從開機、Alarm、Accessibility、Overlay 或背景自動建立 microphone FGS。
- `AudioRecord` 的 PCM16 frame 先進入記憶體中的 50-frame 有界 queue。音訊不寫入檔案、資料庫、Logcat 或診斷記錄。
- 辨識引擎選擇（1.0.16 起）：只使用系統語音辨識（啟動時探測），不再有本機模型後備。
- 系統語音辨識：系統服務自己開麥收音，語音與辨識結果由該系統服務處理，是否上傳／以何雲端處理依各裝置廠商服務而定，不受本 App 控制。
- 本機指令路由優先處理辨識文字，未命中的一般問答才會送到使用者自行設定的 OpenAI-compatible HTTPS Provider；本機 PCM、keyword audio 與診斷不會上傳。
- 喚醒詞偵測與 VAD 永遠在本機。系統語音辨識路徑的語音去向依各裝置系統服務決定（見上）。
- 音樂播放請求若進入 LLM 且回覆表示無法播放，App 會在本機檢查回覆中的歌手或歌曲名稱並嘗試交給已選音樂 App；不會為此另外發送 LLM 請求。
- LLM API Key 使用 Android Keystore 不可匯出 AES-GCM key 加密；App Private Storage 只含 version、alias、IV 與 ciphertext。
- Transcript、回答及最近對話不寫入 DataStore；停止 process 即消失。設定頁可清除 Key、設定與目前記憶體診斷。
- 排程提醒是例外：提醒文字、觸發時間與隨機 ID 會保存在 App 的 device-protected private storage，讓重新開機後能補排程；內容不會送到 LLM 或其他網路服務，觸發後即刪除，也可由設定頁的清除功能一併刪除。
- 音樂 App 選擇器只在本機查詢宣告公開媒體瀏覽服務的已安裝 App；保存所選 App 的顯示名稱、套件名稱與服務類別，不上傳安裝清單。
- MusicCabin 的播放、暫停、切歌、點歌、喜愛清單、循環、收藏與 App 內音量都走它自己的公開語音通道（有序廣播），不需要小助手的通知存取權。收藏變更要由使用者在 MusicCabin 的「設定 → 隱私 → 語音控制」開啟外部收藏，開關由音樂 App 自己管；被關掉時小助手只回覆請對方去開設定。通知存取只在小助手要用車機 fallback（實際在播的是別的播放器時代為暫停）時才需要；Android 此權限同時允許讀取其他 App 通知，小助手的通知監聽服務不處理或保存通知內容，且可隨時在系統設定撤銷。
- Ring Log 最多 200 筆、只存在 process 記憶體；欄位會移除換行並截斷為 160 字元。它只記狀態、固定錯誤碼、計數與安全原因，不記 PCM、完整 tokens、序號或 Android ID。
- App 更新檢查只讀取 GitHub release 資訊，下載的 APK 在 App 內經系統安裝程式安裝。（1.0.16 起不再有 App 內的 X-ASR 模型下載。）
- 安全診斷匯出不含 Key、Authorization、完整私人 URL、PCM、逐字稿或回答，只含 host、長度、hash、狀態、計數與錯誤碼。
- App 強制停止後不自行復活。正常停止、錯誤與 Service 銷毀路徑會 release AudioRecord、WakeLock、sherpa handle、HTTP/SSE、TTS、Audio Focus 與所有 queue。
