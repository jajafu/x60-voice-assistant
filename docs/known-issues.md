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

- 兩個中文 ASR archive 都沒有模型專屬 `LICENSE`／`NOTICE`；目前只允許本機評估，APK 不含 ASR binary。
- 目前 X60 只完成 small ASR 的單句合成短測。standard 模型、20 句真人語意、連續 30 次、P95、Peak PSS、熱降頻與 OOM/LMK 仍待正式量測。
- 沒有使用者在手機 App 內輸入私人 API Key，因此真實 Provider 的 DNS/TLS/認證/Model/Endpoint 與完整問答仍待驗收；MockWebServer 不代表外部服務通過。
- Android TTS 的 Engine/Voice 可用性取決於手機已安裝資料。沒有中文 voice 時保留畫面文字並記錄 `TTS_ENGINE_MISSING`，不自動下載第三方 voice。
- Follow Up、通知 action、Boot Reminder 與 TTS 真機結果需以最終 APK 再驗；鎖屏 action 仍受 OriginOS 鎖屏隱私與省電策略影響。
- X60 reboot 實測沒有自動啟動麥克風（正確），但 Boot Reminder 通知也未出現。Manifest receiver 與權限存在；需開啟 vivo 自啟動／背景權限後重測，現階段不得宣稱 Boot Reminder 通過。
- release variant 沒有 production keystore，不能當正式商店簽章版本。
