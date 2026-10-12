# Transmissão do PC: qualidade e desempenho

Atualização em 12/10/2026. Disponível em chamadas individuais e canais de chamada do PC. O Android conserva seus parâmetros anteriores.

Antes de compartilhar, escolher **750p, 900p, 1080p, 1440p ou 2160p**, com **30 ou 60 fps**. As preferências ficam salvas neste navegador. A resolução corresponde à altura do vídeo; a captura conserva a proporção da fonte. Fontes que não fornecem a altura e o FPS escolhidos são recusadas com aviso. Não há opção abaixo de 750p/30 fps.

## Sugestão para a máquina

O app consulta os processadores lógicos e a memória aproximada expostos pelo navegador, quando disponíveis. Também consulta `MediaCapabilities.encodingInfo` para H.264 e VP8 em 1080p/30 fps. Uma indicação de codificação suave e eficiente pode priorizar esse codec na negociação WebRTC, conservando os demais para interoperabilidade.

Máquinas com até quatro processadores lógicos ou até 4 GB informados recebem a sugestão de 750p/30 fps. Havendo codec indicado como eficiente e pelo menos oito processadores lógicos, a sugestão é 1080p/30 fps. Nos demais casos, é 900p/30 fps. A preferência já salva ou escolhida pelo usuário prevalece. Essa consulta é uma estimativa de capacidade, não um benchmark nem uma garantia de uso da GPU.

## Ajuste durante a transmissão

**Ajustar ao desempenho** começa habilitado e pode ser alterado durante a transmissão. A cada dois segundos, o app lê estatísticas nativas dos emissores WebRTC: quadros codificados, tempo de codificação, bytes enviados, dimensões, indicação de eficiência e motivo de limitação, quando expostos.

Três leituras consecutivas com limitação por CPU/rede ou tempo médio de codificação acima de 85% do orçamento de um quadro permitem uma redução. Primeiro, 60 fps passa a 30 fps; depois, a resolução desce um nível por ajuste, até 750p/30 fps. Há intervalo mínimo de dez segundos entre ajustes. Não há aumento automático nem redução abaixo desse limite. Em grupos, a limitação de qualquer destinatário pode ajustar a captura compartilhada.

O ajuste reaplica restrições à mesma trilha nativa e atualiza os emissores, sem abrir novamente o seletor nem processar pixels em JavaScript. Se a fonte rejeitar a mudança, o app tenta restaurar a configuração anterior e orienta parar e selecionar outra qualidade. Desabilitar o ajuste mantém a escolha e mostra aviso de limitação. A interface apresenta separadamente a configuração em uso e o FPS medido na saída.

## Captura e consumo

A versão atual do PC roda no navegador. Reutiliza a captura nativa `getDisplayMedia`, o encoder WebRTC e a integração com o sistema operacional oferecida pelo navegador. Não acessa diretamente contadores globais de CPU/GPU do Windows, Windows Graphics Capture ou Desktop Duplication. Acesso direto a essas APIs exige uma aplicação ou componente nativo, ainda ausente do repositório.

Existe uma captura por transmissão, compartilhada entre os pares. Cada destinatário ainda exige sua própria codificação/transmissão na arquitetura atual. A prévia local individual começa oculta; grupos não reproduzem sua própria captura. O monitoramento não cria uma segunda captura nem um laço de cópia de quadros.

| Altura | Teto por destinatário em 30 fps | Teto em 60 fps |
| --- | --- | --- |
| 750p | 2,5 Mbit/s | 5 Mbit/s |
| 900p | 4 Mbit/s | 8 Mbit/s |
| 1080p | 5,5 Mbit/s | 11 Mbit/s |
| 1440p | 9 Mbit/s | 18 Mbit/s |
| 2160p | 16 Mbit/s | 32 Mbit/s |

Os tetos não incluem áudio nem overhead de rede. São limites, não reservas de banda. A escolha de codec eficiente depende das capacidades e da negociação entre os dois navegadores; o app não força aceleração por GPU.

O seletor recebe restrições ideais; após selecionar a fonte, o app exige altura e FPS exatos via `applyConstraints`. O encoder usa `scaleResolutionDownBy: 1` e `maintain-framerate-and-resolution` para impedir a redução independente de resolução/FPS. Esse valor exige navegador compatível, como Chrome 144 ou superior. Mesmo assim, CPU/rede insuficientes podem causar descarte de quadros: configurar 30 fps não garante 30 quadros entregues a cada segundo. Tela estática com FPS baixo, sozinha, não dispara redução.

Captura exige localhost ou HTTPS. Redes externas precisam de TURN acessível. A economia real de CPU/GPU precisa ser medida no Windows com uma tela física e o hardware de destino; os testes automatizados não medem um percentual de economia.

## Referências

- [Estatísticas nativas WebRTC — W3C](https://www.w3.org/TR/webrtc-stats/)
- [Consulta de capacidade de codificação — MDN](https://developer.mozilla.org/en-US/docs/Web/API/MediaCapabilities/encodingInfo)
- [Preferência de degradação — W3C](https://www.w3.org/TR/mst-content-hint/#dom-rtcdegradationpreference-maintain-framerate-and-resolution)
- [Suporte no Chrome 144](https://developer.chrome.com/release-notes/144)
