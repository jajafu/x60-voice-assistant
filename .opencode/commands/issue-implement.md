---
description: 依已核准的 Issue Plan 實作並準備 PR
agent: build
---

請處理已核准 Issue：$ARGUMENTS

先讀 `AGENTS.md`、Issue、Plan 與相關程式碼，再載入 `.agents/skills/implement-issue/SKILL.md`；需要時參考專案文件。若工具不能載入 Skill，就直接閱讀該檔。

確認使用者已在 Issue 或當次對話明確要求按 Plan 實作，並記錄來源；若只是分析指示或範圍重大改變，先釐清。依 `docs/DEVELOPMENT.md` 檢查工作樹與全部 worktree、fetch 遠端並核對來源／目標分支；沿用 OpenChamber／Codex 已建立的正確分支，新分支從最新 `origin/main` 建立。同步遇到既有修改、未推送 commit、分歧或遠端問題時保留現況並回報。完成核准範圍內的修改，判斷產品版本並先執行必要的快速檢查；提交、推送後建立 Draft PR，再核對目前 HEAD 與 APK 來源 commit／建置輸入，將 commit 及 SHA-256 寫入驗收紀錄。Android 適用時依 `docs/ANDROID_MUMU_SMOKE.md` 檢查可行的 APK 安裝與開啟，X60 功能驗收依 `docs/testing.md`，未執行寫原因。未變更受測內容時不重複建置；有影響時使舊驗收失效並重新驗證。驗收完成後才標為 Ready；不能 Push／建 PR 時列出剩餘步驟。使用者在遠端合併後依 `docs/DEVELOPMENT.md` fetch 並安全同步本機 `main`，不得重複合併或 push；AI 不自行 Merge。
