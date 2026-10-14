function Get-UsbAndroidSerial {
    param([string]$Adb, [string]$Serial = '')
    $taskLines = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel consultar o ADB.' }
    $taskDevices = @($taskLines | ForEach-Object {
        if ($_ -match '^(\S+)\s+(device|unauthorized|offline)\s*(.*)$') {
            $taskId = $Matches[1]
            $taskState = $Matches[2]
            $taskDetails = $Matches[3]
            if ($taskId -notmatch '^emulator-|:|\._tcp' -and $taskDetails -notmatch '\b(?:product|model|device):(?:sdk|generic|emulator)') {
                [pscustomobject]@{ Serial = $taskId; State = $taskState }
            }
        }
    })
    if ($Serial) { $taskDevices = @($taskDevices | Where-Object { $_.Serial -eq $Serial }) }
    if (-not $taskDevices.Count) { throw 'Celular USB nao encontrado. Conecte um cabo de dados e ative Depuracao USB. Confira adb devices -l.' }
    if ($taskDevices.Count -gt 1) { throw 'Mais de um celular conectado. Informe -Serial com o identificador mostrado em adb devices -l.' }
    if ($taskDevices[0].State -eq 'unauthorized') { throw 'Autorize a depuracao USB na tela do celular e execute novamente.' }
    if ($taskDevices[0].State -ne 'device') { throw 'Celular offline. Reconecte o cabo USB e execute novamente.' }
    return $taskDevices[0].Serial
}
