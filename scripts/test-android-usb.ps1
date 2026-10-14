$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android-usb.ps1')
function Invoke-FakeAdb {
    $global:LASTEXITCODE = 0
    $script:deviceLines
}
$script:deviceLines = @('List of devices attached', 'emulator-5554 device product:sdk', 'phone123 device product:channel', '192.168.1.2:5555 device product:channel')
if ((Get-UsbAndroidSerial -Adb Invoke-FakeAdb) -ne 'phone123') { throw 'Incorrect USB selection.' }
function Assert-Rejected([string[]]$Lines, [string]$Serial = '') {
    $script:deviceLines = $Lines
    $rejected = $false
    try { Get-UsbAndroidSerial -Adb Invoke-FakeAdb -Serial $Serial | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Unsafe or unavailable selection was accepted.' }
}
Assert-Rejected @('emulator-5554 device product:sdk')
Assert-Rejected @('phone123 unauthorized')
Assert-Rejected @('phone123 offline')
Assert-Rejected @('phone123 device product:channel', 'phone456 device product:channel')
Assert-Rejected @('phone123 device product:channel') 'missing'
$script:deviceLines = @('phone123 device product:channel', 'phone456 device product:channel')
if ((Get-UsbAndroidSerial -Adb Invoke-FakeAdb -Serial phone456) -ne 'phone456') { throw 'Explicit USB selection failed.' }
Write-Host 'PASS: USB selection, emulator/Wi-Fi exclusion, authorization, offline, ambiguity and explicit serial.'
