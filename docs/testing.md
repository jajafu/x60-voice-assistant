# M1–M5 Testing

## 自動化

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\scripts\prepare-vad-asset.ps1
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Unit tests 覆蓋權限啟動策略、bounded Audio/TTS queue、狀態機、VAD endpoint/timeout、模型 ID/SHA、URL 正規化、SSE/`[DONE]`、HTTP retry、MockWebServer、AES-GCM round trip、secret redaction、Markdown/SpeechSegmenter、Follow Up monotonic timeout、Audio recovery threshold、Boot reminder policy、安全診斷匯出與車機音樂 session 選擇（`ActiveSessionPicker`）。

## 車機背景操作驗收（Issue #27，待方易通 7862／Android 10 實車驗收）

1. 背景「導航到台北101」：聽到「已備好…請點服務通知上的「開啟地圖」」後，點常駐通知的「開啟地圖」，Google 地圖到前景顯示路線；回到 idle 後按鈕消失。
2. 地圖導航中再叫「導航到 xxx」：記錄 Google 地圖是否直接換成新路線（需實測，不預設結果）。
3. 背景播歌時叫小助手：音樂暫停（不再只變小聲），對話結束後恢復；通知存取權未授權時維持舊行為（duck），不假稱暫停。
4. 以上皆須記錄車機型號、Android 版本、地圖／音樂 App 版本、APK HEAD 與 SHA-256；MockWebServer 不能取代實車。

## 系統語音辨識探測與後備切換（1.0.8；1.0.16 起後備已移除，下列僅歷史紀錄）

1. 系統語音服務存在且支援中文的裝置：X-ASR 未下載直接啟動語音服務，確認診斷「實際載入的模型」為 `system/android-zh`，說話可取得逐字稿並正常回答；聆聽期間 Audio Error 為 0（共用 AudioRecord 已交棒停用）。
2. 系統服務不存在，或探測來源明說不支援中文的裝置（或手動移除語音服務）：X-ASR 未下載時啟動應報 `ASR_MODEL_INVALID` 並指示到「模型」分頁下載；下載完成後重新啟動改回本機 X-ASR。**1.0.16 起不再下載，行為改為啟動失敗並顯示原因（見下方 1.0.16 清單）。**
3. 系統路徑逾時行為：進入「正在聆聽」後（開口前預算從 `onReadyForSpeech` 起算，服務啟動冷啟時間不計入使用者開口預算）靜默 5 秒（設定值）應回到等待喚醒狀態而非服務錯誤；開口後 20 秒上限從實際開口起算亦同。
4. `SystemSpeechRecognizerEngine` 的主執行緒啟動、watchdog 錨點與 Episode 交付依賴 Android 框架的 SpeechRecognizer，沒有 JVM 自動化測試；一律由上述真機項目覆蓋，PR 與驗收說明不得宣稱該部分已由單元測試驗證。
5. 探測在「兩個來源都問不到語言資訊」時仍會選用系統語辨（見 decision-log 1.0.8）：這種裝置的實聽成敗由執行中的請求決定，驗收時須記錄診斷 ring log 的 `SYSTEM_ASR_UNVERIFIED` 原因碼。

### 真機結果（2026-10-19，vivo X200s／Android 16 SDK 36，serial `10AF4E23M30026F`）

- 受測產物：工作區建置 `1.0.8`／`versionCode 42`（`app/build/outputs/apk/debug/app-debug.apk`，13:36 建置；未下載 X-ASR）。
- 探測通過並選用系統引擎：服務持續執行（維持 130 秒以上未停止），診斷 dump `state=IDLE_KWS`、`asr_model_id=system/android-zh`、`last_error=-`、`audio_source=VOICE_RECOGNITION`。
- 匯出安全診斷 ring log：`SYSTEM_ASR_UNVERIFIED|-|check-support-error-14+language-details-unavailable`，其後 `STATE_STARTING → STATE_IDLE_KWS`。兩個探測來源在此機都問不到語言：預設辨識器 `com.google.android.tts/…GoogleTTSRecognitionService` 對 `checkRecognitionSupport` 回 `ERROR_CANNOT_CHECK_SUPPORT`（14），語言詳情廣播只回 `com.google.recognition.extra.OFFLINE_*`（protobuf `byte[]`）。
- 仍未驗收（不得宣稱通過）：系統辨識實聽逐字稿與回答（第 1 項的說話部分）、系統逾時行為、聆聽期間麥克風交棒、雲端 Whisper 優先序、以及第 2／3 項在其他裝置的組合。

