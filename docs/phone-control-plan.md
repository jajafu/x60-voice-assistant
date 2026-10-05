# 小助手 X60：語音操作手機擴充規劃書

日期：2026-09-23　狀態：MusicCabin 基本控制已接入；真人語音／鎖屏驗收待完成
更新：2026-09-30　狀態：語音導航（Google 地圖）已實作並通過 test／lint／assembleDebug；真機語音驗收待完成

## 1. 目標與產品邊界

讓使用者說出「打開音樂 App」、「暫停音樂」、「下一首」或「播放周杰倫的晴天」後，小助手能在 vivo X60 上執行對應操作，並根據手機回報的狀態告知結果。後續可將同一套架構擴充到其他明確列出的手機操作。

第一輪交付以音樂為樣板，但架構應能容納「開啟 App」、「媒體控制」、「系統允許的操作」與「特定 App 的操作介面」。**不承諾任意 App 的任意畫面都能由語音自動點擊**；每個 App、動作及鎖屏情境都要單獨驗證。付款、傳送訊息、撥號及變更系統安全設定不在第一輪自動執行範圍。

本文件是現有 [主規格書](../../vivo-x60-voice-assistant-spec.md) 的後續擴充提案。原規格將音樂服務排除在第一版外，也禁止用 Accessibility Service 繞過 Android 限制；本案不改寫已完成的第一版需求。Issue #20 曾加入音量無障礙 fallback 與 ADB shell helper，但前者在 X60 鎖屏實測失敗，後者已依使用者要求移除；目前回到純 `AudioManager` 方案。

## 2. 現有基礎與待補缺口

目前鏈路是「喚醒 → 本機 ASR → LLM → TTS → 追問」。`VoiceOrchestrator.handleTranscriptReady()` 雖會進入 `ROUTING` 狀態，但隨即檢查 LLM 設定並把文字送往遠端；還沒有本機動作路由、手機動作執行器或成功驗證。Manifest 也未宣告通知監聽或無障礙服務。

現有 X60 測試報告確認 APK 可安裝、服務與錄音資源能啟停；真實 Provider、完整端到端問答、8/24 小時及部分鎖屏測試仍待驗收。新功能應以現有可用的語音管線為基礎，避免把「程式編譯通過」當作「長時間鎖屏可操作」。

## 3. 使用情境與預期結果

| 語音範例 | 預期操作 | 可能限制／回饋 |
| --- | --- | --- |
| 「打開音樂 App」 | 開啟使用者選定的音樂 App | 助理在背景或手機鎖定時，顯示可點擊通知，請使用者解鎖後開啟 |
| 「播放音樂」 | 對選定且可用的媒體工作階段送出播放 | 沒有可用工作階段時，嘗試該 App 已驗證的啟動路徑；否則提示先開啟播放器 |
| 「暫停」、「下一首」、「上一首」 | 對指定播放器送出媒體控制 | App 沒有提供對應動作時明確說明，不假稱完成 |
| 「播放周杰倫的晴天」 | 在指定音樂 App 搜尋並嘗試直接播放 | 僅在該 App 實測支援搜尋播放介面時宣稱「已播放」；否則開啟搜尋結果或回報不支援 |
| 「打開設定」 | 開啟標準 Android 設定入口 | 鎖屏與背景啟動限制同樣適用；不自動修改設定 |

首個目標 App 已由使用者指定為 MusicCabin。X60 上安裝的是 FOSS Release `13.7.13`，套件 `com.jajafu.musiccabin`；另有 GMS 變體 `com.jajafu.musiccabin.gms`，本輪不混用。後續若加入其他音樂 App，再增加使用者可選的預設播放器設定。

### MusicCabin 的已確認介面

