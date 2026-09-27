# 個人開發流程

你是唯一的專案負責人、實作範圍核准者與 PR 合併者。AI 是協作工具，可以在 OpenChamber／OpenCode 或 Codex 執行同一套規則。

## 新專案初版開發

1. 先和你討論並確認產品規格、成功條件、技術與架構選擇、限制及初版完成標準，記在 `docs/PROJECT.md` 與 `docs/ARCHITECTURE.md`。未確認的決定不能當成已核准。
2. 依已確認規格列出 M0、M1…直到初版完成所需的里程碑；數量由實際範圍決定，不固定在 M10。每項列出交付內容、依賴和驗收方式，讓你一次確認整體初版範圍。規格仍可能細化，但不能把未確認的功能當成已核准。
3. 確認後依里程碑順序分段實作、測試並記錄進度。每完成一項先執行相關驗證，再繼續下一項；可分多次對話接續，不必為每項重開 Issue、PR 或重複要求核准。若發現重大範圍、技術、資料格式、權限或部署決定需要改變，提出具體差異再由你決定。
4. 初版期間按變更量與風險選擇可檢查的整合點；需要讓你審查大型變更或使用分支保護／CI 時，可在整合點交付 PR，不必等所有里程碑做完，也不預設每項都要 PR。PR 的建立與合併仍依你的要求及 Repository 設定；只有你決定合併。
5. 初版完成時執行整體驗收，更新文件中「規劃中」與實際現況的差異，並交付可檢查的程式碼、驗證結果和剩餘風險。你確認初版完成後，將專案階段改為維護；後續日常變更按下方 Issue → PR 流程。

## Issue → PR

1. 以 `.github/ISSUE_TEMPLATE/` 建立 Feature 或 Bug Issue。小型文件錯字可略過 Issue。
2. 用 `.agents/skills/discuss-issue/SKILL.md` 整理簡短 Plan：目標、做法、驗收、測試與風險。範圍清楚的小修正可用幾行完成；高風險變更再補資料、相容性與回退細節。
3. 你在 Issue 留言或當次對話明確指示按 Plan 實作。AI 記錄核准來源後，修改前按「Git 同步與 PR 合併」檢查狀態、全部 worktree 並 fetch 遠端；新 Issue 從最新 `origin/main` 建立分支，既有 PR 沿用其來源分支。
4. 在核准分支實作，先執行能及早發現問題的測試與靜態檢查。程式變更原有的 `test`、`lint`、`assembleDebug` 與模型資產準備仍是必要驗證。
5. 將第一個可驗證版本提交並推送後建立關聯 Issue 的 Draft PR。因 `assembleDebug` 本身是必要驗證，可在 PR 前建置；Draft PR 建立後確認 APK 的來源 commit 與建置輸入符合目前 PR HEAD，輸入未變時沿用同一產物。
6. 人工驗收以 Draft PR 目前 HEAD 為準。Android APK 記錄完整 commit、APK SHA-256、package、產品版本與建置號；X60 真機驗收依 `docs/testing.md` 記錄裝置與證據，MuMu smoke 依 `docs/ANDROID_MUMU_SMOKE.md` 記錄執行個體、Android 版本及結果。此 Repository 沒有 Preview／部署產物，寫明「不適用：Android App 以 APK 驗收」。若 HEAD 或影響行為／APK 的輸入改變，舊人工驗收失效並重新驗證；沒有改變就不因 PR 描述或狀態切換重建。
7. 驗收完成後將 PR 標為 Ready。用 `.agents/skills/review-pr/SKILL.md` 檢查重要變更；高風險時可換另一個模型或子 Agent 再看一次。你最後檢查 Diff、驗證結果與風險，決定是否合併；Issue 到合併後才關閉。

## Draft PR HEAD 與 Android 驗收

Draft PR 是供人工驗收與審查的變更集合，不表示可以合併。若環境無法建立 Draft PR，先固定並記錄待提交 commit，再從該 commit 產生驗收內容；建 PR 後核對 HEAD 一致。此 Repository 沒有 Preview URL、套件發布或部署 revision；Android 可交付驗收物以 APK SHA-256 和其來源 commit 識別。X60 真機測試報告另記產品版本、模型識別與測試環境，沿用 `docs/testing.md` 的驗收門檻。

## Git 同步與 PR 合併

本 Repository 使用 GitHub PR；GitHub 目前允許 merge commit、squash merge 與 rebase merge，沒有自動合併、分支保護或 ruleset。合併由你本人決定，網站或 `gh` 合併會更新遠端 `main`，本機分支之後再同步；不要求先在本機 `main` 合併再 push。`main` 目前沒有設定 upstream，操作時明確使用 `origin/main`。

`origin/<branch>` 是最近一次 fetch 後保存的遠端追蹤紀錄，不會即時更新。本專案每次會修改 Git 追蹤內容的工作開始前：

