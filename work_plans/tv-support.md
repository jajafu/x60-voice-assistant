# 計畫：電視版支援（v7a 安裝＋遙控器介面＋電視端 MusicCabin 控制）

規劃日期：2026-11-02。規劃依據：`AGENTS.md`、`docs/PLAN_TEMPLATE.md`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`SystemSpeechRecognizerSupport.kt`、`WakePhraseCompiler.kt`、sherpa-onnx 1.13.8 AAR 實測內容。

## 使用者已確認的事實與決定

1. 海爾電視（2023 年購入）為 Android TV 10 以上 → 高於本 App 最低 SDK 26，可安裝。
2. 電視已安裝 MusicCabin 並使用一段時間。
3. 探測版必須先支援 `armeabi-v7a`，否則裝不上電視 → 32 位元支援排在最前面。
4. 先實測電視有無中文系統語音辨識；若沒有，使用者考慮改用英文啟動詞（如 "OK Google"／"Hey Google" 風格）。
5. 手機版與電視版為**同一個 App 自動切換**，手機行為不變。

## 已查證的關鍵事實

- sherpa-onnx 1.13.8 AAR（Gradle 快取實測）已內含 `armeabi-v7a`／`arm64-v8a`／x86／x86_64 原生庫 → v7a 支援只需改 `abiFilters`，不需升版或換套件。
- `AndroidManifest.xml` 目前 `uses-feature microphone required=true` → 電視若未回報麥克風特徵會被擋安裝，需改 `required=false`。
- `MainActivity` 鎖 `screenOrientation="portrait"`、無 leanback launcher → 電視上畫面錯誤且主畫面找不到圖示。
- `SystemSpeechRecognizerSupport` 目前**只認中文**：語言偏好全是 zh 系，探測結果無中文即回 `Unavailable("chinese-unsupported")`，服務直接啟動失敗。
- `WakePhraseCompiler.validateCharacters` 目前**只接受中文**（限 HAN 字元，錯誤訊息「目前喚醒模型只支援中文喚醒詞」）→ 英文啟動詞需要擴充編譯器。KWS 模型本身為 zh-en，tokens.txt 含英文 token，模型層面可行。
- 啟動詞語言與指令辨識語言是兩件事：就算啟動詞改英文，系統辨識若只有英文，**指令也要講英文才聽得懂**；此限制需如實告知。

## 目標與範圍

- 讓 App 能安裝並執行於海爾 Android TV 10+（armeabi-v7a 32 位元）。
- 新增電視介面：遙控器可啟動／停止語音助理、可完成設定；手機／電視同一 App 自動切換，手機行為不變。
- 電視端可控制 MusicCabin（喚醒後語音指令）。
- 以「探測版」先行，真機確認麥克風、系統辨識語言、MusicCabin 連線、TTS 可用性後，再決定後續範圍。
- **本次不做**：修改手機版既有行為、資料格式、release 簽章策略、開機自動啟動語音服務（`BootReminderReceiver` 維持只提醒）、長期待命防殺機制（另案處理）、`MusicCabinFavoriteListenerService` 確認策略變更。

## 做法（分階段）

### 第 1 階段：armeabi-v7a 支援（探測版前提）

1. `app/build.gradle.kts`：`abiFilters` 加 `armeabi-v7a`（保留 arm64-v8a）。
2. `AndroidManifest.xml`：`uses-feature microphone` 改 `required=false`；加 `uses-feature touchscreen required=false`。`RECORD_AUDIO` 權限宣告不變。
3. 建置 debug APK，確認兩種 ABI 的原生庫都在，記錄 APK 大小變化。
4. `.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug`。

### 第 2 階段：探測版真機驗證（不發布，僅側載）

1. ADB 側載進電視，確認安裝與啟動。
2. 最小讓診斷頁可用遙控器操作：移除 portrait 鎖，改為執行時依裝置類型決定方向（非電視維持直式），確保 D-pad 可聚焦。
3. 於海爾電視實測並記錄（`docs/test-reports/` 新檔，含型號、Android 版本、commit、APK SHA-256）：
   - 麥克風能否錄音（有無波形／丟幀）。
   - 系統語音辨識：探測結果與語言清單（診斷 `unverifiedReason`／`chinese-unsupported`）。
   - MusicCabin 媒體服務能否連線（設定頁選得到音樂 App）。
   - TTS 引擎是否存在、能否發音。
   - （選項）關螢幕隔夜後服務是否仍存活（`adb shell dumpsys` 觀察）。
4. 產出決策：中文辨識有無 → 決定第 4 階段走哪條路。

### 第 3 階段：電視介面（手機／電視同 App 自動切換）

