---
name: plan-task
description: 釐清任務範圍，寫出供個人開發者確認的簡短計畫；不實作程式。
---

# 規劃任務

先讀 `AGENTS.md`、任務描述（對話、外部 Issue 或計畫檔）與相關程式碼。區分已確認事實、推論和待決問題；Bug 類任務記錄實際與預期行為，新功能記錄使用情境。

變更較大或有風險時，依 `docs/PLAN_TEMPLATE.md` 寫出與風險相稱的計畫，並按 `docs/DEVELOPMENT.md` 判斷產品版本影響。小修正保持一兩句；高風險變更補上資料、相容性與回退。計畫同時寫明 Android 驗收交付物的識別方式：以完整 commit 與 APK SHA-256 對應，適用時排 MuMu smoke 與 `docs/testing.md` 的真機項目；Preview／部署不適用。若缺少關鍵資訊，提出具體問題。使用者只有一位，由他本人決定是否按計畫實作；AI 不代寫核准，也不在規劃階段修改產品程式碼。
