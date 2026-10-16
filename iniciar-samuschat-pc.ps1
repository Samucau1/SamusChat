[CmdletBinding()]
param(
    [switch]$NoBrowser,
    [switch]$ReinstallDependencies
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskDesktop = Join-Path $taskRoot 'samuschat-desktop'
$taskLogs = Join-Path $taskRoot 'logs'
$taskUrl = 'http://127.0.0.1:5173/'
$taskOldApiUrl = $env:SAMUSCHAT_API_URL
$taskOldFileBaseUrl = $env:FILE_BASE_URL
$taskMutex = New-Object Threading.Mutex($false, 'Local\SamusChatDesktopLauncher')
$taskOwnsMutex = $false

function Test-Api {
    try { return (Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3).status -eq 'UP' }
    catch { return $false }
}

function Test-Desktop {
    try {
        $taskPage = Invoke-WebRequest -Uri $taskUrl -UseBasicParsing -TimeoutSec 3
        return $taskPage.StatusCode -eq 200 -and $taskPage.Content -match '<title>SamusChat[^<]*</title>' -and $taskPage.Content -match '/src/main\.tsx'
    } catch { return $false }
}

function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Test-Docker {
    $ErrorActionPreference = 'SilentlyContinue'
    $PSNativeCommandUseErrorActionPreference = $false
    try { & docker info --format '{{.ServerVersion}}' *> $null; return $LASTEXITCODE -eq 0 }
    catch { return $false }
}

function Wait-Ready([scriptblock]$Probe, [int]$Seconds, [string]$Description, $Process = $null) {
    $taskDeadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (& $Probe) { return }
        if ($Process) {
            $Process.Refresh()
            if ($Process.HasExited) { throw "$Description encerrou antes de ficar pronto. Consulte os logs em $taskLogs." }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $taskDeadline)
    throw "Tempo esgotado aguardando $Description. Confira as portas 8080/5173 e os logs em $taskLogs."
}

try {
    try { $taskOwnsMutex = $taskMutex.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $taskOwnsMutex = $true }
    if (-not $taskOwnsMutex) { Write-Host 'O launcher PC ja esta preparando o SamusChat. Aguarde a abertura do navegador.'; return }
    New-Item -ItemType Directory -Force -Path $taskLogs | Out-Null
    if (-not (Get-Command node.exe -ErrorAction SilentlyContinue) -or -not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'Instale o Node.js 22 ou superior com npm e abra um novo terminal.'
    }
    $taskNodeVersion = & node.exe --version
    if ($LASTEXITCODE -ne 0 -or $taskNodeVersion -notmatch '^v(\d+)\.' -or [int]$Matches[1] -lt 22) {
        throw 'A versao PC exige Node.js 22 ou superior.'
    }

    Write-Host '[1/3] Conferindo a API compartilhada com o celular...'
    if (Test-Api) {
        Write-Host 'API ja disponivel. Reutilizando os servicos existentes.'
    } else {
        $taskBackend = $null
        if (Test-Port 8080) {
            Write-Host 'Porta 8080 ocupada. Aguardando a API existente, sem iniciar outra instancia...'
        } else {
            if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Instale o Docker Desktop para iniciar banco, Redis e API.' }
            if (-not (Test-Docker)) {
                $taskDocker = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
                if (-not (Test-Path -LiteralPath $taskDocker)) { throw 'Docker Desktop nao encontrado.' }
                Start-Process -FilePath $taskDocker -WindowStyle Hidden
                Wait-Ready { Test-Docker } 180 'Docker Desktop'
            }
            $env:FILE_BASE_URL = 'http://127.0.0.1:8080'
            $taskBackend = Start-Process powershell.exe -ArgumentList @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + (Join-Path $taskRoot 'setup.ps1') + '"')
            ) -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru `
                -RedirectStandardOutput (Join-Path $taskLogs 'pc-api.stdout.log') `
                -RedirectStandardError (Join-Path $taskLogs 'pc-api.stderr.log')
        }
        Wait-Ready { Test-Api } 240 'API em localhost:8080' $taskBackend
    }

    Write-Host '[2/3] Preparando a interface PC...'
    if (Test-Desktop) {
        if ($ReinstallDependencies) { throw 'A interface PC esta ativa. Encerre-a antes de reinstalar as dependencias.' }
        Write-Host 'Interface PC ja disponivel. Reutilizando o servidor existente.'
    } else {
        if (Test-Port 5173) { throw 'A porta 5173 esta ocupada por uma interface que nao corresponde ao SamusChat. Confira o processo antes de continuar.' }
        $taskVite = Join-Path $taskDesktop 'node_modules/vite/bin/vite.js'
        if ($ReinstallDependencies -or -not (Test-Path -LiteralPath $taskVite)) {
            Push-Location $taskDesktop
            try {
                & npm.cmd ci
                if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar as dependencias PC. Confira a conexao e o package-lock.json.' }
            } finally { Pop-Location }
        }
        $env:SAMUSCHAT_API_URL = 'http://127.0.0.1:8080'
        # Launch Node directly: avoids npm.ps1 argument parsing and keeps port 5173 fixed.
        $taskFrontend = Start-Process node.exe -ArgumentList @(
            ('"' + $taskVite + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort'
        ) -WorkingDirectory $taskDesktop -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $taskLogs 'pc-desktop.stdout.log') `
            -RedirectStandardError (Join-Path $taskLogs 'pc-desktop.stderr.log')
        Wait-Ready { Test-Desktop } 60 'Interface PC' $taskFrontend
    }

    if ($NoBrowser) { Write-Host '[3/3] Interface pronta; abertura do navegador desativada nesta execucao.' }
    else { Write-Host '[3/3] Abrindo SamusChat no navegador...'; Start-Process $taskUrl }
    Write-Host "SamusChat PC pronto: $taskUrl"
    Write-Host 'Entre com sua conta PC. Fechar esta janela ou o navegador nao encerra os servicos nem o aplicativo do celular.'
} finally {
    $env:SAMUSCHAT_API_URL = $taskOldApiUrl
    $env:FILE_BASE_URL = $taskOldFileBaseUrl
    if ($taskOwnsMutex) { $taskMutex.ReleaseMutex() }
    $taskMutex.Dispose()
}
