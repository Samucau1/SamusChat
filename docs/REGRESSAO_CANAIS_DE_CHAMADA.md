# Regressão de perfis e canais de chamada — 02/10/2026

Etapa `feature/call-channels`, sobre a `main` após o PR #1. Validação local no Windows, JDK 21, Redis, PostgreSQL separado `samuschat_calls_test` e emuladores Android API 35. O APK de teste usa a API na porta 8081 e força TURN relay.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Backend `verify`, com `RUN_REDIS_TESTS=true` | 88 testes; zero falhas, erros ou ignorados; JAR gerado |
| Android `testDebugUnitTest`, `assembleDebug` e `lintDebug` | 22 testes sem falhas; APK gerado; zero erros e 10 avisos de lint |
| Permissões e persistência | Convite entre contas sem servidor em comum; autenticação, isolamento de SDP, tipos de canal, acesso de membros, capacidade e saída idempotente verificados |
| Presença da sala | Expiração de participantes ausentes e orçamento próprio para heartbeat, sem consumir o limite das mensagens |
| Chamada individual no APK final | Convite, aceite e TURN relay nos dois lados; 50 toques de mute com os mesmos processos |
| Tela e áudio na chamada individual | 149 quadros decodificados na última amostra registrada; 650 pacotes e 194.162 amostras não nulas na primeira medição do áudio do dispositivo |
| Ciclo individual | Bloqueio de captura respeitado; parar/reiniciar tela, encerramento remoto durante transmissão, convite inverso recusado e serviço liberado |
| Sala com três participantes | Três clientes conectados; duas conexões TURN por cliente e pacotes de microfone recebidos de ambos os pares |
| Mute e tela na sala | 50 toques sem reinício dos processos; uma captura enviada aos dois receptores, com 96 e 93 quadros decodificados nas últimas amostras registradas |
| Encerramento da sala | Compartilhamento interrompido, todos os participantes saíram e serviços de mídia liberados |
| Interface | Canais de chat e chamada criados pela interface; telefone no perfil e botão de câmera nas duas modalidades; largura do menu ajustada para não permanecer visível quando fechado |
| Navegação existente | Amigos, conversa demonstrativa, Perfil e servidores; 50 ciclos/200 toques em 50,5 segundos, mesmo processo e interface responsiva |

A execução completa da sala terminou com `passed=true` às 23:32:30; a chamada individual no APK final, às 23:36:26 (UTC−03:00). A recepção de microfone dos dois pares em cada cliente também foi conferida nos logs salvos da sala. Os [prints reais e o guia de uso](CANAIS_DE_CHAMADA.md#capturas-reais) utilizam somente contas fictícias.

## Reproduzir e consultar evidências

Os comandos estão no [guia dos canais](CANAIS_DE_CHAMADA.md#reproduzir-a-regressão). O teste da sala exige três AVDs descartáveis; o individual exige dois. Não limpe dados de dispositivos ou sessões pessoais para reproduzir esses scripts.

Relatórios completos ficam fora do Git:

- `chatapp-backend/target/channel-regression-final.log` e `target/surefire-reports/`;
- `samuschat-android/channel-build-final.log`, `app/build/test-results/testDebugUnitTest/` e `app/build/reports/lint-results-debug.txt`;
- `samuschat-android/app/build/call-e2e/result.json`, logs de mídia e capturas;
- `samuschat-android/app/build/room-e2e/result.json`, logs de mídia e capturas.
- `samuschat-android/channel-navigation-final.log` e `app/build/navigation-regression/result.json`.

Tentativas interrompidas pela indisponibilidade do transporte ADB ou pela queda de mídia não foram consideradas aprovadas. As execuções completas finais ocorreram depois da compilação, com áudio habilitado nos emuladores. Os scripts repetem apenas falhas de transporte anteriores à execução do comando no dispositivo e rejeitam dumps de interface indisponíveis.

## Limites da validação

Foram testados três participantes simultâneos; o limite implementado de seis foi verificado no backend, sem comprovar desempenho de mídia com seis aparelhos. As salas enviam microfone e tela; áudio interno de outros aplicativos permanece disponível nas chamadas individuais. O ícone de câmera inicia captura da tela, sem vídeo da câmera física.

Dispositivos físicos, Bluetooth, mudança de rede, desempenho e chamadas longas continuam pendentes. Os resultados locais não substituem essa rodada de testes.
