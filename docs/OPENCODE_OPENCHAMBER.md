# OpenChamber／OpenCode 與 Codex

`AGENTS.md`、`docs/`、`.agents/skills/` 是兩種工具共用的專案規則。`.opencode/` 只提供 OpenChamber／OpenCode 的快捷命令與可選子 Agent；Codex 直接讀共用檔案。你是唯一人類開發者，模型與 AI Agent 是協作工具。

## 套用後先檢查

| 項目 | 應看到的結果 | 無法檢查時 |
|---|---|---|
| 共用規則 | 目標根目錄有 `AGENTS.md`，三個 `.agents/skills/*/SKILL.md` 有 `name`、`description`，專案文件連結有效 | 記錄缺少的檔案並修正 |
| OpenCode 檔案 | `.opencode/commands/` 有 `issue-plan`、`issue-implement`、`pr-review`；`.opencode/agents/` 有 `issue-analyst`、`pr-reviewer`，V2 frontmatter 可解析 | 先修正格式；不要宣稱命令已載入 |
| OpenChamber／OpenCode 執行 | 在**目標專案**的斜線選單看到三個命令；`issue-analyst`、`pr-reviewer` 可作為 `mode: subagent` 被呼叫，三個共用 Skill 可載入。主要 Agent 切換清單可能只顯示 `build`、`plan` | 記為「執行環境未驗證」；先確認專案根目錄及 OpenCode 版本，再使用直接讀檔提示 |
| Codex 執行 | 在**目標專案**開新對話，請 Codex 讀 `AGENTS.md` 與一個 Skill，能正確摘要「你本人核准／合併」與 Skill 步驟 | 若當次不在 Codex，記為「Codex 實際載入未驗證」，保留檔案靜態檢查結果 |

OpenCode 2.x 的專案命令與 Agent 採用 [Markdown 設定](https://opencode.ai/v2/docs/commands)、[Agent 設定](https://opencode.ai/v2/docs/agents)；`.agents/skills/` 可作為 [Skill 來源](https://opencode.ai/v2/docs/skills)。CLI 指令可先用 `opencode --version` 確認實際執行版本，Windows 再用 `Get-Command opencode -All` 找到路徑。CLI 列表 API 若連內建項目都沒有回傳，結果屬於**無法判定**，以目標專案 UI／實際會話檢查為準。

## 日常使用

第一次套用時，先看 `prompts/README.md` 的使用說明；在目標專案開啟 OpenChamber 或 Codex，將 `prompts/NEW_PROJECT_PROMPT.md` 或 `prompts/EXISTING_PROJECT_PROMPT.md` **整份貼入**對話。已套用舊版要升級時，改從新版範本來源貼入 `prompts/UPDATE_TEMPLATE_PROMPT.md`。檔案整合與上表檢查完成後，OpenChamber／OpenCode 可用：

```text
/issue-plan 123
/issue-implement 123
/pr-review 45
```

也可傳完整 Issue／PR URL。這些命令只會在你使用時執行，不會自動監看 GitHub、核准 Plan 或合併 PR。若命令未載入，直接要求 Agent「讀 `AGENTS.md` 與 `.agents/skills/discuss-issue/SKILL.md`，分析 Issue #123」；實作與審查改用對應 Skill。

Codex 也直接使用同樣的檔案與提示方式，不需要 `.opencode/commands/`。跨工具交接時，把 Plan、核准來源、測試與 PR 連結留在 Issue／PR，不依賴私有聊天記憶。

## 模型與權限

在 OpenChamber Settings → Providers 連接你的免費或自訂 Provider；自訂 OpenAI 相容端點可用 Other / Custom。Session 模型可從對話選擇，子 Agent 可在 Settings → Agents 指定模型；未設定時會繼承主要 Session 的模型。只填實際可用的 `provider/model` ID，不把密鑰或個人 Provider 設定寫進範本。模型選擇依 `docs/AGENT_ROLES.md`，先用小任務確認工具呼叫與上下文能力。[OpenChamber Provider 說明](https://docs.openchamber.dev/providers/)

兩個 `.opencode/agents/` 角色在 OpenCode V2 禁止核心 `edit` 與 `shell`；若你另接可寫入外部系統的 MCP／Plugin，還須檢查其權限。OpenChamber 從 Issue 建立的工作樹可能已有分支，實作前先沿用。Multi-run 適合比較同一提示詞的模型結果；實際有沒有啟動另一個 AI，應如實記錄。[OpenChamber Multi-run 說明](https://docs.openchamber.dev/multi-run/)
