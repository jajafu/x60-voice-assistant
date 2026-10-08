# 系統現況：小助手 X60 Voice Assistant

> 以目前 README、Gradle、Manifest 與程式碼為準；早期規劃文件中的未完成方案不視為已實作。

## 架構與技術

Android Activity 管理設定與顯示，使用者啟動的 microphone foreground service 持有音訊與生命週期；`VoiceOrchestrator` 統一狀態切換，將本機 KWS → VAD → 本機 X-ASR 或選用的整句雲端 Whisper ASR，其文字優先送本機音樂／提醒／導航／音量指令，再將一般問答送使用者設定的 OpenAI 相容服務，按句交給系統 TTS 或選用的雲端 TTS。設定與提醒留在裝置；APK 內的 X-ASR 壓縮檔經校驗後安裝到 App 私有空間。

主要技術：Kotlin、Jetpack Compose／Material 3、Android Gradle Plugin、Coroutines、sherpa-onnx、OkHttp、DataStore、Android Keystore、WorkManager、Media3；單一 Android `app` 模組，compile SDK 36、target SDK 35、最低 SDK 26、APK 限 `arm64-v8a`。目前產品版號為 `1.0.4`／`versionCode 38`，依 `app/build.gradle.kts` 查證。

`VoiceForegroundService` 擁有語音服務生命週期與資源；`AudioCaptureEngine` 建立唯一 `AudioRecord`，以 16 kHz／mono／PCM16／20 ms frame 送入有界佇列。`VoiceOrchestrator` 是唯一狀態切換者，將喚醒後語音交給 VAD／ASR；本機 `MusicCommandRouter`、`NavigationCommandRouter`、`ReminderCommandRouter` 與 `VolumeCommandRouter` 優先處理明確指令，一般問答才走 `OpenAiCompatibleLlmProvider` 的 SSE，完整句送 TTS，結束後可進 Follow Up。實際分支、失敗與恢復策略依程式碼及 `docs/decision-log.md`。

## 重要目錄與模組

`VoiceOrchestrator` 的子工作由專用 Job 管理；切換工作時先取消並等待舊工作完成，事件附工作世代以排除過期結果。回答思考與朗讀期間另以現有 KWS 監聽喚醒詞；再次聽到喚醒詞時取消舊串流與 TTS，保留已暫停的音樂／鬧鐘狀態，播放喚醒回覆、清除其回音後直接進入新一輪 VAD／ASR。服務關閉的背景清理等待所有使用 native handle 的工作結束後才釋放資源。音訊、KWS 與喚醒回覆透過小型介面注入，讓 JVM 測試能驗證跨元件順序；不代表已驗證裝置或 sherpa native 行為。

所有結束一輪語音的出口（`returnToIdle`、`handlePause`、`handleStop`、`handleSessionFailed`、`beginAudioRecovery`、`close`）一律遵守同一順序：先取消 LLM 串流並停止 TTS，再恢復本輪暫停的鬧鐘與音樂。順序相反會讓音樂先播出、正在朗讀的舊回答才補完，聽起來像回答延遲。`beginAudioRecovery` 曾經是唯一寫反的出口，rc26 修正並加上回歸測試。

`StreamingSpeechPlayer` 同時接收回覆與按句朗讀，以 12 個 permit 限制待讀句數，滿時暫停生產者。需要音樂拒絕補救的回覆先緩衝完整文字，決定路由後才朗讀。每次回答獨立持有佇列，取消不會殘留到下一次。

`ConversationHistory` 只保存在 process 記憶體，容量由 `contextTurns` 決定。喚醒詞開啟新一輪（由 `IDLE_KWS` 進入的 `WakeDetected`）時先清空歷史，因此只有同一輪 Follow Up 視窗（`followUpSeconds`，預設 3 秒）內、不必再說喚醒詞的追問才帶著上一輪上下文。回答期間插話的喚醒詞不清空，但該輪回答已被取消、不會寫入歷史。`contextTurns` 因此只影響同一個連續話題內的輪數，跨喚醒詞不保留。

`TtsEngineCatalog` 讀取裝置上的 TTS 引擎與 voice。設定頁只顯示中文 voice（引擎若無中文則顯示全部），排序為繁中優先、離線優先、名稱，並與 `AndroidTtsEngine` 的自動選擇共用同一套規則。繁中判斷同時支援以書寫系統（`zh-Hant`）與以地區（`zh-TW`／`zh-HK`／`zh-MO`）標記的 locale，因為 vivo 引擎回報的是前者。引擎本身仍保有全部 voice，App 不會要求引擎刪除。

