# Regressão da transmissão do PC por hardware

Data: 12/10/2026. Alteração exclusiva do desktop web. API e Android permanecem nos contratos e parâmetros da entrega anterior. Comportamento e limitações: [transmissão do PC](TRANSMISSAO_PC_HARDWARE.md).

## Resultados

| Verificação | Resultado |
| --- | --- |
| `npm run build` | TypeScript e Vite aprovados; 44 módulos, bundle JS de 93,45 kB gzip |
| `npm run test:e2e` | 15 testes aprovados em 39,9 segundos, sem falhas |
| API usada nos testes de navegador | Spring Boot isolado, H2 em memória, porta 18084 |
| Navegador | Chromium do Playwright; mídia sintética, transporte e decodificação WebRTC reais |

Logs locais: `logs/desktop-hardware-build.log` e `logs/desktop-hardware-e2e.log`. Não são necessários para executar novamente a suíte. Usar o procedimento em [DESKTOP_API.md](DESKTOP_API.md), configurando `SAMUSCHAT_TEST_API_URL` se a API descartável estiver em outra porta.

## Cobertura

- Contratos REST, autenticação, erros, sessão, personalização de perfil/GIF e servidor, membros/cargos, exclusão de mensagens, STOMP, anexos, paginação, mensagens privadas e rascunhos.
- Chamada individual: convite, aceite, mídia WebRTC, microfone, compartilhamento em 900p/30 fps e encerramento remoto.
- Canal de chamada: dois clientes, escolha de 1080p/60 fps, decodificação remota com pelo menos 1080 px de altura, controles de qualidade bloqueados durante a captura e parada funcional.
- Limitação por CPU simulada nas estatísticas: ajuste desabilitado conserva 60 fps e informa a limitação; habilitar o ajuste reduz a mesma trilha nativa para 1080p/30 fps.
- Nova transmissão em 750p/30 fps: configuração nativa e vídeo remoto verificados; preferência e ajuste automático salvos no navegador.
- Políticas: todos os perfis permitidos, cadeia de redução até 750p/30 fps, bitrate/FPS por conexão, contadores reiniciados, ausência de quadros em tela estática e rejeição de configurações abaixo do mínimo.

Um erro encontrado durante a regressão mantinha o botão de parada desabilitado no canal de chamada após iniciar a captura. A limpeza do estado de início foi corrigida. O cenário passou isoladamente e na suíte completa posterior.

## Limites da validação

A fonte de tela é sintética. A alteração de FPS/resolução usa `MediaStreamTrack.applyConstraints` real, e os clientes transmitem e decodificam vídeo por WebRTC real. O sinal de CPU usado para exercitar o ajuste automático é simulado; não comprova sobrecarga ou economia de CPU/GPU no Windows. O seletor de tela física, a aceleração por GPU e o desempenho em máquinas específicas precisam de validação manual.

O mínimo corresponde às configurações aceitas pelo app. CPU/rede insuficientes podem causar descarte de quadros mesmo em 750p/30 fps. Não foi medido percentual de economia nem garantido FPS entregue constante sob qualquer carga.

Backend, Android e Postman não foram executados novamente nesta rodada, pois não houve alteração nesses componentes ou contratos. Seus resultados anteriores estão na [regressão de personalização e APIs](REGRESSAO_PERSONALIZACAO_PC_ANDROID_2026-10-12.md). A regressão desktop desta rodada inclui novamente o adaptador REST e os fluxos integrados à API descartável.
