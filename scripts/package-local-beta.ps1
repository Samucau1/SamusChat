[CmdletBinding()]
param([string]$OutputName = 'beta-local-2026-10-20')
$ErrorActionPreference = 'Stop'
if ($OutputName -notmatch '^[A-Za-z0-9_-]+$') { throw 'Nome de pacote invalido.' }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot "dist/celular/$OutputName"
$taskApk = Join-Path $taskRoot 'samuschat-android/app/build/outputs/apk/debug/app-debug.apk'
$taskWeb = Join-Path $taskRoot 'samuschat-desktop/dist'
if (!(Test-Path -LiteralPath $taskApk) -or !(Test-Path -LiteralPath (Join-Path $taskWeb 'index.html'))) { throw 'Execute as builds Android e desktop antes de empacotar.' }
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
Copy-Item -LiteralPath $taskApk -Destination (Join-Path $taskOutput 'SamusChat-beta-local-debug.apk') -Force
Compress-Archive -Path (Join-Path $taskWeb '*') -DestinationPath (Join-Path $taskOutput 'SamusChat-desktop.zip') -Force
$taskArtifacts = @(Get-ChildItem -LiteralPath $taskOutput -File | Where-Object { $_.Extension -in @('.apk','.zip') } | ForEach-Object {
    [ordered]@{ file=$_.Name; bytes=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
$taskCommit = (& git -C $taskRoot rev-parse HEAD).Trim()
$taskChanges = (& git -C $taskRoot diff HEAD -- samuschat-android samuschat-desktop chatapp-backend scripts docs | Out-String)
[ordered]@{
    version='1.2.0-beta-local'; versionCode=3; variant='debug'; distribution='local-only'
    apiBaseUrl='http://127.0.0.1:8080/'; sourceBase=$taskCommit
    sourceHasChanges=[bool]$taskChanges; capturedAtUtc=(Get-Date).ToUniversalTime().ToString('o')
    artifacts=$taskArtifacts
    limitations=@('Requer API local e adb reverse no celular','Nao e release assinada para distribuicao externa','Push FCM depende de configuracao Firebase externa','Avaliacao manual da nova caixa de mensagens deve ser registrada separadamente')
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskOutput 'build-info.json') -Encoding UTF8
Write-Host "Beta local preservada em $taskOutput"