## X-ASR 下載來源與雲端 ASR 移除（1.0.9，待真機驗收）

> **1.0.16 起下載式 X-ASR 整條移除，本節第 1、2 項不再適用；第 3 項（舊版升級不崩潰、設定靜默失效）併入下方 1.0.16 清單。**

1. 「模型」頁只保留本機 X-ASR 與 LLM Provider；確認不再出現 Whisper／雲端 ASR 欄位。
2. 移除 App 內已下載的 X-ASR 後按下載：確認連到 sherpa-onnx 官方 GitHub release，下載完成後大小與 SHA256 驗證通過並完成解壓；系統語音辨識不可用的裝置重新啟動服務可改用 X-ASR。
3. 由舊版（含雲端 ASR 設定）升級安裝：確認不因舊 DataStore 鍵而崩潰，雲端 ASR 設定靜默失效，其餘設定保留。

## 雲端 TTS 移除（1.0.11，待真機驗收）

1. 「模型」頁不再出現雲端語音（spike）／雲端 TTS 區塊；TTS 一律走系統引擎，「測試語音」正常朗讀。
2. 由舊版（含雲端 TTS 設定與 Audio Key）升級安裝：確認不因舊 DataStore 鍵或殘留的 `secrets/audio_api_key.v1.json` 而崩潰，雲端設定靜默失效，其餘設定保留。

## X-ASR 後備移除與提醒頁停止鈴聲（1.0.16，待真機驗收）

語音辨識改為只用系統服務，「模型」頁不再有 X-ASR 下載卡片；提醒鈴聲響起時「提醒」頁顯示「停止鈴聲」橫幅。真機項目：

1. 「模型」頁只有頁首說明與 LLM Provider 卡片；確認沒有「本機語音模型（X-ASR）」卡片與下載按鈕，診斷頁「語音模型」不再顯示「ASR（設定）」列。
2. 系統語音服務可用的裝置：啟動語音服務照常進入待喚醒，診斷 `asr_model_id=system/android-zh`；說話取得逐字稿與回答。
3. 系統語音服務不可用（或明確不支援中文）的裝置／模擬移除：啟動語音服務應停在 `ASR_INIT_FAILED`（ring log 詳情 `system-asr-unavailable:<探測原因>`），畫面不崩潰；沒有任何「下載模型」入口。
4. 由 1.0.15 升級安裝（保留舊 DataStore 與已下載的 X-ASR 模型檔）：確認啟動不崩潰，舊 `asr_model_id` 設定靜默失效，診斷「ASR」顯示 `system/android-zh`；App 內沒有刪除既有模型檔的功能（檔案留在私有空間，屬預期）。
5. 建立一個 1 分鐘後的提醒；鈴聲響起時開啟 App 到「提醒」頁，確認出現「提醒鈴聲響鈴中」紅色橫幅與「停止鈴聲」按鈕，按下去鈴聲與語音播報立即停止、橫幅消失、通知移除。
6. 鈴聲未響時開啟「提醒」頁：不得出現橫幅或按鈕；提醒新增／修改／刪除與鈴聲選擇行為不變。
7. 響鈴中喚醒助理說話：鈴聲暫停、語音互動後恢復（既有行為）；恢復後「停止鈴聲」按鈕仍可正常停止。

rc22 另包含 Orchestrator 跨元件回歸測試：重複取消、暫停後取消、過期回答事件、辨識未完成時關閉／取消父 scope、第一句在串流結束前朗讀、長回答及音樂拒絕補救。JVM 以可控制的 engine、音訊來源及同步阻塞辨識替身驗證生命週期，診斷使用測試 clock shim，不能替代 native 或 Android 實機結果。另測試 12 段背壓、取消清空、網路失敗不補讀半句、逾期一次性／每日／每週提醒，以及鬧鐘 TTS 靜音／恢復。

