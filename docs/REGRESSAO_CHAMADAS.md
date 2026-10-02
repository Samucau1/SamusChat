# Regressão de chamadas — 02/10/2026

Validação local no Windows, JDK 21 e dois emuladores Android API 35. A versão conserva o mute com `JavaAudioDeviceModule.setMicrophoneMute`: o capturador continua funcionando e o módulo substitui as amostras do microfone por silêncio. O teste agora exige os mesmos processos Android depois de 25 ciclos de mute/unmute e salva capturas da ligação e da transmissão recebida.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Backend `verify`, com `RUN_REDIS_TESTS=true` | 78 testes; zero falhas, erros ou ignorados; JAR gerado |
| Android `testDebugUnitTest --rerun-tasks` | 20 testes executados novamente; zero falhas, erros ou ignorados |
| Android `assembleDebug` e `lintDebug` | APK gerado; zero erros e 10 avisos de lint |
| Chamada entre dois clientes | Convite, aceite, conexão e TURN relay nos dois lados |
| Mute/unmute | 50 toques; chamada preservada e processos sem reinício |
| Transmissão da tela | 111 quadros decodificados na última amostra dos logs de mídia |
| Áudio do dispositivo | 650 pacotes e 184.942 amostras não nulas na primeira medição |
| Aplicativo que bloqueia captura | Pacotes continuam chegando sem novas amostras não nulas após estabilização |
| Ciclo de mídia e encerramento | Parar/reiniciar tela, encerrar remotamente durante captura, convite inverso recusado e serviço liberado nos dois lados |
| Navegação existente | Amigos, conversa de demonstração, Perfil e servidores; 50 ciclos/200 toques em 60,4 segundos, mesmo processo e interface responsiva |

As capturas reais estão no [guia de chamadas](CHAMADAS_INDIVIDUAIS.md#capturas-da-versão-atual). Foram usadas contas fictícias e o banco separado `samuschat_calls_test`.

## Reprodução e evidências

Os comandos e pré-requisitos estão no [guia de chamadas](CHAMADAS_INDIVIDUAIS.md#reproduzir-testes). Na execução desta regressão, os testes Android foram forçados com `--rerun-tasks` para gerar resultados novos. O script `scripts/test-calls-emulator.ps1` foi executado com `-Prepare -ResetDisposableEmulators` apenas nos dois emuladores descartáveis.

Relatórios completos permanecem fora do Git:

- `chatapp-backend/target/surefire-reports/` e `target/regression-2026-10-02.log`;
- `samuschat-android/app/build/test-results/testDebugUnitTest/`;
- `samuschat-android/app/build/reports/lint-results-debug.txt`;
- `samuschat-android/app/build/call-e2e/result.json`, logs de mídia e capturas originais;
- `samuschat-android/app/build/navigation-regression/result.json`.

A primeira inicialização simultânea disputou o arquivo de boot do emulador. Uma primeira execução do teste de chamada foi interrompida pela indisponibilidade do servidor ADB enquanto o Gradle compilava. A execução completa seguinte, com compilação e mídia em sequência, terminou com `passed=true`. Esses problemas de infraestrutura não foram considerados resultados aprovados.

## Limites

A regressão em emuladores não substitui testes de qualidade acústica, eco, Bluetooth, mudança de rede, operadoras diferentes ou chamadas longas em aparelhos físicos. O indicador de uso do microfone pode continuar ativo durante mute, pois a captura permanece funcionando. A superfície colorida no print vem do aplicativo de teste; os logs confirmam quadros decodificados e amostras recebidas.
