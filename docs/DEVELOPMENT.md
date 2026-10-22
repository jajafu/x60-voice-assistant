# 個人開發流程

你是唯一的專案負責人與實作範圍核准者。AI 是協作工具，可以在 OpenChamber／OpenCode 或 Codex 執行同一套規則。

## 新專案初版開發

1. 先和你討論並確認產品規格、成功條件、技術與架構選擇、限制及初版完成標準，記在 `docs/PROJECT.md` 與 `docs/ARCHITECTURE.md`。未確認的決定不能當成已核准。
2. 依已確認規格列出 M0、M1…直到初版完成所需的里程碑；數量由實際範圍決定。每項列出交付內容、依賴和驗收方式，讓你一次確認整體初版範圍。規格仍可能細化，但不能把未確認的功能當成已核准。
3. 確認後依里程碑順序分段實作、測試並記錄進度。每完成一項先執行相關驗證，再繼續下一項；可分多次對話接續。若發現重大範圍、技術、資料格式、權限或部署決定需要改變，提出具體差異再由你決定。
4. 初版期間直接在主要分支分段提交；每段 commit 都可檢查、可回退。需要隔離大型變更時，可在整合點開 `task/<主題>` 分支，不必預設每次都開。
5. 初版完成時執行整體驗收，更新文件中「規劃中」與實際現況的差異，並交付可檢查的程式碼、驗證結果和剩餘風險。你確認初版完成後，將專案階段改為維護；後續日常變更按下方流程。

本專案已在維護階段，沿用已確認的規格與里程碑；未完成的驗收依 `docs/testing.md` 與 `docs/known-issues.md` 如實標記。

## 日常變更流程

1. 先讀 `AGENTS.md`、任務描述與相關程式碼。需求不明、資料或公開介面變更時，先釐清範圍；小型文件錯字可直接修正。
2. 變更較大或有風險時，依 `docs/PLAN_TEMPLATE.md` 寫簡短計畫：目標、做法、驗收、測試、產品版本與風險。計畫可貼在對話中，或存成 `work_plans/<主題>.md`（本專案預設納入 Git）。你在對話或計畫檔明確同意後才算核准；AI 記錄核准來源，不能只要求分析就動工。
3. 每次會修改 Git 追蹤內容的工作開始前：執行 `git status --short --branch` 與 `git worktree list`，確認目前分支與未提交內容；執行 `git fetch origin` 取得遠端現況。日常變更直接從最新 `origin/main` 開始；資料格式、權限、公開 API、部署等高風險變更，從最新 `origin/main` 建立 `task/<主題>` 短分支，完成並驗證後合回，不留 PR。
4. 本機 `main` 乾淨、沒有未推送 commit，且只落後遠端時，以 fast-forward 同步。若已分歧、工作樹重疊、遠端或認證失敗，停止相關寫入並回報具體狀態。保留所有既有變更；不得為了同步而自行 commit、stash、rebase、強制移動分支或丟棄內容。
5. 只改核准範圍，先執行能及早發現問題的相關測試與靜態檢查。程式變更原有的 `test`、`lint`、`assembleDebug` 與模型資產準備仍是必要驗證。回報實際命令、結果及未執行原因，不能把未測試寫成通過。
6. 用 `.agents/skills/review-changes/SKILL.md` 審查變更；重要或高風險變更可換另一個模型或子 Agent 複查。沒有實際執行就不要宣稱已獨立審查。
7. 提交並推送。提交訊息說明做了什麼與為什麼；第一行寫成使用者看得懂的內容，發行 release 時會作為更新說明的來源。處理外部 Issue 時在訊息註明編號。範圍較大或高風險的變更，推送前在對話回報改動、驗證與風險，經你同意再推送；小型修正可直接提交推送。推送與發布由你本人決定；你在對話中明確指示由 AI 執行時，AI 可代為執行。
8. 人工驗收以完整 commit 雜湊為基準，而不是未提交的工作區或之後重新整理出的另一份內容：Android 記錄受測 commit、APK SHA-256、package 與 `versionName`／`versionCode`。專案可依實際技術替換識別方式，但必須能證明驗收結果對應目前的內容。
9. 受測內容沒有改變時沿用驗收結果，不重複建置或部署；會影響行為或產物的內容改變後，原驗收失效並重新驗證。發現問題時開新的修正任務；已推送的主要分支歷史不強制改寫，需要回退就建立回退提交。

## 人工驗收與 Android 產物識別