rc23 新增回答期間 KWS 插話測試：再次收到喚醒詞會取消舊串流及 TTS、播放「我在」並回到 `LISTENING`，且不重複暫停或遺失原本應恢復的音樂狀態。回答自身包含或近似喚醒詞造成誤觸是已接受風險，實際喇叭音量下的喚醒率仍須以測試手機真人語音驗收。

## rc23 回答中喚醒的測試手機驗收（待真機驗收）

1. 讓 Provider 產生至少 30 秒的回答；朗讀中正常音量說「小助手」，確認舊回答立即停止、播放一次「我在」，接著可說出並辨識新問題。
2. 在第一個 token 尚未送達的 `THINKING` 階段說「小助手」，確認舊請求取消且同樣進入新一輪聆聽。
3. MusicCabin 播放中開始對話，再於回答中說「小助手」；第二次喚醒不得重複送出暫停，整段互動結束後音樂仍依第一次喚醒記錄恢復。
4. 連續插話 10 次，確認只有一個 AudioRecord，沒有舊回答續播、重複「我在」、殘留 HTTP 或 TTS queue。
5. 讓回答文字包含「小助手」，記錄是否自行觸發；此風險已接受，不以 AEC 或抑制窗阻止。

## rc22 五項修正的測試手機回歸（待真機驗收）

1. 在 X-ASR 辨識中反覆停止服務及重新啟動，確認沒有 native crash、殘留錄音或舊回答；記錄釋放完成時間。**（1.0.16 起 X-ASR 已移除，本項改以系統辨識重複停止／重啟回歸即可。）**
2. 快速連按通知「取消／閉嘴」，以及暫停後送出舊取消操作；不應崩潰或自行恢復錄音。
3. 用超過 12 段的 Provider 回答確認首句提早朗讀、順序完整、朗讀中取消會中止 HTTP 與後續語音；點歌的拒絕回覆仍不朗讀。
4. 對一次性與重複提醒，讓系統延遲送達後先回到 App，再確認補響一次且重複提醒保留下一次；重開機／時區變動亦需測試。
5. 鬧鐘朗讀中喚醒助理，以及喚醒後等待超過 30 秒，確認鈴聲與提醒語音均暫停；互動結束恢復，關閉鬧鐘後不再播報。

## 測試手機測試前記錄

每輪都記錄 App version、Git commit、APK SHA256、model ID／archive SHA256、Android、OriginOS Build、充電狀態、電量、電池溫度、thermal status、Wi-Fi、開始／結束時間、Audio Error、Dropped Frame 與 Service Uptime。

App 自 rc24 起以 `android:screenOrientation="portrait"` 固定直式，不再隨裝置旋轉；`docs/test-reports/m0-2026-09-22-vivo-x60.md` 的橫向結果屬變更前的歷史紀錄，不重測、不改寫。新一輪手動驗收不需再執行橫向項目，僅確認旋轉裝置時畫面維持直式。

## 設定頁數字欄位（rc24）

設定 → 進階設定與語音與追問，10 個數字欄位（Temperature、Max Output Tokens、Connect Timeout、Read Timeout、Context Turns、等待說話、最短語音、結尾靜音、最長單句、Follow Up）逐欄位確認：可清空後逐字重新輸入、可輸入範圍內任意值含上下限、Context Turns 可設 0 且重開 App 後仍為 0、輸入空白或超界時欄位標紅且「儲存設定」不可按、輸入過程不被還原成預設值。

## 話題邊界與 Follow Up 視窗（rc25，待真機驗收）

喚醒詞開啟新一輪時清空對話歷史，只有同一輪 Follow Up 視窗（預設 3 秒，可調 0–30）內的追問才帶著上一輪。需在測試手機搭配真實 Provider 驗收：

