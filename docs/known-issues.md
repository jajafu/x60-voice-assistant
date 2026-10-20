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
- 1.0.9 未驗收：模型頁移除雲端 ASR 後的版面與既有雲端 TTS 設定保存、X-ASR 改由官方 release 下載→大小／SHA256 驗證→解壓安裝、由舊版升級後引擎選擇（系統→X-ASR）皆待真機確認。
- 1.0.10 未驗收：設定頁 App 更新入口、GitHub release 啟動檢查、APK 斷點續傳、未知來源授權與系統安裝流程仍待真機確認；單元測試只覆蓋 release 解析與模擬 HTTP 續傳。

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
- rc27 新增 TTS 音量設定與引擎／音調選擇器。`test`、`lint`、`assembleDebug` 與 MuMu vmindex 0（Android 15）安裝開啟 smoke 已通過，並以截圖確認新 UI 渲染與空狀態提示。**核心假設尚未在 vivo X60 驗證：`TextToSpeech.Engine.KEY_PARAM_VOLUME` 在該機的 `com.vivo.aiservice` 引擎上是否真的改變輸出音量。** Android 標示此參數範圍為 0.0–1.0，超過 1.0 由引擎自行決定，可能破音或被忽略；`TextToSpeech.setVolume()` 不是公開 API 因此無法採用。若調整音量後聲音沒有變化，必須記為未生效而非通過，並另尋管道（例如改走 `STREAM_TTS`，但那需要 `MODIFY_AUDIO_SETTINGS` 權限）。`docs/testing.md`「TTS 音量與引擎／音調選擇（rc27，待真機驗收）」7 個步驟仍待補做。
- rc30 新增語音調整手機音量（Issue #20）。`test`、`lint`、`assembleDebug` 已通過。**已執行** vivo V2046A（Android 13／OriginOS 14.0）的安裝與啟動 smoke：覆蓋安裝至 `1.0.0-rc30` / `34`、`MODIFY_AUDIO_SETTINGS` 已授予、`MainActivity` 前景啟動無崩潰、讀到 `STREAM_MUSIC` 上限 15 格且未靜音。**語音功能本身在該機仍未驗收**，`docs/testing.md`「語音調整手機音量（rc30）」的 12 個功能步驟全數待補做，包含最關鍵的喚醒率項目。
- **rc30 已知且接受的取捨：TTS 與音樂共用 `STREAM_MUSIC`。** rc27 已在該機實測確認 TTS 的 AudioTrack 是 `USAGE_MEDIA`、與音樂同一音量級別，因此使用者說「聲音小一點」時，小助手自己的回話也會一起變小；語音下限設為 2 格緩解，但**下限 2 格時回話是否仍可聽懂未經實測**。若實測發現不可用，需另開 Issue 評估改走 `STREAM_TTS`，本輪不處理。
- **X60 鎖屏語音音量仍待真機驗收。** App UID 的 AudioManager 寫入與 Accessibility 設定頁均已在 vivo V2046A 上證實無效；ADB shell helper 方案也已依使用者要求移除。目前實作回到純 `AudioManager`（相對調整用 `adjustStreamVolume`，絕對音量用 `setStreamVolume`），調整後讀回實際級別，未生效時如實回報。完成實機測試前不能宣稱修復。
- **rc30 的音量指令解析只接受要求改變的說法。** 「音量有點大」這類描述現況的句子不觸發，會照一般問答送 LLM；「音量調高一格」視為調高一格而非 1%。亮度與螢幕亮度不納入，含有這類詞的句子（例如「聲音和亮度都調大」）整句不處理，避免順手改動媒體音量。
- **rc30 調整後會讀回實際音量。** 若系統或 OEM 夾限音量，回話會報讀回的級別並視為未套用，而不是宣稱已調到目標；此行為尚未在 vivo X60 實測。
- **rc30 的喚醒率未經驗證。** `docs/testing.md` 既有的喚醒驗收（1 公尺正常音量、20 次中 18 次）本身仍是待驗收項，音量再往下壓只會更差。使用者可能體感「調小之後小助手壞掉」；此風險在設計時已知並接受，須待真機數據確認實際影響。
- **rc30 未涵蓋的範圍**：只動 `STREAM_MUSIC`。鈴聲與通知音訊流不納入本輪，以避開 vivo 勿擾模式的行為差異；也不含 `STREAM_TTS` 拆分與喚醒率最佳化。
- 依開發者在 PR #21 review 時的決定，rc30 **不提供語音靜音／解除靜音**，只提供相對增減與百分比調整；要求低於 2 格時不執行。靜音仍可由 Android 系統音量控制操作。
- **1.0.2 在 vivo V2458A（Android 16／OriginOS 16.0）的語音音量重驗（2026-10-05，真人實測，未通過）：**「聲音大一點」（UP：current=90 target=91 readBack=90 applied=false）與「音量調到50」（SET_50：current=90 target=75 readBack=90 applied=false）皆解析成功、執行成功、如實回報未套用。\dumpsys audio\ 證實：(1) 本機 \STREAM_MUSIC\ 上限為 **150 級**（X60 為 15 級），百分比映射本身正確（50%→75）；(2) AudioService log 完全沒有 \com.jajafu.x60assistant\ 的 \setStreamVolume\／\djustStreamVolume\ 紀錄（只有 TTS 的 AudioFocus），第三方 UID 的寫入在到達 AudioService 前就被 OEM 丟棄；(3) 連 shell UID 的 \cmd media_session volume --set 80\ 讀回仍是 90。結論：vivo 兩代裝置上 App 側 AudioManager 調音量此路不通，如實回報是目前唯一正確的行為。
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
