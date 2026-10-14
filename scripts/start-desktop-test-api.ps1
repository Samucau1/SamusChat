[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port = 18082)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskRuntimeRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot 'chatapp-backend/target/desktop-test-runtime'))
$taskSnapshot = [IO.Path]::GetFullPath((Join-Path $taskRuntimeRoot ([Guid]::NewGuid().ToString('N'))))
Push-Location (Join-Path $taskRoot 'chatapp-backend')
try {
    & ./mvnw.cmd test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/desktop-test-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel preparar o classpath de teste.' }
    # Keep a running test API independent of subsequent Maven recompilation.
    New-Item -ItemType Directory -Path $taskSnapshot -Force | Out-Null
    Copy-Item -LiteralPath (Resolve-Path target/test-classes).Path -Destination (Join-Path $taskSnapshot 'test-classes') -Recurse
    Copy-Item -LiteralPath (Resolve-Path target/classes).Path -Destination (Join-Path $taskSnapshot 'classes') -Recurse
    $taskClasspath = (Join-Path $taskSnapshot 'test-classes') + ';' + (Join-Path $taskSnapshot 'classes') + ';' + (Get-Content target/desktop-test-classpath.txt -Raw).Trim()
    # Keep the in-memory schema across retirement of the pool's last connection.
    # A unique name and process termination still isolate/discard this test database.
    $taskDatabase = 'jdbc:h2:mem:desktop_' + [Guid]::NewGuid().ToString('N') + ';MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
    Write-Host "API descartavel H2 na porta $Port. Ctrl+C encerra e descarta os dados de teste."
    & java -cp $taskClasspath com.chatapp.chatapp_backend.ChatappBackendApplication `
        '--spring.profiles.active=test' "--server.port=$Port" `
        "--spring.datasource.url=$taskDatabase" '--spring.datasource.driver-class-name=org.h2.Driver' `
        '--spring.datasource.username=sa' '--spring.datasource.password=' `
        '--spring.jpa.hibernate.ddl-auto=create-drop' '--spring.flyway.enabled=false' `
        '--app.redis.enabled=false' '--app.firebase.enabled=false' '--app.notifications.strategy=log' `
        '--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration' `
        '--jwt.secret=chave_exclusiva_de_testes_minimo_32_caracteres_aqui' `
        '--file.upload-dir=./target/desktop-test-uploads' "--file.base-url=http://127.0.0.1:$Port"
    if ($LASTEXITCODE -ne 0) { throw 'A API de testes encerrou com erro.' }
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $taskSnapshot) {
        $taskResolvedSnapshot = (Resolve-Path -LiteralPath $taskSnapshot).Path
        if (!$taskResolvedSnapshot.StartsWith($taskRuntimeRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Diretorio de runtime fora da pasta de teste; limpeza cancelada.'
        }
        Remove-Item -LiteralPath $taskResolvedSnapshot -Recurse -Force
    }
}
