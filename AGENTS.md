# AI 協作規則：小助手 Voice Assistant

本專案只有一位人類開發者。使用者本人決定需求與取捨、核准實作範圍並檢查結果；AI Agent 可以分析、修改、測試及提出審查意見，不得代替使用者做最終決定。提交、推送與發布等 Git 寫入操作由使用者決定；使用者在對話中明確指示由 AI 執行時，AI 可代為執行（等同使用者同意）。既有 Issue／PR 不追溯改寫；從下一個任務起採用本流程。

## 專案設定

- 主要分支：`main`；日常變更直接在此分支修改、驗證、提交並推送
- 高風險變更（資料格式、權限、公開 API、部署或大範圍重構）先開 `task/<主題>` 短分支，完成並驗證後合回主要分支，不使用 PR
- 主要技術：Kotlin、Jetpack Compose／Material 3、Android Gradle Plugin、Coroutines、sherpa-onnx、OkHttp、DataStore、Android Keystore、WorkManager、Media3；單一 Android `app` 模組，compile SDK 36、target SDK 35、最低 SDK 26、APK 限 `arm64-v8a`。
- 架構概要：Android Activity 管理設定與顯示，使用者啟動的 microphone foreground service 持有音訊與生命週期；`VoiceOrchestrator` 統一狀態切換，啟動時探測系統語音辨識（存在且支援中文）並優先使用，系統不可用時退回本機 KWS → VAD → 下載式 X-ASR 後備；辨識文字優先送本機音樂／提醒指令，再將一般問答送使用者設定的 OpenAI 相容服務，按句交給 Android TTS。設定與提醒留在裝置；從 sherpa-onnx 官方 release 下載的 X-ASR 壓縮檔經校驗後安裝到 App 私有空間。

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

專案已在維護階段（release candidate）。尚未完成的初版驗收依 `docs/DEVELOPMENT.md` 的「新專案初版開發」精神，沿用已確認的規格與里程碑，不重新要求相同範圍的核准。日常變更依下列流程。

1. 先讀本文件、任務描述與相關程式碼；涉及功能、資料或結構時讀 `docs/PROJECT.md` 與 `docs/ARCHITECTURE.md`，並按需參考 `docs/DEVELOPMENT.md`。較早規劃文件若與目前程式或決策紀錄不同，以已驗證的現況為準並指出差異。不要把範本占位文字當成專案事實。
2. 釐清範圍：小型文件錯字可直接修正；小變更在對話中確認做法即可。變更較大或有風險時，依 `docs/PLAN_TEMPLATE.md` 寫出與風險相稱的計畫，等使用者明確同意後才實作。記錄同意來源（對話或計畫檔），不得由 AI 假造；只要求分析不算核准。外部使用者開的 Issue 是需求來源，確認可重現與範圍後納入本流程。
3. 修改前檢查 `git status --short --branch`、目前分支與 `git worktree list`，再執行 `git fetch origin`。日常變更從最新 `origin/main` 直接開始；高風險變更才從最新 `origin/main` 建立 `task/<主題>` 分支。僅當本機 `main` 乾淨、沒有未推送 commit 且只是落後時才 fast-forward 同步。遇到未提交變更、未推送 commit、分歧、認證或遠端問題時保留現況並回報；不得自動 commit、stash、rebase、強制移動或丟棄內容。流程範本升級不切換分支。
4. 只改核准範圍；先執行能及早發現問題的相關測試與靜態檢查。回報實際命令、結果及未執行原因，不能把未測試寫成通過。
5. 按 `docs/DEVELOPMENT.md` 判斷產品版本，核對 `app/build.gradle.kts` 的 `versionName`／`versionCode`、App 診斷顯示與相關文件；`TEMPLATE_VERSION.md` 只記錄流程範本版本。Android 行為變更且有對應 APK 時，若有相容的專用 MuMu 執行個體，依 `docs/ANDROID_MUMU_SMOKE.md` 檢查安裝及開啟；未執行就記錄原因，MuMu 不取代測試手機真機驗收。
6. 用 `.agents/skills/review-changes/SKILL.md` 審查變更；重要或高風險變更可請另一個 AI 模型或 Agent 複查。沒有實際執行就不要宣稱已獨立審查。
7. 將可驗證版本提交；提交訊息第一行寫成使用者看得懂的內容（會成為 release 更新說明的來源），內文寫清楚做了什麼與為什麼，處理外部 Issue 時在訊息註明編號。範圍較大或高風險的變更，推送前在對話回報改動、產品版本決定、驗證與風險，經使用者同意再推送；小型修正可直接提交推送。
8. 人工驗收以完整 commit 雜湊為基準。Android 測試記錄受測 commit、APK SHA-256、package、`versionName`／`versionCode`；MuMu 記錄相容執行個體與安裝／啟動結果，測試手機功能驗收依 `docs/testing.md` 記錄裝置及證據。此專案沒有 Preview／部署產物，相關欄位寫「不適用：Android App 以 APK 驗收」。受測內容沒有改變時沿用結果；會影響行為或產物的內容改變後，原驗收失效並重新驗證。使用者本人隨時可看 `git log`、差異與遠端現況，決定接受、退回或修正；發現問題時開新的修正任務，不強制改寫已推送的歷史。

## 停下來釐清的情況

- 需求或驗收條件有重大歧義。
- 變更會超出已核准的初版里程碑或任務範圍，或影響資料格式、權限、公開 API、部署與不可逆資料。
- 重要測試無法執行，且無法判斷結果是否安全。

先完成可確認的部分，再提出具體問題；不要猜測重要專案事實。不得提交密碼、API Key、Token、私鑰或將其寫入計畫檔與 Log。

## 可用工作入口

- `.agents/skills/plan-task/SKILL.md`：規劃任務、寫計畫。
- `.agents/skills/implement-task/SKILL.md`：實作已同意的範圍。
- `.agents/skills/review-changes/SKILL.md`：審查變更。

OpenChamber／OpenCode 與 Codex 都會自動探索這三個 Skill；斜線命令與各工具的呼叫方式見 `docs/OPENCODE_OPENCHAMBER.md`。AI 角色是工具分工，不代表有多位人類開發者。
