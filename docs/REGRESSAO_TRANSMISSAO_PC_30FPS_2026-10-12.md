# Transmissão PC — mínimo 750p e captura a 30 fps

Data: 12/10/2026. Ajuste solicitado depois da entrega de personalização: substituir os perfis de 540p/10 fps e 720p/15 fps por padrão PC de 900p (1600×900), com altura mínima de captura de 750 px e taxa configurada exatamente em 30 fps. Vale para chamadas individuais e canais de grupo. O Android não foi alterado nesta rodada; não recebeu modo econômico de transmissão.

O seletor de qualidade econômica foi removido do PC. Cada conexão usa até 4 Mbit/s de vídeo e escala de encoder 1, sem repartir bitrate entre participantes. A prévia local continua opcional para evitar uma reprodução adicional. `maintain-framerate-and-resolution` impede a redução automática de resolução/fps para otimizar qualidade; se o navegador não suporta essa opção, o app pede atualização. O padrão foi validado em Chromium 156; Chrome oferece essa opção a partir da versão 144.

## Verificação

| Verificação | Resultado |
| --- | --- |
| TypeScript e build Vite | Aprovados |
| Playwright/Chromium, API real H2 e cenários de contrato | 13 cenários aprovados em 32,3 segundos |
| Chamada individual | Captura sintética configurada em 30 fps; segundo cliente decodificou vídeo com altura ≥ 750 px |
| Canal de grupo | Segundo cliente decodificou vídeo com altura ≥ 750 px; transmissão, microfone e saída aprovados |
| Regras de captura | Valores ideais no seletor nativo; mínimo de 750 px e 30 fps exatos depois da escolha; configurações abaixo do mínimo rejeitadas |

A primeira tentativa encontrou API ainda iniciando. Uma rodada seguinte identificou queda automática para 225/336 px com `maintain-framerate`; a política foi substituída pela opção que mantém resolução e taxa simultaneamente. A suíte inteira foi repetida e aprovada. Não foram alterados contratos REST, backend ou Android; suas suítes da [entrega anterior](REGRESSAO_PERSONALIZACAO_PC_ANDROID_2026-10-12.md) não foram repetidas nesta rodada.

API H2 isolada na porta 18084, com runtime de classes exclusivo, contas fictícias e uploads em `target`. Não foi utilizada a API pessoal da porta 8080. Reprodução: iniciar `./scripts/start-desktop-test-api.ps1 -Port 18084`, configurar `SAMUSCHAT_TEST_API_URL=http://127.0.0.1:18084` na pasta desktop e executar `npm run build` e `npm run test:e2e`.

Logs locais: `logs/desktop-30fps-test-api.log` e `logs/desktop-30fps-e2e.log`. Os testes usam canvas de 1600×900 solicitado a 30 fps e microfone sintético. Não medem estabilidade física de 30 quadros recebidos por segundo em todas as máquinas/redes. O navegador ainda pode descartar quadros sob falta de CPU/banda, mesmo mantendo a configuração. Referências: [Chrome 144](https://developer.chrome.com/release-notes/144), [regra W3C](https://www.w3.org/TR/mst-content-hint/#dom-rtcdegradationpreference-maintain-framerate-and-resolution).
