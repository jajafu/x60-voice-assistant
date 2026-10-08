# M1–M5 Testing

## 自動化

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\scripts\prepare-bundled-xasr.ps1
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Unit tests 覆蓋權限啟動策略、bounded Audio/TTS queue、狀態機、VAD endpoint/timeout、模型 ID/SHA、URL 正規化、SSE/`[DONE]`、HTTP retry、MockWebServer、AES-GCM round trip、secret redaction、Markdown/SpeechSegmenter、Follow Up monotonic timeout、Audio recovery threshold、Boot reminder policy 與安全診斷匯出。

## 雲端語音 spike 驗收（待真實 Provider／X60）

1. 在診斷頁確認 LLM Provider 與 Audio Provider 的 Base URL／Key 分開保存；未設定 Audio URL／Key 時，即使雲端開關開啟也維持本機 X-ASR／TTS，不影響既有 LLM。
2. 以「測試雲端 TTS」確認草稿中的 Model、Voice 與獨立 Audio Base URL／Key 被使用；播放期間停止服務或再次說喚醒詞，確認請求與播放可取消。
3. 開啟 Whisper 雲端 ASR 且完成 Audio 設定後說出一般問句，確認 KWS／VAD 仍在本機，VAD 結束才上傳整段 WAV；停止服務時確認上傳請求不需等待完整 read timeout。
4. 實際 Provider、費用、網路錯誤、鎖屏與真人語音結果須記錄 Provider、vivo X60、APK HEAD 與證據；MockWebServer／合成語音只能作自動化測試，不能取代真機驗收。

rc22 另包含 Orchestrator 跨元件回歸測試：重複取消、暫停後取消、過期回答事件、辨識未完成時關閉／取消父 scope、第一句在串流結束前朗讀、長回答及音樂拒絕補救。JVM 以可控制的 engine、音訊來源及同步阻塞辨識替身驗證生命週期，診斷使用測試 clock shim，不能替代 native 或 Android 實機結果。另測試 12 段背壓、取消清空、網路失敗不補讀半句、逾期一次性／每日／每週提醒，以及鬧鐘 TTS 靜音／恢復。

rc23 新增回答期間 KWS 插話測試：再次收到喚醒詞會取消舊串流及 TTS、播放「我在」並回到 `LISTENING`，且不重複暫停或遺失原本應恢復的音樂狀態。回答自身包含或近似喚醒詞造成誤觸是已接受風險，實際喇叭音量下的喚醒率仍須以 X60 真人語音驗收。

## rc23 回答中喚醒的 X60 驗收（待真機驗收）

1. 讓 Provider 產生至少 30 秒的回答；朗讀中正常音量說「小助手」，確認舊回答立即停止、播放一次「我在」，接著可說出並辨識新問題。
2. 在第一個 token 尚未送達的 `THINKING` 階段說「小助手」，確認舊請求取消且同樣進入新一輪聆聽。
3. MusicCabin 播放中開始對話，再於回答中說「小助手」；第二次喚醒不得重複送出暫停，整段互動結束後音樂仍依第一次喚醒記錄恢復。
4. 連續插話 10 次，確認只有一個 AudioRecord，沒有舊回答續播、重複「我在」、殘留 HTTP 或 TTS queue。
5. 讓回答文字包含「小助手」，記錄是否自行觸發；此風險已接受，不以 AEC 或抑制窗阻止。

## rc22 五項修正的 X60 回歸（待真機驗收）

1. 在 X-ASR 辨識中反覆停止服務及重新啟動，確認沒有 native crash、殘留錄音或舊回答；記錄釋放完成時間。
2. 快速連按通知「取消／閉嘴」，以及暫停後送出舊取消操作；不應崩潰或自行恢復錄音。
3. 用超過 12 段的 Provider 回答確認首句提早朗讀、順序完整、朗讀中取消會中止 HTTP 與後續語音；點歌的拒絕回覆仍不朗讀。
4. 對一次性與重複提醒，讓系統延遲送達後先回到 App，再確認補響一次且重複提醒保留下一次；重開機／時區變動亦需測試。
5. 鬧鐘朗讀中喚醒助理，以及喚醒後等待超過 30 秒，確認鈴聲與提醒語音均暫停；互動結束恢復，關閉鬧鐘後不再播報。

