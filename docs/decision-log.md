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
- `zh-en-3M-2025-12-20` 的 ModelScope repo 自 2026-10-08 標示 Apache-2.0；但 sherpa-onnx GitHub archive 未附授權檔，且 archive 內 ONNX／`tokens.txt`／`en.phone` 與訓練資料的授權範圍尚待上游明確確認（追蹤 [#3802](https://github.com/k2-fsa/sherpa-onnx/issues/3802)）。不可把 runtime 的 Apache-2.0 自動套用到模型；上游確認前不公開散布含此模型的 APK。
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

## rc29（2026-09-30，Issue #16）

- 使用者在 vivo X60 實測後回報 TTS 音調清單太長（該機引擎回報 30 個以上語言），要求只保留繁中與簡中。
- **開發者決定**：只縮限**設定頁顯示的清單**，不嘗試刪除引擎內建的 voice。引擎沒有提供刪除 voice 的 API，而且保留全部 voice 才能在中文語音損壞時由系統層改用其他語言。引擎若完全沒有中文 voice，仍顯示完整清單而非空白。
- **修正一個只有真機才發現的缺陷**：`AndroidTtsEngine.selectVoice()` 原本以 `locale.country` 比對 `zh-TW`／`zh-CN` 來優先繁中與簡中，但 vivo 引擎回報的是**以書寫系統標記**的 `zh-Hant`／`zh-Hans`，其 `country` 為空字串，導致那兩條規則永遠不成立，只能靠最後的「任何中文」fallback 僥倖命中。這次改成以 `Locale.forLanguageTag` 取 `language` 與 `script`，同時相容書寫系統與地區兩種標記（`zh-Hant`、`zh-TW`、`zh-HK`、`zh-MO` 視為繁中）。
- 設定頁排序與自動選擇改為共用同一套規則（繁中優先、離線優先、名稱），避免清單第一個與實際被選中的不一致。
- 測試樣本改用 vivo 引擎實際回報的 locale 樣式（`zh-Hant`／`zh-Hans`），先前使用 `zh-TW` 的樣本無法涵蓋這個缺陷。

## rc30（2026-10-02，Issue #20）

- **開發者決定（語意）**：「音量調到 50」的 `50` 定義為**百分比**，目標級別 = `round(0.50 × 裝置上限)`，不在解析階段假設固定格數。這樣換手機不必改程式，回話會報出實際級別與上限。超出 0–100 的輸入夾到邊界而不是整句作廢，避免落回 LLM 亂猜。
- **開發者決定（範圍）**：第一輪**只做 `STREAM_MUSIC`**。鈴聲與通知不納入，因為 vivo 勿擾模式的行為差異會放大驗收範圍。
- **開發者決定（下限）**：語音可調整的音量下限設為 **2 格**。rc27 已實測 TTS 與音樂共用 `STREAM_MUSIC`，壓太低會讓小助手自己的回話與喚醒提示音一起變小聲。要求比下限更低時**不執行**，改為回報已到下限，不調成音量 0。
- **API 選擇更正**：`AudioManager.setStreamVolume()` 是公開 SDK API，且同樣使用 `MODIFY_AUDIO_SETTINGS`；先前將其記為 `@SystemApi` 是錯誤。本版仍沿用已實作的 `adjustStreamVolume(±1)` 逐格路徑，逐格提示音副作用與是否改用公開 setter 留待 review／真機驗收決定。相對音量**刻意不用** `adjustSuggestedStreamVolume`：它會依當下有焦點的播放器決定調整哪個 stream，沒有音訊焦點時可能落到 `STREAM_MUSIC` 以外，與「只動媒體音量」的範圍不符。
- **開發者決定（PR #21 review 時）**：不提供語音靜音／解除靜音選項。因此 `VolumeCommand` 僅保留 `Up`／`Down`／`SetTo(percent)`，相關解析分支、AudioManager 呼叫、回覆與測試皆移除。使用者可透過 Android 系統控制靜音。
- **PR #21 code review 修正**：逐格路徑第一步也必須比較目前級別與目標級別來選方向；已將方向規劃抽至 `VolumeTargetPolicy.adjustmentStepsTo()`，並以單元測試覆蓋上調、單格下調與多格下調。
- **開發者決定（PR #21 code review）**：相對調整一律一格，「音量調高一格／兩格」是格數不是百分比，解析比對時排除「格／级／級／个／個」等量詞。
- **開發者決定（PR #21 code review）**：解析只收「要求改變」的說法，移除「有點大／有点大／有點小／有点小」四個描述性詞，避免使用者只是在描述現況卻被反向操作；「音量大一點」仍因含「大一點」照常解析。
- **開發者決定（PR #21 code review）**：語音只處理音量與聲音。亮度、螢幕、屏幕、背光等詞一出現就整句不處理，因此「亮度調到最大」不會命中「調到最大」而誤改媒體音量；最大／最小的無名詞說法只保留「最大聲／最小聲」白名單。
- **開發者決定（PR #21 code review）**：調整後讀回 `getStreamVolume()`，只有讀回值等於目標才回報「音量已調到」，否則回報實際級別並標為未套用。
- **vivo X60 實測後的修正**：rc30 的 `adjustStreamVolume(STREAM_MUSIC, ±1)` 呼叫有送達系統，但音量級別沒有改變；目前實作改用公開 SDK 的 `setStreamVolume()` 一次設定目標，並在診斷頁記錄 current／target／readBack／max／fixed。此修正仍需用新 APK 在 X60 驗收。`WRITE_MEDIA_AUDIO` 是媒體檔案寫入 AppOp，與此操作無關；本功能使用 `MODIFY_AUDIO_SETTINGS`。
- **修正版 X60 驗收結果（2026-10-02）**：新 APK 的 `UP` 指令解析成功，呼叫 `setStreamVolume()` 後仍為 `current=10 target=11 readBack=10 max=15 applied=false fixed=false`。`MODIFY_AUDIO_SETTINGS` 已授予，`AUDIO_MEDIA_VOLUME` 為 allow；setter 在本機也無效。
- **使用者核准的範圍擴充（本對話，2026-10-02）**：使用者同意加入 Accessibility Service 作為 X60 fallback。服務只接收 `com.android.settings` 的視窗事件，在 Android「聲音與震動」頁定位「媒體」SeekBar，設定後返回；不保存或上傳 UI 文字。需要使用者在系統無障礙設定中親自啟用。
- **X60 無障礙 fallback 追測（2026-10-02）**：使用者回報多輪對話中只有一次「音量調到 50」成功。診斷顯示服務已啟用且手勢完成，但 `setProgress=false`；失敗的 100% 案例讀值為 `current=8 target=15 readBack=8 max=15`，SeekBar bounds 為 `150..1035`，range 為 `0..600`。bounds 含左右 thumb travel inset，舊版用整個 bounds 線性映射，使中間點可成功、端點落在實際軌道外。已改用依 SeekBar 高度估算的軌道內縮座標並重新安裝；端點語音操作仍待 X60 複測，不宣稱已通過。
- **與原 Plan 的偏離**：新增最大／最小聲映射到 `SetTo(100)`／`SetTo(0)`，避免這兩種常見說法落回 LLM 被假稱已完成；語音音量下限策略仍會阻止實際調到低於 2 格。
- **路由順序**：關閉 → 提醒 → 音樂 → 導航 → 音量 → LLM。音量插在導航之後，兩組詞彙沒有交疊；提醒路由雖帶數字優先級更高，但沒有裸數字規則，單元測試鎖住「音量調到50」不會被提醒路由吞掉。
- **回話文字集中於 `VolumeReply`**，並用單元測試鎖住「不可用或未改動時不得出現『音量已調到』」，呼應 `docs/phone-control-plan.md` §4.1 不虛報成功的原則。
- **本輪未做真機驗收**：依開發者指示先做 code review。`test`、`lint`、`assembleDebug` 通過；MuMu smoke 與 vivo X60 驗收均未執行，見 `docs/known-issues.md`。

## 2026-10-02：X60 鎖屏語音音量改用 ADB shell helper

- 使用者回報鎖屏時無障礙音量輔助仍無法控制；確認舊執行器在 AudioManager 寫入無效後會開啟系統設定頁，鎖屏時停在 `TIMED_OUT`。因此移除 Accessibility Service 的 Manifest 註冊與調用路徑。
- 使用者先前已明確要求每次重開機後用電腦 USB/ADB 重新啟用。X60 上 `cmd media_session volume --stream 3 --set N` 以 shell UID 執行可改變 STREAM_MUSIC；改採每次開機由 PowerShell 腳本透過 ADB 啟動的狹窄 app_process helper。它僅讀取 app-specific request 檔、驗證 nonce／時間／數值，固定呼叫 media_session 設定音量並讀回；不使用 Shizuku、root 或 Accessibility，也不接受任意命令字串。
- helper 未啟動時，App 明確回報需由電腦啟動；不再打開音效設定頁。重開機會終止 helper，使用者需再次連接 USB 執行 `scripts/start-x60-volume-helper.ps1`；可由 `scripts/stop-x60-volume-helper.ps1` 手動停止。
- 目前 `test`、`lint`、`assembleDebug` 通過。X60 在安裝前從 ADB 斷線，尚未確認 app_process 載入 APK、持續運作或鎖屏語音操作；真人鎖屏驗收未通過／待執行。

## 2026-10-04：移除 ADB shell helper，回到純 AudioManager

- 依使用者要求移除 ADB shell helper（含 `VolumeAdbShellBridge`、`VolumeAdbShellDaemon`、`VolumeShellProtocol`、啟動／停止腳本與診斷頁 helper 狀態），回到純 `AudioManager` 方案：相對調整用 `adjustStreamVolume(STREAM_MUSIC, ±1)`，絕對音量用 `setStreamVolume()`。
- 診斷頁保留「音量指令／音量實測」（current／target／readBack／max／applied／fixed＋path），未生效時仍如實回報。
## 1.0.5（2026-10-15，Issue #29）

- **開發者決定（範圍）**：MusicCabin 走新的公開語音通道（有序廣播），一次呼叫、對方回什麼就說什麼。舊媒體連線只留給非 MusicCabin 播放器與車機 fallback；舊版 MusicCabin 沒有新通道時提示更新，不留舊路備用（本對話已確認）。
- 新增 `PlayArtist`（只講歌手）、`RepeatAll`（全部循環）、`RepeatState`（查循環模式）三種本機指令；「想聽周杰倫的歌」這類結尾是「的歌／的歌曲」的短名字走歌手播，結尾是歌名的不受影響。
- 收藏／循環不再要求小助手的通知權限，改由音樂 App 的隱私開關管；被擋下時指引用戶去音樂 App 開設定。rc19「喜愛清單送出就不檢查」的例外在新通道退役：改播、收藏、循環都有明確回傳才說成功。
- 暫停／恢復／關閉音樂同樣走通道；空隊列按播放時對方改播喜愛，回話照喜愛的說法。
- 版號 `1.0.5`／`versionCode 39`。`test`、`lint`、`assembleDebug` 通過；X60 真機語音驗收待補做。
## 1.0.6（本對話，使用者核准：改由雲端硬碟下載模型＋App 內更新）

- X-ASR 改由 App「模型」頁按鈕從使用者雲端硬碟下載（單次下載、無續傳，失敗重按；下載中可取消，啟動服務會被擋下直到完成），下載後維持逐檔 SHA256 解壓安裝；APK 不再內建，`prepare-bundled-xasr.ps1` 已停用，release workflow 不再呼叫它。
- App 開啟檢查 GitHub releases 有無新版，有就在 App 內下載 APK（比對 release 檔大小）並叫起系統安裝，不跳瀏覽器；需「安裝未知應用」授權。限制：倉庫目前私有且零 releases，檢查一律 404 靜默；需設為公開並發 release，整條更新路徑才會動、才能真機驗收。
- 版號 `1.0.6`／`versionCode 40`。`test`、`lint`、`assembleDebug` 通過；模型下載→解壓→安裝、App 內更新安裝、MuMu 與真機語音驗收待補做。
## 1.0.7（本對話，使用者核准：修好 release APK 缺 VAD 模型）

- 使用者實測發現 release APK 按下「啟動語音助理」後沒有反應；診斷頁「最後錯誤」顯示 `VAD_INIT_FAILED`。核對 APK 內容確認 `assets/models/vad/silero_vad.onnx` 從未進入 CI 產物：release workflow 只執行 `prepare-m1-kws-assets.ps1`，而 VAD 一直是由 `prepare-m2-model-packs.ps1` 準備、該腳本未被 workflow 呼叫。Debug APK 因本機 assets 目錄已有該檔而正常，所以先前沒被發現；已發佈的 v1.0.6 APK 在任何手機都會啟動失敗。
- 新增 `scripts/prepare-vad-asset.ps1` 專責下載並校驗 Silero VAD；release workflow 改為同時執行 KWS 與 VAD 兩支腳本；`prepare-m2-model-packs.ps1` 改呼叫同一支腳本，URL／SHA256 只有一份。`app/build.gradle.kts` 新增 release 專用建置前檢查 `verifyReleaseModelAssets`（掛在 `preReleaseBuild`），缺 KWS／VAD 資產就讓建置失敗；debug／`test`／`lint` 不受影響。
- 已發佈的 v1.0.6 release 標記為 pre-release，避免下載到無法啟動的 APK；改以 1.0.7 取代。
- 版號 `1.0.7`／`versionCode 41`。`test`、`lint`、`assembleDebug` 通過；release APK 內容與真機啟動驗收見 Issue #36。
## 1.0.8（本對話，使用者核准：「照舊可先裝」方案＋review-loop 流程）

- 辨識引擎選擇改為：明確設定的雲端 Whisper 最優先 ＞ 系統語音辨識（啟動時探測）＞ 已下載的 X-ASR ＞ 全部不可用才報 `ASR_MODEL_INVALID` 並提示到「模型」分頁下載。使用者方向：系統可用就優先用，X-ASR 從「必下載」改為「可先裝的後備」，大部分手機不必下載 136 MB 模型。
- 系統引擎（`SystemSpeechRecognizerEngine`）不接受外部 PCM、只能自己開麥，因此新增 `takesOverMicrophone` 標記：進入聆聽前共用 AudioRecord 先停用、整句從 LISTENING 直接交逐字稿（`VoiceStateMachine` 新增 LISTENING→ROUTING）。等待語音／單句上限沿用 VAD 設定（預設 5 秒／20 秒），逾時視為沒聽到回待喚醒；忙線、權限與 client 類錯誤才記 `ASR_RUNTIME_FAILED`。
- 不做現場自動下載後備模型：兩條路都不可用時維持「拒絕啟動＋提示下載」；「模型」分頁下載按鈕照舊，文案不再聲稱「啟動服務前必須先完成下載」。目前無「停用系統辨識偏好」開關；使用者如要完全不上傳的路徑，系統探測通過時系統辨識仍會優先（風險已寫入 privacy.md，真機服務差異大時再考慮開關）。
- 探測機制（真機 debug 後定案）：API 33+ 用 `SpeechRecognizer.checkRecognitionSupport` 直接問辨識服務支不支援候選中文語言；API < 33 或新版 API 沒答案時，退回 `ACTION_GET_LANGUAGE_DETAILS` 有序廣播讀 `EXTRA_SUPPORTED_LANGUAGES`（要帶 `FLAG_RECEIVER_INCLUDE_BACKGROUND`，且判準是 extras 有沒有語言清單、不是 result code）。**兩個來源都問不到時不再擋掉系統這條路**：只要裝置有辨識服務就先信任它，把未驗證原因記進 ring log `SYSTEM_ASR_UNVERIFIED`，成敗由執行中的辨識請求決定；只有來源明說不支援中文才判 `chinese-unsupported`。理由：2026-10-19 在 vivo X200s／Android 16 實測，預設辨識器 `com.google.android.tts/…GoogleTTSRecognitionService` 對 `checkRecognitionSupport` 回 `ERROR_CANNOT_CHECK_SUPPORT`（14），語言詳情廣播只回 Google 私有的 `com.google.recognition.extra.OFFLINE_*`（protobuf `byte[]`、沒有文件化 key），嚴格要求「先問到中文」會讓系統辨識在這種裝置（含 Google 辨識器）完全不能用。使用者在對話中核准放寬（選「放寬：有服務就用」）。
- 版號 `1.0.8`／`versionCode 42`；驗證與驗收狀態見 Issue #38、known-issues 與 testing.md（vivo X200s 真機已確認探測通過並選用系統引擎；實聽逐字稿等項目仍待驗收）。
## 1.0.1（2026-10-05，Issue #24）

- **開發者決定（版號）**：不再使用 `1.0.0-rcN` 候選版號。從本版起為 `1.0.1`／`versionCode 35`，後續行為變更的 APK 依序為 `1.0.2`、`1.0.3`…直到另行決定。`versionCode 35` 高於各分支已用的 34（含 Issue #20 的 rc30），覆蓋安裝不會被 Android 拒絕。
- X-ASR 同音錯字加 LLM 校正（Typeless 兩段式）：本機 Router 全 miss 才打一次 LLM（`temperature 0.0`、固定 JSON），命中導航／音樂就轉回既有本機 Executor；`AssistantSettings.llmCorrectionEnabled`（預設 true）在 LLM Provider 卡片有開關，關閉即回舊行為。校正 prompt 是程式常數，不受 DataStore 舊值影響。

## 1.0.10（2026-10-20，本對話，使用者核准：補齊 MusicCabin 風格的 App 內更新 UI 與續傳）

- 「設定 → App 更新」固定顯示目前版號、啟動檢查結果與手動檢查入口；自動啟動檢查比照 MusicCabin 使用兩小時記憶體快取，手動按鈕才強制重新請求。查到新版時可在設定頁或對話頁橫幅下載、繼續下載，完成後按「安裝更新」開啟系統安裝器。
- 更新檔以版本標籤分開暫存；網路中斷或取消會保留 `.part`，下一次使用 HTTP Range 續傳並重試。缺少「安裝未知應用」授權時，下載仍可完成，再由 App 引導授權。
- 版號 `1.0.10`／`versionCode 44`。`test` 通過；lint、assembleDebug 與真機 App 內更新驗收待完成。

## 1.0.9（2026-10-20，本對話，使用者核准：下載來源改回官方＋移除雲端 ASR）

- X-ASR 下載來源由使用者私人 Google Drive 改回 sherpa-onnx 官方 GitHub release 的同一個壓縮檔（`sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2`，136,396,739 bytes、SHA256 `5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224`）。理由：私人雲端硬碟將刪除；原檔案即官方 release 產物，HTTP HEAD 大小與官方 Release API 的 digest 均吻合。下載完成後仍先驗證大小與 SHA256 再解壓，失敗即中止，不會安裝錯誤模型。
- 移除「模型」頁的雲端 ASR（Whisper）設定與整條程式路徑：`CloudSpeechRecognizerEngine`、`OpenAiAudioTranscriptionClient`（含 `AudioTranscriber`）、`cloudAsrEnabled`／`Model`／`Language`、啟動時的雲端優先分支、診斷 `cloud_asr` 行；引擎選擇回到「系統語音辨識優先，系統不可用才用已下載的 X-ASR」。雲端 TTS 保留。理由：系統辨識已可優先使用，雲端 ASR 的 spike 不再需要。
- 升級相容：舊 DataStore 的 `cloud_asr_*` 鍵留在裝置但不讀取；既有 X-ASR 模型檔與舊版模型選擇遷移不變。原 `CloudSpeechRecognizerEngine.pcmToWav` 僅測試使用，移到測試 source set 的 `audio/TestWav.kt`。
- 版號 `1.0.9`／`versionCode 43`。`test`、`lint`、`assembleDebug` 通過；真機驗收項目見 testing.md 與 known-issues。

## 1.0.11（2026-10-21，本對話，使用者核准：移除「模型」頁雲端語音／雲端 TTS）

- 移除「模型」頁的「雲端語音（spike）」與「雲端 TTS」區塊，並整條移除雲端 TTS 程式路徑（含對應測試 `CloudAudioSettingsValidatorTest`、`OpenAiAudioSpeechClientTest`、`WavAudioGainTest`、`WavPlaybackSettingsTest`、`RiffWavTest` 與僅測試用的 `audio/TestWav.kt`）：`CloudTtsEngine`、`OpenAiAudioSpeechClient`、`CloudAudioSettingsValidator`（含 `validateTts`）、`RiffWav`、`WavAudioGain`、`WavPlaybackSettings`（後三者僅雲端 TTS 使用）、`cloudTtsEnabled`／`Model`／`Voice`／`audioBaseUrl` 設定欄位與 DataStore 鍵、`AndroidKeystoreSecretStore.Scope.AUDIO`、`VoiceErrorCode.AUDIO_CONFIG_INVALID`、診斷匯出的 `audio_provider_host`／`cloud_tts` 行、模型頁與主活動的 Audio Key 輸入／刪除／測試流程。TTS 回到純系統引擎；「測試語音」與 TTS 播放失敗重建（`speechOutputFactory`）保留，重建時一律用 `AndroidTtsEngine`。
- 動機：雲端 TTS 只是 spike、預設關閉且從未完成真機驗收，使用者明確要求移除 UI 與殘留程式。1.0.9 移除雲端 ASR 時曾保留雲端 TTS，本版一併收掉。
- 升級相容：舊 DataStore 的 `cloud_tts_*`／`audio_base_url` 鍵留在裝置但不讀取（比照 1.0.9 處理 `cloud_asr_*` 的慣例）；舊 Audio Key 加密檔（`secrets/audio_api_key.v1.json`）不再被讀取，「清除私人資料」會一併刪除該檔與暫存檔，Keystore 內舊 audio alias 不再使用。既有 LLM 設定與驗證狀態不受影響。
- 版號 `1.0.11`／`versionCode 45`（行為變更，依 DEVELOPMENT.md 版本規則遞增）。

