---
name: discuss-issue
description: 釐清 Feature 或 Bug Issue，寫出供個人開發者確認的簡短 Plan；不實作程式。
---

# 討論 Issue

先讀 `AGENTS.md`、Issue、相關程式碼與必要專案文件。區分已確認事實、推論和待決問題；Bug 記錄實際與預期行為，Feature 記錄使用情境。

依 `docs/ISSUE_PLAN_TEMPLATE.md` 寫出與風險相稱的 Plan，並按 `docs/DEVELOPMENT.md` 判斷產品版本、Draft PR HEAD 驗收交付及 Android APK／MuMu smoke 是否適用。此專案以 APK SHA-256 和來源 commit 識別 Android 驗收內容；Preview／部署不適用。小修正保持簡短；高風險變更補上資料、相容性與回退。若缺少關鍵資訊，提出具體問題。使用者只有一位，由他本人決定是否按 Plan 實作；AI 不代寫核准，也不在討論階段修改產品程式碼。
