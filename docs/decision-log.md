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

## M2（2026-09-22）

- `VoiceOrchestrator` 是唯一狀態切換者；Service 擁有 SupervisorJob、Audio、sherpa handle、LLM、TTS 與 WakeLock。
- VAD 使用官方 Silero model；ASR 選 small streaming Zipformer CTC 作 X60 預設，standard 候選只保留 benchmark provisioning。
- 大型 ASR model 放 App Private Storage，載入前逐檔 SHA256；簡繁轉換只改顯示及送出文字，不改原始診斷 hash。

## M3（2026-09-22）

- OpenAI-compatible Provider 使用 OkHttp 4.12.0：5.5.0 的 Android artifact 要求 compileSdk 37，與固定 SDK 35 衝突，因此選擇相容的固定版本。
- Provider 不控制 TTS；SSE 經嚴格 JSON parser、`[DONE]` 驗證與有界句子 queue。任何錯誤只結束 session，不重建 AudioRecord。
- API Key 由 Android Keystore AES-GCM 保存；URL 與其他非敏感設定使用 DataStore。Release network security 明確拒絕 cleartext。
- TTS 先找 zh-TW 離線 voice，再找 zh-CN；提示音與 TTS 都取得 transient-may-duck Audio Focus，播放時 AudioRecord 已停止。

## M4（2026-09-22）

- Follow Up 預設 10 秒，使用 monotonic clock；偵測到 speech 的第一個 frame 直接交給 ASR，避免字首遺失。
- Audio 復原採 0、1、2、4、8、30 秒上限；10 分鐘第 6 次失敗進 ERROR。TTS died 僅重建一次，之後降級顯示文字。
- BootReceiver 只發通知，PendingIntent 只開啟 Activity；仍需使用者按啟動才建立麥克風。

## M5（2026-09-22）

- 設定、控制、診斷集中於可捲動 Material 3 控制中心；互動元件至少 48 dp，狀態以文字而非只靠顏色表示。
- 診斷匯出省略完整逐字稿、回答、API Key、Authorization 與 URL path/query，只保留長度、hash、host、計數與安全錯誤碼。

## rc23（2026-09-28，Issue #7）

- 回答思考與 TTS 朗讀期間重新啟動既有單一 AudioRecord，只執行現有 KWS；再次聽到喚醒詞時取消舊 LLM 串流與 TTS，保留音樂／鬧鐘的暫停狀態，播放既有「我在」後清除音訊 queue，再進入完整 VAD／ASR。
- 沿用音樂外放時的喚醒取捨，不新增 AcousticEchoCanceler 或短暫抑制窗。回答自身說出或近似喚醒詞可能觸發新一輪聆聽，使用者已接受此風險；X60 實際音量下的召回與誤觸仍須真機驗收。

## rc25（2026-09-28，Issue #11）

- 喚醒詞是新話題的邊界：由 `IDLE_KWS` 進入的 `WakeDetected` 會先清空 `ConversationHistory`，只有同一輪 Follow Up 視窗內的追問才延續上下文。修正實測「問完一個話題後，後續問題仍持續回答舊話題」——原因是 `ConversationHistory.clear()` 從未被呼叫，歷史會累積整個服務生命週期。已由使用者在真機把 `Context Turns` 設為 0 確認症狀消失，據此定位為上下文殘留而非 Provider 品質。
- `contextTurns` 語意不變（同一個連續話題內最多保留幾輪），預設仍為 6，只是不再跨喚醒詞保留；使用者已決定不變更預設值。
- `followUpSeconds` 預設由 10 秒改為 3 秒，讓「只保留 Follow Up 視窗內那一輪」的邊界與實際口語對話節奏相符。既有安裝若已存過此設定則維持原值，不做資料遷移。
- 不在 `DEFAULT_SYSTEM_INSTRUCTION` 加「不要延續舊話題」一句：使用者決定不修改預設 system instruction，後續若仍有黏話題再評估。

## rc26（2026-09-29，Issue #12）

