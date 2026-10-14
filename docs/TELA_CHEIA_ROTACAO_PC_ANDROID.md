# Tela cheia e orientação da transmissão

Atualização de 14/10/2026 para ligações individuais e canais de chamada. A opção de assistir em tela cheia usa a mesma transmissão e não cria uma captura adicional.

No **celular**, tocar na imagem mostra o botão **Assistir em tela cheia** no centro. Em tela cheia, tocar novamente revela **Sair da tela cheia** e **Preencher tela (corta bordas)**. Voltar também retorna à chamada. Os controles desaparecem após quatro segundos para liberar a imagem.

No **PC**, clicar na imagem mostra o botão de tela cheia no canto. Ele usa a tela cheia nativa do navegador. O botão **Sair da tela cheia** ou Escape retorna à chamada; a ampliação dentro da grade continua disponível pelo controle anterior. Encerrar a transmissão fecha a tela cheia automaticamente.

## Retrato e paisagem

A captura Android passa a usar as dimensões e a proporção reais da tela, com o maior lado limitado a 960 pixels e dimensões pares para o codificador. Ao entrar em um jogo ou vídeo que muda a tela para paisagem, a captura troca largura e altura. Ao voltar ao modo retrato, restaura o formato vertical. Não mantém uma captura 16:9 fixa nem encaixa uma tela horizontal dentro do quadro vertical anterior.

No Android 8–13, um observador do display acompanha as mudanças mesmo com o jogo/vídeo em primeiro plano. No Android 14+, o callback da projeção acompanha o tamanho do conteúdo capturado, inclusive uma janela de aplicativo. O mesmo ajuste vale para chamadas individuais e canais.

O espectador usa as dimensões do vídeo recebido. **Mostrar imagem inteira** conserva todo o quadro e suas proporções. Quando a tela do espectador tem outra proporção, **Preencher tela** elimina as faixas cortando as bordas; não estica o vídeo. Um vídeo exibido dentro de um aplicativo que continua em retrato permanece parte dessa tela vertical: o formato muda quando a região capturada muda de tamanho/orientação.

Referências: [tamanho e rotação na projeção Android](https://developer.android.com/media/grow/media-projection) e [redimensionamento no capturador WebRTC](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/ScreenCapturerAndroid.java).

## Verificação e reteste

Android: 42 testes aprovados, incluindo proporção real, limites do codificador e retrato ↔ paisagem; APK e lint aprovados. Desktop: build aprovado e testes no Edge confirmaram tela cheia nativa, vídeo sintético mudando de formato, preenchimento, saída da tela cheia sem parar a trilha e retorno automático quando a transmissão termina.

A regressão completa do desktop no Edge passou nos 23 cenários, incluindo chat, chamadas, áudio e os dois novos testes de tela cheia. Log local: `logs/desktop-fullscreen-regression-edge.log`. O APK atualizado foi instalado com preservação de dados e aberto no celular USB.

Reteste físico: iniciar uma ligação PC ↔ moto g(7) play, assistir em tela cheia nos dois clientes, compartilhar um jogo/vídeo horizontal no celular e confirmar que o PC recebe um quadro horizontal. Voltar ao Android em retrato e confirmar o retorno ao formato vertical. Esse teste com conteúdo real no aparelho permanece pendente; os testes automatizados não afirmam que um aplicativo específico permite capturar vídeo ou áudio protegido.
