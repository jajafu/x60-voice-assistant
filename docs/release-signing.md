# 正式包簽名設定（只做一次）

之前都是測試包，沒有固定印章。要讓手機可以直接覆蓋安裝，就要先做一個固定印章，以後都用它。

原則：印章只以加密文字藏在 GitHub 密鑰區。你的電腦和 Git 上都不留明文檔案。

## 1. 生一個臨時印章

在自己電腦執行（密碼自己記好）：

```powershell
keytool -genkeypair -v -keystore ci-release.keystore -alias x60assistant -keyalg RSA -keysize 2048 -validity 9125
```

## 2. 把印章藏進 GitHub 密鑰區

1. 把印章轉成文字：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ci-release.keystore")) | Out-File keystore-base64.txt
```

2. 到 GitHub 專案頁 → Settings → Secrets and variables → Actions → New secret，逐一加入 4 個：

- `RELEASE_KEYSTORE_BASE64`：上面那個文字檔的全部內容
- `RELEASE_KEYSTORE_PASSWORD`：做印章時設的密碼
- `RELEASE_KEY_ALIAS`：`x60assistant`
- `RELEASE_KEY_PASSWORD`：跟上面一樣的密碼（當初沒分開設的話）

3. 上傳完就把自己電腦上的 `ci-release.keystore` 和 `keystore-base64.txt` 刪掉。以後要用，直接從密鑰區還原，不需要留檔。

## 3. 打包時怎麼用

雲上打包時才把密文臨時還原成 `app/ci-release.keystore`，打完立刻刪掉（見 `.github/workflows/build-release-apk.yml`）。Git 上永遠看不到明文印章。

## 4. 手動打包

1. 到 GitHub 專案頁 → Actions → Build release APK (manual) → Run workflow
2. 等跑完，在下方 Artifacts 下載 `app-release`
3. 第一次裝到手機後，以後都用同一個印章，就能直接覆蓋升級，不用刪掉重裝。

主倉庫已開源（1.0.12 起），App 的更新檢查與 APK 下載直接指向主倉庫的 GitHub Releases，workflow 打完包就發佈到同一個倉庫的 Release，不再需要另一個公開更新專案與 `RELEASES_REPO_TOKEN`（舊架構已移除）。

本地沒設密碼時打出的正式包仍是未簽名測試用，跟以前一樣；雲上有 3 個密鑰才會打出可安裝的正式包。
