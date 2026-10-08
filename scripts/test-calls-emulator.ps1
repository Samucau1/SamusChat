param(
 [string]$Adb='adb',
 [string]$Api='http://localhost:8081',
 [string]$Caller='emulator-5554', [string]$Callee='emulator-5556',
 [switch]$Prepare, [switch]$ResetDisposableEmulators,
 [string]$AppPackage='com.samuschat', [switch]$IsolatedApp
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$results=Join-Path $root 'samuschat-android/app/build/call-e2e'
New-Item -ItemType Directory -Force $results | Out-Null
$reportPath=Join-Path $results 'result.json'
if(Test-Path $reportPath){Remove-Item -LiteralPath $reportPath}
trap {
 @{passed=$false;error=$_.Exception.Message;caller=$Caller;callee=$Callee;package=$AppPackage;time=(Get-Date).ToString('o')} |
  ConvertTo-Json | Set-Content $reportPath
 throw
}
if($IsolatedApp -and $AppPackage -ne 'com.samuschat.validation'){throw 'IsolatedApp requires com.samuschat.validation; refusing to touch the normal package.'}
function AdbCmd([string]$Serial,[string[]]$Arguments) {
 for($attempt=0;$attempt -lt 4;$attempt++){
  $previousPreference=$ErrorActionPreference
  try {
   $ErrorActionPreference='Continue'
   $value=& $Adb -s $Serial @Arguments 2>&1
   $code=$LASTEXITCODE
  } finally {$ErrorActionPreference=$previousPreference}
  if($code -eq 0){return @($value|ForEach-Object {[string]$_})}
  $message=($value|ForEach-Object {[string]$_}) -join "`n"
  # Retry only transport failures that occurred before the device command ran.
  if($message -notmatch 'cannot connect to daemon|daemon still not running|device offline|device .*not found'){throw "ADB failed on ${Serial}: $message"}
  Write-Warning "ADB transport unavailable on $Serial; retrying"
  Start-Sleep -Seconds 2
 }
 throw "ADB transport failed repeatedly on $Serial"
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
 $headers=@{'X-Forwarded-For'='127.0.0.41'};if($Token){$headers.Authorization="Bearer $Token"}
 $args=@{Method=$Method;Uri="$Api$Path";Headers=$headers;ContentType='application/json'}
 if($null -ne $Body){$args.Body=$Body|ConvertTo-Json}
 return (Invoke-RestMethod @args).data
}
function TypeText([string]$Serial,[string]$Value) {
 Start-Sleep -Milliseconds 400
 foreach($character in $Value.ToCharArray()){
  $null=AdbCmd $Serial @('shell','input','text',[string]$character)
  Start-Sleep -Milliseconds 60
 }
}
function Screenshot([string]$Serial,[string]$Name) {
 $remote="/sdcard/$Name.png"
 $null=AdbCmd $Serial @('shell','screencap','-p',$remote)
 $null=AdbCmd $Serial @('pull',$remote,(Join-Path $results "$Name.png"))
}
if($Caller -eq $Callee){throw 'Two distinct Android clients are required.'}
foreach($serial in @($Caller,$Callee)) {
 $state=(AdbCmd $serial @('get-state')) -join ''
 if($state.Trim() -ne 'device'){throw "Client unavailable: $serial"}
}
if($Prepare) {
 if(!$ResetDisposableEmulators -and !($IsolatedApp -and $AppPackage -eq 'com.samuschat.validation')){throw 'Use disposable emulators with -ResetDisposableEmulators or the isolated validation package with -IsolatedApp.'}
 $accounts=@(@{username='CallFreeA';email='freea@local.test';password='LocalCallsTest42!'},@{username='CallFreeB';email='freeb@local.test';password='LocalCallsTest42!'})
 $tokens=@()
 foreach($a in $accounts){try{$tokens+=(Request POST '/api/auth/login' @{email=$a.email;password=$a.password}).token}catch{$tokens+=(Request POST '/api/auth/register' $a).token}}
 foreach($token in $tokens){foreach($active in (Request GET '/api/calls' $null $token)){$null=Request POST "/api/calls/$($active.id)" @{action='end'} $token}}
 if(@(Request GET '/api/servers' $null $tokens[0]).Count -or @(Request GET '/api/servers' $null $tokens[1]).Count){throw 'Use accounts without server membership for the unrestricted-call regression'}
 for($i=0;$i -lt 2;$i++) {
  $serial=@($Caller,$Callee)[$i]
  Write-Output "Preparing $serial"
  $null=AdbCmd $serial @('install','-r',(Join-Path $root $(if($IsolatedApp){'samuschat-android/app/build/outputs/apk/validation/app-validation.apk'}else{'samuschat-android/app/build/outputs/apk/debug/app-debug.apk'})))
  $null=AdbCmd $serial @('shell','pm','clear',$AppPackage)
  $null=AdbCmd $serial @('shell','pm','grant',$AppPackage,'android.permission.RECORD_AUDIO')
  $null=AdbCmd $serial @('shell','pm','grant',$AppPackage,'android.permission.POST_NOTIFICATIONS')
  $null=AdbCmd $serial @('shell','am','start','-W','-n',"${AppPackage}/com.samuschat.MainActivity")
  Tap $serial 'Email'
  TypeText $serial $accounts[$i].email
  $null=AdbCmd $serial @('shell','input','keyevent','61')
  TypeText $serial $accounts[$i].password
  $null=AdbCmd $serial @('shell','input','keyevent','4')
  Tap $serial 'Entrar'
  $null=WaitText $serial 'Amigos'
  Tap $serial 'Buscar amigos'
  TypeText $serial $accounts[1-$i].username
  $null=AdbCmd $serial @('shell','input','keyevent','4')
  Tap $serial $accounts[1-$i].username
  $null=WaitText $serial 'Ligar'
 }
}
$tone=Join-Path $root 'samuschat-android/call-test-tone/build/outputs/apk/debug/call-test-tone-debug.apk'
if(!(Test-Path $tone)){throw 'Build :call-test-tone:assembleDebug with -PcallTestTone=true first.'}
$null=AdbCmd $Caller @('install','-r',$tone)
foreach($serial in @($Caller,$Callee)){$null=AdbCmd $serial @('logcat','-c')}
Write-Output 'Inviting and accepting'
Screenshot $Caller 'friend-call-profile'
Tap $Caller 'Ligar'
Tap $Callee 'Aceitar'
$null=WaitText $Caller 'Em chamada' 65
$null=WaitText $Callee 'Em chamada' 65
$callerPid=(AdbCmd $Caller @('shell','pidof',$AppPackage)) -join ''
$calleePid=(AdbCmd $Callee @('shell','pidof',$AppPackage)) -join ''
if(!$callerPid -or !$calleePid){throw 'Call process missing'}
Screenshot $Caller 'call-connected'
Write-Output 'Testing speaker, background and screen lock'
Tap $Caller 'Ativar viva-voz'
$null=WaitText $Caller 'Desligar viva-voz'
Tap $Caller 'Desligar viva-voz'
$null=WaitText $Caller 'Ativar viva-voz'
$null=AdbCmd $Caller @('shell','input','keyevent','3')
Start-Sleep -Seconds 5
$null=WaitText $Callee 'Em chamada'
$null=AdbCmd $Caller @('shell','input','keyevent','223')
Start-Sleep -Seconds 5
$null=WaitText $Callee 'Em chamada'
$null=AdbCmd $Caller @('shell','input','keyevent','224')
$null=AdbCmd $Caller @('shell','wm','dismiss-keyguard')
$null=AdbCmd $Caller @('shell','am','start','-W','-n',"${AppPackage}/com.samuschat.MainActivity")
$null=WaitText $Caller 'Em chamada'
if(((AdbCmd $Caller @('shell','pidof',$AppPackage)) -join '') -ne $callerPid){throw 'Caller restarted during background or screen lock'}
Write-Output 'Both clients connected; testing controls and screen'
Tap $Caller 'Silenciar microfone'
$null=WaitText $Caller 'Ativar microfone'
Tap $Caller 'Ativar microfone'
$mute=WaitText $Caller 'Silenciar microfone'
$bounds=[regex]::Matches($mute.bounds,'\d+')|ForEach-Object {[int]$_.Value}
$point="$([int](($bounds[0]+$bounds[2])/2)) $([int](($bounds[1]+$bounds[3])/2))"
$commands=@('i=0','while [ $i -lt 25 ]; do',"input tap $point", "input tap $point",'i=$((i+1))','done')
$stress=Join-Path $results 'call-touch-stress.sh'
[IO.File]::WriteAllText($stress,($commands -join "`n")+"`n",[Text.Encoding]::ASCII)
$null=AdbCmd $Caller @('push',$stress,'/data/local/tmp/call-touch-stress.sh')
$null=AdbCmd $Caller @('shell','sh','/data/local/tmp/call-touch-stress.sh')
$null=WaitText $Caller 'Silenciar microfone'
$null=WaitText $Callee 'Em chamada'
if(((AdbCmd $Caller @('shell','pidof',$AppPackage)) -join '') -ne $callerPid -or
   ((AdbCmd $Callee @('shell','pidof',$AppPackage)) -join '') -ne $calleePid){throw 'Call process restarted during mute stress'}
function StartScreen {
 Tap $Caller 'Compartilhar tela do celular'
 $null=WaitText $Caller 'Start' 20
 $single=@(Find $Caller 'A single app')
 if($single.Count){Tap $Caller 'A single app';Tap $Caller 'Entire screen'}
 Tap $Caller 'Start'
 $null=WaitText $Caller 'Parar transmissão'
 $null=WaitText $Caller 'Sua tela • prévia'
}
StartScreen
Start-Sleep -Seconds 2
Screenshot $Caller 'screen-local-preview'
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
Screenshot $Callee 'screen-received'
Write-Output 'Screen and device audio received; checking capture opt-out'
$null=AdbCmd $Caller @('shell','am','force-stop','com.samuschat.calltest')
$null=AdbCmd $Caller @('shell','am','start','-W','-n','com.samuschat.calltest/.ToneActivity','--ez','blocked','true')
Start-Sleep -Seconds 6
$before=AudioCounts
Start-Sleep -Seconds 5
$after=AudioCounts
if($after.packets -le $before.packets -or $after.samples -ne $before.samples){throw 'Capture opt-out failed or stream stopped'}
$null=AdbCmd $Caller @('shell','am','force-stop','com.samuschat.calltest')
$null=AdbCmd $Caller @('shell','am','start','-W','-n',"${AppPackage}/com.samuschat.MainActivity")
$a=(AdbCmd $Caller @('logcat','-d','-s','CallStats:I','ScreenPreview:I','AndroidRuntime:E')) -join "`n"
$b=(AdbCmd $Callee @('logcat','-d','-s','CallStats:I','ScreenPreview:I','AndroidRuntime:E')) -join "`n"
$a|Set-Content (Join-Path $results 'caller.log')
$b|Set-Content (Join-Path $results 'callee.log')
if($a -notmatch 'localCandidate=relay' -or $b -notmatch 'localCandidate=relay'){throw 'TURN relay not observed on both clients'}
if($b -notmatch 'frames=[1-9][0-9]*'){throw 'No decoded screen frames on receiver'}
if($a -notmatch 'firstFrame local=true' -or $b -notmatch 'firstFrame local=false'){throw 'Screen not rendered in sender preview or receiver'}
if($a -match 'FATAL EXCEPTION' -or $b -match 'FATAL EXCEPTION'){throw 'Android crash detected'}
Tap $Caller 'Parar transmissão'
$null=WaitText $Caller 'Compartilhar tela do celular'
if(@(Find $Caller 'Sua tela • prévia').Count){throw 'Local preview still visible after stopping screen share'}
StartScreen
Start-Sleep -Seconds 3
Tap $Callee 'Encerrar chamada'
$null=WaitText $Caller 'Ligar'
$null=WaitText $Callee 'Ligar'
Tap $Callee 'Ligar'
Tap $Caller 'Recusar'
$null=WaitText $Callee 'Ligar'
Write-Output 'Testing abrupt peer interruption'
Tap $Caller 'Ligar'
Tap $Callee 'Aceitar'
$null=WaitText $Caller 'Em chamada' 65
$null=WaitText $Callee 'Em chamada' 65
$null=AdbCmd $Callee @('shell','am','force-stop',$AppPackage)
$null=WaitText $Caller 'Ligar' 90
foreach($serial in @($Caller,$Callee)) {
 $logs=(AdbCmd $serial @('logcat','-d','-s','CallStats:I','AndroidRuntime:E')) -join "`n"
 $safeSerial=$serial -replace '[^a-zA-Z0-9_-]','_'
 $logs|Set-Content (Join-Path $results "$safeSerial-final.log")
 if($logs -match 'FATAL EXCEPTION'){throw "Crash during hangup on $serial"}
 $services=(AdbCmd $serial @('shell','dumpsys','activity','services',$AppPackage)) -join "`n"
 if($services -match 'ServiceRecord.*CallMediaService'){throw "Call service remained active on $serial"}
}
$report=@{passed=$true;stressTouches=50;deviceAudio=$allowed;screenshots=@('friend-call-profile.png','call-connected.png','screen-received.png','screen-local-preview.png');checks=@('authenticated contacts','invite/accept','connected on both Android clients','speaker toggle','background and screen lock','same caller process after background','abrupt peer interruption','TURN relay','mute/unmute stress','same processes after mute stress','screen decoded','screen rendered in local preview and receiver','local preview removed after stop','device audio received','capture opt-out respected','stop and restart sharing','remote hangup while sharing','reverse invitation declined','foreground service stopped');time=(Get-Date).ToString('o')}
$report|ConvertTo-Json|Set-Content $reportPath
$report|ConvertTo-Json
