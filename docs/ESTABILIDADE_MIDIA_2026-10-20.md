# Estabilidade de mídia PC/Android — 20/10/2026

## Retorno manual

O usuário aprovou PC → Android, pediu 24 fps no celular e depois informou que Android → PC ainda apresentava cortes de áudio e pausas de imagem. A versão não foi aprovada. Esta continuação investiga os cortes e o erro de negociação SDP; não há autorização para commit/push.

## Evidências

O observador de áudio no desktop mediu 953 trechos agendados com 67 lacunas e 19 sobreposições. A chegada à aplicação teve intervalo máximo de aproximadamente 213 ms, com apenas duas descontinuidades de sequência observadas. Não é possível atribuir todo atraso à rede: despacho de eventos e carga do emissor/receptor também influenciam.

O agendamento antigo iniciava trechos imediatamente e, quando a fila ultrapassava 100 ms, voltava o relógio para o instante atual. Isso podia criar intervalos sem som e sobrepor trechos já agendados. A detecção mede horários; não armazena nem grava o áudio.

O receptor WebRTC também registrou pausas curtas de vídeo que o teste inicial de 20 segundos não detectou. Por isso, o roteiro físico passou a acompanhar a fonte, o RTP e o elemento de vídeo por um minuto, incluindo `freezeCount`.

## Correções e limites

### Agendamento do áudio

- Reserva final de reprodução de 240 ms por canal, com horizonte máximo de 400 ms.
- Rajadas excedentes são descartadas sem voltar sobre áudio já agendado.
- Fila de decodificação Opus limitada a 12 trechos, em vez de cinco, para tolerar rajadas sem crescer indefinidamente.
- O microfone continua no RTP; a reserva se aplica ao áudio da tela no DataChannel, inclusive compatibilidade PCM.
- Não foram adicionadas bibliotecas, gravação, armazenamento de áudio ou filas ilimitadas. O custo é maior latência de áudio da tela em troca de tolerância às variações medidas.

Uma reserva intermediária de 80 ms removeu sobreposições, mas ainda registrou 31 lacunas em um minuto. Com 160 ms, uma execução posterior registrou 3.109 trechos, nenhuma lacuna ou sobreposição e intervalo máximo de chegada de aproximadamente 241 ms. Houve quatro pausas de vídeo nessa execução: áudio e vídeo precisam ser julgados separadamente.

Outras execuções com 160 ms ainda registraram uma lacuna por minuto. Também foi removida uma rebufferização prematura: um trecho que chega poucos milissegundos antes do prazo não deve reiniciar a reserva. A configuração de 240/400 ms está no módulo compilado e é coberta por testes; uma tentativa anterior de alterar temporariamente um protótipo após HMR não atingiu o módulo ativo e não valida essa configuração.

### Captura do aparelho

O perfil de pouca memória agora usa até 640 × 1440, 24 fps e 2 Mbit/s. No Moto G7 Play resulta em 640 × 1344, mantendo proporção e reduzindo aproximadamente 21% dos pixels frente a 720 × 1512. O perfil dos demais aparelhos permanece em 900 × 1920, 24 fps e 3 Mbit/s. O limite é aplicado à fonte e ao emissor RTP; o WebRTC pode adaptar sob carga.

O diagnóstico nativo confirmou encoder VP8 por hardware (`OMX.qcom.video.encoder.vp8`). Nos intervalos observados, `totalEncodeTime` ficou próximo de 40 ms por quadro em 720 × 1512 e de 34 ms na captura menor. São amostras de momentos distintos, não um benchmark isolado de CPU.

Uma preferência por H.264 foi experimentada pela API de capacidades, mantendo alternativas. Ela não alterou o codec efetivamente usado nesta negociação. Esse experimento foi removido da implementação final; não foi forçado codec nem alterado SDP por substituição de texto.

Depois, a preferência passou ao offer do desktop: quando a sondagem de eficiência de hardware é inconclusiva, H.264 é priorizado na lista existente. Uma preferência explícita obtida pela sondagem continua sendo respeitada; VP8 e demais alternativas não são removidos. A negociação real então confirmou `OMX.qcom.video.encoder.avc` no Android e `ExternalDecoder (D3D11VideoDecoder)` no PC. Um teste de SDP real verifica a prioridade H.264 e a permanência de VP8.

