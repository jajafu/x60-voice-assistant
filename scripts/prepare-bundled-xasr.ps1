param(
    [string]$CacheDirectory = (Join-Path $env:TEMP 'voiceassistant-asr-cache')
)

# 已停用：X-ASR 改由 App「模型」頁從雲端硬碟下載，APK 不再內建。
# 此腳本保留僅供查閱 SHA256 與檔名；執行會直接失敗，避免把 136 MB 壓縮檔放回 APK。
throw "prepare-bundled-xasr 已停用：X-ASR 改由 App 內從雲端硬碟下載，不再打包進 APK。"

$ErrorActionPreference = 'Stop'
$archiveName = 'sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03.tar.bz2'
$expectedHash = '5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224'
$expectedBytes = 136396739
$archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$archiveName"
$destination = Join-Path (Split-Path -Parent $PSScriptRoot) "app/src/main/assets/models/asr/$archiveName"

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
$archive = Join-Path $CacheDirectory $archiveName
if (!(Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri $archiveUrl -OutFile $archive
}
if ((Get-Item -LiteralPath $archive).Length -ne $expectedBytes) {
    throw "X-ASR archive size mismatch: $archive"
}
$actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) {
    throw "X-ASR archive SHA-256 mismatch: $archive"
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
Copy-Item -LiteralPath $archive -Destination $destination -Force
Write-Output "Verified bundled X-ASR archive: $destination"
