# Regressao da interface de chamadas e launcher — 07/10/2026

Branch: `feature/call-channels`. Rodada realizada antes do commit da interface de chamadas inspirada no Discord e da correcao das consultas de inicializacao no Windows PowerShell.

## Alteracoes organizadas

- Controles reutilizaveis em chamadas privadas e canais: microfone, saida de audio, transmissao e encerramento.
- Fundo escuro, cartoes de participantes e previa, estados de mute/viva-voz e indicacao de transmissao ativa.
- Controles privados elevados em 15% da altura disponivel e icone de celular para compartilhar tela.
- Preferencias de microfone e viva-voz selecionadas durante o toque aplicadas ao criar a conexao privada. Transmissao privada habilitada quando a midia conecta.
- Consultas do Docker e do boot Android tratam stderr esperado localmente, sem alterar a politica de erro das demais etapas.
- Colecao Postman reproduzivel e teste dos probes do launcher.

## Resultados

| Verificacao | Resultado |
| --- | --- |
| Backend `verify`, perfil `test`, `RUN_REDIS_TESTS=true` | 112 testes; zero falhas, erros ou ignorados; JAR e JaCoCo gerados |
| Android `testDebugUnitTest --rerun-tasks` | 30 testes; zero falhas, erros ou ignorados |
| Android `assembleDebug` e `lintDebug --rerun-tasks` | APK gerado; zero erros e 16 avisos de lint |
| Desktop `npm run build` | TypeScript e Vite aprovados |
| Postman via Newman, API isolada na porta 18081 | 34 requisicoes; 73 verificacoes; zero falhas |
| `scripts/test-launcher-probes.ps1` | Sintaxe, Docker indisponivel/pronto, Android offline/iniciando/pronto e isolamento de preferencia de erro aprovados |
| `git diff --check` | Aprovado |

Contagens conferidas nos XMLs das suites e no relatorio JSON do Newman. Os avisos de lint tratam dependencias, login Google, estado de colecoes, backup e construtores de views; nao houve erro de lint.

O Postman exercitou GET, POST, PUT e DELETE: autenticacao, perfil, servidores, membros, canais, envio/listagem/exclusao de mensagem, contatos/ICE, convite/aceite/oferta/resposta/encerramento privado, encerramento idempotente e entrada/consulta/saida de sala com dois membros. O chamador foi impedido de aceitar o proprio convite. [Colecao e instrucoes](postman/README.md).

Tentativas iniciais do roteiro Postman reprovaram por nomes ficticios acima do limite de 30 caracteres, rate limiting entre execucoes e uma expectativa incorreta de `data` na resposta de exclusao. O roteiro foi corrigido para validar `success` nos envelopes, reduzir nomes e renovar variaveis na primeira requisicao. A rodada final completa passou, respeitando o intervalo de autenticacao; nenhuma dessas tentativas foi contabilizada como aprovada.

## Isolamento e limites

Backend e Postman usam H2 de teste, sem gravar no PostgreSQL de desenvolvimento. A suite Redis executou sua integracao real. A API Postman usou banco em memoria e integracoes externas desativadas pelo perfil `test`.

Esta rodada nao executou os roteiros de toques nos emuladores, transmissao WebRTC entre aparelhos, qualidade acustica, Bluetooth, mudanca de rede nem inicializacao completa com Docker desligado. A aparencia da chamada privada foi confirmada pelo usuario antes desta rodada; isso nao substitui regressao automatizada de midia. A colecao testa sinalizacao, nao audio/video reais.

## Evidencias e reproducao

Logs e relatorios locais, fora do Git:

- `chatapp-backend/target/regression-2026-10-07.log`, `target/surefire-reports/` e `target/site/jacoco/`.
- `samuschat-android/app/build/regression-2026-10-07.log`, `test-results/testDebugUnitTest/` e `reports/lint-results-debug.txt`.
- `logs/postman-regression-2026-10-07.log` e `logs/postman-regression-2026-10-07.json`.

Os comandos das suites estao no [guia de regressao da API e Android](REGRESSAO_API_DESKTOP_ANDROID.md#reproduzir-as-suítes). Para testar o launcher sem abrir servicos:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-launcher-probes.ps1
```
