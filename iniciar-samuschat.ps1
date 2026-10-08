[CmdletBinding()]
param(
    [string]$Avd = 'SamusChat_Validacao',
    [int]$Port = 5554,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskSdk = Join-Path $taskRoot '.android-sdk'
$taskAdb = Join-Path $taskSdk 'platform-tools/adb.exe'
$taskEmulator = Join-Path $taskSdk 'emulator/emulator.exe'
$taskSerial = "emulator-$Port"
$taskLogs = Join-Path $taskRoot 'logs'
$taskBackend = $null
$taskOldAndroidHome = $env:ANDROID_HOME
$taskOldAvdHome = $env:ANDROID_AVD_HOME

function Wait-Ready([scriptblock]$Probe, [int]$Seconds, [string]$Description) {
    $taskDeadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (& $Probe) { return }
        if ($taskBackend -and $taskBackend.HasExited) {
            throw 'A API encerrou antes de ficar pronta. Consulte logs/api.stdout.log e logs/api.stderr.log.'
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $taskDeadline)
    throw "Tempo esgotado aguardando $Description. Consulte a documentacao de inicializacao."
}

function Test-Api {
    try {
        $taskResponse = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3
        return $taskResponse.status -eq 'UP'
    } catch { return $false }
}

function Test-Docker {
    # Windows PowerShell converte stderr nativo em NativeCommandError.
    # Durante a inicializacao, engine indisponivel e um resultado esperado.
    $ErrorActionPreference = 'SilentlyContinue'
    $PSNativeCommandUseErrorActionPreference = $false
    try {
        & docker info --format '{{.ServerVersion}}' *> $null
        return $LASTEXITCODE -eq 0
    } catch { return $false }
}

function Test-AndroidBoot {
    # O ADB pode retornar stderr enquanto o emulador ainda esta offline.
    $ErrorActionPreference = 'SilentlyContinue'
    $PSNativeCommandUseErrorActionPreference = $false
    try {
        $taskBoot = & $taskAdb -s $taskSerial shell getprop sys.boot_completed 2>$null
        return $LASTEXITCODE -eq 0 -and ($taskBoot -join '').Trim() -eq '1'
    } catch { return $false }
}

function Invoke-Device([string[]]$DeviceArguments) {
    $taskResult = & $taskAdb -s $taskSerial @DeviceArguments
    if ($LASTEXITCODE -ne 0) { throw "Falha no ADB: $($DeviceArguments -join ' ')" }
    return $taskResult
}

try {
    if ($Avd -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Nome de AVD invalido.' }
    if ($Port -lt 5554 -or $Port -gt 5682 -or $Port % 2 -ne 0) { throw 'Use uma porta par entre 5554 e 5682.' }
    foreach ($taskRequired in @($taskAdb, $taskEmulator)) {
        if (-not (Test-Path -LiteralPath $taskRequired)) { throw "SDK nao encontrado: $taskRequired" }
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop nao instalado.' }
    New-Item -ItemType Directory -Force -Path $taskLogs | Out-Null
    $env:ANDROID_HOME = $taskSdk
    $env:ANDROID_AVD_HOME = Join-Path $taskSdk 'avd'
    $taskAvds = @(& $taskEmulator -list-avds)
    if ($LASTEXITCODE -ne 0 -or $Avd -notin $taskAvds) { throw "AVD nao encontrado: $Avd" }

    Write-Host '[1/5] Preparando Docker...'
    if (-not (Test-Docker)) {
        $taskDockerDesktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $taskDockerDesktop)) { throw 'Docker Desktop nao encontrado.' }
        Start-Process -FilePath $taskDockerDesktop -WindowStyle Hidden
        Wait-Ready { Test-Docker } 180 'Docker'
    }

    Write-Host '[2/5] Preparando PostgreSQL e Redis...'
    & (Join-Path $taskRoot 'setup.ps1') -NoRun

    Write-Host '[3/5] Preparando API...'
    if (-not (Test-Api)) {
        # Nao iniciar outra API em uma porta ocupada por um servico sem saude.
        if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
            Write-Host 'Porta 8080 em uso. Aguardando a API existente ficar saudavel...'
        } else {
            $taskBackend = Start-Process -FilePath 'powershell.exe' -ArgumentList @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + (Join-Path $taskRoot 'setup.ps1') + '"')
            ) -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $taskLogs 'api.stdout.log') `
                -RedirectStandardError (Join-Path $taskLogs 'api.stderr.log')
        }
        Wait-Ready { Test-Api } 240 'API em localhost:8080'
    }

    Write-Host '[4/5] Preparando emulador...'
    $taskDeviceLines = @(& $taskAdb devices)
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel consultar o ADB.' }
    if (-not ($taskDeviceLines -match "^$taskSerial\s")) {
        Start-Process -FilePath $taskEmulator -ArgumentList @('-avd', $Avd, '-port', "$Port") -WindowStyle Normal
    } else {
        $taskCurrentAvd = (& $taskAdb -s $taskSerial emu avd name 2>$null | Select-Object -First 1)
        if ($LASTEXITCODE -eq 0 -and $taskCurrentAvd -ne $Avd) {
            throw "A porta $Port pertence a outro AVD ($taskCurrentAvd). Escolha outra porta com -Port."
        }
    }
    Wait-Ready { Test-AndroidBoot } 240 'inicializacao do Android'

    Write-Host '[5/5] Abrindo SamusChat...'
    $taskAndroidProject = Join-Path $taskRoot 'samuschat-android'
    $taskApk = Join-Path $taskAndroidProject 'app/build/outputs/apk/debug/app-debug.apk'
    Write-Host 'Verificando o build atual do aplicativo...'
    Push-Location $taskAndroidProject
    try {
        $taskGradleArguments = @(':app:assembleDebug', '-PapiBaseUrl=http://10.0.2.2:8080/', '-PcallsForceRelay=false')
        if ($Rebuild) { $taskGradleArguments += '--rerun-tasks' }
        & .\gradlew.bat @taskGradleArguments
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar APK. O aplicativo instalado nao foi substituido.' }
    } finally { Pop-Location }
    $taskInstalled = (Invoke-Device @('shell', 'pm', 'path', 'com.samuschat')) -join ''
    $taskNeedsInstall = $true
    if (-not $Rebuild -and $taskInstalled -match '^package:(/data/app/[A-Za-z0-9_~=/+.-]+/base\.apk)$') {
        $taskInstalledPath = $Matches[1]
        $taskDeviceHash = (Invoke-Device @('shell', 'sha256sum', $taskInstalledPath)) -join ''
        if ($taskDeviceHash -match '^([a-fA-F0-9]{64})\s') {
            $taskNeedsInstall = $Matches[1] -ne (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash
        }
    }
    if ($taskNeedsInstall) {
        Write-Host 'Atualizando APK no emulador, preservando os dados da conta...'
        Invoke-Device @('install', '-r', $taskApk) | Out-Host
    } else {
        Write-Host 'O aplicativo instalado ja corresponde ao build atual.'
    }
    # Confirma conectividade TCP a partir do proprio Android antes de abrir o app.
    Invoke-Device @('shell', 'toybox', 'nc', '-w', '3', '10.0.2.2', '8080', '</dev/null') | Out-Null
    Invoke-Device @('shell', 'am', 'start', '-n', 'com.samuschat/.MainActivity') | Out-Host
    Write-Host 'SamusChat pronto. API, banco, Redis e emulador continuam ativos apos este comando.'
} finally {
    $env:ANDROID_HOME = $taskOldAndroidHome
    $env:ANDROID_AVD_HOME = $taskOldAvdHome
}
