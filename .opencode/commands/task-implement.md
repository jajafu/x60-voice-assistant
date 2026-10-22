---
description: 依已同意的任務計畫實作並提交
agent: build
---

請處理已同意的任務：$ARGUMENTS

先讀 `AGENTS.md`、任務計畫與相關程式碼，再載入 Skill `implement-task`；需要時參考專案文件。無法用 skill 工具載入時，直接閱讀 `.agents/skills/implement-task/SKILL.md`。

確認使用者已在對話或計畫檔明確同意按計畫實作，並記錄來源；若只是分析指示或範圍重大改變，先釐清。依 `docs/DEVELOPMENT.md` 檢查工作樹與全部 worktree、fetch 遠端；日常直接從最新 `origin/main` 工作，高風險變更才從 `origin/main` 建立 `task/<主題>` 短分支。同步遇到既有修改、未推送 commit、分歧或遠端問題時保留現況並回報。完成核准範圍內的修改，判斷產品版本並先執行必要驗證；提交後按流程推送，記錄完整 commit 與 APK SHA-256 作為驗收基準。Android 適用時依 `docs/ANDROID_MUMU_SMOKE.md` 執行 APK 安裝與開啟檢查，測試手機功能驗收依 `docs/testing.md`，未執行寫明原因。受測內容未改變時不重複建置；會影響驗收的內容改變時，將舊驗收標為失效並重新驗證。
