# Validacao de chamadas, transmissao e estabilidade — 07/10/2026

Objetivo: executar os tres primeiros pontos antes de corrigir problemas do produto (etapa 4) e preparar beta (etapa 5). Este relatorio distingue testes executados de cenarios preparados ou pendentes.

**Atualizacao:** apos liberar espaco, a suite completa passou em dois emuladores em 08/10/2026. Veja os [resultados atuais, metricas e capturas](REGRESSAO_MIDIA_ESTABILIDADE_2026-10-08.md). As pendencias das tabelas abaixo descrevem a tentativa inicial de 07/10, preservada como historico; a validacao fisica e de operadora continua pendente.

## Resultado real desta sessao

| Ponto | Executado | Resultado e limite |
| --- | --- | --- |
| 1. Chamadas | Postman: convite, consulta, aceite, oferta, resposta, encerramento, idempotencia e aceite proibido ao chamador; sala com dois membros | Sinalizacao HTTP aprovada. Chamada entre dois clientes Android nao executada |
| 2. Transmissao | Build do app auxiliar de tom e preparo do roteiro de captura/recepcao | Midia, previa e audio nao validados nesta rodada |
| 3. Estabilidade | Roteiro ampliado com viva-voz, segundo plano, bloqueio de tela e interrupcao abrupta do peer | Execucao interrompida no preflight de clientes; cenarios de estabilidade nao aprovados |

API isolada com H2 em memoria e integracoes externas desativadas: **34 requisicoes e 73 verificacoes Postman aprovadas**, sem falhas. Essa evidência cobre regras e sinalizacao; SDP de teste nao comprova transporte WebRTC.

O APK `validation`, com pacote `com.samuschat.validation` e nome `SamusChat Teste`, compilou e passou **30 testes unitarios**, zero falhas ou erros. Foi preparado para instalacao separada, sem substituir o pacote normal. O app auxiliar `call-test-tone` tambem compilou. Nenhum APK foi instalado em celular fisico nesta rodada.

Ao concluir, o build debug normal foi restaurado para `http://10.0.2.2:8080/`, sem relay forcado; APK e 30 testes debug aprovados. API H2 e TURN temporarios foram encerrados, o encaminhamento de teste foi removido e os AVDs descartaveis foram removidos apos confirmar seus caminhos. Os logs das tentativas foram preservados.

O usuario informou ter dois Android, mas depois esclareceu que o segundo celular nao esta disponivel agora. O ADB identificou somente o emulador pessoal `emulator-5554`, sem celular fisico autorizado.

Foram tentados dois AVDs exclusivos de teste. A inicializacao normal falhou por espaco em disco: aproximadamente 5,6 GB disponiveis contra 7,37 GB exigidos para a particao de um AVD. A tentativa de segundo cliente em modo somente leitura foi rejeitada por conflito com a instancia existente. A inicializacao com imagens base pequenas avancou, mas os clientes ficaram offline enquanto a memoria livre caiu a aproximadamente 120 MB. As tentativas nao contam como teste aprovado do aplicativo.

O roteiro terminou com `passed=false` e erro `ADB transport failed repeatedly on emulator-5556`, antes de instalar ou limpar o aplicativo. Os processos extras criados nesta sessao foram encerrados para liberar memoria; a instancia pessoal foi preservada. Nao houve limpeza de conta ou dados pessoais.

## Matriz para a proxima execucao com dois clientes

Usar duas contas ficticias e registrar modelo, Android, build, rede, hora, logs e resultado por cenario. Capturas devem mostrar apenas conteudo de teste.

