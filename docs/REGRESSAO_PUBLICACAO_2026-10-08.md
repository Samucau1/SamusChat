# Regressão antes do commit e push — 08/10/2026

Branch: `feature/call-channels`. A versão reúne o pacote Android de validação, o roteiro ampliado de mídia, os relatórios, o estudo de hospedagem e o [documento Word com imagens](SamusChat_Relatorio_2026-10-08.docx).

## Resultados desta execução

| Verificação | Resultado |
| --- | --- |
| Backend `verify`, perfil `test`, integração Redis habilitada | 112 testes; zero falhas, erros ou ignorados; JAR e JaCoCo gerados |
| Android `testDebugUnitTest`, execução forçada | 30 testes; zero falhas, erros ou ignorados |
| Android `testValidationUnitTest`, execução forçada | 30 testes; zero falhas, erros ou ignorados |
| Android `assembleDebug` e `assembleValidation` | Dois APKs gerados; pacote de validação separado |
| Android `lintDebug` e `lintValidation`, execução forçada | Zero erros; 16 avisos e 1 informação por variante |
| Postman / Newman | 34 requisições e 73 verificações; zero falhas |
| Desktop `npm run build` | TypeScript e Vite aprovados |
| Inicializador | Probes Docker/Android e isolamento da preferência de erro aprovados |
| Roteiro de mídia e exportador Word | Sintaxe PowerShell aprovada |
| Documento Word | Estrutura ZIP/XML válida e três imagens PNG incorporadas |

Contagens conferidas nos XMLs Surefire, testes Android, lint e no JSON Newman. Os avisos de lint não impediram o build; não se afirma ausência de avisos.

A conferência estrutural do DOCX passou. A automação local do Microsoft Word não concluiu a abertura e foi encerrada; não foi possível conferir a paginação renderizada ou produzir a prévia PDF. Essa limitação não altera os resultados dos testes do aplicativo.

A [suíte de mídia aprovada hoje às 00:09:10](REGRESSAO_MIDIA_ESTABILIDADE_2026-10-08.md) continua como evidência dos 19 cenários em dois emuladores, incluindo vídeo, áudio interno e estabilidade. Esta regressão de publicação complementa aquela execução; não repetiu a mídia nem os toques de navegação no desktop. Testes em celulares físicos e troca Wi-Fi/dados móveis continuam pendentes.

## Isolamento e configuração

Backend e Postman usam H2 em memória; não alteram o PostgreSQL de desenvolvimento. A integração Redis usa seus eventos de teste. A API Postman descartável na porta 18081 foi encerrada após a coleção. O aplicativo pessoal, seus dados e o emulador existente foram preservados.

Os APKs desta regressão usam os padrões de desenvolvimento: API `http://10.0.2.2:8080/`, sem relay obrigatório. Para reproduzir a mídia com API isolada, usar as propriedades e os encaminhamentos descritos no relatório específico. Release não recebe a permissão de HTTP da variante de validação.

## Reprodução

Na pasta `chatapp-backend`:

```powershell
$env:RUN_REDIS_TESTS = 'true'
.\mvnw.cmd verify '-Dspring.profiles.active=test'
```

Na pasta `samuschat-android`, com Java 21 e `ANDROID_HOME` configurados:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testValidationUnitTest `
  :app:assembleDebug :app:assembleValidation :app:lintDebug :app:lintValidation `
  --rerun-tasks --no-daemon '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
```

Executar a coleção conforme [Postman](postman/README.md). Na pasta `samuschat-desktop`, executar `npm run build`. Na raiz, executar `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-launcher-probes.ps1`.

O exportador `scripts/export-daily-report.ps1` recebe um JSON com `passed`, `completedAt` e `checks` (objetos com `name` e `result`) pelo parâmetro `ResultsPath`. Usa as três capturas versionadas e gera o DOCX sem dependências externas; o resultado desta execução fica localmente em `logs/publication-regression-2026-10-08.json`.

Logs, relatórios de ferramentas, APKs, SDK, dados e credenciais permanecem fora do Git. O documento Word e suas capturas integram o commit.
