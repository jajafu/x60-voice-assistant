# Known Issues

## M0

- vivo／OriginOS 的自啟動、後台高耗電與最近任務加鎖沒有穩定公開 API，App 只能顯示說明並由真機人工確認。
- `PowerManager.isIgnoringBatteryOptimizations()` 只代表 Android 標準電源白名單，不保證 OEM 不會終止服務。
- TTS 引擎清單不代表中文 voice 可用；完整 locale、voice 與播放測試不屬於 M0。
- UI 內的服務狀態是同一 App process 的即時狀態；真實長時間復原與跨 process 持久狀態在後續里程碑設計。
- 尚未取得 vivo X60 真機測試結果時，不可宣稱安裝、通知、OriginOS 保活或 USB 驗收通過。

## M1

- `sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20` 的 ModelScope repo 已標示 Apache-2.0（2026-10-08），但 sherpa-onnx GitHub archive 沒有模型專屬 `LICENSE`／`NOTICE`；截至 2026-10-19，archive 內權重、`tokens.txt`、`en.phone`、訓練資料及公開再散布的適用範圍仍待上游明確確認（[#3802](https://github.com/k2-fsa/sherpa-onnx/issues/3802)），因此先不公開散布含此模型的 APK。
- sherpa-onnx 1.13.8 的 `KeywordSpotterResult` 不回傳 confidence。UI 的 Score 是 configured keyword boosting score，不是實際偵測信心分數；threshold 需靠真機召回率／誤喚醒資料調校。
- 最終 APK 的短時鎖屏合成測試用 5 次「小助手」刺激得到 6 次事件，顯示目前 2 秒 cooldown／0.25 threshold 仍可能對單一長語句重複觸發；不得只依合成語音調參，需用一公尺真人 20 次與安靜 8 小時資料決定。
- OriginOS 的側載安裝需要手機上人工確認風險，ADB `install -r` 會等待此 UI。
- X60 鎖定後通知內容是否可見受使用者的鎖屏隱私設定影響，不可假設鎖屏一定能直接操作通知 action。
- 接電／未接電各 8 小時、安靜環境 8 小時、三位說話者一公尺 20 次與耗電／溫度結果尚未完成前，不得宣稱 M1 通過。

## M2–M5

- 1.0.6 未驗收：X-ASR 雲端下載→解壓→安裝、App 內更新下載→系統安裝、MuMu smoke 與真機語音皆待補做，不得宣稱通過。更新檢查需倉庫公開＋有 release，目前私有零 releases，檢查一律靜默無橫幅。
- 已發佈的 v1.0.6 release APK 缺 `assets/models/vad/silero_vad.onnx`，任何手機啟動語音助理都會以 `VAD_INIT_FAILED` 立刻停止，不可再使用；該 release 已標為 pre-release。
- 1.0.7 未驗收：修正後的 release APK 真機啟動語音助理、喚醒→VAD→X-ASR→回答整條流程、模型下載與 App 內更新安裝、MuMu smoke 皆待補做；本機只驗證測試、lint、debug 建置與 release APK 內含 KWS／VAD 資產，不得宣稱真機通過。
- 1.0.8 部分驗收：探測與引擎選擇已在 vivo X200s／Android 16 真機確認（未下載 X-ASR 也能啟動，系統服務持續執行、`asr_model_id=system/android-zh`、`last_error=-`，證據見 testing.md）；仍未驗收：系統辨識實聽逐字稿與回答、系統逾時、聆聽期間麥克風交棒、X-ASR 後備順序，不得宣稱通過。此路徑會把語音交給系統服務，語音去向依廠商服務而定（見 privacy.md）。兩個探測來源都問不到語言資訊時程式會先信任系統辨識（`SYSTEM_ASR_UNVERIFIED`），服務其實不支援中文時的使用者體驗（執行中錯誤訊息、是否要有更明確的提示或改用 X-ASR）仍待真機實聽後再決定。
- 1.0.9 未驗收：模型頁移除雲端 ASR 後的版面、X-ASR 改由官方 release 下載→大小／SHA256 驗證→解壓安裝、由舊版升級後引擎選擇（系統→X-ASR）皆待真機確認。原列的「既有雲端 TTS 設定保存」已由 1.0.11 整條移除雲端 TTS 取代（見 decision-log），不再適用。
- 1.0.10 未驗收：設定頁 App 更新入口、GitHub release 啟動檢查、APK 斷點續傳、未知來源授權與系統安裝流程仍待真機確認；單元測試只覆蓋 release 解析與模擬 HTTP 續傳。
- 1.0.11 未驗收：模型頁移除雲端語音（spike）／雲端 TTS 後的版面、由舊版升級（含雲端 TTS 設定與 Audio Key）不崩潰且雲端設定靜默失效、TTS 一律走系統引擎，皆待真機確認。
- 1.0.12 未驗收：更新檢查改指主倉庫後，真機在 1.0.10／1.0.11 上按「檢查更新」能取得 1.0.12、下載與安裝流程完整，待真機確認。主倉庫未開源或無 release 時 App 依設計保持安靜。

- Small／Standard 中文 ASR archive 沒有模型專屬 `LICENSE`／`NOTICE`；Moonshine 中文權重附 Moonshine AI Community License。APK 不含 ASR binary，授權條款見 `docs/model-packs.md`。
- 目前 X60 只完成 Small ASR 的單句合成短測和 Standard 的載入短測。Moonshine 尚未在真機下載、載入或辨識；20 句真人語意、連續 30 次、P95、Peak PSS、熱降頻與 OOM/LMK 仍待正式量測。
- 沒有使用者在手機 App 內輸入私人 API Key，因此真實 Provider 的 DNS/TLS/認證/Model/Endpoint 與完整問答仍待驗收；MockWebServer 不代表外部服務通過。
- Android TTS 的 Engine/Voice 可用性取決於手機已安裝資料。沒有中文 voice 時保留畫面文字並記錄 `TTS_ENGINE_MISSING`，不自動下載第三方 voice。
- Follow Up、通知 action、Boot Reminder 與 TTS 真機結果需以最終 APK 再驗；鎖屏 action 仍受 OriginOS 鎖屏隱私與省電策略影響。
- X60 reboot 實測沒有自動啟動麥克風（正確），但 Boot Reminder 通知也未出現。Manifest receiver 與權限存在；需開啟 vivo 自啟動／背景權限後重測，現階段不得宣稱 Boot Reminder 通過。
- release variant 沒有 production keystore，不能當正式商店簽章版本。

## 設定與版面

- rc24 修正設定頁 10 個數字欄位無法輸入上下限內任意值的問題（欄位無法清空、前導數字被吃掉、超界值靜默夾限），並修正設定頁與模型頁草稿被設定流清空、App 固定直式顯示。`test`、`lint`、`assembleDebug` 與 MuMu vmindex 0 安裝開啟 smoke 已通過；此類問題依賴真機 IME，MuMu 結果不能替代，開發者已自行以 debug APK 在 vivo X60 手工確認可正常輸入，未另建測試紀錄。逐欄位確認清單見 `docs/testing.md`「設定頁數字欄位（rc24）」，供日後回歸時重複使用。
- App 自 rc24 起固定直式；`docs/test-reports/m0-2026-09-22-vivo-x60.md` 的橫向測試屬變更前紀錄，不重測、不改寫。
- rc25 修正「問完一個話題後後續提問仍持續回答舊話題」：喚醒詞開啟新一輪時清空 `ConversationHistory`，只有同一輪 Follow Up 視窗（預設由 10 秒改為 3 秒）內的追問才延續上下文。`test`、`lint`、`assembleDebug` 與 MuMu vmindex 0（Android 15）安裝開啟 smoke 已通過。**vivo X60 真機驗收尚未執行**：合併時開發者無法讓手機連上 USB，選擇先合併。此症狀是真人語音下的對話行為，JVM 測試與 MuMu 都不能替代；`docs/testing.md`「話題邊界與 Follow Up 視窗（rc25，待真機驗收）」7 個步驟仍待補做，包含搭配真實 Provider 的完整問答。
- rc26 修正音訊失敗時的結束順序：`beginAudioRecovery` 先恢復暫停的音樂再停止 TTS，會讓音樂先恢復、正在朗讀的舊回答才補完。已改為先取消串流與 TTS 再恢復音樂，並加回歸測試（修正前該測試失敗）。**vivo X60 真機驗收尚未執行**，`docs/testing.md`「音訊失敗時的結束順序（rc26，待真機驗收）」5 個步驟仍待補做。
- 使用者在 rc26 回報的「播放音樂中喚醒小助手，等候一段時間沒回應」目前只有靜態分析、尚無實測證據：喚醒回應「我在」排在暫停鬧鐘與暫停音樂之後，而兩者都是跨行程媒體往返，最壞約 4–5 秒靜默，且該期間說的話會被 `frameQueue.clear()` 丟棄。需先取得 vivo X60 診斷頁的 Ring Log 與 `Last Error` 才能判斷是否為同一個主因，再決定是否另開 Issue。
- rc27 新增 TTS 音量設定與引擎／音調選擇器。`test`、`lint`、`assembleDebug` 與 MuMu vmindex 0（Android 15）安裝開啟 smoke 已通過，並以截圖確認新 UI 渲染與空狀態提示。**核心假設尚未在 vivo X60 驗證：`TextToSpeech.Engine.KEY_PARAM_VOLUME` 在該機的 `com.vivo.aiservice` 引擎上是否真的改變輸出音量。** Android 標示此參數範圍為 0.0–1.0，超過 1.0 由引擎自行決定，可能破音或被忽略；`TextToSpeech.setVolume()` 不是公開 API 因此無法採用。若調整音量後聲音沒有變化，必須記為未生效而非通過，並另尋管道（例如改走 `STREAM_TTS`，但那需要 `MODIFY_AUDIO_SETTINGS` 權限；1.0.13 起本 App 已無此權限）。`docs/testing.md`「TTS 音量與引擎／音調選擇（rc27，待真機驗收）」7 個步驟仍待補做。
- **語音調整手機音量（rc30，Issue #20）已於 1.0.13 移除。** 原本以 `AudioManager` 調整 `STREAM_MUSIC`：vivo X60／V2046A 實測 `applied=false`；V2458A 的 AudioService log 完全沒有 App UID 的 `setStreamVolume`／`adjustStreamVolume` 紀錄（`dumpsys` 證實寫入在到達 AudioService 前就被 OEM 丟棄）。無障礙 fallback 與 ADB shell helper 也已在當時依使用者要求移除。1.0.13 起改為調整音樂 App 自己的音量（見下），設定開關、診斷欄位與 `MODIFY_AUDIO_SETTINGS` 權限一併移除；完整過程見 `docs/decision-log.md`。
- **1.0.13 新增語音調整音樂 App 音量（走 MusicCabin 公開語音通道的 `volume_up`／`volume_down`／`volume_set`／`volume_get`）。** `test`、`lint`、`assembleDebug` 已通過。**真機功能與真人語音尚未驗收**，`docs/testing.md`「語音調整音樂 App 音量（1.0.13，待真機驗收）」8 個步驟待補做。此路徑只調整音樂 App 自己的音量，不動手機系統媒體音量，因此不受 vivo 攔截 App UID 寫入的影響；但仍須實測確認 MusicCabin 回報值與實際滑桿一致，對方未回報時只說已送出、不假稱已調到。**已知限制**：舊版 MusicCabin 完全沒有語音通道時會回「太舊請更新」；若它有語音通道但還沒有 `volume_*` 指令（介於兩者之間的版本），對方會回一般錯誤訊息，小助手就照念，不會另外提示更新。
- **1.0.13 的音樂 App 音量解析只接受要求改變的說法。** 指定音量要求明確設定動詞（調到／設成／改成／調整為…），「目前音量是 80」「音量 80 分貝」這類描述現況的句子不觸發，會照一般問答送 LLM；「音量調高一格」視為調高一格而非 1%；「現在音量多少」只查詢不改變；「靜音」「取消靜音」不支援。**未支援的說法**：「音量調到一半」「調到五成」「調到 0.5」會被視為格數／比例或小數而不動作（回一般問答），要設 50% 請說「音量調到 50」。亮度與螢幕亮度不納入，含這類詞的句子（例如「聲音和亮度都調大」）整句不處理。其他不支援此通道的播放器會回「不支援用語音調整 App 內音量」，不會改用手機系統音量。
- **1.0.2 在 V2458A 確認 TTS 音量參數無效（第二台裝置證實 rc27 假設）：** 設定頁 TTS 音量從 1.00 改到任何值，朗讀大小聲無變化。vivo 引擎忽略 \KEY_PARAM_VOLUME\，記為未生效而非通過。附帶發現：\dumpsys\ 顯示 \STREAM_TTS（aliased to STREAM_MUSIC）\ 確實存在（Max 15），但本 App TTS 輸出為 \USAGE_MEDIA\，走的仍是音樂音量；改走 \STREAM_TTS\ 需另開 Issue 評估。

## 排程提醒

- Android 12 以上未授予「鬧鐘與提醒」特殊權限時，只能使用近似時間排程，Doze 或 OEM 省電可能造成延遲。
- 強制停止 App 會由 Android 取消鬧鐘且禁止接收廣播；重新開啟 App 後才會從私有儲存補排程。
- 目前支援一次性／每日／每週提醒、提醒清單與逐筆編輯／刪除；尚未提供貪睡。rc22 保留未送達的逾期提醒並補響一次，重複提醒送達後推進下一次；相關裝置行為仍待真機驗收。
- 鈴聲、鎖屏通知、重新開機補排程與 TTS 仍需在 vivo X60／OriginOS 真機驗收；裝置沒有可用中文 TTS 時仍會播放鬧鐘鈴聲，但無法朗讀內容。

## 音樂 App 相容性

- 2026-09-25 使用者回報：舊版「收藏」回覆已送出，但 MusicCabin 喜愛清單未更新。原實作透過平台 `MediaController.sendCustomAction`，無法取得 MusicCabin 的接收結果。已改為 Media3 `sendCustomCommand` 並檢查指令可用性、回傳碼及清單變更；新版仍需 X60 真機重測，未驗證前不宣稱收藏成功。
- 2026-09-25 使用者實測 rc18：說「播放音樂收藏」後 MusicCabin 已開始播放，但小助手仍回覆「尚未確認喜愛清單開始播放」。**開發決定（rc19）**：此操作在確認 MusicCabin 喜愛清單非空、播放器宣告支援 `PLAY_FROM_MEDIA_ID` 後，送出 `playFromMediaId` 就回覆「好的，正在播放喜愛歌曲」，不再等待播放狀態，也不比對歌曲 ID、佇列或曲目資料。MusicCabin 的媒體狀態在這條路徑無法作為可靠的成功判斷；日後 code review 不應恢復此項事後確認。若送出指令後播放器實際未播放，此回覆可能過於樂觀；rc19 仍需 X60 真機重測。收藏與取消收藏的結果驗證維持不變。
- **1.0.5（Issue #29）起 rc19 例外退役**：新版 MusicCabin 的語音通道對播放、喜愛、收藏、循環都有明確回傳，小助手改為按回傳說話，不再用送出即回覆。rc19 例外只留作舊版紀錄；舊版因無通道會提示更新，不走舊連線。
- 選擇器只列出公開 `MediaBrowserService` 的播放器；只有啟動圖示、只提供私有服務或只建立不可瀏覽 MediaSession 的 App 不會列出。
- 各播放器支援的控制動作不同；播放、暫停與切歌通常可用，但語音點歌需要播放器提供 `PLAY_FROM_SEARCH`，否則會明確回覆不支援。
- 部分播放器雖宣告媒體服務，仍可能拒絕第三方連線或要求先開啟 App／登入；需逐一真機驗收，選擇器不代表所有功能保證相容。
