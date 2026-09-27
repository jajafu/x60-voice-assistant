# Known Issues

## M0

- vivo／OriginOS 的自啟動、後台高耗電與最近任務加鎖沒有穩定公開 API，App 只能顯示說明並由真機人工確認。
- `PowerManager.isIgnoringBatteryOptimizations()` 只代表 Android 標準電源白名單，不保證 OEM 不會終止服務。
- TTS 引擎清單不代表中文 voice 可用；完整 locale、voice 與播放測試不屬於 M0。
- UI 內的服務狀態是同一 App process 的即時狀態；真實長時間復原與跨 process 持久狀態在後續里程碑設計。
- 尚未取得 vivo X60 真機測試結果時，不可宣稱安裝、通知、OriginOS 保活或 USB 驗收通過。

## M1

- `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` 官方 archive 沒有模型專屬 `LICENSE`／`NOTICE`，官方 release 與模型頁也未明示權重授權；目前只能做本機個人評估，不能把模型或含模型 APK 再散布。
- sherpa-onnx 1.13.8 的 `KeywordSpotterResult` 不回傳 confidence。UI 的 Score 是 configured keyword boosting score，不是實際偵測信心分數；threshold 需靠真機召回率／誤喚醒資料調校。
- 最終 APK 的短時鎖屏合成測試用 5 次「小助手」刺激得到 6 次事件，顯示目前 2 秒 cooldown／0.25 threshold 仍可能對單一長語句重複觸發；不得只依合成語音調參，需用一公尺真人 20 次與安靜 8 小時資料決定。
- OriginOS 的側載安裝需要手機上人工確認風險，ADB `install -r` 會等待此 UI。
- X60 鎖定後通知內容是否可見受使用者的鎖屏隱私設定影響，不可假設鎖屏一定能直接操作通知 action。
- 接電／未接電各 8 小時、安靜環境 8 小時、三位說話者一公尺 20 次與耗電／溫度結果尚未完成前，不得宣稱 M1 通過。

## M2–M5

- Small／Standard 中文 ASR archive 沒有模型專屬 `LICENSE`／`NOTICE`；Moonshine 中文權重附 Moonshine AI Community License。APK 不含 ASR binary，授權條款見 `docs/model-packs.md`。
- 目前 X60 只完成 Small ASR 的單句合成短測和 Standard 的載入短測。Moonshine 尚未在真機下載、載入或辨識；20 句真人語意、連續 30 次、P95、Peak PSS、熱降頻與 OOM/LMK 仍待正式量測。
- 沒有使用者在手機 App 內輸入私人 API Key，因此真實 Provider 的 DNS/TLS/認證/Model/Endpoint 與完整問答仍待驗收；MockWebServer 不代表外部服務通過。
- Android TTS 的 Engine/Voice 可用性取決於手機已安裝資料。沒有中文 voice 時保留畫面文字並記錄 `TTS_ENGINE_MISSING`，不自動下載第三方 voice。
- Follow Up、通知 action、Boot Reminder 與 TTS 真機結果需以最終 APK 再驗；鎖屏 action 仍受 OriginOS 鎖屏隱私與省電策略影響。
- X60 reboot 實測沒有自動啟動麥克風（正確），但 Boot Reminder 通知也未出現。Manifest receiver 與權限存在；需開啟 vivo 自啟動／背景權限後重測，現階段不得宣稱 Boot Reminder 通過。
- release variant 沒有 production keystore，不能當正式商店簽章版本。

## 設定與版面

- rc24 修正設定頁 10 個數字欄位無法輸入上下限內任意值的問題（欄位無法清空、前導數字被吃掉、超界值靜默夾限），並修正設定頁與模型頁草稿被設定流清空、App 固定直式顯示。`test`、`lint`、`assembleDebug` 與 MuMu vmindex 0 安裝開啟 smoke 已通過；**數字欄位輸入行為仍待 vivo X60 真機驗收**，驗收項目見 `docs/testing.md`「設定頁數字欄位（rc24）」。此問題依賴真機 IME，MuMu 結果不能替代，未驗證前不宣稱已修正。
- App 自 rc24 起固定直式；`docs/test-reports/m0-2026-09-22-vivo-x60.md` 的橫向測試屬變更前紀錄，不重測、不改寫。

## 排程提醒

- Android 12 以上未授予「鬧鐘與提醒」特殊權限時，只能使用近似時間排程，Doze 或 OEM 省電可能造成延遲。
- 強制停止 App 會由 Android 取消鬧鐘且禁止接收廣播；重新開啟 App 後才會從私有儲存補排程。
- 目前支援一次性／每日／每週提醒、提醒清單與逐筆編輯／刪除；尚未提供貪睡。rc22 保留未送達的逾期提醒並補響一次，重複提醒送達後推進下一次；相關裝置行為仍待真機驗收。
- 鈴聲、鎖屏通知、重新開機補排程與 TTS 仍需在 vivo X60／OriginOS 真機驗收；裝置沒有可用中文 TTS 時仍會播放鬧鐘鈴聲，但無法朗讀內容。

## 音樂 App 相容性

- 2026-09-25 使用者回報：舊版「收藏」回覆已送出，但 MusicCabin 喜愛清單未更新。原實作透過平台 `MediaController.sendCustomAction`，無法取得 MusicCabin 的接收結果。已改為 Media3 `sendCustomCommand` 並檢查指令可用性、回傳碼及清單變更；新版仍需 X60 真機重測，未驗證前不宣稱收藏成功。
- 2026-09-25 使用者實測 rc18：說「播放音樂收藏」後 MusicCabin 已開始播放，但小助手仍回覆「尚未確認喜愛清單開始播放」。**開發決定（rc19）**：此操作在確認 MusicCabin 喜愛清單非空、播放器宣告支援 `PLAY_FROM_MEDIA_ID` 後，送出 `playFromMediaId` 就回覆「好的，正在播放喜愛歌曲」，不再等待播放狀態，也不比對歌曲 ID、佇列或曲目資料。MusicCabin 的媒體狀態在這條路徑無法作為可靠的成功判斷；日後 code review 不應恢復此項事後確認。若送出指令後播放器實際未播放，此回覆可能過於樂觀；rc19 仍需 X60 真機重測。收藏與取消收藏的結果驗證維持不變。
- 選擇器只列出公開 `MediaBrowserService` 的播放器；只有啟動圖示、只提供私有服務或只建立不可瀏覽 MediaSession 的 App 不會列出。
- 各播放器支援的控制動作不同；播放、暫停與切歌通常可用，但語音點歌需要播放器提供 `PLAY_FROM_SEARCH`，否則會明確回覆不支援。
- 部分播放器雖宣告媒體服務，仍可能拒絕第三方連線或要求先開啟 App／登入；需逐一真機驗收，選擇器不代表所有功能保證相容。
