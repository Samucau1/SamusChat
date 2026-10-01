param(
 [string]$Adb='adb',
 [string]$Api='http://localhost:8081',
 [string]$Caller='emulator-5554', [string]$Callee='emulator-5556',
 [switch]$Prepare, [switch]$ResetDisposableEmulators
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$results=Join-Path $root 'samuschat-android/app/build/call-e2e'
New-Item -ItemType Directory -Force $results | Out-Null
$reportPath=Join-Path $results 'result.json'
if(Test-Path $reportPath){Remove-Item -LiteralPath $reportPath}
function AdbCmd([string]$Serial,[string[]]$Arguments) {
 $value=& $Adb -s $Serial @Arguments
 if($LASTEXITCODE -ne 0){throw "ADB failed on $Serial"}
 return $value
}
function Ui([string]$Serial) {
 for($attempt=0;$attempt -lt 3;$attempt++) {
  $dump=AdbCmd $Serial @('shell','uiautomator','dump','/sdcard/call-e2e.xml')
  if(($dump -join "`n") -match 'dumped to:') {
   [xml]$xml=(AdbCmd $Serial @('shell','cat','/sdcard/call-e2e.xml')) -join "`n"
   return $xml
  }
  Start-Sleep -Milliseconds 400
 }
 throw "UI hierarchy unavailable on $Serial; refusing a stale dump"
}
function Find([string]$Serial,[string]$Text) {
 $xml=Ui $Serial
 return @($xml.SelectNodes('//node')|Where-Object {$_.text -eq $Text -or $_.'content-desc' -eq $Text})
}
function WaitText([string]$Serial,[string]$Text,[int]$Seconds=40) {
 $until=(Get-Date).AddSeconds($Seconds)
 do { $nodes=@(Find $Serial $Text); if($nodes.Count){return $nodes[-1]};Start-Sleep -Milliseconds 400 } while((Get-Date)-lt $until)
 throw "Missing UI on ${Serial}: $Text"
}
function Tap([string]$Serial,[string]$Text) {
 $node=WaitText $Serial $Text
 $c=[regex]::Matches($node.bounds,'\d+')|ForEach-Object {[int]$_.Value}
 $null=AdbCmd $Serial @('shell','input','tap',"$([int](($c[0]+$c[2])/2))","$([int](($c[1]+$c[3])/2))")
}
function Request([string]$Method,[string]$Path,$Body,[string]$Token='') {
 $headers=@{};if($Token){$headers.Authorization="Bearer $Token"}
 $args=@{Method=$Method;Uri="$Api$Path";Headers=$headers;ContentType='application/json'}
 if($null -ne $Body){$args.Body=$Body|ConvertTo-Json}
 return (Invoke-RestMethod @args).data
}
if($Prepare) {
 if(!$ResetDisposableEmulators){throw 'Use only disposable emulators and explicitly pass -ResetDisposableEmulators.'}
 $accounts=@(@{username='CallTesterA';email='calla@local.test';password='LocalCallsTest42!'},@{username='CallTesterB';email='callb@local.test';password='LocalCallsTest42!'})
 $tokens=@()
 foreach($a in $accounts){try{$tokens+=(Request POST '/api/auth/login' @{email=$a.email;password=$a.password}).token}catch{$tokens+=(Request POST '/api/auth/register' $a).token}}
 foreach($token in $tokens){foreach($active in (Request GET '/api/calls' $null $token)){$null=Request POST "/api/calls/$($active.id)" @{action='end'} $token}}
 $server=Request POST '/api/servers' @{name='Calls regression';description='Disposable call test'} $tokens[0]
 $null=Request POST "/api/servers/$($server.id)/join" $null $tokens[1]
 for($i=0;$i -lt 2;$i++) {
  $serial=@($Caller,$Callee)[$i]
  Write-Output "Preparing $serial"
  $null=AdbCmd $serial @('install','-r',(Join-Path $root 'samuschat-android/app/build/outputs/apk/debug/app-debug.apk'))
  $null=AdbCmd $serial @('shell','pm','clear','com.samuschat')
  $null=AdbCmd $serial @('shell','pm','grant','com.samuschat','android.permission.RECORD_AUDIO')
  $null=AdbCmd $serial @('shell','pm','grant','com.samuschat','android.permission.POST_NOTIFICATIONS')
  $null=AdbCmd $serial @('shell','am','start','-W','-n','com.samuschat/.MainActivity')
  Tap $serial 'Email'
  $null=AdbCmd $serial @('shell','input','text',$accounts[$i].email)
  $null=AdbCmd $serial @('shell','input','keyevent','61')
  $null=AdbCmd $serial @('shell','input','text',$accounts[$i].password)
  $null=AdbCmd $serial @('shell','input','keyevent','4')
  Tap $serial 'Entrar'
  $null=WaitText $serial 'Ligar'
 }
}
$tone=Join-Path $root 'samuschat-android/call-test-tone/build/outputs/apk/debug/call-test-tone-debug.apk'
if(!(Test-Path $tone)){throw 'Build :call-test-tone:assembleDebug with -PcallTestTone=true first.'}
$null=AdbCmd $Caller @('install','-r',$tone)
foreach($serial in @($Caller,$Callee)){$null=AdbCmd $serial @('logcat','-c')}
Write-Output 'Inviting and accepting'
Tap $Caller 'Ligar'
Tap $Callee 'Aceitar'
$null=WaitText $Caller 'Em chamada' 65
$null=WaitText $Callee 'Em chamada' 65
Write-Output 'Both clients connected; testing controls and screen'
Tap $Caller 'Silenciar'
$null=WaitText $Caller 'Ativar microfone'
Tap $Caller 'Ativar microfone'
$mute=WaitText $Caller 'Silenciar'
$bounds=[regex]::Matches($mute.bounds,'\d+')|ForEach-Object {[int]$_.Value}
$point="$([int](($bounds[0]+$bounds[2])/2)) $([int](($bounds[1]+$bounds[3])/2))"
$commands=@('i=0','while [ $i -lt 25 ]; do',"input tap $point", "input tap $point",'i=$((i+1))','done')
$stress=Join-Path $results 'call-touch-stress.sh'
[IO.File]::WriteAllText($stress,($commands -join "`n")+"`n",[Text.Encoding]::ASCII)
$null=AdbCmd $Caller @('push',$stress,'/data/local/tmp/call-touch-stress.sh')
$null=AdbCmd $Caller @('shell','sh','/data/local/tmp/call-touch-stress.sh')
$null=WaitText $Caller 'Silenciar'
$null=WaitText $Callee 'Em chamada'
function StartScreen {
 Tap $Caller 'Compartilhar tela'
 $null=WaitText $Caller 'Start' 20
 $single=@(Find $Caller 'A single app')
 if($single.Count){Tap $Caller 'A single app';Tap $Caller 'Entire screen'}
 Tap $Caller 'Start'
 $null=WaitText $Caller 'Parar compartilhamento'
}
StartScreen
$switch=(Ui $Caller).SelectNodes('//node')|Where-Object {$_.checkable -eq 'true'}|Select-Object -First 1
if(!$switch){throw 'Device audio switch missing'}
$coords=[regex]::Matches($switch.bounds,'\d+')|ForEach-Object {[int]$_.Value}
$null=AdbCmd $Caller @('shell','input','tap',"$([int](($coords[0]+$coords[2])/2))","$([int](($coords[1]+$coords[3])/2))")
$null=AdbCmd $Caller @('shell','am','start','-W','-n','com.samuschat.calltest/.ToneActivity','--ez','blocked','false')
Start-Sleep -Seconds 12
function AudioCounts {
 $logs=(AdbCmd $Callee @('logcat','-d','-s','CallStats:I')) -join "`n"
 $matches=[regex]::Matches($logs,'deviceAudioPackets=(\d+) nonzeroSamples=(\d+)')
 if(!$matches.Count){throw 'No device audio received'}
 $last=$matches[$matches.Count-1]
 return @{packets=[long]$last.Groups[1].Value;samples=[long]$last.Groups[2].Value}
}
$allowed=AudioCounts
if($allowed.samples -le 0){throw 'Allowed media was silent on receiver'}
Write-Output 'Screen and device audio received; checking capture opt-out'
$null=AdbCmd $Caller @('shell','am','force-stop','com.samuschat.calltest')
$null=AdbCmd $Caller @('shell','am','start','-W','-n','com.samuschat.calltest/.ToneActivity','--ez','blocked','true')
Start-Sleep -Seconds 6
$before=AudioCounts
Start-Sleep -Seconds 5
$after=AudioCounts
if($after.packets -le $before.packets -or $after.samples -ne $before.samples){throw 'Capture opt-out failed or stream stopped'}
$null=AdbCmd $Caller @('shell','am','force-stop','com.samuschat.calltest')
$null=AdbCmd $Caller @('shell','am','start','-W','-n','com.samuschat/.MainActivity')
$a=(AdbCmd $Caller @('logcat','-d','-s','CallStats:I','AndroidRuntime:E')) -join "`n"
$b=(AdbCmd $Callee @('logcat','-d','-s','CallStats:I','AndroidRuntime:E')) -join "`n"
$a|Set-Content (Join-Path $results 'caller.log')
$b|Set-Content (Join-Path $results 'callee.log')
if($a -notmatch 'localCandidate=relay' -or $b -notmatch 'localCandidate=relay'){throw 'TURN relay not observed on both clients'}
if($b -notmatch 'frames=[1-9][0-9]*'){throw 'No decoded screen frames on receiver'}
if($a -match 'FATAL EXCEPTION' -or $b -match 'FATAL EXCEPTION'){throw 'Android crash detected'}
Tap $Caller 'Parar compartilhamento'
$null=WaitText $Caller 'Compartilhar tela'
StartScreen
Start-Sleep -Seconds 3
Tap $Callee 'Encerrar'
$null=WaitText $Caller 'Ligar'
$null=WaitText $Callee 'Ligar'
Tap $Callee 'Ligar'
Tap $Caller 'Recusar'
$null=WaitText $Callee 'Ligar'
foreach($serial in @($Caller,$Callee)) {
 $logs=(AdbCmd $serial @('logcat','-d','-s','CallStats:I','AndroidRuntime:E')) -join "`n"
 $logs|Set-Content (Join-Path $results "$serial-final.log")
 if($logs -match 'FATAL EXCEPTION'){throw "Crash during hangup on $serial"}
 $services=(AdbCmd $serial @('shell','dumpsys','activity','services','com.samuschat')) -join "`n"
 if($services -match 'ServiceRecord.*CallMediaService'){throw "Call service remained active on $serial"}
}
$report=@{passed=$true;stressTouches=50;deviceAudio=$allowed;checks=@('authenticated contacts','invite/accept','connected on both Android clients','TURN relay','mute/unmute stress','screen decoded','device audio received','capture opt-out respected','stop and restart sharing','remote hangup while sharing','reverse invitation declined','foreground service stopped');time=(Get-Date).ToString('o')}
$report|ConvertTo-Json|Set-Content $reportPath
$report|ConvertTo-Json