1. 執行 `git status --short --branch` 與 `git worktree list`，確認目前分支、未提交內容及其他 worktree。
2. 保留所有既有變更；不得為了同步而自行 commit、stash、rebase、強制移動分支或丟棄內容。
3. 執行 `git fetch origin`。新 Issue 從最新 `origin/main` 建立；不要只因本機 `main` 存在就假設它已同步。既有 PR 沿用其來源分支，先核對遠端來源／目標及是否落後。
4. 本機 `main` 乾淨、沒有未推送 commit，且只落後遠端時，以 fast-forward 同步。若已分歧、工作樹重疊、遠端或認證失敗，停止相關寫入並回報具體狀態。

PR 在遠端合併後，執行 `git fetch origin`，以 PR 狀態及遠端 `main` 核對結果，再安全 fast-forward 本機 `main`。Squash merge 會建立新的主要分支 commit；不要因此在本機再合併一次或重複 push。若本機 `main` 暫時不能更新，清楚回報，後續新工作仍須以最新 `origin/main` 為起點。

## 產品版本與範本版本

`TEMPLATE_VERSION.md` 只記錄 AI 流程範本版本，不是 Android App 版本。專案目前由 `app/build.gradle.kts` 設定 `versionName = "1.0.0-rc21"`、`versionCode = 25`；診斷頁從已安裝套件讀版本。Release variant 未配置 production signing，不能把候選 APK 寫成正式發布。

- 從下一個 Issue 起，Plan 記錄「產品版本需更新／不需更新／待決」及原因，PR 寫實際版本與建置號。改變 App 行為、並交付新可安裝候選 APK 的 Issue，預設將既有 `1.0.0-rcN` 候選版號與遞增的 `versionCode` 更新一次；同一 Issue 的反覆修正不重複加版。
- 多個 Issue 合併同批候選版，或先實作而尚未交付 APK 時，在各 Plan／PR 記錄預計納入的版本與實際發版安排，由使用者決定。保留現有 release candidate 命名；不自動套用泛用的 patch／minor／major 規則。未來正式版本與相容性破壞的版號、遷移方式須先由使用者決定。
- 純文件、流程範本、測試程式或不改產品行為的內部重構預設不改產品版本，仍在 Plan／PR 說明。建立新候選版時核對 Gradle、診斷顯示、README／現況文件與測試紀錄的一致性；舊測試報告保留其當時版本，不追溯改寫。

## 歷史紀錄與工作日誌

查詢過去修改時，先用 `git log` 找提交、用 `git show`／`git diff` 看程式差異，再讀關聯 Issue、Plan、PR 與 `docs/decision-log.md` 中的需求原因、核准、驗證和風險。重要決策寫回 Issue／PR 或專案文件；單靠 Diff 無法還原理由。

本 Repository 目前沒有 `work_logs/`、沒有 Git 追蹤的工作日誌，也沒有對它的 `.gitignore` 規則；本機 OpenCode 設定中另有全域 `work-log` Skill，並非此專案的 Skill。若該工具產生日誌，Git 會視為未追蹤檔案。保留其既有工具設定；是否將日誌納入 Git、或僅保留本機，待實際需要時由使用者決定，不自動提交、忽略或大量建立空日誌。日誌不得含密鑰、個資或未確認結果，也不能取代 Issue／PR 與 Git 紀錄。

## Android 的 MuMu 安裝與開啟檢查

Android App 行為變更時，從 Draft PR 目前 HEAD 依專案既有命令建置或取得 APK；本機 MuMuPlayer 與專用相容測試執行個體可用才按 [`ANDROID_MUMU_SMOKE.md`](ANDROID_MUMU_SMOKE.md) 覆蓋安裝與開啟。本專案 APK 僅封裝 `arm64-v8a`，先核對執行個體的 ABI／Android 版本；MuMu smoke 不能替代 `docs/testing.md` 要求的 vivo X60 真人語音、鎖屏或長時間驗收。

若本次沒有 APK、MuMu 不可用或相容性未確認，在 PR 寫「未執行」和原因；不因流程範本升級而建 APK、啟動／清除模擬器或宣稱測試通過。

GitHub Issue 的 Open／Closed 與 PR 狀態就是進度紀錄，不維護多組人工狀態標籤。Issue 表單只用 `type:feature`、`type:bug`；若要自動套用，先在目標 Repository 建立這兩個標籤。Plan 核准以明確留言或當次指示為準，不能只靠標籤判定。

## 單人 Repository 設定

若使用 GitHub 分支保護，可要求透過 PR、必要 CI 通過並禁止主要分支強制推送／刪除。**不要設定必須由另一人 Approve 的審查門檻**，否則你自己建立的 PR 可能無法合併；GitHub 不允許作者核准自己的 PR。設定後用測試 PR 確認你能完成合併。參考 [GitHub 分支保護](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches) 與 [PR 審查規則](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-proposed-changes-in-a-pull-request)。

`.github/workflows/` 目前沒有自動 AI 派工或審查。斜線命令需要你主動執行；工具操作見 `docs/OPENCODE_OPENCHAMBER.md`。