1. 以 `UiModeManager.CURRENT_MODE_TELEVISION` 偵測電視模式，自動載入電視版單頁 UI；手機沿用現有 UI 與直式方向。
2. Manifest：加 leanback launcher intent-filter 與 `android.software.leanback` uses-feature `required=false`，讓電視主畫面出現圖示。
3. 電視 UI 用現有 Compose Material3 + `focusable()`（不新增 androidx.tv 依賴）：
   - 大字「啟動／停止語音助理」主卡，狀態文字沿用服務通知的狀態對應邏輯。
   - 設定清單：喚醒詞、音樂 App 選擇、LLM 連線（含 API Key，用系統鍵盤輸入）、TTS、診斷頁。
   - 全部操作遙控器上下／OK／返回可完成。
4. 單元測試：裝置類型判定與 UI 分流邏輯。

### 第 4 階段：語音辨識語言後備（依第 2 階段結果，三選一）

1. 電視有中文辨識：不需變更。
2. 只有英文辨識：`SystemSpeechRecognizerSupport` 語言偏好加入 en-US 後備（zh 優先、en 後備），`MODEL_ID` 改為 `system/android-<lang>` 反映實際語言，診斷顯示實際語言；如實標明「指令語言跟著辨識語言走」的限制。同時擴充 `WakePhraseCompiler` 接受英文啟動詞（以 KWS 模型既有英文 token 組音節），新增單元測試。
3. 英文也沒有：電視版停用語音流程，僅保留遙控器 UI 與 MusicCabin 遙控（需使用者同意此降級）。

### 第 5 階段：電視端 MusicCabin 控制

1. 沿用 `MusicCommandRouter`／`MusicCabinController`，確認電視版 MusicCabin 套件名與服務名一致（設定頁可選）。
2. 確認「通知使用權」（收藏／循環控制）在電視設定中的授權路徑；不可用時以錯誤訊息如實提示，不更改 rc19 確認策略。
3. 真機驗收：喚醒後下播放／暫停／下一首指令。

### 第 6 階段：收尾

1. 版本：建議 `versionName 1.1.0`／`versionCode 52`（待使用者核准）。
2. 文件：README（ABI 與電視安裝需求）、`docs/ARCHITECTURE.md`、`docs/testing.md`（電視測試項目）、`docs/known-issues.md`（電視限制與語言限制）。
3. `.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug`；MuMu smoke 依 `docs/ANDROID_MUMU_SMOKE.md` 判斷（MuMu 若為 x86_64 則不適用 v7a，記錄原因）。

## 驗收

- 以完整 commit 雜湊與 APK SHA-256 識別受測內容；`docs/test-reports/` 記錄海爾電視真機結果。
- 真機項目：APK 安裝成功、電視主畫面有圖示、遙控器完成啟動／停止與設定、喚醒成功、辨識成功（語言如實記錄）、MusicCabin 播放控制成功。
- 未執行項目如實標記「待驗收」，不以模擬器或單元測試替代真機驗收。

## 測試

- 自動：`.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug`（需先依 README 準備模型資產）。
- 人工：海爾電視實機（第 2／5 階段清單）、依 `docs/testing.md` 核對既有手機項目不受影響。

## 產品版本

- 需更新（行為與產物大改）：建議 `versionName 1.1.0`／`versionCode 52`，待使用者核准。
- Android APK 驗收適用；MuMu smoke 依 ABI 適用性記錄。

## 風險／回退

- **麥克風**：電視的收音開關麥克風未必以標準錄音裝置開放給 App；若不可用，語音功能整條失效，計畫重議。→ 探測版先行降低風險。
- **辨識語言**：電視可能無中文系統辨識；英文啟動詞不解決「指令也要英文」的問題。→ 第 4 階段三選一，由實測與使用者決定。
- **TTS**：電視可能無中文 TTS 引擎；語音回覆不可用時先靠畫面顯示回覆。
- **APK 變大**：加入 v7a 原生庫，體積約增加；arm64 裝置不受影響。
- **回退**：全部改動在 `task/tv-support` 分支進行（高風險：權限／Manifest／ABI），驗證不通過不合併 `main`；不合併即回退。

## 待決問題

1. 第 2 階段探測結果出來後，第 4 階段走哪條路（中文辨識／英文後備／純遙控降級）。
2. 英文啟動詞用什麼詞：建議中性詞（如 "hey assistant"）；"OK Google"／"Hey Google" 涉及 Google 商標，由使用者自行斟酌。
3. `versionName 1.1.0`／`versionCode 52` 是否採用。
4. 是否同意以 `task/tv-support` 分支（從最新 `origin/main`）進行本高風險變更。

## 核准狀態

- 尚未核准。依 `AGENTS.md` 待使用者明確同意本計畫範圍後才實作，並記錄同意來源。
