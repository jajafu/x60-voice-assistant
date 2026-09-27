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
3. 你在 Issue 留言或當次對話明確指示按 Plan 實作。AI 記錄核准來源後，使用對應分支實作、測試並建立 PR。
4. 用 `.agents/skills/review-pr/SKILL.md` 檢查重要變更；高風險時可換另一個模型或子 Agent 再看一次。你最後檢查 Diff、驗證結果與風險，決定是否合併。

## 產品版本與範本版本

`TEMPLATE_VERSION.md` 只記錄 AI 流程範本版本，不是 Android App 版本。專案目前由 `app/build.gradle.kts` 設定 `versionName = "1.0.0-rc21"`、`versionCode = 25`；診斷頁從已安裝套件讀版本。Release variant 未配置 production signing，不能把候選 APK 寫成正式發布。

- 從下一個 Issue 起，Plan 記錄「產品版本需更新／不需更新／待決」及原因，PR 寫實際版本與建置號。改變 App 行為、並交付新可安裝候選 APK 的 Issue，預設將既有 `1.0.0-rcN` 候選版號與遞增的 `versionCode` 更新一次；同一 Issue 的反覆修正不重複加版。
- 多個 Issue 合併同批候選版，或先實作而尚未交付 APK 時，在各 Plan／PR 記錄預計納入的版本與實際發版安排，由使用者決定。保留現有 release candidate 命名；不自動套用泛用的 patch／minor／major 規則。未來正式版本與相容性破壞的版號、遷移方式須先由使用者決定。
- 純文件、流程範本、測試程式或不改產品行為的內部重構預設不改產品版本，仍在 Plan／PR 說明。建立新候選版時核對 Gradle、診斷顯示、README／現況文件與測試紀錄的一致性；舊測試報告保留其當時版本，不追溯改寫。

## 歷史紀錄與工作日誌

查詢過去修改時，先用 `git log` 找提交、用 `git show`／`git diff` 看程式差異，再讀關聯 Issue、Plan、PR 與 `docs/decision-log.md` 中的需求原因、核准、驗證和風險。重要決策寫回 Issue／PR 或專案文件；單靠 Diff 無法還原理由。

本 Repository 目前沒有 `work_logs/`、沒有 Git 追蹤的工作日誌，也沒有對它的 `.gitignore` 規則；本機 OpenCode 設定中另有全域 `work-log` Skill，並非此專案的 Skill。若該工具產生日誌，Git 會視為未追蹤檔案。保留其既有工具設定；是否將日誌納入 Git、或僅保留本機，待實際需要時由使用者決定，不自動提交、忽略或大量建立空日誌。日誌不得含密鑰、個資或未確認結果，也不能取代 Issue／PR 與 Git 紀錄。

## Android 的 MuMu 安裝與開啟檢查

Android App 行為變更且已有對應 APK 時，本機 MuMuPlayer 與專用相容測試執行個體可用才按 [`ANDROID_MUMU_SMOKE.md`](ANDROID_MUMU_SMOKE.md) 檢查覆蓋安裝與開啟。本專案 APK 僅封裝 `arm64-v8a`，先核對執行個體的 ABI／Android 版本；MuMu smoke 不能替代 `docs/testing.md` 要求的 vivo X60 真人語音、鎖屏或長時間驗收。

若本次沒有 APK、MuMu 不可用或相容性未確認，在 PR 寫「未執行」和原因；不因流程範本升級而建 APK、啟動／清除模擬器或宣稱測試通過。

GitHub Issue 的 Open／Closed 與 PR 狀態就是進度紀錄，不維護多組人工狀態標籤。Issue 表單只用 `type:feature`、`type:bug`；若要自動套用，先在目標 Repository 建立這兩個標籤。Plan 核准以明確留言或當次指示為準，不能只靠標籤判定。

## 單人 Repository 設定

若使用 GitHub 分支保護，可要求透過 PR、必要 CI 通過並禁止主要分支強制推送／刪除。**不要設定必須由另一人 Approve 的審查門檻**，否則你自己建立的 PR 可能無法合併；GitHub 不允許作者核准自己的 PR。設定後用測試 PR 確認你能完成合併。參考 [GitHub 分支保護](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches) 與 [PR 審查規則](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/reviewing-proposed-changes-in-a-pull-request)。

`.github/workflows/` 目前沒有自動 AI 派工或審查。斜線命令需要你主動執行；工具操作見 `docs/OPENCODE_OPENCHAMBER.md`。