1. 喚醒問一個話題 A（例：「BLACKPINK 是哪一年出道的」），等回答完畢，**不要**在 3 秒內追問，確認診斷頁狀態回到 `IDLE_KWS`。
2. 重新說喚醒詞問不相關的 B（例：「明天台北天氣如何」），確認回答針對 B，且診斷頁 LLM 完成記錄的 `length`／`hash` 對應的是 B 的回答而非 A 的續篇。若診斷頁允許查看，逐字稿應只有 B。
3. 重複步驟 1–2 共 10 次，確認不再出現沿用舊話題的情況。
4. 喚醒問 A，等回答完畢後在 3 秒內直接說「那第二首呢」（不說喚醒詞），確認追問成立且回答與 A 相關。
5. 設定 → Follow Up 設為 0，確認回答結束後直接回到 `IDLE_KWS`、不接受無喚醒詞的追問。
6. 設定 → Follow Up 設為 10，確認步驟 4 在 10 秒內仍成立。
7. 回答朗讀中說喚醒詞插話改問 B，確認舊回答停止、B 的請求不帶入 A 的上下文。

## 音訊失敗時的結束順序（rc26，待真機驗收）

音訊失敗時必須先停掉 LLM 串流與 TTS，再恢復本輪暫停的音樂，避免音樂先恢復、舊回答的最後一句才補上。需在測試手機搭配真實 Provider 驗收：

1. 播放音樂中喚醒小助手，問一個會產生多句回答的問題，確認音樂在「我在」之前就暫停、回答完畢後音樂才恢復。
2. 在 TTS 朗讀到一半時製造音訊失敗（可用 `adb shell` 讓另一個 App 佔用麥克風，或在 `docs/troubleshooting.md` 既有方式下觸發），確認順序是：舊回答**立刻停止** → 短暫恢復 → 再重新聆聽，而不是先恢復音樂再補完回答。
3. 重複步驟 2 共 10 次，每次確認都沒有「音樂恢復後才出現的 TTS」。
4. 音訊失敗進入 `RECOVERING` 後，確認喚醒詞仍可正常觸發新一輪，且沒有重複暫停或遺失應恢復的音樂狀態。
5. 搭配鬧鐘暫停一起確認：音訊失敗後鬧鐘與音樂都只恢復一次。

## TTS 音量與引擎／音調選擇（rc27，待真機驗收）

設定 →「語音與追問」新增 TTS 引擎選擇、TTS 音調選擇與 TTS 音量滑桿。需在測試手機驗收：

1. 確認設定頁顯示裝置上的 TTS 引擎清單，且「系統預設」項與系統「文字轉語音輸出」頁面一致。
2. 選擇某個引擎後，音調清單會重新載入；確認清單有顯示語言，並標示「離線」或「需要網路」。
3. **音調清單只顯示中文**（rc29）：測試手機的引擎回報 30 個以上語言（歷史機型 vivo X60），設定頁應只列出 `zh-Hant`／`zh-Hans` 等中文 voice，不出現 `af`、`ak`、`az` 等項目。排序第一個應為繁體中文，與未指定時的自動選擇一致。
4. 選一個語音穩定的引擎與 voice，停止並重新啟動語音服務，確認診斷頁「TTS」顯示的引擎／voice 與選擇相符。
5. **音量驗收**：在音樂播放中把音量調到 1.50，用「測試語音」比較 1.00 與 1.50 的聲音大小；若兩個值沒有差異，代表該引擎忽略 `KEY_PARAM_VOLUME`，需如實記錄為未生效而非通過。
6. 把音量調到 0.50 確認變小聲；調到 0 確認靜音，且畫面仍正常顯示文字。
7. 確認重開 App 後引擎、音調與音量都保留；清除資料後回到預設（引擎自動選擇、音調自動選擇、音量 1.00）。

## 語音調整音樂 App 音量（1.0.13，**待真機驗收**）

「聲音大一點」「小聲一點」「音量調到 80」由本機 `MusicVolumeCommandRouter` 解析後，走音樂 App 的公開語音通道調整 **App 自己的音量**（`volume_up`／`volume_down`／`volume_set`），「現在音量多少」走 `volume_get` 只查詢。沒有獨立開關，與音樂控制一起依「設定 → 音樂 App」生效。需在測試手機用真人語音驗收：

