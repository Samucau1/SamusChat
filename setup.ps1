[CmdletBinding()]
param(
    [switch]$Check,
    [switch]$NoRun
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskPreviousEnv = @{}

try {
    $taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    if (-not (Get-Command $taskJava -ErrorAction SilentlyContinue)) {
        throw 'Instale o JDK 21 e configure JAVA_HOME antes de continuar.'
    }
    $taskJavaVersion = & $taskJava --version | Out-String
    if ($LASTEXITCODE -ne 0 -or $taskJavaVersion -notmatch '(?m)^(openjdk|java) 21[.\s]') {
        throw 'Este projeto usa JDK 21. Ajuste JAVA_HOME e abra um novo terminal.'
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Instale o Docker Desktop e ative o suporte a containers Linux.'
    }
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Abra o Docker Desktop e aguarde o engine iniciar.' }
    & docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Atualize o Docker Desktop para usar Docker Compose v2.' }

    $taskEnvPath = Join-Path $taskRoot '.env'
    if (-not (Test-Path -LiteralPath $taskEnvPath)) {
        if ($Check) { $taskEnvPath = Join-Path $taskRoot '.env.example' }
        else { Copy-Item -LiteralPath (Join-Path $taskRoot '.env.example') -Destination $taskEnvPath }
    }

    # Parse only known settings as data; never execute contents of .env.
    $taskAllowed = @('DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'NOTIFICATION_STRATEGY', 'FILE_BASE_URL')
    foreach ($taskLine in Get-Content -LiteralPath $taskEnvPath -Encoding UTF8) {
        if ($taskLine -match '^\s*(#|$)') { continue }
        if ($taskLine -notmatch '^\s*([A-Z_][A-Z0-9_]*)\s*=(.*)$') {
            throw 'Formato invalido em .env. Use NOME=valor, uma variavel por linha.'
        }
        $taskKey = $Matches[1]
        $taskValue = $Matches[2].Trim()
        if ($taskKey -notin $taskAllowed) { continue }
        if ($taskValue.Length -ge 2 -and (($taskValue.StartsWith('"') -and $taskValue.EndsWith('"')) -or
            ($taskValue.StartsWith("'") -and $taskValue.EndsWith("'")))) {
            $taskValue = $taskValue.Substring(1, $taskValue.Length - 2)
        }
        # Explicit process environment takes precedence over the local file.
        if ($null -eq [Environment]::GetEnvironmentVariable($taskKey, 'Process')) {
            $taskPreviousEnv[$taskKey] = $null
            [Environment]::SetEnvironmentVariable($taskKey, $taskValue, 'Process')
        }
    }
    if (-not $env:DB_USER -or -not $env:DB_PASSWORD -or $env:JWT_SECRET.Length -lt 32) {
        throw 'Configure DB_USER, DB_PASSWORD e JWT_SECRET (pelo menos 32 caracteres) em .env.'
    }
    & docker compose --project-directory $taskRoot -f (Join-Path $taskRoot 'docker-compose.yml') config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'A configuracao do Docker Compose e invalida.' }
    if ($Check) { Write-Host 'Pre-requisitos e configuracao local verificados.'; return }

    & docker compose --project-directory $taskRoot -f (Join-Path $taskRoot 'docker-compose.yml') up -d --wait --wait-timeout 90
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL/Redis nao ficaram prontos. Verifique docker compose logs.' }
    if ($NoRun) { Write-Host 'PostgreSQL e Redis prontos. Execute setup.ps1 para iniciar a API.'; return }

    Write-Host 'Iniciando API em http://localhost:8080. Ctrl+C encerra a API; Docker continua ativo.'
    Push-Location (Join-Path $taskRoot 'chatapp-backend')
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'A API encerrou com erro. Consulte a saida do Maven acima.' }
    } finally { Pop-Location }
} finally {
    foreach ($taskKey in $taskPreviousEnv.Keys) {
        [Environment]::SetEnvironmentVariable($taskKey, $taskPreviousEnv[$taskKey], 'Process')
    }
}
