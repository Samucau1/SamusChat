# SamusChat Desktop

Para abrir tudo automaticamente, dê dois cliques em **`iniciar-samuschat-pc.cmd`**, na raiz, ou execute `./iniciar-samuschat-pc.ps1`. O launcher reutiliza a API já iniciada pelo celular, prepara o que falta e abre o navegador. Veja [uso e diagnóstico](../docs/LAUNCHER_PC.md).

MVP para computador em TypeScript, React e CSS, organizado em MVC e integrado à mesma API Spring Boot usada pelo Android. A versão atual roda no navegador; o instalador desktop continua como etapa futura.

Inicie a API na raiz com `./setup.ps1`. Em outro terminal, nesta pasta:

```powershell
npm ci
npm run dev
```

Abra `http://127.0.0.1:5173` e entre com a mesma conta do celular. O Vite encaminha `/api`, `/ws` e `/uploads` para `http://127.0.0.1:8080`. Para outra API, configure `SAMUSCHAT_API_URL` antes de `npm run dev`.

`npm run build` verifica os tipos e gera `dist/`. `npm run preview` serve o build localmente e reutiliza o proxy configurado no Vite. Em produção, o servidor de hospedagem deve oferecer HTTPS/WSS e proxy na mesma origem.

Testes reais de navegador: inicie `./scripts/start-desktop-test-api.ps1` na raiz e execute `npm run test:e2e` nesta pasta. Os testes usam somente o Microsoft Edge já instalado no computador, sem baixar Chrome/Chromium de testes. O ambiente de testes usa H2 descartável na porta 18082.

Veja o [plano, tecnologias, entradas e arquitetura](../docs/DESKTOP_MVC.md).
Veja também a [seleção de microfone e áudio de transmissão entre PC e celular](../docs/REGRESSAO_AUDIO_PC_CELULAR_2026-10-14.md).
Veja [tela cheia e rotação da transmissão](../docs/TELA_CHEIA_ROTACAO_PC_ANDROID.md): controles sobre a imagem, espectador nativo em tela cheia e adaptação entre retrato e paisagem.
Veja também [funcionalidades, limites e testes da integração](../docs/DESKTOP_API.md).

Veja [personalização compartilhada com Android e canais de chamada](../docs/PERSONALIZACAO_PC_ANDROID.md). A regressão inclui testes dos contratos REST, perfil/GIF, servidor, membros, salas e rascunhos; todos usam contas fictícias em H2 ou respostas simuladas.

A transmissão permite escolher de 750p a 2160p e 30 ou 60 fps, com ajuste por estatísticas nativas sem descer abaixo de 750p/30 fps. Veja [configuração, feedback e limites de hardware](../docs/TRANSMISSAO_PC_HARDWARE.md) e [regressão](../docs/REGRESSAO_TRANSMISSAO_PC_HARDWARE_2026-10-12.md).
