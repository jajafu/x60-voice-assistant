# MusicCabin 控制短測 — 2026-09-23 — vivo X60

## 測試環境

- 裝置：vivo X60 `V2046A`，Android 13 / API 33，ADB 序號 `9654983303001DD`。
- MusicCabin：GitHub 正式版 `v13.7.13-car`，FOSS 套件 `com.jajafu.musiccabin`，`versionCode=243`；下載 APK SHA-256 `4F40FA31B538F2A91212853ED1C6071D97EB70A28C27112843101D6F38A6CE1C`，與 GitHub release asset digest 相符；APK v2 簽章驗證通過，安裝後裝置簽章相同。
- 小助手：本機 Debug APK，套件 `com.jajafu.x60assistant`，既有版本顯示 `1.0.0-rc1` / code 5，這次功能建置 SHA-256 `CCC2E37445508716877899A5FB6CCA7224B4B3F3565530D220F636BD019DBCA1`。正式發布前需另設新版號並重跑驗收。
- 後續口語指令規則更新版 Debug APK SHA-256 `D4AB389AD25C8B3E2DF627E10950BAAF749A9F00C19DF3B784DF9334457C76F7`，已於同日覆蓋安裝到 X60（保留 App 資料）；`testDebugUnitTest`、`lintDebug`、`assembleDebug` 通過。「幫我放晴天」等說法通過本機單元測試。
- 自動暫停／恢復更新版 Debug APK SHA-256 `93A925E6F6525EB43E0C5DDBAF506411DF4501CD65A8F8031B973BC7D1A7CD21`，已覆蓋安裝；`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通過。Instrumentation APK SHA-256 `458078D46065DFFE596F65A860A87D7B8737F486008784BA1FE2208EC95D1C8C`。
- MusicCabin 首次啟動後已允許通知，未授予小助手通知監聽或無障礙權限。

## 已驗證

1. `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通過。
2. 真機 `musiccabin_connection`：小助手透過 MusicCabin 的公開 MediaBrowserService 連線，能讀取媒體狀態。
3. 真機 `musiccabin_search`：查詢「晴天」後，MusicCabin 回報正在播放《晴天》（周杰倫）；測試隨即送出暫停，系統 MediaSession 顯示 `STATE_PAUSED`。
4. 真機 `musiccabin_controls`：播放、下一首、上一首依序成功，測試最後送出暫停；MediaSession 再次顯示 `STATE_PAUSED`。
5. 真機 `musiccabin_voice_interruption`：播放中呼叫暫停並確認 MediaSession 已暫停；暫停期間執行下一首／上一首，每次切歌後再確認播放器維持暫停；會話結束呼叫恢復，成功回到播放狀態，測試最後恢復原先的暫停狀態。結果 `PASS:playing-paused-resumed-paused`。
6. 真人語音端到端：MusicCabin 正在播放時對 X60 說喚醒詞，再說「下一首」。助手畫面顯示 ASR 轉寫「下一首」，並回覆「MusicCabin 已選好：周杰倫 的 安靜；對話結束後繼續播放。」；MusicCabin 曲目由 active item `0` 切到 `1`，MediaSession 回報 `STATE_PLAYING`，助手回到「等待喚醒」。確認喚醒、收音、ASR、本機音樂指令路由、切歌及會話結束後恢復播放整段鏈路成功。測試後以 App 內「停止服務」收尾，確認助手服務已停止而 MusicCabin 持續播放。

## 待驗收

- 無指令逾時情境：真人只說喚醒詞、不再下達命令，驗證聆聽逾時後恢復音樂；目前真機確認的是完成指令後恢復。
- 音樂播放時喚醒後暫停的即時延遲尚未量測；本次已以真機 MediaSession instrumentation 驗證暫停／恢復控制，並以真人語音驗證整段操作與最終恢復狀態。
- 鎖屏和熄屏下的媒體控制與指定歌曲搜尋。
- 多首中文歌曲、同名不同歌手、網路失敗及 MusicCabin 未開啟時的結果。
- 音樂外放時的喚醒率、誤喚醒、TTS 壓低／恢復音量。
- 主規格尚待的 8/24 小時長時間測試。本短測不能代替正式驗收。
