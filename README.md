# 小助手 X60 Voice Assistant

Android 13／vivo X60 的本機喚醒語音助理 release candidate。使用者必須在可見 Activity 明確啟動 microphone foreground service；之後流程為：

`小助手 → 喚醒回覆 → VAD → 本機 ASR → OpenAI-compatible SSE → 完整句 TTS → Follow Up`

## 範圍與安全

- 單一 `AudioCaptureEngine` 擁有唯一 `AudioRecord`：16000 Hz、mono、PCM16、20 ms。
- KWS、VAD、ASR 使用 sherpa-onnx `1.13.8`；PCM 只存在 50-frame 有界記憶體 queue，永不落盤或上傳。
- API Key 由 Android Keystore 的不可匯出 AES-256-GCM key 加密；DataStore 只放非敏感設定。
- Release 僅允許 HTTPS；Debug HTTP 只白名單 `localhost`、`127.0.0.1`、`10.0.2.2`。
- Transcript、回答與最近 6 組對話預設只在 process 記憶體。
- `BOOT_COMPLETED` 只顯示提醒，不會啟動 microphone FGS；強制停止後不復活。

## 準備模型

模型 binary 不進 Git。KWS 模型仍需先於建置時準備；ASR 模型可直接在 App「設定 → 本機語音辨識」按各模型的下載按鈕，下載並校驗後保存在 App 私有儲存空間。下載會在鎖屏後繼續；允許通知時可查看下載進度。網路中斷時會自動重試並從已下載的位元組續傳。若重試次數用盡，回到 App 按「繼續下載」。Standard、Moonshine 與 X-ASR 可同時保留，切換不會刪除其他模型。也可透過腳本手動安裝：

```powershell
.\scripts\prepare-m1-kws-assets.ps1
.\scripts\prepare-m2-model-packs.ps1 -Pack standard -InstallToDevice -Serial 9654983303001DD
.\scripts\prepare-m2-model-packs.ps1 -Pack moonshine -InstallToDevice -Serial 9654983303001DD
.\scripts\prepare-m2-model-packs.ps1 -Pack xasr -InstallToDevice -Serial 9654983303001DD
```

到 App「設定 → 本機語音辨識」選擇 Standard、Moonshine v2 中文或 X-ASR 中英混合，儲存後重新啟動語音服務。舊版若選用 Small，升級後會改選 Standard。Moonshine 與 X-ASR 在一句話結束後辨識並交付結果。診斷頁會分別顯示設定值與實際載入的模型。完整來源、大小、SHA256 與授權見 [docs/model-packs.md](docs/model-packs.md)，候選比較見 [docs/asr-benchmark.md](docs/asr-benchmark.md)。

明確的音樂播放指令優先交給選用的音樂 App。若語句進入 LLM，而回答表示無法播放音樂，App 會嘗試從回答及原語句還原歌手或歌曲，再交給音樂 App 搜尋播放；不朗讀或保留該段 LLM 拒絕回答。找不到目標時會請使用者重說，音樂 App 未確認播放時會回報實際狀態。

## 建置

需求：JDK 17、Android SDK 35、arm64-v8a。

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。Release variant 未配置 production signing，因此只能視為未正式簽章的候選產物。

## 手機設定

1. App 分成「對話／設定／診斷」三個分頁；日常使用只需停留在「對話」。
2. 在「設定」輸入 2–8 個中文字作為喚醒詞；自訂詞會在 Android 10 以上裝置本機轉為 KWS 發音。
3. 在 LLM Provider 區輸入 Base URL、API Key、Model 與 Endpoint（預設 `/chat/completions`）。
4. API Key 只能直接輸入手機 App；不要貼到聊天、終端或 source。
5. 按「測試 LLM 連線」。成功後設定才標示可用。
6. 視需要調整 VAD、Follow Up、TTS 語速與音高，按「測試語音」。
7. 在「對話」按「啟動語音助理」。啟動後會顯示即時逐字稿與回答；設定變更需先停止服務。
8. 回答不合用時按畫面的「停止回答」或通知的「閉嘴」；回到等待後再說喚醒詞。回答結束後的 Follow Up 期間可直接追問。
9. 依畫面指引設定 vivo 自啟動、背景高耗電、最近任務加鎖；Android 電池白名單不等同 OEM 保活保證。

