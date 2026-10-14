# Regressao da paleta e inicializacao — 07/10/2026

Branch: `feature/call-channels`. Design aprovado pelo usuario antes da regressao.

## Alteracoes

Fundos principais e de chamadas em preto puro, paineis quase pretos e destaques azul, verde e vermelho mais fortes. Cores dos controles de chamadas compartilham a paleta do tema. Texto preto nos botoes verdes preserva contraste. Gradientes de login e servidor mantem a identidade azul em tons mais escuros.

O launcher inicia o emulador antes de preparar Docker e API, usa `-gpu auto -no-boot-anim`, registra logs e detecta encerramento do processo durante as esperas. A janela pode abrir enquanto os servicos sao preparados; o app so abre depois das verificacoes de saude e conectividade.

## Resultados desta rodada

| Verificacao | Resultado |
| --- | --- |
| Backend `verify`, perfil `test`, Redis real habilitado | 112 testes; zero falhas, erros ou ignorados |
| Android `testDebugUnitTest --rerun-tasks` | 30 testes; zero falhas, erros ou ignorados |
| Android `assembleDebug` e `lintDebug --rerun-tasks` | APK gerado; zero erros e 16 avisos de lint |
| Desktop `npm run build` | TypeScript e Vite aprovados |
| Probes do launcher | Sintaxe e cenarios Docker/Android aprovados |
| Launcher completo | Servicos e emulador reutilizados; build incremental, instalacao do APK, conectividade TCP pelo Android e abertura de `MainActivity` aprovados |
| Contraste calculado dos destaques | Branco/azul 4,59:1; branco/vermelho 4,51:1; preto/verde 9,35:1; texto/painel 17,46:1 |
| `git diff --check` | Aprovado |

Contagens conferidas nos XMLs gerados pelas suites. Os 16 avisos de lint nao impediram o build. A verificacao de contraste cobre as combinacoes principais acima, sem afirmar auditoria completa de acessibilidade.

A execucao completa do launcher usou o emulador ja ligado. Na validacao anterior desta alteracao, sua abertura com graficos automaticos confirmou WHPX, GPU NVIDIA e boot em 28 segundos. Nao foi repetido o boot com todos os servicos desligados nesta rodada.

## Escopo e evidencias

As suites backend usam H2 de teste; Redis foi exercitado pela integracao dedicada. O launcher usa o ambiente local normal, preservando dados e contas. Nesta rodada nao foram repetidos Postman/Newman, toques de navegacao ou midia WebRTC entre aparelhos. A colecao Postman aprovada na rodada anterior continua disponivel em [postman/](postman/README.md).

Logs e relatorios locais ficam fora do Git:

- `chatapp-backend/target/regression-palette-launcher.log` e `target/surefire-reports/`.
- `samuschat-android/app/build/regression-palette-launcher.log`, `test-results/testDebugUnitTest/` e `reports/lint-results-debug.txt`.
- `logs/launcher-regression-palette.log`.

Para reproduzir, use os comandos das suites em [REGRESSAO_API_DESKTOP_ANDROID.md](REGRESSAO_API_DESKTOP_ANDROID.md#reproduzir-as-suítes), o teste `scripts/test-launcher-probes.ps1` e o comando `./iniciar-samuschat.ps1` na raiz.
