---
description: 審查目前變更或指定提交的差異與測試證據
agent: plan
---

請審查變更：$ARGUMENTS

先讀 `AGENTS.md`、任務計畫與測試紀錄，再載入 Skill `review-changes`；需要時參考專案文件。無法用 skill 工具載入時，直接閱讀 `.agents/skills/review-changes/SKILL.md`。

$ARGUMENTS 留空時審查未提交變更與最後一次提交；也可以指定 commit 或外部 Issue 編號。取得完整差異與驗收紀錄，核對紀錄的 commit、APK SHA-256、產品版本與建置號是否對應；適用時核對 MuMu／測試手機結果。此專案沒有 Preview／部署 revision。高風險變更可交給 `change-reviewer` 作第二次檢查；沒有實際啟動就不要宣稱完成獨立審查。依嚴重程度列出具體發現、檔案與位置、觸發條件及影響，區分必須修正與建議事項。只審查與回報，不修改程式碼。
