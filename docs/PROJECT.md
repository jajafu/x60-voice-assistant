# 專案需求：小助手 Voice Assistant

> 本檔只記錄現有程式與文件可核對的範圍；新增需求仍由使用者決定。

- 摘要：供 Android 13 以上手機個人使用的語音助理 release candidate；以本機喚醒、VAD、ASR 與 TTS 串接使用者設定的 OpenAI 相容問答服務，也提供本機音樂控制與提醒。
- 階段：開發中（`app/build.gradle.kts` 為 `1.0.12`／`versionCode 46`；部分真機驗收未完成）。
- 目標使用者：在自己手機上自行安裝與設定的個人使用者；更廣泛裝置支援待決定：尚無跨裝置驗收證據。
- 目前問題：在 Android 背景與鎖屏限制下，提供可由使用者控制、兼顧本機語音處理與隱私的助理，並完成仍待驗收的可靠性與 MusicCabin 情境。

## 成功條件

- 單元測試、Lint、Debug APK 建置通過；每次執行結果須另記錄。
- 適用的測試手機真人語音、鎖屏、連續操作及長時間測試按 `docs/testing.md` 的門檻驗收；未跑完維持待驗收。
- 實際 Provider 連線、音樂操作與提醒僅依使用者設定及真機證據宣稱成功，不以 MockWebServer 或合成語音替代。

## 本階段範圍

- 使用者啟動的前景語音服務、本機 KWS／VAD、系統語音辨識優先與本機 X-ASR 後備、可取消的問答與本機 TTS／Follow Up。
- 使用者設定的 OpenAI 相容 Provider、裝置內設定與密鑰保護、診斷、下載式 X-ASR 模型安裝／校驗，以及 GitHub release 的 App 內更新檢查與安裝。
- 本機提醒、可選媒體播放器控制，以及 MusicCabin 已實作的收藏／喜愛清單操作；未完成情境仍須真機驗收。

## 本階段不做

- 不承諾任意 App 的 UI 自動操作，也不以無障礙權限繞過鎖屏或 Android 背景限制。
- 不自動付款、傳訊、撥號、解鎖或修改系統安全設定。
- 不把未簽章 Release APK 或尚未完成的真實 Provider／長時間測試稱為正式發行驗收通過。

## 限制與重要決定

- Android compile SDK 36、最低 SDK 26、target SDK 35，APK 限 `arm64-v8a`；主要實測裝置見 `docs/testing.md` 與 `docs/test-reports/`（現為 Android 13 以上手機）。
- 麥克風前景服務只能由可見 Activity 的使用者操作啟動；開機廣播不直接啟動錄音。
- PCM 不落盤或上傳，API Key 透過 Android Keystore 保護，Release 網路只允許 HTTPS；詳見 `docs/privacy.md`。
- 模型 binary 不進 Git；授權及來源依 `docs/model-packs.md`、`THIRD_PARTY_NOTICES.md`，未確認授權不得推定可散布。
- MusicCabin 播放、收藏與循環按語音通道回傳說話（rc19 例外自 1.0.5 退役，見 `docs/known-issues.md`）；未完成情境仍須真機驗收。

現有測試與里程碑證據見 `docs/testing.md`、`docs/test-reports/`、`docs/decision-log.md`；初版完成後，日常變更直接在主要分支進行，較大任務的計畫放 `work_plans/`，重要決定寫回本檔。架構現況見 `docs/ARCHITECTURE.md`。
