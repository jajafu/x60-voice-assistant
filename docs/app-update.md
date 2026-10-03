# App 自動更新（Issue #22，rc31 起）

手動 CI 編譯正式 APK，並由 App 偵測 GitHub Releases 新版、下載驗證後引導安裝。
Android 非系統 App 無法靜默安裝，安裝一律跳系統確認畫面。

## CI：手動建置正式 APK

Workflow：`.github/workflows/manual-build-release.yml`，只有 `workflow_dispatch`，
不設 push／PR 自動觸發。在 Actions 頁按 Run workflow，輸入：

- `release_tag`：例如 `v1.0.0-rc31`，必須唯一；workflow 會直接建立**公開** Release。
- `release_notes`：繁中簡短說明，可空。

流程：準備模型資產 → `test` → `lint` → `assembleRelease`（正式簽章）→
從 `app/build.gradle.kts` 讀版號 → 產出 `x60-assistant-<versionName>-release.apk`、
`.sha256`、`update.json` → 上傳 Actions artifact → 建立公開 Release。

### 必要 Secrets

Repository Settings → Secrets and variables → Actions：

| Secret | 內容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 正式 keystore 的 base64（keystore 本人不上 Git） |
| `RELEASE_KEYSTORE_PASSWORD` | keystore 密碼 |
| `RELEASE_KEY_ALIAS` | key 別名 |
| `RELEASE_KEY_PASSWORD` | key 密碼 |

本機建置沒有這些環境變數時維持既有未簽章行為，不受影響。

### 授權提醒

含模型 APK 的公開 Release 屬再散布。KWS 模型目前無明確模型授權
（見 `docs/known-issues.md` M1），只能本機個人評估；公開前由需求決定者承擔此風險。

### 簽章更換注意

正式簽章與既有 debug 簽章不同，X60 上的舊 debug 版需解除安裝重裝一次
（設定與提醒會遺失），之後同把正式 key 的更新可覆蓋安裝。

## App：檢查、下载、安裝

- 位置：診斷頁「App 版本與更新」區塊（版本號由已安裝套件讀取）。
- 自動檢查：設定預設開啟，每 24 小時連網檢查一次（WorkManager，
  需連網才執行）；有新版只發通知，點擊回到 App。
- 手動檢查：按「檢查更新」，比對 `update.json` 的 `versionCode` 是否大於
  `BuildConfig.VERSION_CODE`。
- 下載：按「下載更新」存到 App 私有快取（`cacheDir/updates`），
  SHA-256 通過才保留，失敗自動刪除。
- 安裝：按「安裝更新」經 FileProvider 交給系統安裝程式；
  未允許「安裝未知應用」時先跳系統授權頁，允許後再按一次。
- 信任範圍：只接受 `https` 且 host 為 `api.github.com`、`github.com`、
  `objects.githubusercontent.com`、`release-assets.githubusercontent.com`
  的連結；Release 只允許 HTTPS（沿用既有網路安全設定）。

## X60 真機手動驗收（待補）

1. 診斷頁顯示版本與 `1.0.0-rc31`／`35` 一致。
2. 按「檢查更新」：無新版顯示已是最新；有新版顯示版本與說明。
3. 關閉自動檢查後，排程工作不發通知（以 Logcat 或隔日通知確認）。
4. 下載完成顯示安裝按鈕；進度顯示正常。
5. 按「安裝更新」：未授權時跳系統授權頁；授權後安裝成功，版本更新。
6. 飛航模式下按檢查，顯示檢查失敗且不影響其他功能。
