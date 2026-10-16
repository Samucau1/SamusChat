[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
function Listener([int]$Port) {
    $taskConnection = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($taskConnection) { return $taskConnection.OwningProcess }
    return $null
}
if ((Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3).status -ne 'UP') {
    throw 'Este smoke exige a API local ativa, como apos executar o launcher do celular.'
}
$taskApiBefore = Listener 8080
$taskDesktopBefore = Listener 5173
$taskOldApi = $env:SAMUSCHAT_API_URL
$taskOldFiles = $env:FILE_BASE_URL
& (Join-Path $taskRoot 'iniciar-samuschat-pc.ps1') -NoBrowser
$taskDesktopAfter = Listener 5173
if (-not $taskDesktopAfter -or (Listener 8080) -ne $taskApiBefore) { throw 'A API compartilhada foi substituida ou o desktop nao iniciou.' }
if ($taskDesktopBefore -and $taskDesktopBefore -ne $taskDesktopAfter) { throw 'O servidor PC existente foi substituido.' }
$taskPage = Invoke-WebRequest 'http://127.0.0.1:5173/' -UseBasicParsing -TimeoutSec 5
if ($taskPage.Content -notmatch '/src/main\.tsx' -or $taskPage.Content -notmatch '<title>SamusChat') { throw 'Pagina PC incorreta.' }
try {
    $null = Invoke-WebRequest 'http://127.0.0.1:5173/api/users/me' -UseBasicParsing -TimeoutSec 5
    throw 'O proxy deve exigir autenticacao.'
} catch {
    if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
& (Join-Path $taskRoot 'iniciar-samuschat-pc.ps1') -NoBrowser
if ((Listener 5173) -ne $taskDesktopAfter -or (Listener 8080) -ne $taskApiBefore) { throw 'Execucao repetida duplicou ou substituiu os servicos.' }
if ($env:SAMUSCHAT_API_URL -ne $taskOldApi -or $env:FILE_BASE_URL -ne $taskOldFiles) { throw 'Launcher alterou o ambiente do terminal chamador.' }
Write-Host 'APROVADO: pagina PC, proxy autenticado, reutilizacao da API, execucao repetida e ambiente preservado.'