- 使用者回報：播放音樂中喚醒小助手，等候一段時間沒回應、音樂恢復後不久又傳出語音 TTS 回覆。靜態分析找到 `beginAudioRecovery` 是唯一把「恢復暫停的鬧鐘／音樂」排在「取消 LLM 串流並停止 TTS」之前的出口，其餘五條結束路徑順序都相反。改為先 `releaseSessionResources()` 再恢復，並加上回歸測試。
- 已用測試證實：修正前在 TTS 朗讀中注入音訊失敗，恢復音樂的當下 TTS 尚未被停止（`朗讀前=7，恢復時=7`）；修正後該測試通過。
- 本次只處理順序，不改喚醒流程、暫停／恢復音樂的策略、TTS 重試或音訊失敗的復原策略。
- 尚未處理、需另行決定：`handleWakeDetected` 把喚醒回應「我在」排在暫停鬧鐘與暫停音樂之後，而這兩者都是跨行程媒體往返（最壞約 4–5 秒靜默），且期間說的話會被 `frameQueue.clear()` 丟棄；`MusicCabinController.pauseForVoice` 在未確認暫停成功時仍回傳 `true`。這兩項需先取得 vivo X60 的診斷 Ring Log 才能確認是否為使用者當次的主因。

## rc27（2026-09-30，Issue #14）

- 使用者回報 TTS 聲音比音樂小聲。以 vivo X60 實測釐清：`dumpsys audio` 的播放器紀錄顯示 TTS 的 AudioTrack 是 `usage=USAGE_MEDIA content=CONTENT_TYPE_SPEECH`，uid 10064（`com.vivo.aiservice`），也就是 TTS 與音樂共用 STREAM_MUSIC 同一個音量級別（該機為 10/15）。**不是音量路由問題，是引擎輸出增益較低。**
- **開發者決定（rc27）**：`AndroidTtsEngine` 從未設定輸出音量，改為每個語句帶 `TextToSpeech.Engine.KEY_PARAM_VOLUME`；`TextToSpeech.setVolume()` 並非公開 API，因此不採用。Android 標示此參數範圍為 0.0–1.0，超過 1.0 是否放大取決於引擎，設定預設維持 1.0 以維持既有行為。
- 同時補上設定頁一直缺少的 TTS 引擎與 voice 選擇器：`ttsEngine` 與 `ttsVoice` 兩個欄位自 M3 起就存在於 `AssistantSettings` 與 `SettingsRepository`，但設定頁沒有任何 UI 可以編輯，使用者只能靠系統「文字轉語音輸出」頁面變更。新增 `TtsEngineCatalog` 讀取引擎清單（PackageManager）與 voice 清單（需綁定引擎，帶 4 秒逾時），排序與 `selectVoice` 的自動選擇一致。
- **不變更**：`USAGE_MEDIA` 不改成 `USAGE_ASSISTANT`（會連帶影響 requestAudioFocus 與喚醒回應、鬧鐘朗讀的混音行為，範圍過大）；不自動下載第三方語音包（沿用 `docs/known-issues.md` 既定設計）；未新增任何權限。
- 引擎清單讀不到語音資料時維持原行為：記 `TTS_ENGINE_MISSING` 並只顯示文字。

## rc28（2026-09-30，Issue #14 + Issue #15 整合）

- **開發者決定**：rc27（TTS 音量／音調選擇，PR #13）與 rc27（語音導航 Google 地圖，PR #15 / PR #16）原本各自獨立開發、互相不知情，兩邊都把版本設成 `rc27` / `versionCode 31`。整合時由本合併PR 統一升為 `1.0.0-rc28` / `versionCode 32`，避免 Android 因 versionCode 相同拒絕覆蓋安裝。
- 兩條分支都從 `main` 開出，重疊 6 個檔案；實際只有 `AssistantSettings.kt` 產生內容衝突，且是雙方各自新增不同欄位，保留兩邊即可。合併時另發現自動合併在該檔留下重複的 `/**`，造成巢狀註解破壞解析並產生一整串假的「Unresolved reference」錯誤；移除重複行後即恢復。其餘 5 個檔案自動合併結果正確（DataStore key 無撞名、兩邊 UI 都在、所有新欄位都有對應的 `normalized()` 夾限）。
- 路由順序為 關閉 → 提醒 → 音樂 → 導航 → LLM，導航插在音樂之後、LLM 之前，兩者沒有觸及同一組詞彙。
- 本次只做整合與版號統一，**未修改兩個功能的任何行為**。rc27 的 TTS 音量假設仍未在真機驗證，見 `docs/known-issues.md`。
