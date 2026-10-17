# Enquadramento de avatar e áudio de transmissão — 16/10/2026

## Pedidos e comparação com histórico

Relato: sem áudio ao compartilhar YouTube, jogo, aba, janela ou tela inteira no Edge; posteriormente, ausência nos dois sentidos. Foto retangular perdia o enquadramento desejado na chamada. O usuário solicitou comparação com versões anteriores, pesquisa da arquitetura do Discord e teste visível com as duas contas de desenvolvimento e um vídeo real do YouTube.

O histórico `f74f453` já reproduzia áudio de dispositivo como voz (`USAGE_VOICE_COMMUNICATION`). `53a205a` introduziu no desktop o envio/recebimento de PCM16/16 kHz em DataChannel. A implementação Android de sala aceitava apenas `call-control`, descartando `device-audio`; o controle de captura chamava apenas `rtc`, sem acionar `roomRtc`. Esses defeitos são verificáveis no código; não comprovam, sozinhos, a origem de todos os relatos anteriores.

## Referências e adaptação

- [Discord: visão técnica do Go Live](https://discord.com/blog/how-it-all-goes-live-an-overview-of-discords-streaming-technology): captura conforme a plataforma, compressão e transporte de áudio/vídeo separados, uso de codecs nativos e controle de latência.
- [Discord: conexões de voz](https://github.com/discord/discord-api-docs/blob/main/developers/topics/voice-connections.mdx): Opus e distinção entre fala e áudio da transmissão.
- [Captura no navegador](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia): o seletor e a fonte precisam entregar uma faixa de áudio; solicitar áudio não garante sua disponibilidade.
- [Android: atributos de áudio](https://developer.android.com/reference/android/media/AudioAttributes): mídia e comunicação usam políticas de volume e rota diferentes.

O SamusChat continua com sua sinalização, autenticação, limite de seis participantes e regras de qualidade existentes. O áudio de transmissão negocia Opus mono de 48 kHz e 64 kbit/s, usando WebCodecs no desktop e MediaCodec no Android. Não foi adicionada biblioteca nativa ao APK. Uma captura e um encoder são compartilhados entre os destinatários de cada emissor.

O transporte da transmissão permanece no DataChannel protegido do WebRTC, com cabeçalho versionado, sequência, pacotes de 20 ms, filas curtas e descarte de áudio atrasado. A voz continua no RTP do WebRTC. Essa é uma adaptação dos princípios de captura/compressão; não implementa a SFU nem o protocolo de mídia completo do Discord. Clientes anteriores continuam com PCM de 16 kHz quando não negociam Opus.

O Android aceita áudio nos canais de chamada, compartilha o som nos dois motores e tenta habilitar captura de dispositivo ao iniciar a tela, a partir do Android 10. A captura exclui o próprio UID para evitar recapturar a chamada. Restrições impostas pelo aplicativo de origem continuam válidas. A reprodução usa atributos de mídia e preferência por fone conectado ou alto-falante, preservando o volume do sistema.

No PC, o estado de áudio distingue conexão pendente, fonte silenciosa e envio com sinal. O microfone permanece independente da transmissão.

## Editor de foto

PC e Android permitem arrastar, ajustar zoom e posição horizontal/vertical, centralizar ou cancelar. A prévia é circular; o arquivo salvo é quadrado, com amostragem uniforme e sem esticar a imagem. O cliente prepara PNG de 512 × 512 e o servidor aplica o limite existente do avatar. GIF oferece salvar recorte estático ou usar a animação original explicitamente. A foto anterior permanece até salvar; cancelar não envia upload.

## Verificações

- Desktop: build aprovado; 28 testes Playwright aprovados, incluindo decodificação Opus com RMS maior que 0,05, 48 kHz, fila curta, parada do envio e enquadramento salvo validado por cor dos pixels.
- Android: 49 testes unitários, lint e build normal com API 8080 aprovados. Incluem limites do protocolo e geometria de recorte em retrato/paisagem/quadrado.
- Edge: captura nativa de uma aba sintética, sem dispositivo falso de tela, entregou faixa de áudio e RMS positivo. O mute padrão do navegador de testes precisa ser removido nesse roteiro.
- No aparelho, o caminho PCM anterior recebeu sinal e avançou a reprodução, mas a rota observada foi `TYPE_BUILTIN_EARPIECE`. Somente trocar o atributo para mídia manteve essa rota; foi adicionada preferência explícita de saída.
- Teste real PC → celular: Edge visível com as duas contas do banco e vídeo público do YouTube. O usuário confirmou que ouviu normalmente. O Android registrou 1.350 quadros Opus decodificados, avanço do cursor de reprodução e rota `TYPE_BUILTIN_SPEAKER` (2).
- Teste real celular → PC: YouTube aberto no Chrome do celular, com captura de tela e áudio ativa na mesma chamada. A medição no PC observou 778 blocos de áudio decodificado, a 48 kHz, com RMS máximo de 0,394. O usuário confirmou a escuta, mas relatou baixa qualidade da imagem; esse problema ficou como próxima correção. Somente níveis e contagens foram medidos, sem armazenar fala ou áudio.

Logs: `logs/opus-crop-full-regression.log`, `logs/opus-crop-normal-android.log`, `logs/edge-native-tab-audio.log`, `logs/screen-audio-route-before-stats.log`, `logs/real-audio-validation.log`. Credenciais do teste visível são fornecidas apenas em memória ao processo, sem persistência em arquivos.

## Teste visível solicitado

Após a confirmação do áudio nos dois sentidos, foi ajustada a qualidade da captura Android: o limite anterior de 960 pixels no lado maior e 15 fps passou a preservar a resolução nativa, limitado a 900 pixels no lado menor e 1920 no maior, sem ampliar a fonte, com até 30 fps. No Moto G7 Play, isso permite 720 × 1512 em vez de 456 × 960. O emissor recebe teto de 4 Mbit/s e preferência por manter a resolução; o consumo efetivo depende da cena e da rede. Os 49 testes unitários, lint e build da versão normal passaram novamente (`logs/android-native-quality.log`). O usuário confirmou que a correção funcionou e depois relatou queda de desempenho. A validação funcional não encerra a investigação de fluidez.

`scripts/open-real-audio-validation.mjs` abre Edge visível, usa as contas autorizadas, encerra suas sessões de voz anteriores, confirma a conta do celular, abre vídeo público do YouTube e compartilha somente a aba escolhida. O navegador permanece aberto para escuta. Não remove contas, mensagens ou servidores. A sessão precisa de confirmação manual antes de commit/push.

A primeira tentativa encontrou uma conta ainda ocupada em canal de voz, conforme a regra existente. A sessão anterior foi encerrada por API autenticada, sem alterar dados persistentes, liberando a chamada individual. O app YouTube do celular apresentou login Google; a verificação inversa usou a versão web no Chrome, sem autenticação Google.

## Build preservada e regressão

Snapshot local em `dist/celular/validada-2026-10-16/`: APK debug para API 8080, pacote web compilado e `build-info.json` com SHA-256, tamanhos e commit-base. Há alterações de código ainda sem commit; o commit-base sozinho não reproduz esse APK. Esses artefatos são ignorados pelo Git. APK preservado: SHA-256 `BE42BFB5088214A5BB61D37B698BBB0A2C1D3D5B5EA60D33C74669980643004E`.

Foi acrescentado `ScreenCaptureRegressionTest`: seis cenários com resultados esperados para Moto G7 Play, orientação invertida, fonte pequena, Full HD, tela alongada e 4K. Verifica resolução, rotação, ausência de ampliação e teto de pixels por segundo. Esse teto impede aumento acidental de trabalho; não comprova desempenho aceitável no aparelho.

A suíte Android passou a 55 testes, todos aprovados. Antes da publicação, foram repetidos testes, lint e build Android e build TypeScript/Vite do desktop. O APK regenerado manteve o mesmo SHA-256 da cópia preservada. Esta versão registra o funcionamento confirmado e a pendência de desempenho, sem afirmar que a lentidão foi corrigida.

A regressão de áudio do navegador verifica captura de tom, negociação Opus, decodificação a 48 kHz com sinal, filas limitadas e parada do envio. A instrumentação foi corrigida para aceitar pacotes Opus com quantidade ímpar de bytes, sem interpretá-los como PCM16. Os quatro testes passaram novamente (`logs/media-audio-regression.log`).

Para repetir: configurar `ANDROID_HOME` para `.android-sdk`, executar `samuschat-android/gradlew.bat :app:testDebugUnitTest -PapiBaseUrl=http://127.0.0.1:8080/` a partir da pasta Android; na pasta desktop, executar `npx playwright test tests/device-audio.spec.ts`. Os testes sintéticos não substituem a escuta no aparelho: repetir YouTube nos dois sentidos, chamada individual e sala, com e sem microfone, rotação e parada de compartilhamento.

## Desempenho: investigação aberta

A captura passou de 437.760 para 1.088.640 pixels por quadro e de até 15 para até 30 fps: teto de trabalho aproximadamente 4,97 vezes maior. Isso não significa que o consumo de CPU quintuplicou; depende do encoder, cena e rede. É uma hipótese concreta para a piora durante transmissão.

Na amostra atual do aparelho, `top` informou cerca de 1,7 GB de RAM utilizável, 50 MB livres e 677 MB de swap em uso. Memória livre isolada não diagnostica falta de RAM; o conjunto indica investigar pressão de memória e espera por armazenamento. O SamusChat apareceu com 57,5% de CPU na convenção por núcleo do `top` (não 57,5% dos oito núcleos). A amostra histórica de `dumpsys cpuinfo` também mostrou atividade da Play Store. O serviço térmico informou status 0, sem limitação térmica reportada. Não há comparação controlada antes/depois, portanto não foi isolada uma causa única.

Próxima validação de desempenho: comparar períodos equivalentes sem transmissão, com áudio e com tela, registrando CPU, memória, quadros e latência da interface. Avaliar perfil equilibrado/adaptação de captura antes de considerar esta build aprovada em fluidez. Não foi alterada a qualidade novamente nesta etapa.
