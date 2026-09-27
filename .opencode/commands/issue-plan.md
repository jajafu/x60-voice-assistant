---
description: 分析 Issue 並提出供個人開發者確認的簡短 Plan
agent: plan
---

請討論 Issue：$ARGUMENTS

先讀 `AGENTS.md`、Issue 與相關程式碼，再載入 `.agents/skills/discuss-issue/SKILL.md`；需要時參考專案文件。若工具不能載入 Skill，就直接閱讀該檔。

先取得 Issue 原文、留言及現有 Plan，並查核相關程式碼。若沒有 Issue 編號或連結，先請使用者提供。遇到範圍不清或高風險變更時，可交給 `issue-analyst` 再檢查一次；沒有實際啟動就不要宣稱使用了子 Agent。

依 `docs/ISSUE_PLAN_TEMPLATE.md` 產出簡短 Plan，列明做法、驗收條件、測試、Draft PR HEAD 的 APK commit／SHA-256 識別方式、產品版本與風險。此專案沒有 Preview／部署產物，標示不適用。等待使用者本人明確同意後再實作；不修改產品程式碼，也不替使用者寫核准紀錄。
