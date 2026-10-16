# AI 協作規則：小助手 Voice Assistant

本專案只有一位人類開發者。使用者本人決定需求與取捨、核准實作範圍、檢查結果並合併 PR；AI Agent 可以分析、修改、測試及提出審查意見。合併由使用者決定；使用者在對話中明確指示由 AI 合併時，AI 可代為執行（等同使用者同意），不需再要求使用者親自按。既有 Issue／PR 不追溯改寫；從下一個 Issue 起採用本流程。

## 專案設定

- 主要分支：`main`
- Issue 分支格式：`issue/<id>-<summary>`（新流程慣例；既有分支保持原狀）
- 主要技術：Kotlin、Jetpack Compose／Material 3、Android Gradle Plugin、Coroutines、sherpa-onnx、OkHttp、DataStore、Android Keystore、WorkManager、Media3；單一 Android `app` 模組，compile SDK 36、target SDK 35、最低 SDK 26、APK 限 `arm64-v8a`。
- 架構概要：Android Activity 管理設定與顯示，使用者啟動的 microphone foreground service 持有音訊與生命週期；`VoiceOrchestrator` 統一狀態切換，將本機 KWS → VAD → 下載式 X-ASR 的文字優先送本機音樂／提醒指令，再將一般問答送使用者設定的 OpenAI 相容服務，按句交給 Android TTS。設定與提醒留在裝置；從雲端硬碟下載的 X-ASR 壓縮檔經校驗後安裝到 App 私有空間。

模組、資料流與相容性細節見 `docs/ARCHITECTURE.md`。以上依 `README.md`、Gradle、Manifest 與程式碼查證；實際驗收狀態見 `docs/testing.md` 與 `docs/known-issues.md`。

必要驗證：

- 程式變更執行 `.\gradlew.bat test`、`.\gradlew.bat lint`、`.\gradlew.bat assembleDebug`；需先依 `README.md` 準備模型資產。
- 涉及音訊、權限、音樂或提醒時，依 `docs/testing.md` 與相關真機紀錄核對適用的測試手機手動項目；未執行不得寫成通過。

額外規則：

- 保留既有未提交修改，不擅改 Android 權限、資料格式、模型授權、部署或 release 簽章策略。
- API Key 只由使用者在 App 輸入；不得寫入原始碼、Issue、PR、Log 或診斷匯出。模型授權、私有儲存與資料保留依 `README.md`、`docs/privacy.md`、`docs/decision-log.md`。
- 真實 Provider、真人語音、長時間鎖屏與 MusicCabin 未完成的驗收項目應如實標記，不以單元測試、合成音或短測替代。
- MusicCabin 喜愛清單播放的 rc19 回覆行為與風險依 `docs/known-issues.md`、`docs/phone-control-plan.md`，未核准前勿改其確認策略。

## 每次工作的順序

既有專案已在 release candidate 階段。尚未完成的初版驗收依 `docs/DEVELOPMENT.md` 的「新專案初版開發」精神，沿用已確認的規格與里程碑，不重新要求相同範圍的核准。日常新變更從下一個 Issue 起依下列流程。

