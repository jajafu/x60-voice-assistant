# AI 開發流程範本版本

- 範本 ID：`ai-development-template`
- 版本：`1.5.1`

此檔在來源目錄表示範本發行版本，在目標專案表示已完整套用的版本，**不是產品或 App 版本**。Agent 會自行讀取，使用者不需填寫；首次完整套用或升級完成並驗證後才更新目標專案的版本。每次正式修改範本後應提升版本，並保留上一版來源供差異比對。

## 1.5.1 變更

- Codex 已原生支援 `.agents/skills/`：`docs/OPENCODE_OPENCHAMBER.md` 改為兩種工具的共用機制對照與檢查方式；三個命令與提示詞改以 Skill ID 載入，直接讀檔僅作為無法載入時的備援。
- 流程、Skill 內容與檔案位置不變。已套用 1.5 的專案用 `prompts/UPDATE_TEMPLATE_PROMPT.md` 小幅同步即可，不必重新套用。

## 1.5 變更

- 日常變更不再使用 Issue → PR 流程：直接在主要分支修改、驗證、提交並推送；高風險變更才開 `task/<主題>` 短分支，完成合回。
- 移除 PR 範本；Skill 改名為 `plan-task`／`implement-task`／`review-changes`，命令改為 `task-plan`／`task-implement`／`review-changes`，子 Agent 改為 `task-analyst`／`change-reviewer`。
- `docs/ISSUE_PLAN_TEMPLATE.md` 改為 `docs/PLAN_TEMPLATE.md`；較大任務的計畫可存 `work_plans/`。
- 驗收基準從 PR HEAD 改為綁定完整 commit 雜湊；產物識別方式（雜湊、Preview URL、部署 revision）不變。
- 保留 GitHub Issue 表單供外部使用者回報；外部 Issue 是需求來源，自己開發不開 Issue。
- 不建議啟用需要 PR 的分支保護，避免擋住直接推送；修改前 fetch 與安全同步規則維持 1.4 內容。

## 1.4 變更

- 修改前檢查工作樹與 worktree、fetch 遠端；新工作從最新遠端主要分支建立，避免沿用過期的本機 main。
- 明確說明 GitHub PR 合併先更新遠端是正常流程，並加入遠端合併後安全同步本機主要分支的步驟。
- 同步遇到既有修改、未推送 commit、分歧或遠端問題時保留現況並回報，不自動 stash、rebase 或丟棄內容。

## 1.3 變更

- 日常變更在第一個可驗證 commit 後建立 Draft PR，再從目前 PR HEAD 產生人工驗收所需的安裝檔、預覽、套件或部署。
- 驗收紀錄綁定 commit 與產物雜湊、Preview URL 或部署 revision；影響受測內容的變更會使舊結果失效。
- PR HEAD 與建置輸入未變時，不因建立 PR、修改描述或改為 Ready 重複建置或部署。

## 1.2 變更

- Issue Plan、實作與 PR 增加產品版本影響判斷；發行版 App 的行為修正預設每個 Issue 更新一次產品版本，文件或純測試變更不觸發。
- 說明 Git／Issue／PR 與可選工作日誌各自保存的歷史資訊。
- Android 專案可在具備 APK 與 MuMuPlayer 的環境執行安裝及開啟 smoke test，並明確回報未執行原因。