## X60 測試前記錄

每輪都記錄 App version、Git commit、APK SHA256、model ID／archive SHA256、Android、OriginOS Build、充電狀態、電量、電池溫度、thermal status、Wi-Fi、開始／結束時間、Audio Error、Dropped Frame 與 Service Uptime。

App 自 rc24 起以 `android:screenOrientation="portrait"` 固定直式，不再隨裝置旋轉；`docs/test-reports/m0-2026-09-22-vivo-x60.md` 的橫向結果屬變更前的歷史紀錄，不重測、不改寫。新一輪手動驗收不需再執行橫向項目，僅確認旋轉裝置時畫面維持直式。

## 設定頁數字欄位（rc24）

設定 → 進階設定與語音與追問，10 個數字欄位（Temperature、Max Output Tokens、Connect Timeout、Read Timeout、Context Turns、等待說話、最短語音、結尾靜音、最長單句、Follow Up）逐欄位確認：可清空後逐字重新輸入、可輸入範圍內任意值含上下限、Context Turns 可設 0 且重開 App 後仍為 0、輸入空白或超界時欄位標紅且「儲存設定」不可按、輸入過程不被還原成預設值。

## 話題邊界與 Follow Up 視窗（rc25，待真機驗收）

喚醒詞開啟新一輪時清空對話歷史，只有同一輪 Follow Up 視窗（預設 3 秒，可調 0–30）內的追問才帶著上一輪。需在 vivo X60 搭配真實 Provider 驗收：

1. 喚醒問一個話題 A（例：「BLACKPINK 是哪一年出道的」），等回答完畢，**不要**在 3 秒內追問，確認診斷頁狀態回到 `IDLE_KWS`。
2. 重新說喚醒詞問不相關的 B（例：「明天台北天氣如何」），確認回答針對 B，且診斷頁 LLM 完成記錄的 `length`／`hash` 對應的是 B 的回答而非 A 的續篇。若診斷頁允許查看，逐字稿應只有 B。
3. 重複步驟 1–2 共 10 次，確認不再出現沿用舊話題的情況。
4. 喚醒問 A，等回答完畢後在 3 秒內直接說「那第二首呢」（不說喚醒詞），確認追問成立且回答與 A 相關。
5. 設定 → Follow Up 設為 0，確認回答結束後直接回到 `IDLE_KWS`、不接受無喚醒詞的追問。
6. 設定 → Follow Up 設為 10，確認步驟 4 在 10 秒內仍成立。
7. 回答朗讀中說喚醒詞插話改問 B，確認舊回答停止、B 的請求不帶入 A 的上下文。

## 音訊失敗時的結束順序（rc26，待真機驗收）

音訊失敗時必須先停掉 LLM 串流與 TTS，再恢復本輪暫停的音樂，避免音樂先恢復、舊回答的最後一句才補上。需在 vivo X60 搭配真實 Provider 驗收：

1. 播放音樂中喚醒小助手，問一個會產生多句回答的問題，確認音樂在「我在」之前就暫停、回答完畢後音樂才恢復。
2. 在 TTS 朗讀到一半時製造音訊失敗（可用 `adb shell` 讓另一個 App 佔用麥克風，或在 `docs/troubleshooting.md` 既有方式下觸發），確認順序是：舊回答**立刻停止** → 短暫恢復 → 再重新聆聽，而不是先恢復音樂再補完回答。
3. 重複步驟 2 共 10 次，每次確認都沒有「音樂恢復後才出現的 TTS」。
4. 音訊失敗進入 `RECOVERING` 後，確認喚醒詞仍可正常觸發新一輪，且沒有重複暫停或遺失應恢復的音樂狀態。
5. 搭配鬧鐘暫停一起確認：音訊失敗後鬧鐘與音樂都只恢復一次。

