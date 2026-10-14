[CmdletBinding()]
param(
    [string]$ApiBaseUrl = 'http://127.0.0.1:8080/',
    [string]$GoogleClientId = '',
    [switch]$AllowWithoutGoogle
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOldAndroidHome = $env:ANDROID_HOME
$taskUri = $null
if (-not [Uri]::TryCreate($ApiBaseUrl, [UriKind]::Absolute, [ref]$taskUri) -or
    $taskUri.Scheme -notin @('http', 'https') -or -not $ApiBaseUrl.EndsWith('/') -or
    $ApiBaseUrl -match '[\s"\\]' -or $taskUri.UserInfo -or $taskUri.Query -or $taskUri.Fragment) {
    throw 'Informe uma URL HTTP/HTTPS sem credenciais, terminada em /.'
}
if ($taskUri.Host -eq '10.0.2.2') { throw '10.0.2.2 funciona somente no emulador. Use o IP do computador ou 127.0.0.1 com adb reverse.' }

# Use o mesmo ID Web configurado na API, sem carregar os demais segredos.
if (-not $GoogleClientId) { $GoogleClientId = $env:GOOGLE_CLIENT_ID }
if (-not $GoogleClientId -and (Test-Path -LiteralPath (Join-Path $taskRoot '.env'))) {
    foreach ($taskLine in Get-Content -LiteralPath (Join-Path $taskRoot '.env') -Encoding UTF8) {
        if ($taskLine -match '^\s*GOOGLE_CLIENT_ID\s*=(.*)$') {
            $GoogleClientId = $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
}
if ($GoogleClientId -and $GoogleClientId -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') {
    throw 'GOOGLE_CLIENT_ID deve ser o Client ID OAuth Web do Google.'
}
if (-not $GoogleClientId -and -not $AllowWithoutGoogle) {
    throw 'Configure GOOGLE_CLIENT_ID no .env ou informe -GoogleClientId. Para um APK preliminar sem Google, use -AllowWithoutGoogle.'
}

Push-Location (Join-Path $taskRoot 'samuschat-android')
try {
    $taskSdk = Join-Path $taskRoot '.android-sdk'
    if (Test-Path -LiteralPath $taskSdk) { $env:ANDROID_HOME = $taskSdk }
    & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:signingReport "-PapiBaseUrl=$ApiBaseUrl" "-PgoogleClientId=$GoogleClientId"
    if ($LASTEXITCODE -ne 0) { throw 'Build ou verificacoes falharam. O APK nao foi copiado para entrega.' }
    $taskOutput = Join-Path $taskRoot 'dist/celular'
    New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
    $taskApk = Join-Path $taskOutput 'SamusChat-teste.apk'
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $taskApk -Force
    $taskHash = (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash
    [ordered]@{
        apiBaseUrl = $ApiBaseUrl
        googleConfigured = [bool]$GoogleClientId
        package = 'com.samuschat'
        variant = 'debug'
        sha256 = $taskHash
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'build-info.json') -Encoding UTF8
    Write-Host "APK: $taskApk"
    Write-Host "SHA256: $taskHash"
    if (-not $GoogleClientId) { Write-Warning 'APK preliminar: login Google indisponivel ate configurar OAuth e gerar novamente.' }
} finally {
    Pop-Location
    $env:ANDROID_HOME = $taskOldAndroidHome
}
