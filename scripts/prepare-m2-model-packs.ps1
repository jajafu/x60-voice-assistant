param(
    [ValidateSet('standard', 'moonshine', 'xasr', 'all')]
    [string]$Pack = 'standard',
    [string]$CacheDirectory = (Join-Path $env:TEMP 'voiceassistant-asr-cache'),
    [string]$Serial = '',
    [switch]$InstallToDevice
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.va.voiceassistant'

$packs = [ordered]@{
    standard = [ordered]@{
        Id = 'sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30'
        ArchiveSha256 = 'f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448'
        Files = [ordered]@{
            'model.int8.onnx' = '24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e'
            'tokens.txt' = '6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652'
        }
    }
    moonshine = [ordered]@{
        Id = 'sherpa-onnx-moonshine-base-zh-quantized-2026-02-27'
        ArchiveSha256 = '6495d4240f66bcdd2bbbbfefaa687c463150d467854001270934e88940b733c1'
        Files = [ordered]@{
            'encoder_model.ort' = 'c725a24b58595905921ea2a47e2bcf0f18c78f4d171d96136f2dcbc8c77a58a6'
            'decoder_model_merged.ort' = 'bf79fce626e123739ec37eceb2b2a010a93d720da266dd5d8ef9a47ef9a7dc36'
            'tokens.txt' = '2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049'
            'LICENSE' = '6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd'
        }
    }
    xasr = [ordered]@{
        Id = 'sherpa-onnx-x-asr-zipformer-transducer-zh-en-punct-int8-2026-06-03'
        ArchiveSha256 = '5d02c36d7b44e886b7c8f0d8e051f8713acab96c264bb6ef9e718be39a6a2224'
        Files = [ordered]@{
            'encoder-epoch-99-avg-1.int8.onnx' = '7f6aa62056efd8af9da13e0faa81cd3f284d2fb2e3b63de56fd2dfd3450910dc'
            'decoder-epoch-99-avg-1.onnx' = '72f47405d3c1033bebccbef82f90071e7b4ba3e71b9c986f2b74244b25723aed'
            'joiner-epoch-99-avg-1.int8.onnx' = 'aedb7fa697b2ab43f20499826fff7c997eea7d67db77be97769aeeeb726e63b3'
            'bpe.model' = 'f87a38025a5fdd1e4e9591f6a44bb81295097ce0b80df6f4ab9f44e52c64ca5f'
            'tokens.txt' = 'b818a60878b9aae978cbb8ad594acbd403d76d1af2e31ef4197c84e2dbdba27c'
        }
    }
}

function Assert-Sha256([string]$Path, [string]$Expected) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "SHA256 mismatch for $Path`: $actual"
    }
}

New-Item -ItemType Directory -Force -Path $CacheDirectory | Out-Null
# VAD is a bundled APK asset; keep one source of truth for its URL and SHA256.
& (Join-Path $PSScriptRoot 'prepare-vad-asset.ps1') -CacheDirectory $CacheDirectory

$selected = if ($Pack -eq 'all') { @('standard', 'moonshine', 'xasr') } else { @($Pack) }
foreach ($packName in $selected) {
    $metadata = $packs[$packName]
    $archiveName = "$($metadata.Id).tar.bz2"
    $archivePath = Join-Path $CacheDirectory $archiveName
    $downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$archiveName"
    if (!(Test-Path -LiteralPath $archivePath)) {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
    }
    Assert-Sha256 $archivePath $metadata.ArchiveSha256

    $modelDirectory = Join-Path $CacheDirectory $metadata.Id
    if (!(Test-Path -LiteralPath $modelDirectory)) {
        $legacyDirectory = Join-Path (Join-Path $CacheDirectory "extracted-$packName") $metadata.Id
        if (Test-Path -LiteralPath $legacyDirectory) {
            $modelDirectory = $legacyDirectory
        } else {
            tar -xjf $archivePath -C $CacheDirectory
            if ($LASTEXITCODE -ne 0) { throw "Model extraction failed: $archiveName" }
        }
    }
    foreach ($entry in $metadata.Files.GetEnumerator()) {
        Assert-Sha256 (Join-Path $modelDirectory $entry.Key) $entry.Value
    }
    Write-Output "Verified ASR pack $($metadata.Id): $($metadata.ArchiveSha256)"

    if ($InstallToDevice) {
        if ([string]::IsNullOrWhiteSpace($Serial)) {
            throw 'Serial is required with -InstallToDevice.'
        }
        $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
        if (!(Test-Path -LiteralPath $adb)) {
            throw "adb not found at $adb"
        }
        & $adb -s $Serial shell run-as $packageName mkdir -p "files/model-packs/asr/$($metadata.Id)"
        if ($LASTEXITCODE -ne 0) { throw 'run-as failed; install the debuggable APK first.' }
        foreach ($entry in $metadata.Files.GetEnumerator()) {
            $sourcePath = Join-Path $modelDirectory $entry.Key
            $temporaryPath = "/data/local/tmp/va-$($packName)-$($entry.Key)"
            & $adb -s $Serial push $sourcePath $temporaryPath
            if ($LASTEXITCODE -ne 0) { throw "adb push failed for $($entry.Key)" }
            & $adb -s $Serial shell run-as $packageName cp $temporaryPath "files/model-packs/asr/$($metadata.Id)/$($entry.Key)"
            if ($LASTEXITCODE -ne 0) { throw "run-as copy failed for $($entry.Key)" }
            & $adb -s $Serial shell rm $temporaryPath
        }
        Write-Output "Installed verified ASR pack into app-private storage on $Serial"
    }
}

Write-Warning 'Standard/X-ASR weights have no model-specific LICENSE in their archives. Moonshine Chinese weights use the Moonshine AI Community License included in its archive. Check model terms before redistribution.'
