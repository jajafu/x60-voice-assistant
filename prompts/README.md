# 如何使用首次套用與版本更新提示詞

**本檔只是使用說明，不要貼給 AI Agent。** 下列三份提示詞從第一行到最後一行都是要貼給 Agent 的內容；不必找起點或避開說明文字。先判斷是首次套用還是舊版升級。

## 既有專案首次套用

1. 開啟 [EXISTING_PROJECT_PROMPT.md](EXISTING_PROJECT_PROMPT.md)。
2. 只把 `- 目標目錄：[[目標專案完整路徑]]` 換成既有專案的完整路徑，例如 `- 目標目錄：F:\Projects\MyApp`。其他行都不用填。
3. 在該專案工作目錄開啟 OpenChamber／OpenCode 或 Codex，複製**整份提示詞檔案**貼入對話。

## 新專案首次套用

1. 開啟 [NEW_PROJECT_PROMPT.md](NEW_PROJECT_PROMPT.md)。
2. 只把 `- 目標目錄：[[目標專案完整路徑]]` 換成新專案的完整路徑，例如 `- 目標目錄：F:\Projects\MyNewApp`。其他行都不用填；目錄尚未存在時由 Agent 建立。名稱與用途查不到就由 Agent 標「待決定」。
3. 在新專案工作目錄開啟 OpenChamber／OpenCode 或 Codex，複製**整份提示詞檔案**貼入對話。

## 已套用舊版，現在要更新

1. 從**新版範本來源**開啟 [UPDATE_TEMPLATE_PROMPT.md](UPDATE_TEMPLATE_PROMPT.md)，不要使用目標專案內舊版提示詞。
2. 只把 `- 目標專案目錄：[[目標專案完整路徑]]` 換成要升級的專案完整路徑。新版來源已寫在新版提示詞中；舊版來源若仍存在，Agent 會嘗試查找並做三方比對。
3. 在要更新的專案工作目錄開啟 OpenChamber／OpenCode 或 Codex，複製**整份更新提示詞**貼入對話。

三份提示詞都已填來源 `F:\DevelopWorkFlow\ai-development-template-v1.4`；搬動範本或正式發行新版時由範本維護者同步更新。其他資料可讓 Agent 從目標專案查證或標為待決；你不用手填 `TEMPLATE_GUIDE.md` 的 24 個專案參數。

三份提示詞各只有一個 `[[目標專案完整路徑]]`，是你貼入前唯一要替換的位置。`TEMPLATE_VERSION.md` 是 Agent 自行讀取的版本檔，不是輸入欄位。`{{大寫名稱}}` 是由 Agent 在目標專案文件中處理的專案參數。

Agent 會直接改目標專案的範本檔案，逐一替換全部參數，最後檢查 24 個專案參數沒有殘留。查不到的事實會寫「待決定／規劃中」並回報給你；你只需處理真正需要自己決策的內容。