2026-09-23 於 X60（Android 13）確認 APK 可安裝並啟動，啟動後系統出現 `com.jajafu.musiccabin` 的 MediaSession。對應 [v13.7.13 原始碼](https://github.com/jajafu/MusicCabin/tree/v13.7.13-car) 宣告對外可連接的 `MediaLibraryService`／`MediaBrowserService`，並在媒體回呼中處理 `searchQuery`。小助手已直接連上此媒體服務；真機短測確認「晴天」可搜尋播放，並通過播放、暫停、下一首及上一首控制。這不需要跨 App 通知監聽權限。真人語音、熄屏及多首歌曲正確率仍待驗收。手機上已允許 MusicCabin 發送通知，以便顯示播放控制。

## 4. 技術方案

### 4.1 指令判斷與執行

```text
本機 ASR 文字
   → ActionRouter（明確中文指令與參數擷取）
      ├─ 支援的手機指令 → ActionPolicy → ActionExecutor → 狀態驗證 → 語音回饋
      └─ 一般問題／無法判斷 → 現有 LLM 問答流程
```

在 `VoiceOrchestrator` 完成 ASR 後、檢查 LLM 設定前接入 `ActionRouter`，使手機操作在未設定 LLM 時仍可使用。第一輪以本機可測的指令規則處理「打開、播放、暫停、下一首、上一首」，並加入繁簡、口語同義詞及 ASR 常見誤字測試。指定歌曲需擷取歌曲名、歌手名與目標 App；資訊不足或辨識不清時先追問。

指令使用封閉的型別，例如 `OpenApp(appId)`、`MediaPlay(appId)`、`MediaPause(appId)`、`MediaNext(appId)`、`PlaySong(appId, title, artist?)`。未來若用 LLM 協助理解長句，它只能產出候選型別與欄位；`ActionPolicy` 必須再比對允許的 App／動作、參數與使用情境。不得把 LLM 文字當成 shell 指令、Intent 字串或畫面座標直接執行。一般問答內容也不得觸發手機動作。

`ActionExecutor` 在現有服務生命週期內工作，動作有逾時、取消及單次請求 ID。它只回報 `success`、`needs_user_action`、`unsupported` 或 `failed` 等可驗證結果；TTS 在結果確認後才說「已播放」。**MusicCabin 的「播放喜愛清單」是例外**：使用者於 rc18 真機實測歌曲已播放、事後媒體狀態確認卻誤報失敗，因此從 rc19 起在清單非空且播放器宣告支援播放指令後，送出 `playFromMediaId` 即回覆「正在播放喜愛歌曲」，不再檢查狀態、歌曲 ID 或佇列。這是刻意取捨，code review 不應恢復該項確認；收藏／取消收藏仍需確認喜愛清單已變更。記錄動作類型、目標 App、結果與錯誤碼，不記完整歌曲語句或螢幕文字。

### 4.2 執行能力的優先順序

1. **標準 Android Intent**：開啟已選 App 或已驗證的搜尋／播放入口。查詢特定 App 時只宣告必要的 `<queries>`；找不到目標時回報並提供設定入口。
2. **MediaSession 控制**：MusicCabin 首先透過其對外公開的 MediaBrowserService 連接到指定套件，再用 `MediaController.TransportControls` 執行播放、暫停、上一首、下一首。執行前檢查該 session 公告的 `PlaybackState` 動作，通常於執行後觀察狀態變化；「播放喜愛清單」採上段所記錄的例外。MusicCabin 的收藏切換是受信任控制端專用的自訂指令，使用者須額外啟用小助手的通知存取權；這項授權可隨時撤銷。日後若要控制其他未公開媒體服務的 App，也可評估使用 `MediaSessionManager.getActiveSessions()`。[Android MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)、[TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)
3. **搜尋並播放**：先探測選定 App 是否支援 `MediaController.TransportControls.playFromSearch()`，或 Android 的 `MEDIA_PLAY_FROM_SEARCH` Intent；兩者都要在 X60 與該 App 版本上實測。支援搜尋結果不等於已開始播放，必須確認播放器狀態與曲目。若不支援，再評估該服務公開且授權可用的官方 API／深層連結。[Android 常見 Intent](https://developer.android.com/guide/components/intents-common)
4. **App 專用 UI 自動化**：只在上述入口不可用、且使用者確實需要該 App 流程時，為單一 App 製作可關閉的適配器。使用 Android Accessibility Service 的節點文字／語義定位與 `performAction()`；不以固定座標作主要定位方式。每一步檢查目前 App、畫面與目標節點，遇到登入、驗證碼、付款、權限對話框或畫面不符立即停止。使用者需在系統設定手動啟用，且限已解鎖、螢幕可見的情境。[Android 無障礙服務](https://developer.android.com/guide/topics/ui/accessibility/views/service)

一般 App 開啟不能保證從鎖屏中的前景服務直接彈出：Android 10 起限制背景啟動 Activity，target SDK 35 對 `PendingIntent` 也有更嚴格條件。因此鎖屏時先支援已有媒體工作階段的控制；需要開新畫面時以通知請使用者點擊並解鎖，不把「點亮／解鎖後才完成」寫成全程免手動。[Android 背景 Activity 限制](https://developer.android.com/guide/components/activities/secure-bal)

### 4.3 音訊與對話狀態

執行手機動作時新增可觀察的 `EXECUTING_ACTION` 階段，並明確定義取消、逾時、失敗後回到 `IDLE_KWS` 或 `FOLLOW_UP`。喚醒確認後先記錄 MusicCabin 是否正在播放，若正在播放就暫停，並在語音互動及 Follow Up 等候期間保持暫停；回到待命時才恢復。使用者明確要求暫停／停止時取消自動恢復；下一首／上一首若使播放器自動續播，需再次暫停。語音提示與 TTS 仍使用現有 Audio Focus。X60 已驗證媒體服務可暫停、切歌與恢復，音樂外放時 KWS／ASR 喚醒率仍待真人語音驗收。取消回答不應被誤解為「暫停音樂」，兩個命令應分開。

## 5. 權限、風險與使用者控制

- 初版只要求執行某類能力所必需的權限。MusicCabin 的一般播放與喜愛清單瀏覽不需要通知監聽或無障礙服務；收藏與取消收藏才需使用者授予通知存取。音量不宣告 Accessibility Service。
- 設定頁顯示目前選定的音樂 App、通知存取狀態、可用的操作，以及可隨時關閉手機操作的總開關。
- 開啟 App、播放、暫停、切歌可直接執行；傳訊、撥號、購買、刪除資料及修改安全設定應另訂逐項確認流程，在本期一律不執行。
- 不自動解鎖、不輸入密碼、不使用 root、ADB 或 overlay 規避 Android 限制。第三方 App 的訂閱、地區、登入和內容授權可能使搜尋或播放失敗，需回報真實狀態。
- 若未來準備上架 Google Play，Accessibility Service 的用途、揭露及政策審核需另做檢查；本規劃不把側載可行等同商店可上架。[Google Play Accessibility Service 政策](https://support.google.com/googleplay/android-developer/answer/10964491)

## 6. 分階段交付與驗收門檻

| 階段 | 交付內容 | 完成門檻 |
| --- | --- | --- |
| P0：X60 能力盤點與原型 | 已確認 MusicCabin FOSS `13.7.13` 可安裝、啟動並建立 session；公開 MediaBrowserService 已可連接；搜尋「晴天」及播放／切歌短測通過 | 補齊真人語音、鎖屏及不同歌曲的「MusicCabin × 動作 × 螢幕狀態」矩陣 |
| P1：本機動作框架 | `ActionRouter`、型別化動作、政策檢查、結果回饋、取消／逾時；接入現有狀態機 | 不需 LLM 即可解析基本指令；一般問題仍正常問答；否定句與模糊句不誤觸操作；單元測試覆蓋指令與拒絕路徑 |
| P2：開啟 App 與媒體控制 | 使用者選擇預設音樂 App、Intent 啟動、通知存取引導、MediaSession 播放／暫停／切歌 | X60 亮屏 30 次受支援操作至少 27 次成功；鎖屏中的既有 session 控制另測 30 次，受支援操作至少 27 次成功；不可用或不支援時 100% 不虛報成功 |
| P3：指定歌曲 | 歌曲／歌手擷取、可用的 `playFromSearch()` 或播放 Intent、結果驗證；對一個選定 App 做完整適配 | 20 條含中文歌手／歌名的測試語句，逐條記錄「實際播對」、「只開搜尋頁」及「失敗」；只有實際播對才回報完成。是否可設定成功率門檻依 P0 能力盤點決定 |
| P4：擴大手機操作 | 依需求逐一加入其他 App 或受限 UI 適配器；每個適配器有開關、畫面驗證與回歸案例 | 每個 App／動作單獨簽收；更新 App 後重新測試；未授權時不讀取畫面也不操作 |

各階段都要執行 `test`、`lint`、`assembleDebug`，並記錄 APK 版本、裝置版本、音樂 App 版本及測試結果。P2／P3 必須在 vivo X60 真機驗收；模擬器、單元測試或合成語音不能取代真機。需同時測「亮屏 App 在前景」、「助理在背景但手機已解鎖」、「鎖屏且播放器已有 session」、「鎖屏且播放器未啟動」，並在音樂外放時重測喚醒和 ASR。原專案未完成的 8/24 小時及真實 Provider 驗收仍維持待辦，不因新增手機操作而視為通過。

## 7. 預估工作量與決策點

以一名熟悉現有 Kotlin 專案的開發者、X60 可連線測試為前提：P0 約 1–2 天，P1 約 2–4 天，P2 約 3–5 天，P3 每個音樂 App 約 3–7 天；P4 依 App 與操作流程另估。這些是規劃估算，不包含第三方服務登入／審核等待或長時間真機 soak test。

MusicCabin 已選定。需要開啟 App 畫面時採通知請使用者點擊並解鎖；既有媒體工作階段的控制及搜尋播放則以不開畫面為目標。P0 真機結果決定 P3 能否做到真正免手動指定歌曲播放；若實際播放無法完成，就將「開啟搜尋結果頁」與「自動播放」分開交付，不宣稱具備後者。

## 8. 語音導航（2026-09-30 實測後納入）

### 8.1 各 URI 策略的實測結果

2026-09-30 於 X60（Android 13）先後完成 Google 地圖 `26.39.04` 與高德地圖 `10.25.0.3203` 的首次設定，再實測各 URI 落點。結果顯示**不能用同一套 Intent 對待兩家地圖**：

| App | URI | 實測落點 | 尚需點擊 |
| --- | --- | --- | --- |
| Google 地圖 | `https://www.google.com/maps/dir/?api=1&destination=<地點>` | 直達路線預覽頁（起點、終點、時間距離、交通分頁齊全） | 1 次「開始」 |
| Google 地圖 | `geo:0,0?q=<地點>` | 停在搜尋結果頁 | 2 次 |
| Google 地圖 | `google.navigation:q=<lat>,<lon>` | 首次啟動頁印出 `Activity not started, its current task has been brought to the front`，目的地是否帶入無法確認 | — |
| 高德地圖 | `androidamap://route/plan/?dname=<地點>` | 「確定終點」頁（已排序 POI 清單） | 1 次選點 |
| 高德地圖 | `geo:0,0?q=<地點>` | 停在搜尋結果頁 | 2 次 |
| 高德地圖 | `https://uri.amap.com/navigation?...` | `Activity not started, unable to resolve` | — |

`geo:` 看似通用，實測反而是兩家中落點最差的，只能當降級備援。另一項必須注意的事實：兩家共存時未指定套件的 `geo:` 會開系統 `ResolverActivity` 選擇器，因此 `NavigationAppLauncher` 一律 `setPackage()`。

### 8.2 本期範圍

只納入 Google 地圖。指令為 `NavigationCommand.Navigate(destination)`，由 `NavigationCommandRouter` 在本機解析，必須帶目的地，純「導航」不觸發；喚醒詞與客套話一併剝除。目的地字串交給地圖自行搜尋，小助手不做地理編碼、不呼叫任何雲端地理 API。TTS 只說已開啟路線預覽並請使用者確認地點後點開始導航，**不宣稱已在導航**；Google 地圖會把「台北101」自行解析成「台北101購物中心」，回話不得宣稱目的地已正確選定。

### 8.3 不做 Android Auto 的依據

2026-09-30 查證後決定不整合 Android Auto：

1. AA 投影時手機端 UI mode 不會變成 `UI_MODE_TYPE_CAR`，投影服務獨立於 dock 模式，用 `UiModeManager` 判斷會誤判。
2. 沒有公開 API 查詢投影狀態；反射隱藏 API 會被 Google 斷除且違反 Play 政策。
3. 即便不判斷而直接啟動，背景啟動限制擋著：AA 連接時手機是暗的／鎖定的，§4.2 為音樂設計的「發通知請使用者點擊」在車上無效。
4. 結構性不對稱：地圖的本質是開畫面，Android 10+ 限制背景開畫面；MediaSession 那套在鎖屏與車機都管用的機制，地圖沒有對應物。
5. 功能重疊：AA 下「導航到台北101」本就是 Google 助理的職責。
6. 唯一合法的車機全自動路徑是成為 Google 認證的 AA 導航應用（`NavigationActivity`、`CarAppService` 與 Google 認證審核），工作量遠超本專案規模且需等待審核。

### 8.4 已知限制

- **首次設定死路**：兩家地圖首次啟動都需同意條款，語音觸發會撞到條款頁而非路線圖。沒有公開 API 可偵測首次設定狀態，緩解方式是回話措辭納入提醒，並在設定頁提供「開啟導航 App 完成首次設定」按鈕。
- **高德延後**：實測可用但需多一次選點（「台北101」會展開成 10 個以上 POI，含購物店等子項）。已與使用者確認接受，TTS 須說明請在終點清單選擇要去的地方。
- 「回家／去公司」等常用目的地簡稱、導航中的語音控制（暫停／繼續／重新規劃）未納入本輪。

## 8.5 語音音量（2026-10-02 納入，rc30）

### 8.5.1 本期範圍

只調整 `STREAM_MUSIC`。指令為 `VolumeCommand` 的 `Up`／`Down`／`SetTo(percent)`，由 `VolumeCommandRouter` 在本機解析，與音樂、提醒、導航同一條鏈路，未設定 LLM 時也能使用。**不提供靜音或解除靜音指令**。路由順序為 關閉 → 提醒 → 音樂 → 導航 → 音量 → LLM。

「音量調到 50」的 `50` 是**百分比**，目標級別 = `round(0.50 × 裝置上限)`；回話報實際級別與上限。語音可調整的**下限為 2 格**：要求更低時不執行，改為回報已到下限。此功能不改變靜音狀態或提供靜音／解除靜音指令。

相對調整每次只走一格（`Up`／`Down`），「音量大一點」「聲音大一點」「大聲一點」「音量調高一格」都視為調高一格；「一格」「兩格」是格數而不是百分比。解析只收「要求改變」的說法，「音量有點大」這種描述現況的句子不觸發。語音只處理音量與聲音，亮度與螢幕亮度不納入，含有這類詞的句子整句不處理。

### 8.5.2 技術選擇與其代價

`AudioManager.setStreamVolume()` 與 `adjustStreamVolume()` 都是公開 SDK API，可在宣告 `MODIFY_AUDIO_SETTINGS` 後呼叫。vivo X60 實測曾證實兩者呼叫送達但音量沒變；目前仍回到此方案，調整後讀回實際級別，未生效時如實回報而不虛報成功。

- **相對調整**用 `adjustStreamVolume(STREAM_MUSIC, ±1)` 走一格。刻意**不用** `adjustSuggestedStreamVolume()`：它依當下有焦點的播放器決定目標 stream，沒有音訊焦點時可能落到 `STREAM_MUSIC` 以外，與本期範圍不符。
- **絕對音量**以 `getStreamVolume()`／`getStreamMaxVolume()` 換算目標，再用 `setStreamVolume(STREAM_MUSIC, target, 0)` 一次設定，不逐格播放調整提示音。
- `WRITE_MEDIA_AUDIO` 管理媒體音訊檔案寫入，與音量無關；音量操作所需 manifest 權限為 `MODIFY_AUDIO_SETTINGS`。Android 音訊服務也有獨立的 `AUDIO_MEDIA_VOLUME` AppOp，若裝置上該項被明確拒絕，應檢查它而不是 `WRITE_MEDIA_AUDIO`。
- 決策邏輯集中在 `VolumeTargetPolicy`（純函式），`VolumeActionExecutor` 只負責呼叫 Android API，因此換算與夾限可完整單元測試。
- 調整後**讀回 `getStreamVolume()`** 再決定回話：讀回值等於目標才回報「音量已調到」；不一致則回報實際級別並視為未套用，避免系統或 OEM 夾限時虛報成功。
- 回話文字集中在 `VolumeReply`，並以單元測試鎖住「未實際調整時不得出現『音量已調到』」。

### 8.5.3 已知限制

- **TTS 與音樂共用 `STREAM_MUSIC`**（§3 rc27 的 X60 實測）。使用者說「小聲一點」時，小助手自己的回話也會一起變小；下限 2 格只是緩解，該下限的回話是否可聽懂未經實測。若實測不可用，才另開 Issue 評估改走 `STREAM_TTS`，本期不處理。
- **低音量下的喚醒率未驗證**。§4.3 既有的喚醒率驗收本身就是待驗收項，音量再壓只會更差。使用者可能體感「調小之後小助手壞掉」；此取捨在設計時已知並接受。
- **vivo／OriginOS 可能攔截背景音量變更**或另存音量曲線，必須真機驗收，不得以單元測試結論代替。
- **未納入**：鈴聲與通知音訊流（避開 vivo 勿擾模式的行為差異）、`STREAM_TTS` 拆分、喚醒率最佳化、反向操作（「取消調高音量」）。

### 8.5.4 X60 路徑（2026-10-04 更新）

X60 上 App UID 的 AudioManager 寫入曾被系統忽略；無障礙 fallback 與 ADB shell helper 方案均已依使用者要求移除。目前回到純 `AudioManager` 方案（§8.5.2），調整後讀回實際級別，未生效時如實回報。此路徑仍需 X60 真人語音驗收後才可標為通過。

## 9. 參考依據

- 現有程式：`app/src/main/java/com/jajafu/x60assistant/orchestration/VoiceOrchestrator.kt`、`app/src/main/AndroidManifest.xml`、`docs/test-reports/m5-2026-09-22-vivo-x60.md`。
- Android 官方：[Package visibility](https://developer.android.com/training/package-visibility/use-cases)、[MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)、[媒體控制](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)、[常見 Intent](https://developer.android.com/guide/components/intents-common)、[背景 Activity 啟動限制](https://developer.android.com/guide/components/activities/secure-bal)、[Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/views/service)。
