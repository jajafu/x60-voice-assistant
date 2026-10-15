---
name: implement-issue
description: 按個人開發者已明確同意的 Issue 範圍實作、驗證並準備 PR。
---

# 實作 Issue

先讀 `AGENTS.md`、Issue、Plan 與相關程式碼。確認使用者已在 Issue 或當次對話明確要求按該範圍實作，並記錄核准來源；若只要求分析或範圍重大改變，先釐清。

先依 `docs/DEVELOPMENT.md` 檢查目前分支、未提交內容與全部 worktree，執行 `git fetch origin`。沿用已由 OpenChamber 或 Codex 建立且對應此 Issue 的正確分支；新分支必須從最新 `origin/main` 建立。同步會碰到既有修改、未推送 commit、分歧、遠端或認證問題時保留現況並回報，不自動 commit、stash、rebase、強制移動或丟棄內容。

只改核准範圍，依 `docs/DEVELOPMENT.md` 判斷及處理產品版本，先執行快速測試與靜態檢查，再保留專案必要的 `test`、`lint`、`assembleDebug` 和模型資產準備。將可驗證版本提交、推送並依 `.github/PULL_REQUEST_TEMPLATE.md` 建立 Draft PR；人工驗收以目前 HEAD 為準，記錄 commit 與 APK SHA-256。Android 行為變更且有對應 APK、相容專用 MuMu 執行個體時依 `docs/ANDROID_MUMU_SMOKE.md` 檢查安裝和啟動；另依 `docs/testing.md` 保留測試手機真機驗收要求。HEAD 或影響行為／產物的輸入改變後，將舊驗收標為失效並重新驗證；未改變則不因 PR 狀態變更重建。驗收通過後才標為 Ready；環境無法 Push／建 PR 時，清楚交代剩餘步驟。使用者在 GitHub 合併後，依 `docs/DEVELOPMENT.md` fetch 並安全同步本機 `main`，不得重複合併或 push。AI 不自行合併。
