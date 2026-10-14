# Regressão da API, Android e desktop — 06/10/2026

Código validado: commit `0462c3d`, branch `feature/call-channels`, após a inclusão da prévia local de compartilhamento de tela. Esta rodada não alterou o código do produto.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Backend `verify`, perfil `test`, Redis real habilitado | 110 testes; zero falhas, erros ou ignorados; JAR e relatório JaCoCo gerados |
| Android `testDebugUnitTest`, execução forçada | 24 testes; zero falhas, erros ou ignorados |
| Android `assembleDebug` | APK debug gerado com API `http://10.0.2.2:8080/` e relay não forçado |
| Android `lintDebug` | Zero erros e 14 avisos |
| Front desktop `npm run build` | TypeScript e build Vite aprovados |
| Front desktop no Edge, via Playwright | 11 verificações aprovadas; sem erros JavaScript ou requisições à API |

Os resultados foram conferidos também nos XMLs de Surefire e dos testes Android. Nenhum teste da suíte backend foi ignorado; a integração Redis foi efetivamente executada.

## Escopo da API

A suíte cobre autenticação e JWT, recuperação de senha, validação de login Google com verificadores de teste, segurança dos endpoints, servidores, mensagens em canais, mensagens privadas, dispositivos, chamadas individuais, configuração ICE e permissões de salas.

As verificações de sala incluem malha de sessões para três membros, isolamento de SDP, capacidade, presença expirada, saída idempotente e restrições entre chamadas individuais e salas. A regressão REST/WebSocket e a distribuição Redis também passaram. Esses testes verificam API, sinalização e regras de acesso; não renderizam vídeo nem comprovam a mídia WebRTC nos aparelhos.

Os contextos Spring usam H2 de teste; não usam o PostgreSQL de desenvolvimento. O teste Redis usa o Redis local e seus próprios eventos de regressão. Não foi executado o smoke test da infraestrutura completa de produção com três instâncias e Nginx.

## Escopo da regressão Android

Foram repetidos os testes de contratos da API, regras de chamada, composição de SDP, navegação e busca. As tarefas foram executadas com `--rerun-tasks`, sem aceitar apenas resultados antigos em cache. Build e análise estática também passaram.

Esta rodada não executou o roteiro de toques em emulador, chamadas entre aparelhos ou captura/renderização da prévia. A [validação de mídia da alteração](PREVIA_COMPARTILHAMENTO.md#validação) continua pendente após as falhas anteriores de emuladores/ADB. Os testes unitários aprovados não encerram essa pendência.

## Regressão do front para PC

Em uma sessão isolada do Edge, com viewport inicial de 1440 × 900 e depois de 520 × 900, foram verificados:

1. Servidor inicial e histórico de demonstração.
2. Busca e estado sem resultados.
3. Troca de servidores e canais.
4. Bloqueio de envio vazio.
5. Envio local e isolamento do histórico por conversa.
6. Conversa privada, Enter e mensagem com quebra de linha.
7. Edição do perfil e autor das mensagens seguintes.
8. Cancelamento do perfil com Escape.
9. Ausência de overflow horizontal na largura reduzida.
10. Restauração dos dados fictícios após recarregar.
11. Ausência de erros JavaScript e de requisições ao backend.

O roteiro inicial precisou corrigir um seletor: na largura reduzida, o status do perfil fica oculto e deixa de integrar o nome acessível do botão. A execução final passou após esse ajuste no roteiro, sem mudança na interface.

## Reproduzir as suítes

Na pasta `chatapp-backend`, com Redis local acessível:

```powershell
$env:RUN_REDIS_TESTS = 'true'
.\mvnw.cmd verify '-Dspring.profiles.active=test'
```

Na pasta `samuschat-android`, configure `ANDROID_HOME` para o SDK e execute:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks
```

Na pasta `samuschat-desktop`:

```powershell
npm ci
npm run build
npm run dev
```

Os 11 cenários acima também servem como roteiro manual de revisão. A verificação automática no Edge usou ferramentas temporárias da sessão; elas não foram adicionadas como dependências do front.

## Evidências locais

- `chatapp-backend/target/regressao-api-atual.log` e `target/surefire-reports/`.
- `chatapp-backend/target/site/jacoco/`.
- `samuschat-android/app/build/regressao-android-atual.log`.
- `samuschat-android/app/build/test-results/testDebugUnitTest/`.
- `samuschat-android/app/build/reports/lint-results-debug.txt`.
- `logs/desktop-regression.json`.

Logs, relatórios gerados, dependências e binários permanecem fora do Git. O commit desta rodada registra o resultado da validação e seus limites.
