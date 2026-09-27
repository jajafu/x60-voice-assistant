---
name: review-pr
description: 依 Issue Plan 審查 PR 的正確性、回歸、測試與重要風險，交由個人開發者決定是否合併。
---

# 審查 PR

先讀 `AGENTS.md`、Issue Plan、PR Diff 與測試紀錄。人工驗收以 Draft PR 目前 HEAD 為準；核對紀錄中的 commit 是否相同，並檢查 Android APK SHA-256、產品版本／建置號及適用的 MuMu／X60 證據。HEAD 或相關建置輸入不一致時，將舊結果標為失效或未驗證。此專案沒有 Preview／部署 revision。優先找出可重現的錯誤、範圍偏離、相容性與安全風險；未執行的測試標為未驗證。

每項發現寫出檔案位置、觸發條件、影響與建議，區分必須修正與可選改善。若使用另一個模型／子 Agent，記錄實際使用情況；同一模型重看不稱為獨立審查。只回報結論，不編輯程式或合併 PR。最後由使用者本人決定。