## TTS 音量與引擎／音調選擇（rc27，待真機驗收）

設定 →「語音與追問」新增 TTS 引擎選擇、TTS 音調選擇與 TTS 音量滑桿。需在 vivo X60 驗收：

1. 確認設定頁顯示裝置上的 TTS 引擎清單，且「系統預設」項與系統「文字轉語音輸出」頁面一致。
2. 選擇某個引擎後，音調清單會重新載入；確認清單有顯示語言，並標示「離線」或「需要網路」。
3. **音調清單只顯示中文**（rc29）：vivo X60 的引擎回報 30 個以上語言，設定頁應只列出 `zh-Hant`／`zh-Hans` 等中文 voice，不出現 `af`、`ak`、`az` 等項目。排序第一個應為繁體中文，與未指定時的自動選擇一致。
4. 選一個語音穩定的引擎與 voice，停止並重新啟動語音服務，確認診斷頁「TTS」顯示的引擎／voice 與選擇相符。
5. **音量驗收**：在音樂播放中把音量調到 1.50，用「測試語音」比較 1.00 與 1.50 的聲音大小；若兩個值沒有差異，代表該引擎忽略 `KEY_PARAM_VOLUME`，需如實記錄為未生效而非通過。
6. 把音量調到 0.50 確認變小聲；調到 0 確認靜音，且畫面仍正常顯示文字。
7. 確認重開 App 後引擎、音調與音量都保留；清除資料後回到預設（引擎自動選擇、音調自動選擇、音量 1.00）。

## 語音調整手機音量（rc30，**功能待真機驗收**）

### 安裝與啟動 smoke（rc30 commit `3e9b742`，已執行）

`docs/ANDROID_MUMU_SMOKE.md` 的 smoke 範圍套用在實體 X60：安裝成功、從 launcher 開啟、啟動畫面可見且沒有立即崩潰。

| 項目 | 結果 |
| --- | --- |
| 裝置 | vivo V2046A，Android 13（SDK 33），OriginOS 14.0，`arm64-v8a` |
| 受測 commit | `3e9b7428aebf4af703fe00c4c61fb11e2a2ef9e2` |
| APK SHA-256 | `C204A4FCFDEA8CBBF6C2E8848AB9828F9EC9AD2317BD3B9952636FBFCB88C7E6` |
| package／版本 | `com.jajafu.x60assistant`／`1.0.0-rc30` / `34`（由 rc29 / 33 覆蓋安裝） |
| 權限 | `MODIFY_AUDIO_SETTINGS: granted=true` |
| 啟動 | `am start -n com.jajafu.x60assistant/.MainActivity` 成功，pid 29608，前景顯示「運作中」／「等待喚醒」 |
| 崩潰檢查 | logcat 無 `FATAL`／`AndroidRuntime`／ANR |
| `STREAM_MUSIC` 上限 | `Max: 15`，未靜音（`Muted: false`），當下為 10 格 |

安裝過程：`adb install -r` 逾時，經裝置端人工確認後完成；後續 `dumpsys package` 確認已升級至 `versionCode 34`。一次重試的 `adb install -r -g` 回報失敗但未改變已安裝版本。測試結束時裝置已從 adb 斷線（`device not found`），如需補測須重新連線。

此 smoke 只代表安裝與啟動正常，**不代表語音音量功能驗收通過**；以下 10 個步驟仍需真人語音在該機執行。

### 功能步驟（**本輪尚未執行**）

設定 →「語音調音量」預設開啟。需在 vivo X60 驗收：