1. 先讀本文件、目前任務／Issue 與相關程式碼；涉及功能、資料或結構時讀 `docs/PROJECT.md` 與 `docs/ARCHITECTURE.md`，並按需參考 `docs/DEVELOPMENT.md`。較早規劃文件若與目前程式或決策紀錄不同，以已驗證的現況為準並指出差異。
2. 程式變更原則上有 Issue。小型文件錯字可直接修正；需求不明、資料或公開介面變更時，先釐清範圍。
3. 用 `docs/ISSUE_PLAN_TEMPLATE.md` 寫出與風險相稱的 Plan。使用者在 Issue 留言或目前對話明確要求按該範圍實作，才算核准；只要求分析不算核准。記錄核准來源，不得由 AI 假造。
4. 修改前檢查 `git status --short --branch`、目前分支與 `git worktree list`，再執行 `git fetch origin`。新 Issue 從最新 `origin/main` 建立分支；既有 PR 沿用其來源分支並核對遠端來源／目標。僅當本機 `main` 乾淨、沒有未推送 commit 且只是落後時才 fast-forward 同步。遇到未提交變更、未推送 commit、分歧、認證或遠端問題時保留現況並回報；不得自動 commit、stash、rebase、強制移動或丟棄內容。不得直接在主要分支開發正式功能。流程範本升級不切換分支。
5. 只改核准範圍；執行相關測試與必要驗證。回報實際命令、結果及未執行原因，不能把未測試寫成通過。
6. 按 `docs/DEVELOPMENT.md` 判斷產品版本，核對 `app/build.gradle.kts` 的 `versionName`／`versionCode`、App 診斷顯示與相關文件；`TEMPLATE_VERSION.md` 只記錄流程範本版本。Android 行為變更且有對應 APK 時，若有相容的專用 MuMu 執行個體，依 `docs/ANDROID_MUMU_SMOKE.md` 檢查安裝及開啟；未執行就記錄原因，MuMu 不取代測試手機真機驗收。
7. 將已通過快速檢查且可驗證的版本提交並推送到 Issue 分支，建立關聯 Issue 的 Draft PR。PR 標題寫成使用者看得懂的一句話（會自動變成 release 更新內容）。既有必要的 PR 前 `assembleDebug` 保留；建立 Draft PR 後核對 APK 對應的 commit 與建置輸入仍符合目前 PR HEAD。
8. 人工驗收以 Draft PR 目前 HEAD 為準。Android 測試記錄受測 commit、APK SHA-256、package、`versionName`／`versionCode`；MuMu 記錄相容執行個體與安裝／啟動結果，測試手機功能驗收依 `docs/testing.md` 記錄裝置及證據。此專案沒有 Preview／部署產物，相關欄位寫「不適用：Android App 以 APK 驗收」。PR HEAD 或影響 APK／行為的建置輸入改變，舊人工驗收失效；若 commit、輸入和產物未變，不因 PR 描述或 Draft／Ready 狀態改變而重建。
9. 驗收完成後將 PR 標為 Ready，說明改動、產品版本決定、HEAD、APK 識別方式、驗證與風險。重要或高風險變更可請另一個 AI 模型或 Agent 審查；沒有實際執行就不要宣稱已獨立審查。
10. 由使用者本人看 Diff、測試和風險後決定是否合併；使用者在對話中明確指示由 AI 合併時，AI 可直接執行合併，不需再要求使用者親自按。GitHub PR 在網站或 `gh` 合併會更新遠端主要分支，這是正常流程。
11. 得知 PR 已在遠端合併後，執行 `git fetch origin` 並核對合併結果；本機 `main` 可安全 fast-forward 時同步到 `origin/main`。無法安全同步就回報狀態，下一個 Issue 仍從最新遠端主要分支建立。不得為了同步而在本機重複合併或 push。

## 停下來釐清的情況

- 需求或驗收條件有重大歧義。
- 變更會超出已核准的初版里程碑或 Issue 範圍，或影響資料格式、權限、公開 API、部署與不可逆資料。
- 重要測試無法執行，且無法判斷結果是否安全。

先完成可確認的部分，再提出具體問題；不要猜測重要專案事實。不得提交密碼、API Key、Token、私鑰或將其寫入 Issue／PR／Log。

## 可用工作入口

- `.agents/skills/discuss-issue/SKILL.md`：整理 Issue Plan。
- `.agents/skills/implement-issue/SKILL.md`：實作已核准範圍。
- `.agents/skills/review-pr/SKILL.md`：審查 PR。

OpenChamber／OpenCode 的斜線命令與 Codex 的使用方式見 `docs/OPENCODE_OPENCHAMBER.md`。AI 角色是工具分工，不代表有多位人類開發者。
