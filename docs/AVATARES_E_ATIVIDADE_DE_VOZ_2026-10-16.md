# Fotos compartilhadas e atividade de voz — 16/10/2026

## Comportamento

Contatos e participantes de canais de chamada agora incluem `avatarUrl`. Mensagens de canal e privadas incluem `senderAvatarUrl`; membros incluem `avatarUrl`. Os identificadores públicos continuam substituindo e-mails de outras contas. A foto usa o caminho de mídia de aparência existente, sem novas permissões ou migração de banco.

PC e Android exibem a foto nos amigos, mensagens e cartões de chamada/transmissão, com iniciais como alternativa. O desktop também mostra uma miniatura no nome do participante durante a transmissão; o Android mostra identificação na tela cheia. A preferência existente de economizar animações mantém a versão estática dos GIFs.

A lista de contatos atualiza ao recuperar foco no PC e a cada 15 segundos enquanto a tela está ativa nos dois clientes. A sala recebe fotos pelo seu fluxo normal de atualização. Uma mensagem já renderizada pode exigir recarregar o histórico para atualizar sua foto histórica após uma alteração ou remoção.

Atividade de áudio destaca o participante em verde e mostra “Falando”. No PC, um analisador mede a faixa de áudio sem conectá-la aos alto-falantes. No Android, as estatísticas WebRTC fornecem o nível local e remoto. Limiar RMS de 0,02 e retenção de 400 ms evitam piscar entre sílabas. Silenciar o próprio microfone apaga seu destaque. Nenhuma gravação ou detecção de palavras é realizada; música ou ruído forte na faixa também pode ativar o indicador. Áudio de dispositivo transportado em canal separado não aciona o indicador de voz.

Referências primárias consultadas: [W3C: níveis de áudio nas estatísticas WebRTC](https://www.w3.org/TR/webrtc-stats/) e [MDN: amostras temporais do AnalyserNode](https://developer.mozilla.org/en-US/docs/Web/API/AnalyserNode/getFloatTimeDomainData).

## Verificação

- Backend: regressão de 144 testes, sem falhas; três testes condicionais de Redis/PostgreSQL ficaram desativados. Após adicionar o teste de foto entre contas, 28 testes focados passaram, incluindo envio, leitura e remoção da foto sem expor e-mail.
- Desktop: build aprovado e 25 testes Playwright aprovados. Incluem foto carregada de outra conta na lista/chamada, remoção e retorno às iniciais, voz com vídeo sem quadros, tela cheia, som e silêncio. O teste focado adicional de mute local preservando atividade remota também passou.
- Android: 45 testes unitários, lint e build debug aprovados; teste novo cobre silêncio, ataque, retenção, expiração, mute e retomada.
- O roteiro USB usa API H2, contas sintéticas e pacote validation separado, preservando o aplicativo normal e as duas contas de desenvolvimento.
- Moto G7 Play: foto opaca sintética recebida e inspecionada, indicador “Falando” acionado por áudio PC → Android e identificação em tela cheia confirmados. Transmissão H.264 a 1600 × 900, 28 fps, 522 quadros enviados e contagem recebida avançando de 0 a 384. O primeiro teste revelou botão de tela cheia fora da área visível por identificação duplicada; a duplicação foi removida e a nova execução passou. Pacote de teste removido, API normal reiniciada e APK debug atualizado preservando dados.

Logs locais: `logs/avatars-speaking-backend.log`, `logs/avatars-speaking-backend-focused.log`, `logs/avatars-speaking-desktop.log`, `logs/avatars-speaking-mute.log`, `logs/avatars-speaking-android-final.log` e `logs/avatars-speaking-physical.log`.

## Validação manual

Atualizar a página do PC e abrir nova chamada. Conferir fotos nos dois sentidos, fala alternada PC/celular, silêncio e mute, chamada individual e canal, transmissão e tela cheia. Alterar/remover foto e aguardar atualização da lista ou reabrir o histórico. Conferir GIF com economia de animações. Commit e push aguardam validação manual.
