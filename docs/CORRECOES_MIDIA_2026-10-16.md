# Voz, áudio de tela e tela cheia — 16/10/2026

Relato confirmado: transmissão PC → celular mostrando a própria transmissão apresentou congelamento, tons verdes e cursor repetido. O efeito de espelhos é desejado. Também foram relatados ausência de áudio de vídeos e microfone Android inaudível no PC.

## Alterações

No PC, a voz remota usa um elemento `audio` independente, com apenas faixas de áudio. A imagem usa `video` sem áudio. Assim, um vídeo negociado sem quadros não impede a voz; iniciar/parar imagem e entrar em tela cheia não recriam o elemento de voz. Bloqueios de autoplay oferecem botão para ativar áudio.

A captura solicita áudio de sistema/janela, sem supressão de reprodução local, cancelamento de eco ou redução de ruído na transmissão. A própria aba continua permitida para preservar os espelhos. Essas opções são sugestões ao navegador: é necessário selecionar fonte compatível e autorizar compartilhar áudio no seletor. A interface informa quando a fonte não entrega áudio.

No Android, uma área de toque Compose acima da TextureView permite revelar os controles de tela cheia. O teste físico anterior não conseguia revelar esses controles pelo toque no contêiner. Logs de diagnóstico incluem codec, implementação do decoder e quadros descartados, sem gravar fala ou imagem.

Referências: [captura de tela e áudio](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia), [captura concorrente de microfone Android](https://developer.android.com/media/platform/sharing-audio-input) e [decoder padrão WebRTC Android](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/DefaultVideoDecoderFactory.java).

## Testes automatizados

O novo teste gera um tom e vídeo sem quadros. Mede a saída efetiva do elemento de áudio com `captureStream` e analisador independente, exigindo RMS maior que 0,05. Depois inicia vídeo, entra em tela cheia e para imagem, verificando continuidade do áudio. A chamada entre navegadores também exige avanço de quadros em tela cheia e predominância azul da fonte recebida.

Build TypeScript/Vite e regressão Chromium de 24 cenários aprovados. Dois testes focados no Edge instalado passaram: chamada WebRTC e voz com vídeo sem quadros/tela cheia. Logs: `logs/media-fixes-regression-final.log` e `logs/media-fixes-edge.log`.

## Teste físico PC → Moto G7 Play

O roteiro `scripts/test-pc-android-mirror.mjs` usa API H2 isolada em 18082, Vite em 5174 e APK validation com pacote separado. Cria contas sintéticas, transmite canvas recursivo por WebRTC real e abre tela cheia no aparelho USB. Preserva contas e dados do aplicativo normal. Requer APK validation compilado com `-PapiBaseUrl=http://127.0.0.1:18082/`.

Execução: `node scripts/test-pc-android-mirror.mjs SERIAL [default|VP8|H264]`.

Resultados a 1600 × 900, aproximadamente 27 fps:

| Codec | Quadros enviados | Quadros recebidos observados | Tela cheia |
| --- | ---: | --- | --- |
| VP8 | 503 | 16 → 426 | Espelhos com cores corretas |
| H.264 | 494 | 6 → 416 | Espelhos com cores corretas |

As capturas foram inspecionadas visualmente. Não houve verde nem congelamento nessas execuções. O PC recebeu 945 pacotes de áudio Android na execução H.264, com energia acumulada positiva. Isso comprova chegada de sinal, mas não substitui escuta nos alto-falantes reais. Evidências em `logs/pc-android-mirror/` e subdiretório `h264/`.

## Validação manual pendente

O teste físico usa fonte sintética e navegador isolado; não captura a janela real do Discord nem exercita sua aceleração gráfica. Portanto, o defeito verde original ainda não foi reproduzido ou declarado corrigido. Não foi imposta troca de codec sem evidência de falha.

Atualizar a página do PC e iniciar nova chamada. Confirmar voz celular → PC, compartilhar vídeo com áudio habilitado no seletor e repetir o espelhamento da transmissão real em tela cheia. Registrar em qual aparelho a tela cheia é acionada se houver nova falha. Alterações aguardam validação; sem novo commit ou push.

Android: 44 testes unitários, lintDebug e assembleDebug aprovados. APK debug instalado via USB com preservação de dados; API normal em 8080. Nenhuma conta normal foi criada ou apagada nestes testes.