1. 先記錄基準值：`adb shell dumpsys audio` 讀 `STREAM_MUSIC` 的目前級別與上限。本輪 smoke 已在該機讀到 `Max: 15`，`docs/decision-log.md` rc27 曾寫的「10/15」指目前 10 格、上限 15 格，語意已釐清；功能測試前仍須重新讀取當下值。
2. 相對調整：說「聲音小一點」，確認音量降一格並聽到小助手的回報；連續 10 次確認每次只動一格。
3. **共用音量級別的已知取捨**：TTS 與音樂共用 `STREAM_MUSIC`，因此確認小助手的回話音量與音樂同步變化；在下限 2 格時確認回話仍可聽懂，若小到不可用需如實記錄。
4. 百分比：說「音量調到50」，確認回報的實際級別約為上限的一半，且**沒有**出現「音量已調到」以外的假稱完成。
5. 中文數字與邊界：說「音量調到五十」應等同「音量調到50」；說「音量調到0」或「音量最小」確認**不會**降到 0，而是回報已到下限 2 格。
6. 總開關：關閉「允許語音調整手機音量」並重新啟動服務，確認音量指令不再執行。
7. 靜音指令不支援：說「靜音」與「取消靜音」都不應改變音量或觸發本機音量操作。
8. **喚醒率（重點）**：把音量調到下限 2 格，一公尺說「小助手」20 次，記錄喚醒次數。此項目與 `docs/testing.md` 既有的喚醒驗收並列，不得以單元測試或合成語音替代。
9. 副作用：確認一次設定沒有連續逐格提示音，且不會意外拉出音量面板（目前使用 `setStreamVolume(..., flags = 0)`）。
10. 確認一般問答不受影響：說「什麼時候音量太大」應走一般問答而非調整音量。
11. **PR #21 code review 新增解析案例**（rc30 commit `3e9b742` 後補）：說「音量調高一格」應只調一格而非 1%；說「音量有點大」應**不動作**並照送 LLM；說「亮度調到最大」應**不動作**，不得改動媒體音量。
12. **讀回一致性**（rc30 commit `3e9b742` 後補）：若 vivo／OriginOS 夾限音量，小助手應回報讀回的實際級別而非宣稱已調到目標。可用實體音量鍵製造不一致後再說指令觀察。

若指令有命中但音量未變，診斷頁的「音量實測」會顯示 `current`、`target`、`readBack`、`max`、`applied` 與 `fixed`。ADB 檢查音量 AppOp 時使用 `AUDIO_MEDIA_VOLUME`；`WRITE_MEDIA_AUDIO` 是音訊檔案寫入操作，與調整媒體音量無關。

### X60 無障礙 fallback 追測（2026-10-02；端點修正後待複測）

使用者已在 vivo V2046A／OriginOS 14.0 啟用無障礙服務，並回報多輪測試只有一次「音量調到 50」成功。診斷服務已收到呼叫且手勢 callback 完成；一次 100% 失敗為 `current=8 target=15 readBack=8 max=15 applied=false`，SeekBar 回報 `range=0..600 current=320 target=600 bounds=150,796,1035,850`、`setProgress=false`。實際 slider 軌道比 Accessibility bounds 左右各內縮約 36 px，因此舊端點位置落在軌道外；50% 接近中央才曾成功。已以 bounds 高度估算內縮後的軌道座標、改為 thumb 起點拖曳，新 APK 已安裝至裝置，並通過 `test lint assembleDebug`。需真人複測「調到 100」、「調到 50」、「音量大／小一點」，查看每次讀回是否等於目標；尚未完成此複測。

最新一次「聲音小一點」在使用者回報的鎖屏狀態失敗；診斷為 `accessibility=TIMED_OUT trace=[not-started]`，表示設定頁滑桿事件未開始。使用者明確要求螢幕故障時仍能鎖屏操作，因此不能以解鎖螢幕當作功能解法。2026-10-02 的 X60 ADB 探測中，AppOp `AUDIO_MEDIA_VOLUME` 為 allow；`AudioManager.setStreamVolume`／`adjustStreamVolume`（含 `FLAG_SHOW_UI`）、自建本機 MediaSession、MusicCabin 與 Spotify 的本機 MediaSession 設音量後，讀值都仍為 8。Settings volume panel 在該機只顯示「音效與震動」面板，沒有滑桿。已移除曾加入的鎖屏拒絕分支；目前仍沒有找到可在該機鎖屏調整系統媒體音量的 SDK 路徑，功能未通過。

