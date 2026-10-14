# Regressão PC e Android — 12/10/2026

Entrega: personalização de perfil e servidor compartilhada pela API, contratos REST desktop, gestão de membros, exclusão de mensagens, canais de chamada e transmissão econômica. Inclui a melhoria de rascunhos desenvolvida nesta sessão. Branch: `feature/call-channels`.

## Verificações da publicação

| Verificação | Resultado |
| --- | --- |
| Maven verify, H2 e integração Redis real | 116 testes; zero falhas, erros ou ignorados; JAR e JaCoCo gerados |
| Android debug e validation | 34 testes por variante; zero falhas, erros ou ignorados |
| Android assembleDebug | APK gerado |
| Android lintDebug | Zero erros; 16 avisos e uma informação |
| Desktop TypeScript e build Vite | Aprovados |
| Desktop Playwright/Chromium | 12 cenários aprovados em 35,4 segundos |
| Postman/Newman — ambiente PC | 108 requisições e 206 verificações; zero falhas |
| Postman/Newman — ambiente Android | 108 requisições e 206 verificações; zero falhas |

Contagens backend/Android conferidas nos XMLs Surefire/JUnit; Postman nos JSONs Newman. A integração Redis usa container descartável exclusivo, exposto apenas em `127.0.0.1:16379`, sem alterar o Redis do aplicativo. API de navegador/Postman usa H2 na porta 18084, uploads em `target` e integrações externas desabilitadas. Dados pessoais e a API normal da porta 8080 não são usados pela suíte.

## Cobertura e correções

API: persistência e leitura da aparência, nomes aparados/acentuados, edição idempotente, limites de bio/cor, upload validado pelo conteúdo, redimensionamento, JPEG para fotos opacas, GIF e versão estática, substituição/remoção, rejeição de GIF com canvas enorme, slots inválidos, autenticação e permissão de administrador. Migração Flyway V6 validada pelo Hibernate. Exclusão rejeita IDs de mensagem de outro canal e publica o evento `DELETE` após o commit.

Desktop: cadastro, sessão, perfil, logout, servidor/canais, STOMP e anexo, mensagens privadas e falhas, paginação, chamadas individuais e em canal, microfone, tela sintética recebida/decodificada pelo segundo cliente, prévia opcional, GIF com modo estático, fundo/cor do perfil, servidor, cargos, exclusão no outro cliente, limites de captura/bitrate e rascunhos isolados. Testes de contrato conferem verbos HTTP, corpos, codificação e autorização dos adaptadores REST.

Android: contratos Retrofit de aparência e multipart, campos opcionais, limites de arquivos/cópia, nomes e tratamento de exclusão em tempo real; testes existentes de navegação/regras, anexos, chamadas, SDP e API continuam na regressão. APK compilado nas duas variantes de testes unitários.

Postman: coleção única importável, dois ambientes e pastas de paridade para PC/Android. Inclui perfil, GIF/capa/fundo, versão estática, remoção, validação, permissões, cargos e dispositivos, além dos fluxos anteriores de chat, anexos e sinalização de chamadas.

Falhas intermediárias corrigidas: expectativa antiga da versão Flyway; cliente simulado consumindo o limite de requisições; uso de `URL` indisponível no sandbox Newman; textos e rótulos com codificação incorreta durante a escrita via PowerShell; ausência de publicação de exclusão remota; recompilação Maven invalidando classes de uma API já iniciada. O launcher agora copia classes e recursos para um runtime exclusivo por execução, removido ao encerrar. A suíte final utilizou esse runtime estável. Somente as rodadas completas aprovadas contam como validação da entrega.

## Reprodução

Iniciar Redis descartável para o teste de integração e executar, na pasta backend:

```powershell
$env:RUN_REDIS_TESTS = 'true'
$env:REDIS_TEST_PORT = '16379'
./mvnw.cmd verify '-Dspring.profiles.active=test'
```

Na raiz, iniciar `./scripts/start-desktop-test-api.ps1 -Port 18084`. Na pasta desktop:

```powershell
$env:SAMUSCHAT_TEST_API_URL = 'http://127.0.0.1:18084'
npm run build
npm run test:e2e
```

Na pasta Android, com `ANDROID_HOME` configurado:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:testValidationUnitTest :app:assembleDebug :app:lintDebug
```

Na raiz, repetir para PC e Android:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json `
  -e docs/postman/SamusChat-pc.postman_environment.json `
  --env-var baseUrl=http://127.0.0.1:18084 --working-dir . --bail

npx --yes newman run docs/postman/SamusChat.postman_collection.json `
  -e docs/postman/SamusChat-android.postman_environment.json `
  --env-var baseUrl=http://127.0.0.1:18084 --working-dir . --bail
```

Nesta sessão, o Newman já instalado no cache npm foi executado diretamente para evitar uma espera de resolução/download do npx. Os scripts da coleção mantêm o mesmo comportamento no Postman.

## Limites e evidências

Nenhum aparelho/emulador estava conectado ao ADB para uma nova rodada de interface Android. Microfone e tela dos testes Chromium são sintéticos. Áudio físico, seletor nativo de captura, interoperabilidade PC–celular, uso de CPU/GPU em máquina física e redes externas/TURN precisam de validação com dispositivos. Os limites de captura e codificação foram verificados; não se afirma uma porcentagem medida de economia de CPU.

OAuth Google, FCM web, SMTP, instalador e hospedagem externa não foram liberados nesta entrega. A personalização implementada está no [guia dos recursos e limites](PERSONALIZACAO_PC_ANDROID.md); não equivale a todos os recursos comerciais do Discord Nitro.

Logs locais, fora do Git: `logs/pc-android-backend-final.log`, `logs/pc-android-final-android.log`, `logs/pc-android-desktop-final.log`, `logs/pc-android-postman-pc-final.log`, `logs/pc-android-postman-android-final.log` e seus JSONs Newman. Capturas/traces ficam em `samuschat-desktop/test-results`; testes/APK/lint em `samuschat-android/app/build`. Logs, tokens fictícios e binários gerados não integram o commit.
