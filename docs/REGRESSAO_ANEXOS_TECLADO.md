# Regressão de anexos e teclado — 06/10/2026

Código validado: `462c1e6`, na branch `feature/call-channels`, incluindo o envio de anexos de `af3deeb`. Rodada repetida antes do push para `Samucau1/SamusChat`.

| Verificação | Resultado |
| --- | --- |
| API, perfil `test`, integração Redis real habilitada | 112 testes aprovados; zero falhas, erros ou ignorados |
| Android, testes executados com `--rerun-tasks` | 30 testes aprovados; zero falhas, erros ou ignorados |
| Android `assembleDebug` | APK gerado |
| Android `lintDebug` | Zero erros e 16 avisos |

Os testes da API usam H2 de teste e o Redis local; não usam o PostgreSQL de desenvolvimento. A suíte cobre os novos formatos de anexos, histórico, mensagens, autenticação, permissões, chamadas e distribuição de mensagens. Os testes Android incluem o contrato multipart, formatos permitidos e limite real dos bytes do arquivo.

```powershell
# Na pasta chatapp-backend
$env:RUN_REDIS_TESTS='true'
.\mvnw.cmd test '-Dspring.profiles.active=test'

# Na pasta samuschat-android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks
```

A rodada anterior no emulador confirmou que o toque fora fecha o teclado, o toque dentro mantém a edição, o rascunho permanece e o envio local na conversa de demonstração funciona. Esta rodada antes do push repetiu os testes automatizados, build e lint; não repetiu as interações no emulador. Reprodução de vídeos em aplicativos externos, upload entre dois dispositivos e mídia de chamadas entre aparelhos continuam exigindo validação específica.
