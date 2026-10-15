# 正式包簽名設定（只做一次）

之前都是測試包，沒有固定印章。要讓手機可以直接覆蓋安裝，就要先做一個固定印章，以後都用它。

## 1. 在自己電腦做一個印章

裝有 JDK 的電腦上執行（密碼自己記好，以後不能改）：

```powershell
keytool -genkeypair -v -keystore release.keystore -alias x60assistant -keyalg RSA -keysize 2048 -validity 9125
```

會問名字、密碼，照著填就好。做完會得到 `release.keystore`，好好保存，不要進 Git。

## 2. 把印章放進 GitHub

1. 把 `release.keystore` 轉成文字：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File keystore-base64.txt
```

2. 到 GitHub 專案頁 → Settings → Secrets and variables → Actions → New secret，逐一加入 4 個：

- `ANDROID_KEYSTORE_BASE64`：上面那個文字檔的全部內容
- `ANDROID_KEYSTORE_PASSWORD`：做印章時設的密碼
- `ANDROID_KEY_ALIAS`：`x60assistant`
- `ANDROID_KEY_PASSWORD`：跟上面一樣的密碼（當初沒分開設的話）

## 3. 手動打包

1. 到 GitHub 專案頁 → Actions → Build release APK (manual) → Run workflow
2. 等跑完，在下方 Artifacts 下載 `app-release`
3. 第一次裝到手機後，以後都用同一個印章，就能直接覆蓋升級，不用刪掉重裝。

本地沒設密碼時打出的正式包仍是未簽名測試用，跟以前一樣；雲上有 4 個密鑰才會打出可安裝的正式包。