1. 相對調整：播放中說「聲音大一點」，確認 MusicCabin 畫面那條音量滑桿變大，且小助手照回傳的訊息回報新音量；連說「小聲一點」數次，確認每次只動一段（MusicCabin 每次約 10%）。
2. 指定音量：說「音量調到 80」，確認滑桿到 80% 且回話出現 80；說「音量調到五十」應等同「音量調到 50」。
3. 查詢：說「現在音量多少」，確認只回報目前音量、不改變。
4. 邊界：說「音量最大」「音量最小」確認分別到 100% 與 0%，回話與實際一致。
5. 不誤觸：說「什麼時候音量太大」「亮度調到最大」「不要調小音量」都不應執行音量指令，會走一般問答。
6. 沒開也能用：MusicCabin 沒開或沒在播時說「音量調到 30」，確認能冷啟動 MusicCabin 並生效，或照回傳訊息說明目前狀態。順便記一次冷啟動的反應時間。1.0.14 起逾時不再誤報「太舊」：聽到「回應逾時，請再試一次」代表廣播逾時（對方可被喚起但回得慢，動作通常仍會完成；查詢與指定音量會自動重送一次），「太舊沒有語音控制」只出現在對方完全沒有語音通道的接收器時；MusicCabin 已是含音量指令的新版卻聽到「太舊」才需要記錄。逾時時可觀察音量是否實際已調整（第一次廣播仍會送達），重試與否以診斷 Ring Log 的 `MUSIC_VOICE` 事件核對（重送會有兩筆）。
7. 其他播放器：把音樂 App 換成不支援此通道的播放器，說「聲音大一點」應回覆「不支援用語音調整 App 內音量」，且不改動任何音量。
8. 不影響回話音量：確認調整音樂 App 音量時，小助手自己的 TTS 音量與手機系統媒體音量都不變。

小助手只調整音樂 App 自己的音量；**手機系統媒體音量自 1.0.13 起不再由小助手控制**。

### 歷史：手機系統媒體音量語音控制（rc30，已於 1.0.13 移除）

rc30 曾以 `AudioManager` 調整 `STREAM_MUSIC`，要求 `MODIFY_AUDIO_SETTINGS`。在 vivo X60／V2046A 與 V2458A 上實測 App UID 的 `setStreamVolume`／`adjustStreamVolume` 寫入在到達 AudioService 前就被 OEM 丟棄（`applied=false`）；無障礙 fallback、ADB shell helper 與鎖屏方案都在當時移除。此路徑、設定開關、診斷欄位與相關驗收步驟已隨功能一起移除，完整過程見 `docs/decision-log.md`；取代方案為上方「語音調整音樂 App 音量」。

## 外觀模式設定（1.0.15，**待真機驗收**）

設定 →「外觀」可指定畫面顏色：跟隨系統（預設）、日間（永遠淺色）、夜間（永遠深色）。需在測試手機驗收：

1. 預設狀態顯示「跟隨系統」；把手機系統切深色／淺色，App 畫面跟著切換（與 1.0.14 前行為一致）。
2. 選「夜間」按「儲存設定」，畫面立即變深色，不需重啟語音服務；此時把手機系統切淺色，App 畫面維持深色。
3. 選「日間」按「儲存設定」，畫面立即變淺色；系統切深色時 App 維持淺色。
4. 重開 App（甚至重開機）後選擇保留。
5. 「清除私人資料」後回到「跟隨系統」。
6. 留意狀態列（時間、電池那排）圖示在夜間模式下是否清楚可讀；這排的顏色行為與 1.0.14 相同，若在夜間模式下不易閱讀，記錄機型與畫面，另開任務處理。

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

所有指令都必須指定實體測試手機 serial，避免誤用已連線模擬器：

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
$serial = "9654983303001DD"

& $adb -s $serial shell dumpsys activity services com.va.voiceassistant
& $adb -s $serial shell cmd appops get com.va.voiceassistant RECORD_AUDIO
& $adb -s $serial shell dumpsys audio | Select-String 'com.va.voiceassistant|VOICE_RECOGNITION' -Context 1,2
& $adb -s $serial shell dumpsys power | Select-String 'com.va.voiceassistant:VoiceKws'
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