## 音樂 App 語音控制（X60 試用）

預設沿用 MusicCabin；也可在「設定 → 音樂 App」從手機已安裝且公開媒體瀏覽服務的播放器中選擇，或關閉音樂控制。畫面顯示 App 名稱，內部保存套件名稱與媒體服務 ID；因此不依賴可能重複或變更的顯示名稱。選好並儲存後重新啟動語音服務生效。

可說「播放音樂」、「暫停音樂」、「下一首」、「上一首」，或用「幫我放晴天」、「我想聽晴天」、「來首晴天」、「播放周杰倫的晴天」等方式點歌。這些指令由本機規則判斷，不需設定 LLM；未匹配的自由說法仍不會自動操作手機。說「打開音樂 App」或所選 App 名稱時，小助手會顯示通知，點擊並解鎖後開啟播放器；鎖屏時不會強制彈出畫面。

MusicCabin 可說「收藏」或「取消收藏」，操作目前播放的歌曲；成功時分別回覆「收藏成功」與「已取消」。已收藏時再說「收藏」，小助手會回覆「已經收藏過了」，不會反向取消。說「播放音樂收藏」或「播放喜愛歌曲」會從 MusicCabin 的喜愛清單開始播放；送出播放指令後不再等待歌曲／清單狀態確認，因為實測已播放仍會誤報未確認。收藏變更需先在「設定 → 音樂 App」開啟小助手的通知存取權；一般播放控制及讀取喜愛清單不需要。若 MusicCabin 未回報明確結果，小助手不會宣稱收藏成功。

播放成功時，小助手直接用歌名回覆，例如「好的，準備播放周杰倫的《晴天》。」；切歌時會說「好的，下一首是……，稍後播放。」成功回覆不再以播放器名稱開頭。若播放器沒有回報歌名，則只說「準備播放音樂」，不猜測曲目。

喚醒小助手時，若 MusicCabin 正在播放，會先暫停，讓它收清楚指令；一般語音互動結束（含 Follow Up 等候逾時）後恢復播放。若指令是暫停或關閉音樂，就維持暫停；下一首／上一首造成播放器自行續播時，小助手會再次暫停到互動結束。這項 MediaSession 暫停、切歌、恢復與真人說「下一首」的基本流程已在 X60 真機測試；無指令逾時及喚醒後的暫停延遲仍待驗收。

小助手透過 MusicCabin 對外提供的媒體服務控制播放；收藏變更另需使用者啟用通知存取權，不需要無障礙權限。詳見 [規劃書](docs/phone-control-plan.md) 與 [真機短測紀錄](docs/test-reports/musiccabin-2026-09-23-vivo-x60.md)。

## 本機排程提醒

啟動語音服務後，可說「9:00 提醒我倒垃圾」、「明天下午三點半提醒我開會」或「50 分鐘後提醒我吃藥」。指令在本機解析與保存，不依賴 LLM；語音服務或 App 關閉後，已排程提醒仍會到點觸發。提醒會播放系統鬧鐘鈴聲，間隔朗讀提醒內容，並在通知提供「停止鬧鐘」按鈕；無人操作時最多響 10 分鐘。重新開機、系統時間或時區變更後會自動補排程。

Android 12 以上需在「診斷 → 必要權限」允許「精準鬧鐘」。尚未允許時仍會建立近似時間提醒，並顯示授權通知，但系統可能延遲觸發；授權後，尚未到期的提醒會自動改回精準排程。

首次啟動時若尚未授權，App 會先說明用途並提供前往 Android 系統授權頁的按鈕；選擇稍後處理時不會在每次啟動重複打擾，診斷頁仍保留授權入口。

## 驗證與限制

自動化、ADB 證據及正式驗收門檻見 [docs/testing.md](docs/testing.md)。M2 短測使用 Windows 合成語音，不能等同真人 20 句或 30 次壓力驗收。沒有使用者在 App 內提供的私人 API Key 時，真實 Provider 測試必須保持「待驗收」；MockWebServer 只證明協定處理。

另見：[隱私](docs/privacy.md)、[疑難排解](docs/troubleshooting.md)、[已知限制](docs/known-issues.md)、[決策紀錄](docs/decision-log.md)、[第三方聲明](THIRD_PARTY_NOTICES.md)。
