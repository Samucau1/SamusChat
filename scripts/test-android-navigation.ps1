param(
    [string]$Serial = '',
    [int]$Cycles = 50,
    [string]$Adb = (Join-Path $PSScriptRoot '../.android-sdk/platform-tools/adb.exe')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-usb.ps1')
$Serial = Get-UsbAndroidSerial -Adb $Adb -Serial $Serial
if ($Cycles -lt 1 -or $Cycles -gt 500) { throw 'Cycles must be between 1 and 500.' }
$output = Join-Path $PSScriptRoot '../samuschat-android/app/build/navigation-regression'
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        # ADB prints successful push/install progress on stderr in Windows PowerShell.
        $ErrorActionPreference = 'Continue'
        $result = @(& $Adb -s $Serial @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($code -ne 0) { throw "ADB failed: $Arguments" }
    return @($result | ForEach-Object { [string]$_ })
}
function Read-Ui {
    Start-Sleep -Milliseconds 400
    $dump = Invoke-Adb @('shell','uiautomator','dump','/sdcard/navigation-test.xml')
    if ($dump -notmatch 'dumped to:') { throw 'Unable to inspect the Android UI.' }
    [xml]$xml = (Invoke-Adb @('shell','cat','/sdcard/navigation-test.xml')) -join "`n"
    return $xml
}
function Find-Node([string]$Text) {
    $deadline = (Get-Date).AddSeconds(10)
    do {
        $xml = Read-Ui
        $nodes = @($xml.SelectNodes('//node') | Where-Object { $_.text -eq $Text -or $_.'content-desc' -eq $Text })
        if ($nodes.Count) { return $nodes[-1] }
    } while ((Get-Date) -lt $deadline)
    throw "UI missing: $Text"
}
function Tap-Node([string]$Text) {
    $node = Find-Node $Text
    $target = $node.SelectSingleNode('ancestor-or-self::node[@clickable="true"][1]')
    if ($target) { $node = $target }
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($coords[0] + $coords[2]) / 2)
    $y = [int](($coords[1] + $coords[3]) / 2)
    Invoke-Adb @('shell','input','tap',"$x","$y") | Out-Null
}
function Assert-Text([string]$Text) { $null = Find-Node $Text }

# Requires a logged-in test account. Never changes credentials or profile data.
$initialUi = Read-Ui
if ($initialUi.SelectSingleNode('//node[@content-desc="Fechar servidores"]')) { Tap-Node 'Amigos' }
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
Assert-Text 'Escolher foto ou GIF'
Tap-Node 'Servidores'
Assert-Text 'SEUS SERVIDORES'
Tap-Node 'Amigos'
Tap-Node 'Amigos'

# Gesture contract: semantic selection, screen-relative coordinates and bounded polling.
function Assert-Page([string]$Label) {
    $node = Find-Node $Label
    $selected = $node.SelectSingleNode('ancestor-or-self::node[@selected="true"][1]')
    if (-not $selected) { throw "Navigation item is not selected: $Label" }
}
function Swipe-Page([bool]$Left, [int]$Duration = 350) {
    $xml = Read-Ui
    $bounds = [regex]::Matches($xml.hierarchy.node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    if ($bounds.Count -ne 4) { throw 'Unable to resolve screen bounds.' }
    $width = $bounds[2] - $bounds[0]; $height = $bounds[3] - $bounds[1]
    $from = [int]($bounds[0] + $width * 0.8); $to = [int]($bounds[0] + $width * 0.2)
    $y = [int]($bounds[1] + $height * 0.45)
    if (-not $Left) { $temp = $from; $from = $to; $to = $temp }
    Invoke-Adb @('shell','input','swipe',"$from","$y","$to","$y","$Duration") | Out-Null
}
foreach ($duration in @(250, 500)) {
    Tap-Node 'Servidores'
    Assert-Page 'Servidores'
    $xml = Read-Ui
    if ($xml.SelectSingleNode('//node[@content-desc="Fechar servidores"]')) { throw 'Removed close header still exists.' }
    Swipe-Page $false $duration
    Assert-Page 'Servidores'
    Swipe-Page $true $duration
    Assert-Page 'Amigos'
    Assert-Text 'Alex'
    Swipe-Page $true $duration
    Assert-Page 'Perfil'
    Assert-Text 'Seu perfil'
    Swipe-Page $true $duration
    Assert-Page 'Perfil'
    Swipe-Page $false $duration
    Assert-Page 'Amigos'
    Swipe-Page $false $duration
    Assert-Page 'Servidores'
}
Tap-Node 'Perfil'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Assert-Page 'Amigos'

# Resolve coordinates from actual layout rather than assuming a screen resolution.
function Get-Point([string]$Text) {
    $node = Find-Node $Text
    $target = $node.SelectSingleNode('ancestor-or-self::node[@clickable="true"][1]')
    if ($target) { $node = $target }
    $c = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    return "$([int](($c[0]+$c[2])/2)) $([int](($c[1]+$c[3])/2))"
}
$friends = Get-Point 'Amigos'
$profile = Get-Point 'Perfil'
$servers = Get-Point 'Servidores'
Tap-Node 'Servidores'
$close = Get-Point 'Amigos'
Tap-Node 'Amigos'
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
    serial = $Serial; processId = $appPid; checked = @('friends','demo conversation','profile','server page','bidirectional gestures at 250/500ms','page boundaries','system back','close header absent','same process after stress','responsive after stress') }
$result | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $output 'result.json')
$result | ConvertTo-Json
