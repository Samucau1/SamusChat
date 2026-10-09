[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port = 18082)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $taskRoot 'chatapp-backend')
try {
    & ./mvnw.cmd test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/desktop-test-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel preparar o classpath de teste.' }
    $taskClasspath = (Resolve-Path target/test-classes).Path + ';' + (Resolve-Path target/classes).Path + ';' + (Get-Content target/desktop-test-classpath.txt -Raw).Trim()
    $taskDatabase = 'jdbc:h2:mem:desktop_' + [Guid]::NewGuid().ToString('N') + ';MODE=PostgreSQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE'
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
} finally { Pop-Location }
