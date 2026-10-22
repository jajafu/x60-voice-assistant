# OpenChamber／OpenCode 與 Codex

`AGENTS.md`、`docs/`、`.agents/skills/` 是兩種工具共用的專案規則：OpenChamber／OpenCode 與 Codex 都會自動讀取 `AGENTS.md`，也都會原生探索 `.agents/skills/` 的 Skill。`.opencode/` 只提供 OpenChamber／OpenCode 的快捷命令與可選子 Agent。你是唯一人類開發者，模型與 AI Agent 是協作工具。

## 共用機制

| 機制 | OpenChamber／OpenCode | Codex |
|---|---|---|
| 專案規則 | 自動讀取根目錄 `AGENTS.md` | 自動讀取根目錄 `AGENTS.md` |
| Skill | 模型依 description 自動配對並用 skill 工具載入；也可在對話中用 `@plan-task` 指定 | 用 `$plan-task` 明確呼叫，或 `/skills` 瀏覽；任務符合 description 時也會自動採用 |
| 斜線命令 | `.opencode/commands/` 的 `/task-plan`、`/task-implement`、`/review-changes` | 沒有專案層級命令；直接貼任務描述，或把命令內容複製到 `~/.codex/prompts/` 作為本機全域命令 |
| 子 Agent | `.opencode/agents/` 的 `task-analyst`、`change-reviewer` | 無對應機制；需要第二意見時開新對話，貼入該 Skill 的內容 |

Skill 本體只有一份，放在 `.agents/skills/`；不要為個別工具複製第二份。命令與子 Agent 是 OpenChamber／OpenCode 的快捷方式；Codex 用同一批 Skill 與提示方式，不讀 `.opencode/`。

## 套用後先檢查

| 項目 | 應看到的結果 | 無法檢查時 |
|---|---|---|
| 共用規則 | 目標根目錄有 `AGENTS.md`，三個 `.agents/skills/*/SKILL.md` 有 `name`、`description`，專案文件連結有效 | 記錄缺少的檔案並修正 |
| OpenCode 檔案 | `.opencode/commands/` 有 `task-plan`、`task-implement`、`review-changes`；`.opencode/agents/` 有 `task-analyst`、`change-reviewer`，V2 frontmatter 可解析 | 先修正格式；不要宣稱命令已載入 |
| OpenChamber／OpenCode 執行 | 在**目標專案**的斜線選單看到三個命令，Agent 清單可選兩個角色，三個 Skill 出現在模型可載入清單；`task-analyst`、`change-reviewer` 可作為 `mode: subagent` 被呼叫。主要 Agent 切換清單可能只顯示 `build`、`plan` | 記為「執行環境未驗證」；先確認專案根目錄及 OpenCode 版本，再使用直接讀檔提示 |
| Codex 執行 | 在**目標專案**開新對話，`/skills` 或 `$` 清單能看到三個 Skill；請它摘要 `AGENTS.md`，能正確說出核准由使用者本人做 | 舊版 Codex 沒有 Skill 清單時，退為請它直接讀取 Skill 檔再摘要；記為「Skill 原生載入未驗證」 |

OpenCode 2.x 的專案命令與 Agent 採用 [Markdown 設定](https://opencode.ai/v2/docs/commands)、[Agent 設定](https://opencode.ai/v2/docs/agents)；`.agents/skills/` 同時是 OpenCode 與 Codex 的 [Skill 來源](https://opencode.ai/v2/docs/skills)。CLI 指令可先用 `opencode --version` 確認實際執行版本，Windows 再用 `Get-Command opencode -All` 找到路徑。CLI 列表 API 若連內建項目都沒有回傳，結果屬於**無法判定**，以目標專案 UI／實際會話檢查為準。

## 日常使用

第一次套用時，先看 `prompts/README.md` 的使用說明；在目標專案開啟 OpenChamber 或 Codex，將 `prompts/NEW_PROJECT_PROMPT.md` 或 `prompts/EXISTING_PROJECT_PROMPT.md` **整份貼入**對話。已套用舊版要升級時，改從新版範本來源貼入 `prompts/UPDATE_TEMPLATE_PROMPT.md`。

OpenChamber／OpenCode 可用：

```text
/task-plan <任務描述或外部 Issue 編號>
/task-implement <任務描述或計畫主題>
/review-changes <留空、commit 或外部 Issue 編號>
```

命令會選對應的 Agent 並展開 `$ARGUMENTS`。也可以不打命令，直接在對話描述任務，讓模型依 Skill description 自動載入。這些命令只會在你使用時執行，不會自動監看 GitHub、核准計畫或推送內容。若命令未載入，直接要求 Agent「載入 Skill `plan-task`，規劃這個任務」；實作與審查改用對應 Skill。

Codex 用 `$plan-task`、`$implement-task`、`$review-changes` 呼叫同一套 Skill，或直接貼入任務描述與相關檔案。跨工具交接時，把計畫、核准來源與測試結果留在計畫檔或專案文件，不依賴私有聊天記憶。

## 模型與權限

在 OpenChamber Settings → Providers 連接你的免費或自訂 Provider；自訂 OpenAI 相容端點可用 Other / Custom。Session 模型可從對話選擇，子 Agent 可在 Settings → Agents 指定模型；未設定時會繼承主要 Session 的模型。只填實際可用的 `provider/model` ID，不把密鑰或個人 Provider 設定寫進範本。模型選擇依 `docs/AGENT_ROLES.md`，先用小任務確認工具呼叫與上下文能力。[OpenChamber Provider 說明](https://docs.openchamber.dev/providers/)

兩個 `.opencode/agents/` 角色在 OpenCode V2 以 `permissions` 禁止 `edit` 與 `shell`；若你另接可寫入外部系統的 MCP／Plugin，還須檢查其權限。OpenChamber 從任務建立的工作樹可能已有分支，實作前先沿用。Multi-run 適合比較同一提示詞的模型結果；實際有沒有啟動另一個 AI，應如實記錄。[OpenChamber Multi-run 說明](https://docs.openchamber.dev/multi-run/)