- `app/src/main/java/com/jajafu/x60assistant/`：Activity、UI 與 Android 應用入口。
- `service/`、`audio/`、`orchestration/`：前景服務、單一音訊來源、狀態機與恢復策略。
- `kws/`、`vad/`、`asr/`、`model/`：本機語音偵測／固定 X-ASR 辨識、APK 內建模型安裝與 SHA256 驗證；舊版下載工作保留升級相容處理。
- `music/`、`reminder/`：媒體操作與本機提醒路由、執行與排程。
- `navigation/`：語音導航的本機指令解析、地圖 App 目錄與路線預覽啟動。只開啟使用者選定的地圖 App 到路線預覽頁，最後的開始導航由使用者點擊。
- `volume/`：本機 `STREAM_MUSIC` 音量指令解析、級別換算與 Android `AudioManager` 執行（相對調整用 `adjustStreamVolume`，絕對音量用 `setStreamVolume`）；不使用無障礙設定頁，不提供語音靜音／解除靜音。
- `llm/`、`tts/`：可設定的 OpenAI 相容 SSE 用戶端與 Android 語音輸出；另有預設關閉的雲端語音 spike（`/audio/speech` TTS、`whisper-1` 整句上傳 ASR），使用獨立 Audio Base URL／Key，失敗不靜默退回本機；未完成 Audio 設定時則維持本機引擎。
- `data/`、`security/`、`diagnostics/`：DataStore 設定、Keystore 密鑰、有限且遮罩的診斷。
- `app/src/test/`、`app/src/androidTest/`、`docs/test-reports/`：單元測試、裝置測試與真機紀錄。
- `scripts/`、`docs/model-packs.md`：模型資產準備及來源／授權資訊。

## PR 驗收產物識別

人工驗收的來源是 Draft PR 目前 HEAD。Android 可安裝驗收產物以完整 commit 和 APK SHA-256 對應，並記錄 package、`versionName`／`versionCode` 及測試裝置；X60 功能證據依 `docs/testing.md` 記錄模型識別、裝置狀態與執行結果。此 Repository 沒有 Preview 網站、套件發布或部署 revision。`assembleDebug` 是既有必要驗證，可在 PR 前建置；建立 Draft PR 後要確認來源 commit 與建置輸入仍符合 HEAD，受測內容未變則沿用該產物。

## 資料與外部整合

- 16 kHz PCM frame 保持在記憶體的 50-frame 有界佇列；逐字稿、回覆與近期對話預設只在 process 記憶體，不寫入診斷匯出。
- 非敏感設定使用 DataStore；API Key 由 Android Keystore AES-GCM 加密；提醒由裝置內儲存與 Android alarm 排程，重開機或時間變動後補排程。
- KWS／VAD／ASR 模型本機執行；X-ASR 壓縮檔由 `scripts/prepare-bundled-xasr.ps1` 放進被 Git 忽略的 APK assets，建置與首次啟動時依現有程式校驗 SHA256，解壓至 App 私有空間。App 不再提供 ASR 模型下載／選擇；舊設定改用 X-ASR，既有模型檔不自動刪除。模型 binary 不進 Git。
- 一般問答由使用者在 App 設定的 OpenAI 相容 HTTPS endpoint 處理，沿用 LLM Base URL／Model／Endpoint／Key；雲端 TTS／Whisper 另使用 Audio Base URL／Key。未提供對應 API Key 時不能宣稱真實 Provider 已驗收。
- 選定的播放器以 Android 媒體服務／MediaSession 控制；MusicCabin 收藏與循環另需使用者授予通知存取。裝置提醒使用系統鬧鐘與通知。
- 語音導航以 Intent 開啟使用者選定的地圖 App 到路線預覽頁，不查詢任何雲端地理編碼服務。

## 需保持相容的介面／資料

- `com.jajafu.x60assistant` application ID、最低／目標 SDK、`arm64-v8a`、錄音格式與 foreground service 啟動／停止規則不可無意改動。
- DataStore 設定、Keystore 密文、提醒持久化格式與 X-ASR 模型包 ID／SHA256 需在變更時檢查升級相容；舊版 ASR 選擇遷移及既有檔案保留行為見 `README.md`、`docs/model-packs.md`。
- OpenAI 相容端點使用可設定 Base URL／Model／Endpoint 及 SSE；Release 僅 HTTPS，Debug HTTP 僅限明列本機位址。
- 音樂控制依對方公開媒體能力；不能將發出指令一概表述為已播放。MusicCabin 喜愛清單播放另依 rc19 已記錄例外，詳見 `docs/known-issues.md`。
- 診斷不得輸出 PCM、完整逐字稿、回答、API Key 或敏感 URL 部分；模型授權與分發限制依 `docs/model-packs.md`。

## 測試方式

- `app/src/test/` 的 JUnit、Coroutines Test 與 MockWebServer 覆蓋狀態機、指令路由、模型、SSE、密鑰與提醒等邏輯；`app/src/androidTest/` 含 Keystore 裝置測試。
- `.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug` 為程式變更的基本自動驗證；KWS 與內建 X-ASR 資產須依 `README.md` 先準備。行為變更且有對應 APK 時，按 `docs/ANDROID_MUMU_SMOKE.md` 記錄可行的安裝／啟動 smoke，仍以 X60 真機資料判定語音功能。
- vivo X60 實機依 `docs/testing.md` 與 `docs/test-reports/` 記錄錄音、鎖屏、音樂、提醒、真實 Provider 與長時間穩定性；短測及合成音不替代正式門檻。

## 已知風險

- 真實 Provider 需使用者在 App 內提供私人設定；現有 MockWebServer 結果不能替代真實服務驗收。
- vivo 背景保活、長時間鎖屏、真人語音與部分 MusicCabin 操作仍須依 `docs/testing.md`、`docs/known-issues.md` 完成驗收。
- 模型來源／授權與含模型 APK 分發受 `docs/model-packs.md`、`docs/decision-log.md` 限制。
- Release variant 尚未配置正式簽章，不可將 `assembleRelease` 產物當作正式可發行版本。
