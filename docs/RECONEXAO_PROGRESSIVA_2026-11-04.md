# Reconexão progressiva e recuperação sem consultas sobrepostas

Data: 04/11/2026.

## Diagnóstico e decisão

O desktop repetia a reconexão STOMP e a recuperação do histórico a cada três segundos. A consulta de não lidas podia ser disparada simultaneamente pelo foco, visibilidade e reconexão. No Android, a caixa privada verificava a conexão a cada três segundos e podia substituir um socket ainda em negociação; o histórico privado recebia disparos de fontes diferentes. Canais Android dependiam de reconexão manual.

Foi adotada espera exponencial com **equal jitter**, um temporizador por conexão e recuperação serial com uma única execução pendente. Consultas disparadas durante uma consulta lenta são agrupadas; não se cria uma fila ilimitada de requisições. Essa execução pendente preserva uma reconciliação solicitada enquanto a consulta anterior ainda estava em andamento.

## Política aplicada

| Etapa consecutiva | Janela de espera sorteada |
| --- | --- |
| Primeira | 1,5 a 3 segundos |
| Segunda | 3 a 6 segundos |
| Terceira | 6 a 12 segundos |
| Quarta | 12 a 24 segundos |
| Demais | 15 a 30 segundos |

Os limites superiores são exclusivos por arredondamento/sorteio. Após uma conexão STOMP bem-sucedida, a janela volta à primeira etapa. O tempo de negociação não está incluído na espera; a tentativa de conexão tem timeout de dez segundos.

- Desktop: os callbacks públicos do STOMP acionam um temporizador próprio; o retry automático fixo fica desativado. Saída da tela/sessão cancela o temporizador. O estado offline do navegador evita novas tentativas agendadas e consultas de recuperação; o retorno online agenda uma tentativa com variação aleatória.
- Desktop: histórico e não lidas usam recuperação serial, preservando os mecanismos existentes de combinação de mensagens e contadores. Consultas HTTP são canceladas ao desmontar a conversa/caixa e mantêm timeout de 30 segundos, mesmo quando há sinal externo de cancelamento.
- Contatos desktop: atualizações periódicas em 15–30 segundos após sucesso; falhas ampliam a janela até 30–60 segundos. Aba oculta/offline não inicia essa atualização periódica. Foco também passa pelo controle de concorrência.
- Android: a caixa privada e os canais mantêm uma tentativa até abrir, falhar ou esgotar dez segundos. Após falha, aguardam a janela sorteada. A conexão bem-sucedida reconcilia o histórico; o fallback de histórico privado/canais também usa espera progressiva.
- Android: `CoalescingRecovery` impede sobreposição de consultas de histórico/contadores, agrupando disparos concorrentes. Os loops de socket privados acompanham `STARTED`; canais acompanham os callbacks de início/parada da tela.
- Não há retry automático de envio de mensagens: repetir um POST sem idempotência poderia criar duplicatas.

## Validação

- Desktop: build de produção aprovado.
- Playwright: 11 testes aprovados em `recovery`, `api-contract`, `direct-inbox` e `direct-realtime`. Incluem crescimento/teto/reset da espera, falhas repetidas de socket, cancelamento de temporizador, HTTP lento com 20 eventos de foco, ausência de sobreposição, estado offline e recuperação de mensagens sem duplicatas visíveis.
- Android: 64 testes unitários aprovados, incluindo quatro testes novos de crescimento/reset, agrupamento de consultas, timeout de conexão e cancelamento. `assembleDebug` e `lintDebug` aprovados.
- APK `1.2.2-beta-local`, código `5`, instalado com `adb install -r` no Moto G7 Play. Abertura autenticada e tela de amigos conferidas, sem crash no buffer consultado. Dados e conta temporária preservados.
- APK local: `dist/celular/SamusChat-teste.apk`; SHA-256 `ED4F2BFEFADD3325A7B66D15672E420A22395148BE8548BD1F4AB0122C1437B0`.

Os cenários de falha desta etapa são automatizados; não foi feita uma campanha de perda/jitter de rede no aparelho físico nem teste de alta concorrência. O histórico ainda reconcilia pela página mais recente, sem cursor durável: lacunas maiores que a página exigem a evolução descrita no relatório de estabilidade. Polling de sinalização de chamadas e tratamento da sessão antiga com 401 ficam fora desta alteração. Este APK continua configurado para API local por USB e sem OAuth Google.

## Referências

- [STOMP.js: Client, callbacks e ciclo de ativação](https://stomp-js.github.io/api-docs/latest/classes/Client.html). A implementação também foi conferida no código da versão instalada em `node_modules`.
- [Kotlin coroutines: Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/).
- [Relatório de estabilidade e escalabilidade](RELATORIO_ESTABILIDADE_ESCALABILIDADE_2026-10-31.md).

Comandos de validação: `npm run build` no desktop; `node node_modules/@playwright/test/cli.js test tests/recovery.spec.ts tests/api-contract.spec.ts tests/direct-realtime.spec.ts tests/direct-inbox.spec.ts`; e `scripts/build-apk-celular.ps1 -ApiBaseUrl 'http://127.0.0.1:8080/' -AllowWithoutGoogle` na raiz.
