<#
Read-only PostgreSQL export. ConnectionFile contains one private Render external URL.
The connection file and output must stay outside Git (for example logs/private-backups).
Requires Docker Desktop and the current user's Windows DPAPI. Never restores production.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ConnectionFile,
    [Parameter(Mandatory=$true)][string]$ExpectedHostname,
    [string]$ExpectedDatabase = 'samuschat',
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This backup uses Windows DPAPI; run it as the Windows user who will restore it.' }
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateRoot = [IO.Path]::GetFullPath((Join-Path $workspace 'logs/private-backups'))
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $output.StartsWith($privateRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be a new directory under logs/private-backups.'
}
if (Test-Path -LiteralPath $output) { throw 'Choose a new backup directory. Existing backups are never overwritten.' }
$connectionPath = (Resolve-Path -LiteralPath $ConnectionFile).Path
if (-not $connectionPath.StartsWith($privateRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ConnectionFile must be private and located under logs/private-backups.'
}
try { $connection = [Uri]([IO.File]::ReadAllText($connectionPath).Trim()) }
catch { throw 'ConnectionFile must contain one valid PostgreSQL URL. Its contents are never logged.' }
if ($connection.Scheme -notin @('postgres','postgresql') -or $connection.Host -ne $ExpectedHostname -or -not $connection.Host.EndsWith('.render.com')) {
    throw 'The URL does not match the explicitly expected Render hostname.'
}
$database = [Uri]::UnescapeDataString($connection.AbsolutePath.TrimStart('/'))
if ($database -ne $ExpectedDatabase) { throw 'The URL does not match the expected database.' }
$identity = $connection.UserInfo.Split([char[]]@(':'), 2, [StringSplitOptions]::None)
if ($identity.Count -ne 2 -or -not $identity[0] -or -not $identity[1]) { throw 'The URL is missing the user or password.' }
$port = if ($connection.Port -gt 0) { $connection.Port } else { 5432 }
$values = @{
    PGHOST=$connection.Host; PGPORT=[string]$port; PGDATABASE=$database;
    PGUSER=[Uri]::UnescapeDataString($identity[0]); PGPASSWORD=[Uri]::UnescapeDataString($identity[1]);
    PGSSLMODE='verify-full'; PGSSLROOTCERT='/etc/ssl/certs/ca-certificates.crt'; PGCONNECT_TIMEOUT='15'
}
$previous = @{}
New-Item -ItemType Directory -Path $output -Force | Out-Null
$dumpPath = Join-Path $output 'database.dump'
$encryptedPath = $dumpPath + '.dpapi'
$sqlDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'sql'))
$started = (Get-Date).ToUniversalTime().ToString('o')
try {
    foreach ($key in $values.Keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process'); [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process') }
    $dockerArgs = @('run','--rm')
    foreach ($key in $values.Keys) { $dockerArgs += @('--env', $key) }
    $dockerArgs += @('--mount', "type=bind,source=$output,target=/backup", '--mount', "type=bind,source=$sqlDirectory,target=/preflight,readonly", 'postgres:16-alpine')
    & docker @dockerArgs psql --no-psqlrc --set ON_ERROR_STOP=1 --file=/preflight/preflight-atualizacao-render.sql --output=/backup/preflight-before.txt
    if ($LASTEXITCODE -ne 0) { throw 'Read-only inventory failed. No deployment or database changes were attempted.' }
    & docker @dockerArgs pg_dump --format=custom --no-owner --no-privileges --lock-wait-timeout=15s --file=/backup/database.dump
    if ($LASTEXITCODE -ne 0) { throw 'Database export failed. Any partial output must not be used as a valid backup.' }
    & docker @dockerArgs pg_restore --list --file=/backup/restore-list.txt /backup/database.dump
    if ($LASTEXITCODE -ne 0) { throw 'The archive could not be listed. It is not an accepted backup.' }
    $plainHash = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
    Add-Type -AssemblyName System.Security
    $bytes = [IO.File]::ReadAllBytes($dumpPath)
    try {
        $protected = [Security.Cryptography.ProtectedData]::Protect($bytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllBytes($encryptedPath, $protected)
        $roundTrip = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($encryptedPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $verifiedHash = [BitConverter]::ToString($sha.ComputeHash($roundTrip)).Replace('-', '') } finally { $sha.Dispose() }
        if ($verifiedHash -ne $plainHash) { throw 'Encrypted backup verification failed; keep the original archive for investigation.' }
    } finally {
        if ($null -ne $bytes) { [Array]::Clear($bytes, 0, $bytes.Length) }
        if ($null -ne $roundTrip) { [Array]::Clear($roundTrip, 0, $roundTrip.Length) }
    }
    # Nonrecursive removal of this script's own verified plaintext export only.
    if ([IO.Path]::GetFullPath($dumpPath) -ne (Join-Path $output 'database.dump')) { throw 'Unexpected export path.' }
    Remove-Item -LiteralPath $dumpPath
    [ordered]@{
        startedUtc=$started; completedUtc=(Get-Date).ToUniversalTime().ToString('o');
        sourceHostname=$ExpectedHostname; database=$database; clientImage='postgres:16-alpine';
        tls='verify-full'; encryption='Windows DPAPI CurrentUser';
        plaintextSha256=$plainHash; encryptedSha256=(Get-FileHash -LiteralPath $encryptedPath -Algorithm SHA256).Hash;
        encryptedBytes=(Get-Item -LiteralPath $encryptedPath).Length;
        archiveReadable=$true; encryptionRoundTripVerified=$true;
        restoreTested=$false; legacyFilesVerified=$false; readyToDeploy=$false
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding UTF8
    Write-Output 'Encrypted database export created and archive verified. Restore and legacy-file verification are still required.'
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    $connection = $null; $identity = $null; $values = $null
}