### 修正版追測（2026-10-02，X60；**未通過**）

| 項目 | 結果 |
| --- | --- |
| 裝置／版本 | vivo V2046A、Android 13／OriginOS 14.0；`1.0.0-rc30`／`versionCode 34` |
| APK SHA-256 | `6C1A16182053EE467149CBB4CFB270DAC69704380FF2F337B6F142A843FA30F0` |
| 權限／AppOp | `MODIFY_AUDIO_SETTINGS: granted=true`；`AUDIO_MEDIA_VOLUME: allow`；`isVolumeFixed=false` |
| 語音結果 | `UP` 命中；診斷讀值 `current=10 target=11 readBack=10 max=15 applied=false`；音量沒有增加 |
| 系統日誌 | 本次檢查未找到明確的 AudioService／AppOps 拒絕訊息 |

此結果確認解析與執行器都有走到設定呼叫，但系統讀回仍未改變。`adjustStreamVolume()` 和 `setStreamVolume()` 在此機上都無效。使用者已於本對話核准加入無障礙 fallback；需在系統設定啟用後完成下方真機驗收。

### 鎖屏限制修正後安裝確認（2026-10-02；功能未驗收）

| 項目 | 結果 |
| --- | --- |
| 來源 | `issue/20-voice-media-volume`，HEAD `8c42a1a` 加上未提交工作目錄變更（已移除鎖屏拒絕分支） |
| APK SHA-256 | `9CF31F8CB5D41D81C665D254DC3F5D0DD6F329ED6353C978FFCCC94937963F70` |
| 安裝／啟動 | vivo V2046A 上 `adb install -r` 成功；`MainActivity` 啟動成功 |
| 無障礙服務 | `VolumeAccessibilityService` 仍啟用且系統已綁定 |
| 音量功能 | 本次只確認安裝與服務狀態，沒有做新的鎖屏音量測試；仍未通過 |

### ADB shell helper 建置（2026-10-02；X60 安裝驗證待完成）

| 項目 | 結果 |
| --- | --- |
| 來源 | `issue/20-voice-media-volume`，目前工作目錄包含本次未提交修正 |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| SHA-256 | `636F617206956C8A81DF1144B32C0DD65D01B6AC240E2814963CA7F05F2B5516` |
| package／版本 | `com.jajafu.x60assistant`／`1.0.0-rc30`／`versionCode 34` |
| 自動驗證 | `.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug` 均成功；新增 4 個 shell protocol 測試 |
| X60 安裝 | 未執行：建置完成後 X60 serial `9654983303001DD` 從 ADB 斷線；目前只剩 SM-A528B 模擬器連線，不曾對它安裝或執行此 X60 helper |
| 真機結果 | 直接以 ADB shell 執行 media_session 命令先前已在 X60 改變並還原音量；APK 內 app_process 載入、helper 心跳、拔線後存活、鎖屏語音控制均待重新連接 X60 後驗證 |

X60 重新連接後，先安裝本節 APK，再執行 `scripts/start-x60-volume-helper.ps1 -Serial 9654983303001DD` 確認 helper 回報心跳，透過 helper 協定讀回一個可恢復的音量變更；最後仍需使用者在鎖屏下真人語音測試。已移除 Accessibility Service 註冊與設定頁啟用按鈕。

### ADB shell helper 移除（2026-10-04；以上 helper 方案作廢）

依使用者要求移除 helper（含 bridge、daemon、protocol、啟動／停止腳本與診斷頁 helper 狀態），回到純 `AudioManager`：相對調整用 `adjustStreamVolume(STREAM_MUSIC, ±1)`，絕對音量用 `setStreamVolume()`。本節之前的 helper 安裝指示不再適用；音量功能驗收回到「語音調整手機音量（rc30）」12 個功能步驟。

