param(
    [string]$Serial = 'emulator-5554',
    [int]$Cycles = 50,
    [string]$Adb = (Join-Path $PSScriptRoot '../.android-sdk/platform-tools/adb.exe')
)
$ErrorActionPreference = 'Stop'
if ($Cycles -lt 1 -or $Cycles -gt 500) { throw 'Cycles must be between 1 and 500.' }
$output = Join-Path $PSScriptRoot '../samuschat-android/app/build/navigation-regression'
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $result
}
function Read-Ui {
    Start-Sleep -Milliseconds 400
    $dump = Invoke-Adb @('shell','uiautomator','dump','/sdcard/navigation-test.xml')
    if ($dump -notmatch 'dumped to:') { throw 'Unable to inspect the Android UI.' }
    [xml]$xml = (Invoke-Adb @('shell','cat','/sdcard/navigation-test.xml')) -join "`n"
    return $xml
}
function Find-Node([string]$Text) {
    $xml = Read-Ui
    $nodes = @($xml.SelectNodes('//node') | Where-Object { $_.text -eq $Text -or $_.'content-desc' -eq $Text })
    if (!$nodes.Count) { throw "UI missing: $Text" }
    return $nodes[-1]
}
function Tap-Node([string]$Text) {
    $node = Find-Node $Text
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($coords[0] + $coords[2]) / 2)
    $y = [int](($coords[1] + $coords[3]) / 2)
    Invoke-Adb @('shell','input','tap',"$x","$y") | Out-Null
}
function Assert-Text([string]$Text) { $null = Find-Node $Text }

# Requires a logged-in test account. Never changes credentials or profile data.
Tap-Node 'Amigos'
Assert-Text 'Alex'
Tap-Node 'Alex'
Assert-Text 'Enviar mensagem de teste'
$xml = Read-Ui
$send = $xml.SelectNodes('//node') | Where-Object { $_.'content-desc' -eq 'Enviar mensagem de teste' }
if ($send.ParentNode.enabled -eq 'true' -and $send.enabled -eq 'true') {
    # The icon can be enabled even when the enclosing button is disabled; inspect the clickable ancestor.
    $button = $send.SelectSingleNode('ancestor::node[@clickable="true"][1]')
    if ($button -and $button.enabled -eq 'true') { throw 'Empty message can be sent.' }
}
Tap-Node 'Voltar aos amigos'
Tap-Node 'Perfil'
Assert-Text 'Seu perfil'
Assert-Text 'Escolher foto'
Tap-Node 'Servidores'
Assert-Text 'SEUS SERVIDORES'
Tap-Node 'Fechar servidores'
Tap-Node 'Amigos'

# Resolve coordinates from actual layout rather than assuming a screen resolution.
function Get-Point([string]$Text) {
    $node = Find-Node $Text
    $c = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    return "$([int](($c[0]+$c[2])/2)) $([int](($c[1]+$c[3])/2))"
}
$friends = Get-Point 'Amigos'
$profile = Get-Point 'Perfil'
$servers = Get-Point 'Servidores'
Tap-Node 'Servidores'
$close = Get-Point 'Fechar servidores'
Tap-Node 'Fechar servidores'
$appPid = (Invoke-Adb @('shell','pidof','com.samuschat')).Trim()
$started = Get-Date
$commands = @('i=0', "while [ `$i -lt $Cycles ]; do",
    "input tap $profile", "input tap $friends", "input tap $servers",
    'sleep 0.3', "input tap $close", 'sleep 0.3', 'i=$((i+1))', 'done')
$scriptPath = Join-Path $output 'touch-stress.sh'
[IO.File]::WriteAllText($scriptPath, ($commands -join "`n") + "`n", [Text.Encoding]::ASCII)
Invoke-Adb @('push',$scriptPath,'/data/local/tmp/samus-touch-stress.sh') | Out-Null
Invoke-Adb @('shell','sh','/data/local/tmp/samus-touch-stress.sh') | Out-Null
if ((Invoke-Adb @('shell','pidof','com.samuschat')).Trim() -ne $appPid) { throw 'Application process changed during stress.' }
Tap-Node 'Amigos'
Assert-Text 'Alex'
Tap-Node 'Perfil'
Assert-Text 'Seu perfil'
Tap-Node 'Amigos'
$crashes = Invoke-Adb @('logcat','-d','-b','crash',"--pid=$appPid")
if ($crashes -match 'FATAL EXCEPTION|ANR in com.samuschat') { throw 'Crash detected.' }
$result = [ordered]@{ passed = $true; cycles = $Cycles; stressTouches = $Cycles * 4;
    elapsedSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1);
    serial = $Serial; processId = $appPid; checked = @('friends','demo conversation','profile','server drawer','same process after stress','responsive after stress') }
$result | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $output 'result.json')
$result | ConvertTo-Json
