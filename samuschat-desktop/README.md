# SamusChat Desktop

MVP para computador em TypeScript, React e CSS, organizado em MVC e integrado à mesma API Spring Boot usada pelo Android. A versão atual roda no navegador; o instalador desktop continua como etapa futura.

Inicie a API na raiz com `./setup.ps1`. Em outro terminal, nesta pasta:

```powershell
npm ci
npm run dev
```

Abra `http://127.0.0.1:5173` e entre com a mesma conta do celular. O Vite encaminha `/api`, `/ws` e `/uploads` para `http://127.0.0.1:8080`. Para outra API, configure `SAMUSCHAT_API_URL` antes de `npm run dev`.

`npm run build` verifica os tipos e gera `dist/`. `npm run preview` serve o build localmente e reutiliza o proxy configurado no Vite. Em produção, o servidor de hospedagem deve oferecer HTTPS/WSS e proxy na mesma origem.

Testes reais de navegador: inicie `./scripts/start-desktop-test-api.ps1` na raiz, execute `npx playwright install chromium` nesta pasta e depois `npm run test:e2e`. O ambiente de testes usa H2 descartável na porta 18082.

Veja o [plano, tecnologias, entradas e arquitetura](../docs/DESKTOP_MVC.md).
Veja também [funcionalidades, limites e testes da integração](../docs/DESKTOP_API.md).
