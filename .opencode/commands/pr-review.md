---
description: 依 Issue Plan 審查 PR 差異與測試證據
agent: plan
---

請審查 PR：$ARGUMENTS

先讀 `AGENTS.md`、Issue Plan、PR Diff 與測試紀錄，再載入 `.agents/skills/review-pr/SKILL.md`；需要時參考專案文件。若工具不能載入 Skill，就直接閱讀該檔。

取得 PR 描述、完整 Diff、對應 Issue、已核准的 Issue Plan 與測試紀錄。若沒有 PR 編號或連結，先請使用者提供。高風險變更可交給 `pr-reviewer` 作第二次檢查；沒有實際啟動就不要宣稱完成獨立審查。

依嚴重程度列出具體發現、檔案與位置、觸發條件及影響，區分 Blocking 與建議事項。只審查與回報，不修改程式碼或自行 Merge。
