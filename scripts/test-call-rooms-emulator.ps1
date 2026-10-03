param(
 [string]$Adb='adb', [string]$Api='http://localhost:8081',
 [string[]]$Serials=@('emulator-5556','emulator-5558','emulator-5560'),
 [switch]$ResetDisposableEmulators
)
$ErrorActionPreference='Stop'
if(!$ResetDisposableEmulators -or $Serials.Count -ne 3){throw 'Use exactly three disposable emulators and -ResetDisposableEmulators'}
$root=Split-Path $PSScriptRoot
$output=Join-Path $root 'samuschat-android/app/build/room-e2e'
New-Item -ItemType Directory -Force $output | Out-Null
$resultFile=Join-Path $output 'result.json'
if(Test-Path $resultFile){Remove-Item -LiteralPath $resultFile}
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
function Ui([string]$Serial){
 for($attempt=0;$attempt -lt 4;$attempt++){
  $dump=AdbCmd $Serial @('shell','uiautomator','dump','/sdcard/room-e2e.xml')
  if(($dump -join "`n") -match 'dumped to:'){[xml]$value=(AdbCmd $Serial @('shell','cat','/sdcard/room-e2e.xml')) -join "`n";return $value}
  Start-Sleep -Milliseconds 500
 };throw "UI hierarchy unavailable on $Serial"
}
function Find([string]$Serial,[string]$Text){return @((Ui $Serial).SelectNodes('//node')|Where-Object {$_.text -eq $Text -or $_.'content-desc' -eq $Text})}
function WaitText([string]$Serial,[string]$Text,[int]$Seconds=60){
 $until=(Get-Date).AddSeconds($Seconds)
 do{$nodes=@(Find $Serial $Text);if($nodes.Count){return $nodes[-1]};Start-Sleep -Milliseconds 400}while((Get-Date)-lt $until)
 throw "Missing UI on ${Serial}: $Text"
}
function Tap([string]$Serial,[string]$Text){
 $node=WaitText $Serial $Text;$bounds=[regex]::Matches($node.bounds,'\d+')|ForEach-Object {[int]$_.Value}
 $null=AdbCmd $Serial @('shell','input','tap',"$([int](($bounds[0]+$bounds[2])/2))","$([int](($bounds[1]+$bounds[3])/2))")
}
function Request([string]$Method,[string]$Path,$Body,[string]$Token=''){
 $headers=@{'X-Forwarded-For'='127.0.0.42'};if($Token){$headers.Authorization="Bearer $Token"}
 # Enrollment fixtures represent separate clients; actual emulator logins still share their real NAT address.
 if($Path.StartsWith('/api/auth') -and $Body.email){$headers['X-Forwarded-For']="127.0.0.$(100 + [int][char]$Body.email[4])"}
 $args=@{Method=$Method;Uri="$Api$Path";Headers=$headers;ContentType='application/json'}
 if($null -ne $Body){$args.Body=$Body|ConvertTo-Json};return (Invoke-RestMethod @args).data
}
function TypeText([string]$Serial,[string]$Value){
 Start-Sleep -Milliseconds 400
 foreach($character in $Value.ToCharArray()){$null=AdbCmd $Serial @('shell','input','text',[string]$character);Start-Sleep -Milliseconds 60}
}
function Screenshot([string]$Serial,[string]$Name){
 $null=AdbCmd $Serial @('shell','screencap','-p',"/sdcard/$Name.png")
 $null=AdbCmd $Serial @('pull',"/sdcard/$Name.png",(Join-Path $output "$Name.png"))
}
$accounts=@(@{username='RoomTesterA';email='rooma@local.test';password='LocalCallsTest42!'},@{username='RoomTesterB';email='roomb@local.test';password='LocalCallsTest42!'},@{username='RoomTesterC';email='roomc@local.test';password='LocalCallsTest42!'})
$tokens=@()
foreach($a in $accounts){try{$tokens+=(Request POST '/api/auth/register' $a).token}catch{$tokens+=(Request POST '/api/auth/login' @{email=$a.email;password=$a.password}).token}}
$server=Request POST '/api/servers' @{name="Voice room regression $(Get-Date -Format HHmmss)";description='Disposable room test'} $tokens[0]
foreach($token in $tokens[1..2]){$null=Request POST "/api/servers/$($server.id)/join" $null $token}
for($i=0;$i -lt 3;$i++){
 $serial=$Serials[$i];Write-Output "Preparing $serial"
 $null=AdbCmd $serial @('install','-r',(Join-Path $root 'samuschat-android/app/build/outputs/apk/debug/app-debug.apk'))
 $null=AdbCmd $serial @('shell','pm','clear','com.samuschat')
 foreach($permission in @('android.permission.RECORD_AUDIO','android.permission.POST_NOTIFICATIONS')){$null=AdbCmd $serial @('shell','pm','grant','com.samuschat',$permission)}
 $null=AdbCmd $serial @('shell','am','start','-W','-n','com.samuschat/.MainActivity')
 Tap $serial 'Email';TypeText $serial $accounts[$i].email
 $null=AdbCmd $serial @('shell','input','keyevent','61');TypeText $serial $accounts[$i].password
 $null=AdbCmd $serial @('shell','input','keyevent','4');Tap $serial 'Entrar';$null=WaitText $serial 'Amigos'
 Tap $serial 'Servidores';Tap $serial "Servidor $($server.name)"
}
$owner=$Serials[0]
foreach($type in @('Chat','Chamada')){
 Tap $owner 'Criar canal';Tap $owner 'Nome do canal'
 $name=if($type -eq 'Chat'){'chat-regression'}else{'call-regression'}
 TypeText $owner $name;$null=AdbCmd $owner @('shell','input','keyevent','4')
 Tap $owner $type;Tap $owner 'Salvar canal';$null=WaitText $owner $name
}
Screenshot $owner 'server-channel-types'
$updated=Request GET "/api/servers/$($server.id)" $null $tokens[0]
$voice=@($updated.channels|Where-Object {$_.name -eq 'call-regression' -and $_.type -eq 'VOICE'})[0]
if(!$voice -or !@($updated.channels|Where-Object {$_.name -eq 'chat-regression' -and $_.type -eq 'TEXT'}).Count){throw 'Channel creation types were not persisted'}
foreach($serial in $Serials){Tap $serial 'Atualizar servidores';Tap $serial 'call-regression';Tap $serial 'Entrar na chamada';$null=WaitText $serial 'Sair da chamada'}
foreach($serial in $Serials){$null=WaitText $serial '3/6 participantes' 90}
foreach($i in 0..2){$serial=$Serials[$i];foreach($j in 0..2){if($i -ne $j){$null=WaitText $serial "$($accounts[$j].username) $([char]0x2022) conectado" 90}}}
Write-Output 'Three clients connected; testing mute and shared screen'
foreach($serial in $Serials){$null=AdbCmd $serial @('logcat','-c')}
$pids=@{};foreach($serial in $Serials){$pids[$serial]=(AdbCmd $serial @('shell','pidof','com.samuschat')) -join ''}
Tap $owner 'Silenciar';Tap $owner 'Ativar microfone'
$mute=WaitText $owner 'Silenciar';$bounds=[regex]::Matches($mute.bounds,'\d+')|ForEach-Object {[int]$_.Value}
$point="$([int](($bounds[0]+$bounds[2])/2)) $([int](($bounds[1]+$bounds[3])/2))"
$stress=Join-Path $output 'mute-stress.sh'
[IO.File]::WriteAllText($stress,('i=0', 'while [ $i -lt 25 ]; do', "input tap $point", "input tap $point", 'i=$((i+1))', 'done' -join "`n")+"`n",[Text.Encoding]::ASCII)
$null=AdbCmd $owner @('push',$stress,'/data/local/tmp/room-mute-stress.sh');$null=AdbCmd $owner @('shell','sh','/data/local/tmp/room-mute-stress.sh')
Tap $owner 'Compartilhar tela';$null=WaitText $owner 'Start'
if(@(Find $owner 'A single app').Count){Tap $owner 'A single app';Tap $owner 'Entire screen'}
Tap $owner 'Start';$null=WaitText $owner 'Parar compartilhamento'
$tone=Join-Path $root 'samuschat-android/call-test-tone/build/outputs/apk/debug/call-test-tone-debug.apk'
$null=AdbCmd $owner @('install','-r',$tone)
$null=AdbCmd $owner @('shell','am','start','-W','-n','com.samuschat.calltest/.ToneActivity','--ez','blocked','false')
Start-Sleep -Seconds 12
foreach($i in 1..2){$serial=$Serials[$i];$null=WaitText $serial 'Tela de RoomTesterA';Screenshot $serial "room-receiver-$i"}
foreach($serial in $Serials){
 if(((AdbCmd $serial @('shell','pidof','com.samuschat')) -join '') -ne $pids[$serial]){throw "Process restarted on $serial"}
 $logs=(AdbCmd $serial @('logcat','-d','-s','RoomStats:I','AndroidRuntime:E')) -join "`n"
 $safeSerial=$serial -replace '[^a-zA-Z0-9_-]','_'
 $logs|Set-Content (Join-Path $output "$safeSerial-media.log")
 if($logs -match 'FATAL EXCEPTION'){throw "Crash on $serial"}
 $peers=@([regex]::Matches($logs,'peer=([^ ]+).*localCandidate=relay')|ForEach-Object {$_.Groups[1].Value}|Select-Object -Unique)
 if($peers.Count -lt 2){throw "Two TURN peer connections not observed on $serial"}
 foreach($peer in $peers){
  if($logs -notmatch ("peer="+[regex]::Escape($peer)+'.*kind=audio packets=[1-9][0-9]*')){throw "No microphone packets from peer $peer on $serial"}
 }
 if($serial -ne $owner -and $logs -notmatch 'frames=[1-9][0-9]*'){throw "Screen not decoded on $serial"}
}
$null=AdbCmd $owner @('shell','am','force-stop','com.samuschat.calltest')
$null=AdbCmd $owner @('shell','am','start','-W','-n','com.samuschat/.MainActivity')
Tap $owner 'Parar compartilhamento'
foreach($serial in $Serials){Tap $serial 'Sair da chamada';$null=WaitText $serial 'Entrar na chamada'}
foreach($serial in $Serials){
 $services=(AdbCmd $serial @('shell','dumpsys','activity','services','com.samuschat')) -join "`n"
 if($services -match 'ServiceRecord.*CallMediaService'){throw "Media service still running on $serial"}
 $crashes=(AdbCmd $serial @('logcat','-d','-b','crash')) -join "`n"
 if($crashes -match 'com.samuschat'){throw "Crash buffer contains SamusChat on $serial"}
}
$result=@{passed=$true;participants=3;muteTouches=50;serverId=$server.id;channelId=$voice.id;checks=@('chat and voice channels created in UI','three participants connected','two TURN links per client','microphone packets from both peers','same processes after mute stress','screen decoded on both receivers','sharing stopped','all participants left','foreground services stopped');time=(Get-Date).ToString('o')}
$result|ConvertTo-Json|Set-Content $resultFile;$result|ConvertTo-Json
