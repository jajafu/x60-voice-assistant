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

## 話題邊界與 Follow Up 視窗（rc25）

喚醒詞開啟新一輪時清空對話歷史，只有同一輪 Follow Up 視窗（預設 3 秒，可調 0–30）內的追問才帶著上一輪。需在 vivo X60 搭配真實 Provider 驗收：

1. 喚醒問一個話題 A（例：「BLACKPINK 是哪一年出道的」），等回答完畢，**不要**在 3 秒內追問，確認診斷頁狀態回到 `IDLE_KWS`。
2. 重新說喚醒詞問不相關的 B（例：「明天台北天氣如何」），確認回答針對 B，且診斷頁 LLM 完成記錄的 `length`／`hash` 對應的是 B 的回答而非 A 的續篇。若診斷頁允許查看，逐字稿應只有 B。
3. 重複步驟 1–2 共 10 次，確認不再出現沿用舊話題的情況。
4. 喚醒問 A，等回答完畢後在 3 秒內直接說「那第二首呢」（不說喚醒詞），確認追問成立且回答與 A 相關。
5. 設定 → Follow Up 設為 0，確認回答結束後直接回到 `IDLE_KWS`、不接受無喚醒詞的追問。
6. 設定 → Follow Up 設為 10，確認步驟 4 在 10 秒內仍成立。
7. 回答朗讀中說喚醒詞插話改問 B，確認舊回答停止、B 的請求不帶入 A 的上下文。

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
