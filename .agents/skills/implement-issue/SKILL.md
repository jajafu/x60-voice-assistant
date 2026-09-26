---
name: implement-issue
description: 按個人開發者已明確同意的 Issue 範圍實作、驗證並準備 PR。
---

# 實作 Issue

先讀 `AGENTS.md`、Issue、Plan 與相關程式碼。確認使用者已在 Issue 或當次對話明確要求按該範圍實作，並記錄核准來源；若只要求分析或範圍重大改變，先釐清。

檢查目前工作樹／分支，沿用已由 OpenChamber 或 Codex 建立的正確分支；否則按 `AGENTS.md` 建立。只改核准範圍，依 `docs/DEVELOPMENT.md` 判斷及處理產品版本，執行相關測試與必要驗證，記錄實際結果。Android 行為變更且有對應 APK、相容專用 MuMu 執行個體時依 `docs/ANDROID_MUMU_SMOKE.md` 檢查安裝和啟動；無法執行時記錄原因，並維持 X60 真機驗收要求。依 `.github/PULL_REQUEST_TEMPLATE.md` 準備 PR；環境無法 Push／建 PR 時，清楚交代剩餘步驟。AI 不自行合併。