驗收紀錄記錄受測 commit、APK SHA-256、package、產品版本與建置號；測試手機真機驗收依 `docs/testing.md` 記錄裝置與證據，MuMu smoke 依 `docs/ANDROID_MUMU_SMOKE.md` 記錄執行個體、Android 版本及結果。此 Repository 沒有 Preview 網站、套件發布或部署 revision，相關欄位寫「不適用：Android App 以 APK 驗收」。`assembleDebug` 是既有必要驗證；若影響 APK 的內容或建置輸入改變，舊驗收失效並重新產生、驗證。測試手機真機測試報告另記產品版本、模型識別與測試環境，沿用 `docs/testing.md` 的驗收門檻。

## 產品版本與範本版本

`TEMPLATE_VERSION.md` 只記錄 AI 流程範本的套用版本。任務計畫要檢查的是目標專案自己的產品版本；本專案由 `app/build.gradle.kts` 設定 `versionName`、`versionCode`，診斷頁從已安裝套件讀版本。

- **開發者決定（2026-10-05）**：不再使用 `1.0.0-rcN` 候選版號，改為 `1.0.1`、`1.0.2`…依序遞增，直到另行決定。每次任務的計畫記錄「產品版本需更新／不需更新／待決」及原因，完成後回報實際版本與建置號。改變 App 行為、並交付新可安裝 APK 的任務，預設將 `1.0.N` 與遞增的 `versionCode` 各更新一次；同一任務的反覆修正不重複加版。
- 多個任務合併同批版本，或先實作而尚未交付 APK 時，在計畫／回報記錄預計納入的版本與實際發版安排，由使用者決定；不自動套用泛用的 patch／minor／major 規則。未來正式版本與相容性破壞的版號、遷移方式須先由使用者決定。
- 純文件、流程範本、測試程式或不改產品行為的內部重構預設不改產品版本，仍在計畫或回報說明。建立新候選版時核對 Gradle、診斷顯示、README／現況文件與測試紀錄的一致性；舊測試報告保留其當時版本，不追溯改寫。
- 發行版以 Git 標籤 `v<versionName>` 觸發 `.github/workflows/build-release-apk.yml` 建置簽章 APK 並發布 GitHub Release；標籤與 `versionName` 不一致時建置會失敗。簽章設定依 `docs/release-signing.md`。

## 歷史紀錄與工作日誌

查詢過去修改時，先用 `git log` 找提交、用 `git show`／`git diff` 看程式差異，再讀關聯 Issue、計畫與 `docs/decision-log.md` 中的需求原因、核准、驗證和風險。重要決策寫回計畫檔或專案文件；單靠 Diff 無法還原理由。

`work_logs/YYYY-MM-DD.md` 是**可選**的跨對話工作交接。本專案的 `work_logs/` 已在 `.gitignore`，僅留本機、不納入 Git；本機 OpenCode 設定中的全域 `work-log` Skill 可寫入，不屬於專案 Skill。簡記已完成、實際驗證、阻塞與下一步，不自動提交、不大量建立空日誌；是否改為納入 Git 由使用者決定。日誌不得含密鑰、個資或未確認結果，也不能取代提交紀錄與專案文件。

## 外部 Issue

開源專案的外部使用者可在 GitHub 開 Issue 回報問題或提議。收到後先確認可重現、範圍與優先順序，再納入日常變更流程處理；處理時在提交訊息註明編號（例如 `(#12)`），完成推送後在 Issue 說明結果再關閉。自己開發不另外開 Issue。Issue 表單只用 `type:feature`、`type:bug`；若要自動套用，先在 Repository 建立這兩個標籤。核准以明確留言或當次指示為準，不能只靠標籤判定。

## Android 的 MuMu 安裝與開啟檢查

Android App 行為變更時，從要驗收的 commit 依專案既有命令建置或取得 APK；本機 MuMuPlayer 與專用相容測試執行個體可用才按 [`ANDROID_MUMU_SMOKE.md`](ANDROID_MUMU_SMOKE.md) 覆蓋安裝與開啟。本專案 APK 僅封裝 `arm64-v8a`，先核對執行個體的 ABI／Android 版本；MuMu smoke 不能替代 `docs/testing.md` 要求的測試手機真人語音、鎖屏或長時間驗收。

若本次沒有 APK、MuMu 不可用或相容性未確認，回報「未執行」和原因；不因流程範本升級而建 APK、啟動／清除模擬器或宣稱測試通過。

## Git 與 GitHub 現況

本 Repository 允許 merge commit、squash merge 與 rebase merge，沒有自動合併、分支保護或 ruleset。`main` 沒有設定 upstream，操作時明確使用 `origin/main`。日常變更直接推送 `main`，**不要啟用需要 Pull Request 的分支保護**，否則直接推送會被擋下。若想加自動檢查，可在 `.github/workflows/` 設推送時執行的 CI，讓失敗在推送後回報；要不要因此擋住分支，先實測再決定。

`.github/workflows/` 目前沒有自動 AI 派工或審查。斜線命令需要你主動執行；工具操作見 `docs/OPENCODE_OPENCHAMBER.md`。
