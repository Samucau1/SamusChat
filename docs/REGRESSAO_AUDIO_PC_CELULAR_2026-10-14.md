# Áudio PC ↔ celular — 14/10/2026

O teste manual do usuário confirmou imagem nos dois sentidos e voz do microfone do celular. Não houve voz do microfone do PC nem som dos aplicativos compartilhados em qualquer direção.

## Diagnóstico

O desktop solicitava `getDisplayMedia` com `audio: false` e recusava o canal `device-audio` usado pelo Android para som dos aplicativos. O microfone do PC usava apenas a entrada padrão do Windows, sem seleção ou medidor.

Sondagem no Edge com entradas reais, sem gravar áudio: a entrada padrão **Saída do MicrofoneMic (Realtek)** estava ativa, mas retornou nível RMS máximo zero. A entrada **Grupo de microfones (Intel Smart Sound)** retornou RMS máximo de aproximadamente 0,0094. Isso confirma sinal nesta entrada durante a sondagem; não substitui ouvir uma conversa completa.

## Correção

- Desktop passa a pedir áudio da fonte compartilhada e oferece aviso quando o navegador devolve somente vídeo.
- Ligações individuais usam um canal `device-audio` nos dois sentidos, compatível com o APK Android atual. PCM16 little-endian, mono, 16 kHz, pacotes de 20 ms; filas de envio e reprodução limitadas. O microfone continua em sua trilha WebRTC própria.
- AudioWorklet converte o áudio capturado pelo PC para o formato do Android. A reprodução do Android usa Web Audio no PC, com controle para ativar o áudio quando o navegador bloquear reprodução.
- O botão **Microfone** no cabeçalho permite listar entradas, testar o nível e salvar a seleção. Durante a chamada, a troca substitui a trilha do microfone, conserva o estado de silêncio e libera a entrada anterior.
- Encerrar a chamada libera canais, captura, fontes de áudio e AudioContext. Parar a transmissão interrompe seu som, preservando o microfone.

A implementação desktop também compartilha a captura com participantes de canais. O Android mantém o áudio interno de transmissão restrito à ligação individual; o canal de chamada Android não recebeu essa funcionalidade nesta alteração.

## Reteste no aparelho

1. Encerrar a chamada anterior e atualizar o PC com Ctrl+F5.
2. Abrir **Microfone**, clicar **Testar microfone** e selecionar **Grupo de microfones (Intel Smart Sound)**. Falar e verificar o medidor.
3. Iniciar nova ligação individual; conferir voz nos dois sentidos.
4. No PC, compartilhar uma aba com vídeo/música e marcar **Compartilhar áudio** no seletor. Conferir o som no celular, parar e confirmar que a voz continua.
5. No celular, compartilhar tela, autorizar e ativar **Áudio do dispositivo**. Reproduzir conteúdo de um aplicativo que permite captura e conferir no PC. Se aparecer, clicar **Ativar áudio da transmissão**.

A disponibilidade de som na captura depende da fonte e do navegador. Uma janela pode vir sem áudio mesmo quando solicitado. Priorizar uma aba diferente da aba da chamada para evitar recapturar a voz recebida; tela inteira com som do sistema pode incluir o áudio da própria conversa. Android 10+ respeita aplicativos que bloqueiam captura de áudio ou conteúdo protegido.

## Verificação

Build TypeScript/Vite aprovado. Testes verificam PCM, rejeição de pacotes inválidos, redução de 48/44,1 kHz para 16 kHz, transporte real de tom sintético por RTCDataChannel, decodificação, limite de fila e parada. A regressão de chamada inclui troca de entrada com um tom sintético, nível no medidor, transmissão de imagem e encerramento.

Os 21 cenários foram aprovados em Chromium e Edge: em cada rodada ampla passaram 20 cenários; o cenário de chamada passou no reteste após estabilizar o tom sintético e aguardar a lista assíncrona de entradas. Não houve alteração do código de produção para contornar essas falhas de fixture. Logs da rodada Edge em `logs/desktop-audio-regression-edge.log`; o reteste da chamada foi aprovado separadamente. A API H2 e as contas fictícias ficaram isoladas na porta 18082.

O teste manual de áudio entre Windows e celular físico após a correção permanece pendente. O APK Android já possui o protocolo usado na ligação individual; não foi necessário reinstalá-lo nesta rodada.

Referências: [Screen Capture — W3C](https://www.w3.org/TR/2026/WD-screen-capture-20260716/) e [seleção de dispositivos — Chrome for Developers](https://developer.chrome.com/blog/media-devices).
