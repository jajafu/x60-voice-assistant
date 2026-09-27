---
description: 依已核准的 Issue Plan 實作並準備 PR
agent: build
---

請處理已核准 Issue：$ARGUMENTS

先讀 `AGENTS.md`、Issue、Plan 與相關程式碼，再載入 `.agents/skills/implement-issue/SKILL.md`；需要時參考專案文件。若工具不能載入 Skill，就直接閱讀該檔。

確認使用者已在 Issue 或當次對話明確要求按 Plan 實作，並記錄來源；若只是分析指示或範圍重大改變，先釐清。核對現有工作樹與分支，沿用 OpenChamber／Codex 已建立的正確分支。完成核准範圍內的修改，按 `docs/DEVELOPMENT.md` 判斷產品版本並記錄實際驗證；Android 適用時依 `docs/ANDROID_MUMU_SMOKE.md` 檢查可行的 APK 安裝與開啟，未執行寫原因。依 `.github/PULL_REQUEST_TEMPLATE.md` 準備 PR；不能 Push／建 PR 時列出剩餘步驟，不自行 Merge。
