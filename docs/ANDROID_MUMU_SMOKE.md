# Android：MuMu 安裝與開啟 smoke test

此步驟適用於 Android App 行為變更。先依專案既有方式建置或取得對應 APK。驗證範圍只有「安裝成功、從 launcher 開啟、啟動畫面可見且沒有立即崩潰」。登入、權限授予、網路與業務功能依各專案另訂測試。沒有可用 APK 或 MuMu 環境時，回報未執行原因。

本專案以 `app/build.gradle.kts` 的 `com.jajafu.x60assistant`、`versionName`／`versionCode` 為版本依據；`minSdk = 26`、`targetSdk = 35`，APK 只含 `arm64-v8a`。依 `README.md` 先執行 `scripts/prepare-m1-kws-assets.ps1` 與 `scripts/prepare-bundled-xasr.ps1`，再使用 `.\gradlew.bat assembleDebug`；Debug APK 路徑與 Release 簽章限制見 README。MuMu 執行個體的 ABI、Android 版本及是否允許覆蓋安裝須在當次檢查，不能因本機已安裝 MuMu CLI 就假定相容或可用。此 smoke 不能替代 `docs/testing.md` 的 vivo X60 驗收。

## 前置確認

1. 從專案建置設定或 APK 查證 package ID、版本與 APK 絕對路徑；確認是要測的 build variant。不得只靠檔名猜 package 或環境。
2. 確認 Windows 上已安裝 MuMuPlayer 且可找到 `mumu-cli.exe`。使用 `mumu-cli info --vmindex all` 盤點執行個體，選擇允許覆蓋安裝的**專用測試機**並記下 vmindex。對不明用途或有重要資料的執行個體先停止。
3. 先確認 APK 的最低 Android 版本、CPU ABI 與 MuMu 執行個體相容。沿用專案現有建置與測試工具；不要為了此步驟下載 SDK、重建執行個體、解除安裝 App 或清除資料。

## 執行（PowerShell 範例）

以下值要由目標專案實際查證後填入，不是固定路徑或預設測試機。命令範例使用 MuMuPlayer 6+ 的 CLI；先用 `info` 確認本機 CLI 支援。

```powershell
$mumuCli = '<mumu-cli.exe 絕對路徑>'
$vmindex = '<專用測試機 vmindex>'
$apk = '<APK 絕對路徑>'
$package = '<已查證的 package ID>'

& $mumuCli info --vmindex all
# 若專用測試機未啟動，確認 vmindex 後再啟動：
& $mumuCli control --vmindex $vmindex launch
& $mumuCli control --vmindex $vmindex app install --apk $apk
& $mumuCli control --vmindex $vmindex app launch --package $package
& $mumuCli adb --vmindex $vmindex --cmd "shell pidof $package"
& $mumuCli adb --vmindex $vmindex --cmd 'shell dumpsys activity activities'
& $mumuCli adb --vmindex $vmindex --cmd 'shell logcat -d -v threadtime -t 400'
& $mumuCli adb --vmindex $vmindex --cmd 'shell screencap -p /sdcard/app-launch.png'
& $mumuCli adb --vmindex $vmindex --cmd 'pull /sdcard/app-launch.png "<截圖絕對路徑>"'
```

安裝與啟動命令須成功；稍待啟動畫面穩定後，確認目標 package 仍在前景、畫面可見，且沒有該次啟動的致命錯誤。必要時透過 MuMu CLI ADB 截圖並人工檢視；截圖、logcat 若含帳號或個資，不要提交到 Git。若首次啟動停在 Android 權限或登入畫面，應如實回報可見狀態；若無法判斷是否正常開啟，標為未驗證，不推論通過。

PR 記錄 APK 路徑或可重建方式、package、產品版本與建置號、MuMu vmindex／Android 版本、安裝結果、啟動結果及失敗原因。此 smoke test 不代表功能驗收完成。
