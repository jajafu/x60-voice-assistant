---
description: 分析任務並提出供個人開發者確認的簡短計畫
agent: plan
---

請規劃任務：$ARGUMENTS

先讀 `AGENTS.md` 與相關程式碼，再載入 Skill `plan-task`；需要時參考專案文件。無法用 skill 工具載入時，直接閱讀 `.agents/skills/plan-task/SKILL.md`。

任務可能來自對話中的描述或外部使用者開的 Issue；若有 Issue 編號或連結，先取得原文與留言。變更較大或有風險時，依 `docs/PLAN_TEMPLATE.md` 產出簡短計畫，列明做法、驗收條件、測試、以完整 commit 與 APK SHA-256 識別的驗收交付物及風險；小修正直接說明做法即可。高風險任務可交給 `task-analyst` 再檢查一次；沒有實際啟動就不要宣稱使用了子 Agent。等待使用者本人明確同意後再實作；不修改產品程式碼，也不替使用者寫核准紀錄。