O código do WebRTC contém um tratamento de quadros-chave periódicos no VP8 Qualcomm. Isso é uma hipótese para parte das pausas observadas, não causa única comprovada. [Implementação oficial](https://webrtc.googlesource.com/src/+/f95278f0d28616f6d5d785d3a9f0ed527bb170a9/sdk/android/api/org/webrtc/HardwareVideoEncoderFactory.java).

### Reserva do receptor de vídeo

O desktop solicita `jitterBufferTarget = 320` no receptor de vídeo, em chamada individual e sala, quando a API existe. Implementações ausentes ou que rejeitam a configuração mantêm o comportamento automático. O receptor de voz não recebe essa atribuição; ainda assim, fluxos sincronizados podem compartilhar decisões de atraso do navegador. A latência da conversa precisa de aprovação manual.

A reserva isolada com VP8 não aprovou o teste. Com H.264, o experimento de reserva eliminou novas pausas de vídeo na janela observada, mas o áudio ainda precisava da configuração compilada final. Não confundir esse experimento com validação definitiva da build.

### Negociação SDP

`applyRemoteAnswer` compartilha a operação quando a mesma resposta chega em paralelo, aceita repetição da resposta já aplicada e valida `have-local-offer` antes de tocar o SDP nativo. Respostas diferentes em estado incompatível continuam produzindo erro, sem serem ignoradas como válidas.

O tratamento de erro do polling também confere a geração da chamada, evitando que uma operação antiga encerre a nova sessão. Isso protege casos de concorrência; ainda não foi isolada a origem exata do primeiro erro `Called in wrong state: stable`.

Referência: [estados de sinalização WebRTC](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/signalingState).

## Testes

- `audio-playout.spec.ts`: variação de chegada, rajadas, ausência de sobreposição, teto de fila e retomada após esgotamento.
- `remote-answer.spec.ts`: oferta/resposta WebRTC reais; três aplicações concorrentes e uma repetição em estado estável resultam em uma única aplicação nativa; resposta sem oferta é rejeitada.
- Regressão desktop final: 20 testes de mídia/negociação aprovados e build TypeScript/Vite aprovada (`logs/stability-release-tests.log`, `logs/stability-release-desktop-build.log`). Inclui teste real da API de reserva do receptor e manutenção do comportamento de voz/implementações incompatíveis.
- Android: testes, lint e build da captura menor aprovados (`logs/stability-balanced-final-build.log`). Os testes específicos verificam 640 × 1344 no Moto, 24 fps, orçamento de pixels, memória e rotação.
- `probe-audio-timing.mjs`: coleta somente contagens, horários, descontinuidades de sequência e RMS máximo.
- O teste físico agora exige vídeo de origem reproduzindo continuamente, ausência de novas pausas curtas de vídeo e ausência de lacunas/sobreposições no áudio, além de sinal e progresso de exibição.

## Execuções inválidas e pendências

Na primeira repetição com captura menor, o YouTube pausou após cerca de 26 segundos. A fonte avançou apenas 22 segundos durante um minuto de coleta. As pausas de vídeo desse intervalo não comprovam travamento do SamusChat. O teste foi ajustado para rejeitar uma fonte pausada, em vez de aceitar apenas dez segundos de avanço.

As evidências anteriores e os resultados posteriores não devem ser somados como uma única execução. Uma passagem curta não prova ausência de falha intermitente. Repetir os dois sentidos, sala, rotação/início/parada e sessão prolongada após estabilizar o cenário. Registrar a aprovação manual antes de qualquer commit/push.

Na repetição com a fonte efetivamente contínua, ela avançou 61,03 segundos sem pausa e o receptor exibiu 1.471 quadros. O critério reforçado reprovou a execução por duas pausas curtas de vídeo; também houve uma lacuna de agendamento de áudio e nenhuma sobreposição. Evidência: `logs/stability-balanced-continuous-60s.log`. Portanto, reduzir pixels e corrigir o agendamento não bastou para aprovar a versão.

Foi iniciado experimento de `RTCRtpReceiver.jitterBufferTarget = 320` somente no receptor de vídeo da chamada de teste. Essa API influencia a reserva do navegador e pode aumentar a latência; não impõe um atraso exato. Em fluxos sincronizados, o navegador pode considerar a maior reserva para áudio e vídeo, por isso a resposta da conversa também deve ser validada. O experimento precisa de resultado antes de virar configuração do produto. [Referência oficial](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpReceiver/jitterBufferTarget).

## Resultado da configuração incorporada ao código

Depois dos experimentos, a configuração foi incorporada e a página foi recarregada para eliminar alterações temporárias e referências de módulos antigos. Em sessão limpa, o roteiro confirmou reserva de vídeo 320 no receptor de vídeo e nenhuma atribuição direta no receptor de voz.

O teste contínuo de um minuto passou: fonte avançou 61,03 segundos sem pausas, 1.461 quadros decodificados/exibidos, nenhum novo congelamento WebRTC, nenhuma lacuna ou sobreposição no áudio e RMS máximo 0,154. Evidências: `logs/stability-release-60s.log` e `logs/android-performance/playback-release-60s.json`. Isso valida a janela observada, não garante ausência de instabilidade em toda rede ou sessão prolongada.

### Correção adicional: reinício do áudio

A revisão dos ciclos identificou sequência de pacotes reiniciando em zero a cada nova captura. O receptor mantinha o último número do mesmo canal e podia descartar o áudio reiniciado até a numeração alcançá-lo.

- No desktop, `captureSequence` permanece no transporte entre capturas.
- No Android, `DeviceAudio` mantém um contador atômico compartilhado com cada novo encoder. A sequência de saída deixa de ser derivada do timestamp do codec e permanece crescente no mesmo transporte.
- Novas chamadas criam novos transportes/canais normalmente; não houve alteração do cabeçalho nem remoção da compatibilidade PCM.
- A regressão WebRTC do navegador agora para e reinicia a captura no mesmo canal e exige novos quadros decodificados em 300 ms. Essa janela detecta o descarte por sequência anterior.

Na build com essa correção, os 58 testes Android, lint, build e os 20 testes desktop passaram novamente (`logs/stability-restart-android-build.log`, `logs/stability-restart-desktop-tests.log`, `logs/stability-restart-desktop-build.log`).

O roteiro USB aqueceu o áudio por 20 segundos, acumulando mais de 500 pacotes, e passou em dois ciclos de parar/reiniciar com som decodificado. Depois do reinício, houve 189 quadros/RMS 0,0067 no primeiro ciclo e 134 quadros/RMS 0,0339 no segundo. Também confirmou 1344 × 640 em paisagem e 640 × 1344 em retrato. Evidências: `logs/stability-restart-physical.log` e `logs/android-performance/lifecycle.json`.

Uma tentativa de ciclo foi descartada porque o YouTube voltou silenciado após a navegação. O roteiro agora confere fonte reproduzindo e sem mute antes de exigir sinal no receptor. Gaps e congelamentos durante paradas/rotações intencionais não são métricas de estabilidade contínua.

Na direção PC → Android, o aparelho decodificou H.264 (`OMX.qcom.video.decoder.avc`) e áudio de tela Opus com rota 2. Ao parar/reiniciar no mesmo canal, o contador de áudio avançou de 2.900 para 3.050 quadros em aproximadamente 3,5 segundos, sem esperar a sequência reiniciada alcançar a anterior.

## Build e aprovação

APK e pacote web atuais estão preservados em `dist/celular/estabilidade-2026-10-20/`, com tamanhos e hashes SHA-256 em `build-info.json`. O manifesto registra o commit-base e alterações ainda sem commit. O APK instalado é debug/API local 8080; não é uma entrega release externa.

Em 20/10/2026, o usuario confirmou a transmissao celular -> PC a 24 fps: "Imagem e audio fluidos, atraso aceitavel". Validacao manual aprovada e commit/push autorizados para Samucau1/SamusChat. Sala e sessao prolongada permanecem como cobertura adicional pendente.
