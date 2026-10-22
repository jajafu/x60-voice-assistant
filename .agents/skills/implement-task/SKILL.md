---
name: implement-task
description: 按個人開發者已明確同意的任務範圍實作、驗證並提交。
---

# 實作任務

先讀 `AGENTS.md`、任務計畫與相關程式碼。確認使用者已在對話或計畫檔明確同意範圍，並記錄核准來源；若只要求分析或範圍重大改變，先釐清。

先依 `docs/DEVELOPMENT.md` 檢查目前分支、未提交內容與全部 worktree，執行 `git fetch origin`。日常直接在最新 `origin/main` 工作；資料格式、權限、公開 API、部署等高風險變更才從最新 `origin/main` 建立 `task/<主題>` 短分支，完成並驗證後合回。同步會碰到既有修改、未推送 commit、分歧、遠端或認證問題時保留現況並回報，不自動 commit、stash、rebase、強制移動或丟棄內容。

只改核准範圍，依 `docs/DEVELOPMENT.md` 判斷及處理產品版本，先執行能及早發現問題的測試與靜態檢查，包含專案必要的 `test`、`lint`、`assembleDebug` 與模型資產準備。提交訊息寫清楚做了什麼與為什麼；範圍較大或高風險的變更，推送前回報改動、驗證與風險，經使用者同意再推送。人工驗收以完整 commit 雜湊為基準，記錄 APK SHA-256；Android 行為變更且有對應 APK 時，若有相容的專用 MuMu 執行個體，依 `docs/ANDROID_MUMU_SMOKE.md` 驗證 APK 安裝和啟動，未執行就記錄原因，並核對 `docs/testing.md` 的測試手機驗收要求。受測內容沒有改變時不得重複建置；會影響行為或產物的內容改變後，將舊驗收標為失效並重新驗證。