## 手動步驟

1. 強制停止 App，確認 Service、AudioRecord 與 WakeLock 不存在。
2. 啟動 Activity 但不要按按鈕；再次確認沒有錄音。
3. 由使用者按「啟動常駐 KWS」。確認通知存在、Audio Frame 持續增加、Audio Source 是 `VOICE_RECOGNITION`（不支援時才允許 `MIC`）、Audio Error／Dropped Frame 初始為 0。
4. 確認系統同時只有一個屬於本 package 的 active AudioRecord，格式為 mono／16000 Hz／PCM16。
5. 說「小助手」並確認 Wake Count、Timestamp 更新且播放固定短音；不能出現 TTS、ASR 文字或網路請求。
6. 從通知停止，確認 AudioRecord、WakeLock、Service 與通知都消失。
7. 重新啟動後鎖屏／熄屏，先測 30 分鐘，再測接電 8 小時與未接電 8 小時。
8. 一公尺正常音量測 20 次（至少三位說話者），目標至少 18 次；每次間隔超過 2 秒 cooldown。
9. 安靜房間連續 8 小時，目標誤喚醒不超過 1 次；另外記錄電視背景 8 小時，但不先宣稱門檻。

## ADB 證據

所有指令都必須指定實體 X60 serial，避免誤用已連線模擬器：

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$serial = "9654983303001DD"

& $adb -s $serial shell dumpsys activity services com.jajafu.x60assistant
& $adb -s $serial shell cmd appops get com.jajafu.x60assistant RECORD_AUDIO
& $adb -s $serial shell dumpsys audio | Select-String 'com.jajafu.x60assistant|VOICE_RECOGNITION' -Context 1,2
& $adb -s $serial shell dumpsys power | Select-String 'com.jajafu.x60assistant:VoiceKws'
& $adb -s $serial shell dumpsys battery | Select-String 'status:|level:|temperature:'
& $adb -s $serial shell dumpsys thermalservice
```

8 小時測試若中斷、未跑滿或缺少開始／結束證據，結果只能是「待執行」或「未完成」，不能寫成通過。

## M2–M5 完整流程

1. 啟動前確認沒有錄音；在 Activity 按啟動後確認唯一 mono/16 kHz/PCM16 recorder。
2. 說「小助手」，提示音後說一整句，確認 `LISTENING → TRANSCRIBING → ROUTING`、VAD endpoint、ASR partial/final、RTF 與 final latency。
3. 在 App 內填私人 Provider 設定並主動測試連線；401/403、404、429、5xx、timeout、SSE 中斷以 MockWebServer 自動化，真實服務只記實際結果。
4. 驗證回答按完整句朗讀、Markdown 不朗讀、TTS 期間維持唯一 AudioRecord 並只執行 KWS；再次說「小助手」會取消 HTTP／TTS、播放「我在」、清 queue 後進入 `LISTENING`，通知取消／閉嘴仍會取消 HTTP 並清 queue。
5. TTS 後確認 `FOLLOW_UP` 10 秒可直接追問，逾時回 `IDLE_KWS`，前後 session 不重疊。
6. 從 App 與鎖屏通知測暫停、恢復、取消、閉嘴、停止；每次停止確認 recorder、WakeLock、HTTP、TTS 與通知釋放。
7. Reboot 後只應出現提醒，Service/recorder 不存在；開啟 Activity 後仍不能自動錄音。
8. 強制停止後等待至少 10 分鐘，確認 service、通知、recorder 都不復活。

正式門檻仍包括：真人 20 句至少 18 句保留語意、30 次連續無 OOM/重啟、RTF median &lt; 0.6、final P95 &lt; 3 秒，以及接電／未接電／安靜環境各 8 小時與建議 24 小時穩定性。短測、合成音、MockWebServer 都不得標成正式驗收通過。