| ID | Acao | Evidencia/criterio | Estado atual |
| --- | --- | --- | --- |
| C01 | A liga; B aceita | Ambos em chamada; audio recebido nos dois sentidos; log sem crash | API aprovada; midia pendente |
| C02 | B recusa; inverter chamada | Convite removido e clientes disponiveis novamente | Pendente em Android |
| C03 | Silenciar/reativar, 25 ciclos | Silencio percebido; processo preservado; chamada continua | Pendente |
| C04 | Alternar viva-voz | Saida muda corretamente; verificar manualmente no celular | Pendente |
| C05 | Encerrar em cada lado | Peer sai da chamada; servico de midia liberado | API aprovada; Android pendente |
| T01 | Compartilhar tela com consentimento | Primeiro frame local e remoto, frames recebidos e imagem visivel | Pendente |
| T02 | Capturar audio permitido | Pacotes e amostras nao nulas no receptor | Pendente |
| T03 | App auxiliar bloqueia captura | Pacotes continuam; amostras nao nulas param de crescer | Pendente |
| T04 | Parar e reiniciar transmissao | Previa removida, novo consentimento e retomada da imagem | Pendente |
| T05 | Peer encerra durante captura | Captura/servico liberados, sem crash | Pendente |
| E01 | Home por 5 s, voltar | Peer permanece conectado; mesmo processo | Pendente |
| E02 | Bloquear tela por 5 s, desbloquear | Chamada continua; confirmar audio e estado | Pendente |
| E03 | Encerrar processo do cliente de teste | Peer encerra ou informa falha dentro da janela do roteiro (90 s); sem servico preso | Pendente |
| E04 | Wi-Fi para dados e retorno | Medir interrupcao/reconexao/encerramento; sem prometer reconexao automatica | Depende de celular e acesso externo |
| E05 | Perder toda a rede por 10/40 s | Registrar comportamento nos dois lados, timeout e liberacao de recursos | Depende da montagem de rede |
| E06 | Wi-Fi e operadora em aparelhos diferentes | Chamada e transmissao por TURN publico, evidencias de rede e midia | Depende do segundo celular e servidor |

O roteiro automatizado e voltado a emuladores com textos do Android em ingles e tela sem PIN. Em aparelhos fisicos, consentimento de captura e desbloqueio devem ser acompanhados pelo usuario. Nao executar resets do pacote normal em celulares pessoais.

## Reproduzir a parte automatizada de midia

Com dois clientes prontos, API descartavel e TURN corretamente configurado, a opcao abaixo usa somente o pacote separado. Para emuladores, o TURN local pode usar `10.0.2.2`; a API do APK validation usa `127.0.0.1` por encaminhamento TCP.

```powershell
$taskAdb = '.\.android-sdk\platform-tools\adb.exe'
& $taskAdb -s SERIAL_A reverse tcp:8081 tcp:8081
& $taskAdb -s SERIAL_B reverse tcp:8081 tcp:8081
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-calls-emulator.ps1 `
  -Adb $taskAdb -Caller SERIAL_A -Callee SERIAL_B `
  -Prepare -IsolatedApp -AppPackage com.samuschat.validation
```

O roteiro exige dois clientes distintos e conectados antes de preparar contas ou instalar APKs. `-IsolatedApp` aceita apenas `com.samuschat.validation`; o preparo limpa apenas os dados desse pacote de teste. Instala tambem `com.samuschat.calltest` como fonte de midia. TURN USB em celulares ainda exige configuracao especifica; os comandos acima nao validam operadora.

## Evidencias locais

- `logs/media-study-postman.json` e `logs/media-study-postman.log`: 34 requisicoes/73 verificacoes.
- `samuschat-android/app/build/physical-validation-build.log` e `test-results/testValidationUnitTest/`: build e 30 testes.
- `samuschat-android/app/build/media-study-build.log`: APK auxiliar de midia.
- `samuschat-android/app/build/call-e2e/result.json` e `logs/media-study-e2e.log`: preflight reprovado.
- `logs/SamusChat_Midia_A.stdout.log`, `logs/SamusChat_Midia_B.stdout.log` e `logs/media-readonly.stdout.log`: tentativas de boot.

Logs, APKs, contas ficticias e AVDs nao entram no Git. O relatorio registra a evidencia, sem substituir testes fisicos por testes unitarios. O [estudo de hospedagem, testes em celulares e atualizacoes](ESTUDO_HOSPEDAGEM_CELULARES_ATUALIZACOES.md) descreve como resolver a montagem para a proxima rodada; as etapas 4 e 5 nao foram iniciadas.
