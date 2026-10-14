[CmdletBinding()]
param([switch]$Send)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskReportPath = Join-Path $taskRoot 'docs/RELATORIO_AUTENTICACAO.md'
$taskResultsPath = Join-Path $taskRoot 'chatapp-backend/target/surefire-reports/TEST-com.chatapp.chatapp_backend.AccountRecoveryTests.xml'
if (-not (Test-Path -LiteralPath $taskResultsPath)) { throw 'Execute os testes da API antes de gerar o relatorio.' }
[xml]$taskResults = Get-Content -LiteralPath $taskResultsPath -Raw
$taskLines = @('# Relatorio de autenticacao', '', ('Gerado em: ' + [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'E. South America Standard Time').ToString('yyyy-MM-dd HH:mm:ss zzz')), '', 'Destinatario: sgfernandes12@gmail.com', '', 'Ambiente: testes automatizados com banco H2 isolado. SMTP capturado em memoria e identidade Google simulada; entrega real e login Google em dispositivo ainda nao validados.', '', 'Senhas, codigos e tokens omitidos. A senha antiga e verificada como rejeitada, e a nova como aceita.', '', '| Verificacao | Resultado |', '| --- | --- |')
$taskNames = @{
 publicApiCompletesRecoveryAndDoesNotReturnCode = 'Fluxo pelas tres rotas HTTP publicas concluido sem devolver codigo no pedido'
 expiredAuthorizationAndResendInvalidatePreviousSecrets = 'Autorizacao expirada e autorizacao anterior ao reenvio rejeitadas'
 emailCodeValidationPasswordChangeAndOldSessionRevocation = 'Email destinado a conta de teste; codigo de 4 digitos capturado; validacao concluida; senha antiga rejeitada; nova aceita; sessao antiga revogada; reutilizacao bloqueada'
 fiveFailedAttemptsPersistAndBlockCorrectCode = 'Cinco erros persistidos no banco; codigo correto bloqueado depois do limite'
 expiredCodeAndUnauthorizedResetAreRejected = 'Codigo expirado e troca sem autorizacao rejeitados'
 resendCooldownAndUnknownAccountDoNotSendMail = 'Reenvio limitado e conta inexistente sem envio'
 smtpFailureDoesNotLeaveAnActiveCode = 'Falha SMTP desfaz codigo no banco'
 googleLinksExistingAccountWithoutChangingPassword = 'Google vincula conta existente e preserva senha'
 thirdPartyGoogleEmailNeedsCurrentPasswordBeforeLinking = 'Email externo exige senha antes de vincular ao Google'
 googleCreatesAccountAndRejectsInvalidTokens = 'Google cria conta nova; token invalido rejeitado'
}
foreach ($taskCase in $taskResults.testsuite.testcase) {
    $taskLabel = $taskNames[$taskCase.name]
    if (-not $taskLabel) { $taskLabel = $taskCase.name }
    $taskStatus = if ($taskCase.failure -or $taskCase.error) { 'FALHOU' } elseif ($taskCase.skipped) { 'IGNORADO' } else { 'PASSOU' }
    $taskLines += '| ' + $taskLabel + ' | ' + $taskStatus + ' |'
}
$taskLines += @('', ('Total desta suite: ' + $taskResults.testsuite.tests + '; falhas: ' + $taskResults.testsuite.failures + '; erros: ' + $taskResults.testsuite.errors), '', 'O aplicativo deve ser testado manualmente com SMTP e OAuth configurados para confirmar entrega na caixa de entrada e seletor de conta Google.')
$taskLines += @('', 'Verificacoes adicionais GIS e token Google:', '', '| Verificacao | Resultado |', '| --- | --- |')
$taskGoogleNames = @{
 matchingJsonTokensAllowGoogleVerification = 'GIS JSON: cookie e corpo iguais liberam verificacao Google'
 missingMismatchedEmptyAndDuplicateCookiesNeverVerifyGoogleToken = 'CSRF ausente, divergente, vazio ou cookie duplicado rejeitado antes da identidade'
 formRequiresExactlyOneBodyTokenAndRejectsQueryOnlyToken = 'GIS formulario: token unico no corpo; query string nao substitui corpo'
 validCsrfStillRejectsInvalidGoogleToken = 'CSRF valido nao libera token Google invalido'
 nativeJsonRemainsCompatibleAndRejectsBrowserForm = 'Android JSON preservado; formulario rejeitado na rota nativa'
 acceptsBothGoogleIssuersAndUsesStableSubject = 'RSA local: aceita os dois emissores Google e identifica pelo sub'
 rejectsWrongSignatureAudienceIssuerAndExpiredToken = 'RSA local: assinatura, audiencia, emissor incorretos e expiracao rejeitados'
 workspaceRequiresVerifiedEmailAndNonemptyHostedDomain = 'Workspace: email verificado e hd nao vazio; email externo sem autoridade'
 rejectsMissingSubjectAndMalformedTokens = 'Rejeita sub ausente e tokens malformados'
}
foreach ($taskSuiteName in @('GoogleGisCsrfTests','GoogleTokenValidationTests')) {
    $taskPath = Join-Path $taskRoot ('chatapp-backend/target/surefire-reports/TEST-com.chatapp.chatapp_backend.' + $taskSuiteName + '.xml')
    if (-not (Test-Path -LiteralPath $taskPath)) { continue }
    [xml]$taskGoogle = Get-Content -LiteralPath $taskPath -Raw
    foreach ($taskCase in $taskGoogle.testsuite.testcase) {
        $taskLabel = $taskGoogleNames[$taskCase.name]
        if (-not $taskLabel) { $taskLabel = $taskCase.name }
        $taskStatus = if ($taskCase.failure -or $taskCase.error) { 'FALHOU' } elseif ($taskCase.skipped) { 'IGNORADO' } else { 'PASSOU' }
        $taskLines += '| ' + $taskLabel + ' | ' + $taskStatus + ' |'
    }
}
$taskTotal = 0; $taskFailures = 0; $taskErrors = 0; $taskSkipped = 0
foreach ($taskFile in Get-ChildItem -LiteralPath (Join-Path $taskRoot 'chatapp-backend/target/surefire-reports') -Filter 'TEST-*.xml') {
    [xml]$taskSuite = Get-Content -LiteralPath $taskFile.FullName -Raw
    $taskTotal += [int]$taskSuite.testsuite.tests; $taskFailures += [int]$taskSuite.testsuite.failures
    $taskErrors += [int]$taskSuite.testsuite.errors; $taskSkipped += [int]$taskSuite.testsuite.skipped
}
$taskLines += @('', ('Suite API: ' + $taskTotal + ' testes; ' + $taskFailures + ' falhas; ' + $taskErrors + ' erros; ' + $taskSkipped + ' ignorados.'))
$taskAndroidPath = Join-Path $taskRoot 'samuschat-android/app/build/test-results/testDebugUnitTest'
if (Test-Path -LiteralPath $taskAndroidPath) {
    $taskAndroidTotal = 0; $taskAndroidFailures = 0; $taskAndroidErrors = 0
    foreach ($taskFile in Get-ChildItem -LiteralPath $taskAndroidPath -Filter 'TEST-*.xml') {
        [xml]$taskSuite = Get-Content -LiteralPath $taskFile.FullName -Raw
        $taskAndroidTotal += [int]$taskSuite.testsuite.tests; $taskAndroidFailures += [int]$taskSuite.testsuite.failures; $taskAndroidErrors += [int]$taskSuite.testsuite.errors
    }
    $taskLines += ('Suite Android: ' + $taskAndroidTotal + ' testes; ' + $taskAndroidFailures + ' falhas; ' + $taskAndroidErrors + ' erros.')
}
$taskLines += @('', 'Estes resultados correspondem aos arquivos JUnit locais. Envio real do relatorio pendente ate configurar SMTP; o script informa aceite SMTP somente depois de Send concluir.')
$taskBody = $taskLines -join [Environment]::NewLine
Set-Content -LiteralPath $taskReportPath -Value $taskBody -Encoding UTF8
Write-Host ('Relatorio salvo em ' + $taskReportPath)
if (-not $Send) { return }
# Read only SMTP keys; process environment takes precedence. Never print secrets.
$taskSettings = @{}
$taskAllowed = @('SMTP_HOST','SMTP_PORT','SMTP_USERNAME','SMTP_PASSWORD','SMTP_AUTH','SMTP_STARTTLS','AUTH_MAIL_FROM')
$taskEnvPath = Join-Path $taskRoot '.env'
if (Test-Path -LiteralPath $taskEnvPath) {
    foreach ($taskLine in Get-Content -LiteralPath $taskEnvPath) {
        if ($taskLine -match '^\s*([A-Z_]+)=(.*)$' -and $Matches[1] -in $taskAllowed) {
            $taskSettings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
}
foreach ($taskKey in $taskAllowed) {
    $taskValue = [Environment]::GetEnvironmentVariable($taskKey)
    if ($null -ne $taskValue) { $taskSettings[$taskKey] = $taskValue }
}
if (-not $taskSettings['SMTP_HOST'] -or -not $taskSettings['AUTH_MAIL_FROM']) { throw 'Relatorio salvo; envio pendente: configure SMTP_HOST e AUTH_MAIL_FROM em .env.' }
$taskPort = if ($taskSettings['SMTP_PORT']) { [int]$taskSettings['SMTP_PORT'] } else { 587 }
$taskClient = New-Object System.Net.Mail.SmtpClient($taskSettings['SMTP_HOST'], $taskPort)
$taskClient.Timeout = 10000
$taskClient.EnableSsl = $taskSettings['SMTP_STARTTLS'] -ne 'false'
if ($taskSettings['SMTP_AUTH'] -ne 'false') {
    if (-not $taskSettings['SMTP_USERNAME'] -or -not $taskSettings['SMTP_PASSWORD']) { throw 'Configure credenciais SMTP em .env.' }
    $taskClient.Credentials = New-Object System.Net.NetworkCredential($taskSettings['SMTP_USERNAME'], $taskSettings['SMTP_PASSWORD'])
}
$taskMessage = New-Object System.Net.Mail.MailMessage($taskSettings['AUTH_MAIL_FROM'], 'sgfernandes12@gmail.com', 'SamusChat - relatorio de testes de autenticacao', $taskBody)
try { $taskClient.Send($taskMessage); Write-Host 'Relatorio aceito pelo servidor SMTP para sgfernandes12@gmail.com. Confirme recebimento na caixa de entrada.' }
finally { $taskMessage.Dispose(); $taskClient.Dispose() }
