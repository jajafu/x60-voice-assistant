param(
    [string]$CacheDirectory = (Join-Path $env:TEMP 'voiceassistant-model-cache')
)

# Silero VAD is a bundled APK asset (unlike X-ASR, which the app downloads from the sherpa-onnx release).
# The service refuses to start when this file is missing (VoiceErrorCode.VAD_INIT_FAILED),
# so every release build must prepare it before packaging.
$ErrorActionPreference = 'Stop'

$fileName = 'silero_vad.onnx'
$downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$fileName"
$expectedSha256 = '9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$assetDirectory = Join-Path $repositoryRoot 'app\src\main\assets\models\vad'
$cachedFile = Join-Path $CacheDirectory $fileName

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null

if (!(Test-Path -LiteralPath $cachedFile)) {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $cachedFile
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $cachedFile).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "VAD asset SHA256 mismatch: $actualSha256"
}

Copy-Item -Force -LiteralPath $cachedFile -Destination (Join-Path $assetDirectory $fileName)

Write-Output "Prepared verified Silero VAD asset in $assetDirectory"
Write-Output "SHA256: $expectedSha256"
Write-Output "Source: $downloadUrl (MIT)"
