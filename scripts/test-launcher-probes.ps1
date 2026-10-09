$ErrorActionPreference = 'Stop'
$taskLauncher = Join-Path (Split-Path $PSScriptRoot) 'iniciar-samuschat.ps1'
$taskTokens = $null
$taskErrors = $null
$taskAst = [System.Management.Automation.Language.Parser]::ParseFile($taskLauncher, [ref]$taskTokens, [ref]$taskErrors)
if ($taskErrors.Count) { throw ($taskErrors | Out-String) }
# Load only probes; do not launch Docker, API or a user's emulator.
$taskAst.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -in @('Test-Docker', 'Test-AndroidBoot')
}, $true) | ForEach-Object { Invoke-Expression $_.Extent.Text }

function docker { & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo engine unavailable 1>&2 & exit /b 1' }
if (Test-Docker) { throw 'Unavailable Docker must return false.' }
if ($ErrorActionPreference -ne 'Stop') { throw 'Probe changed caller error preference.' }
function docker { & "$env:SystemRoot\System32\cmd.exe" /d /c 'exit /b 0' }
if (-not (Test-Docker)) { throw 'Ready Docker must return true.' }

$taskAdb = 'Invoke-TestAdb'
$taskSerial = 'disposable-test'
function Invoke-TestAdb { & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo device offline 1>&2 & exit /b 1' }
if (Test-AndroidBoot) { throw 'Offline Android must return false.' }
function Invoke-TestAdb { & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo 0 & exit /b 0' }
if (Test-AndroidBoot) { throw 'Booting Android must return false.' }
function Invoke-TestAdb { & "$env:SystemRoot\System32\cmd.exe" /d /c 'echo 1 & exit /b 0' }
if (-not (Test-AndroidBoot)) { throw 'Booted Android must return true.' }
if ($ErrorActionPreference -ne 'Stop') { throw 'Probe changed caller error preference.' }
Write-Host 'PASS: launcher syntax, Docker unavailable/ready, Android offline/booting/ready and error preference isolation.'
