param(
    [ValidateSet('small', 'standard', 'all')]
    [string]$Pack = 'small',
    [string]$CacheDirectory = (Join-Path $env:TEMP 'x60-voice-assistant-asr-cache'),
    [string]$Serial = '',
    [switch]$InstallToDevice
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.jajafu.x60assistant'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$vadAssetDirectory = Join-Path $repositoryRoot 'app\src\main\assets\models\vad'
$vadUrl = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx'
$vadSha256 = '9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6'

$packs = [ordered]@{
    small = [ordered]@{
        Id = 'sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01'
        ArchiveSha256 = 'b3b309f7ce4a737195fcc6963ea19b0653a7d3401580af5ae0d3e284cbb71f0b'
        Files = [ordered]@{
            'model.int8.onnx' = '68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a'
            'tokens.txt' = '6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd'
            'bbpe.model' = '503204e0690eff065e30d0e01898c9ab06d0e6dc376a741eb6846198f95b2f82'
        }
    }
    standard = [ordered]@{
        Id = 'sherpa-onnx-streaming-zipformer-ctc-zh-int8-2025-06-30'
        ArchiveSha256 = 'f2ab7a5deb02717801f6a5b26c751b42f8a2db891b07f5b095e6da7442081448'
        Files = [ordered]@{
            'model.int8.onnx' = '24ffdc19ba9aaed5a6a9beaede1e087745217d82425cf4041bca0c696661801e'
            'tokens.txt' = '6193c7ea1c96d0d9a1e9652789b40d13a8a913b434a5451e93158f5a09fd6652'
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
New-Item -ItemType Directory -Force -Path $vadAssetDirectory | Out-Null
$vadCachePath = Join-Path $CacheDirectory 'silero_vad.onnx'
if (!(Test-Path -LiteralPath $vadCachePath)) {
    Invoke-WebRequest -Uri $vadUrl -OutFile $vadCachePath
}
Assert-Sha256 $vadCachePath $vadSha256
Copy-Item -Force -LiteralPath $vadCachePath -Destination (Join-Path $vadAssetDirectory 'silero_vad.onnx')
Write-Output "Prepared verified VAD asset: $vadSha256"

$selected = if ($Pack -eq 'all') { @('small', 'standard') } else { @($Pack) }
foreach ($packName in $selected) {
    $metadata = $packs[$packName]
    $archiveName = "$($metadata.Id).tar.bz2"
    $archivePath = Join-Path $CacheDirectory $archiveName
    $downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$archiveName"
    if (!(Test-Path -LiteralPath $archivePath)) {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
    }
    Assert-Sha256 $archivePath $metadata.ArchiveSha256

    $extractRoot = Join-Path $CacheDirectory "extracted-$packName"
    $modelDirectory = Join-Path $extractRoot $metadata.Id
    if (!(Test-Path -LiteralPath $modelDirectory)) {
        New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
        tar -xjf $archivePath -C $extractRoot
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
            $temporaryPath = "/data/local/tmp/x60-$($packName)-$($entry.Key)"
            & $adb -s $Serial push $sourcePath $temporaryPath
            if ($LASTEXITCODE -ne 0) { throw "adb push failed for $($entry.Key)" }
            & $adb -s $Serial shell run-as $packageName cp $temporaryPath "files/model-packs/asr/$($metadata.Id)/$($entry.Key)"
            if ($LASTEXITCODE -ne 0) { throw "run-as copy failed for $($entry.Key)" }
            & $adb -s $Serial shell rm $temporaryPath
        }
        Write-Output "Installed verified ASR pack into app-private storage on $Serial"
    }
}

Write-Warning 'The ASR archives do not contain a model-specific LICENSE or NOTICE. Use only for local evaluation; do not redistribute the weights or an APK containing them.'
