param(
    [string]$CacheDirectory = (Join-Path $env:TEMP 'x60-voice-assistant-model-cache')
)

$ErrorActionPreference = 'Stop'

$modelId = 'sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20'
$archiveName = "$modelId.tar.bz2"
$archiveSha256 = '68447f4fbc67e70eee3a93961f36e81e98f47aef73ce7e7ca00885c6cd3616a6'
$downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$archiveName"
$expectedFiles = [ordered]@{
    'encoder-epoch-13-avg-2-chunk-16-left-64.int8.onnx' = '408bbd740838c42d5bf6d1c5b80b3c88b616c7860b92d980328b5b068c76ae48'
    'decoder-epoch-13-avg-2-chunk-16-left-64.onnx' = '63a22dd60f40fff082ac3e09afa507f6787da36df76ded2fbe145fa233e22c21'
    'joiner-epoch-13-avg-2-chunk-16-left-64.int8.onnx' = '190d4067b4cc20b72a42a1916e69d92052000fb7051a427ebb1bc72a69207dc1'
    'tokens.txt' = '2d3f32311f9b692b964da3c90e830258d3e78e013cb0c992dbfb15cd5a1a71b0'
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$assetDirectory = Join-Path $repositoryRoot "app\src\main\assets\models\kws\$modelId"
$archivePath = Join-Path $CacheDirectory $archiveName
$extractRoot = Join-Path $CacheDirectory 'extracted'
$modelSource = Join-Path $extractRoot $modelId

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
if (!(Test-Path -LiteralPath $archivePath)) {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
}

$actualArchiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($actualArchiveHash -ne $archiveSha256) {
    throw "KWS archive SHA256 mismatch: $actualArchiveHash"
}

if (!(Test-Path -LiteralPath $modelSource)) {
    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
    tar -xjf $archivePath -C $extractRoot
}

New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null
foreach ($entry in $expectedFiles.GetEnumerator()) {
    $sourcePath = Join-Path $modelSource $entry.Key
    $actualFileHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourcePath).Hash.ToLowerInvariant()
    if ($actualFileHash -ne $entry.Value) {
        throw "KWS file SHA256 mismatch for $($entry.Key): $actualFileHash"
    }
    Copy-Item -Force -LiteralPath $sourcePath -Destination (Join-Path $assetDirectory $entry.Key)
}

Write-Output "Prepared verified M1 KWS assets in $assetDirectory"
Write-Output "Archive SHA256: $archiveSha256"
Write-Warning 'The upstream model archive has no explicit model-specific LICENSE. Do not commit or redistribute these generated model assets.'
