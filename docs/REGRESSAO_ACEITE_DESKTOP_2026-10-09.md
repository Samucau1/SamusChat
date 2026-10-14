# Aceite da primeira entrega PC e regressao — 09/10/2026

O usuario validou manualmente a primeira entrega para PC: informou que conseguiu localizar e carregar servidores que ja estavam no banco por meio da pesquisa, usando a versao de navegador conectada a API local. Este relato valida o fluxo exercitado; nao declara aprovacao de todos os recursos de midia, acesso externo ou instalador.

Branch de publicacao: `feature/call-channels`. Implementacao desktop de referencia: commit `9da949c`. Esta rodada registra o aceite e amplia a colecao Postman, sem alterar os contratos da API ou a implementacao do aplicativo.

## Verificacoes antes do commit

| Verificacao | Resultado |
| --- | --- |
| Backend Maven verify, perfil test, integracao Redis habilitada | 112 testes; zero falhas, erros ou ignorados; JAR e JaCoCo gerados |
| Android testDebugUnitTest, execucao forcada | 30 testes; zero falhas, erros ou ignorados |
| Android lintDebug, execucao forcada | Zero erros; 16 avisos e uma informacao |
| Desktop TypeScript e build Vite | Aprovados |
| Desktop Playwright/Chromium | Seis cenarios aprovados em 25,1 segundos |
| API Postman/Newman | 44 requisicoes e 100 verificacoes aprovadas; zero falhas; 4,886 segundos |

Contagens conferidas nos XMLs Surefire/JUnit, no XML de lint e no JSON Newman. O tempo mede a execucao desta rodada e nao e uma meta de desempenho.

## Cobertura desktop e API

Playwright verificou cadastro, login, recarga da sessao, perfil e logout; criacao/entrada em servidor, canal de texto, recepcao STOMP sem duplicata e anexos; conversa privada entre duas contas e preservacao do rascunho em erro; paginacao de 52 mensagens; convite/aceite de chamada, microfone, transmissao de tela sintetica decodificada pelo segundo cliente e encerramento remoto; erro de login com recuperacao do formulario e largura de 1024 px.

A [colecao Postman](postman/SamusChat.postman_collection.json) mantem os cenarios REST de servidores, membros, mensagens e sinalizacao de chamadas, e acrescenta dez requisicoes desktop: envio/resposta privada, historicos dos dois lados, privado vazio rejeitado, pagina anterior, envio sem autenticacao rejeitado, upload, download com conteudo preservado e consulta ao mesmo servidor pelo segundo cliente. O [ambiente importavel](postman/SamusChat-local.postman_environment.json) contem apenas a URL da API descartavel. O arquivo de upload e ficticio.

## Isolamento e limites

Backend verify usa H2 por contexto de teste. Redis real foi utilizado para sua integracao. Postman e navegador utilizaram uma API H2 em memoria na porta 18081, iniciada pelo launcher que define explicitamente driver, banco, DDL e integracoes externas. As contas e os servidores criados nesta regressao ficaram no banco descartavel. PostgreSQL, contas pessoais, API normal na porta 8080 e frontend manual na porta 5173 foram preservados.

Os clientes simulados possuem identificadores distintos para manter os limites de requisicoes habilitados sem colisao entre suites. Duas tentativas iniciais do Newman nao contam como aprovadas: a primeira usou sintaxe de reporters inadequada ao PowerShell; a segunda encontrou o limite de autenticacao consumido por aquela tentativa. A lista de reporters foi colocada entre aspas e a colecao passou a gerar identificadores de clientes por execucao; a suite inteira foi repetida e passou.

O microfone e simulado pelo Chromium; a tela transmitida vem de um canvas sintetico. Nao foram repetidos audio fisico, seletor real de tela, interoperabilidade desktop–Android, operadoras ou login Google. Postman testa sinalizacao SDP, nao media WebRTC. Instalador e hospedagem externa continuam fora desta primeira entrega.

## Reproducao e evidencias locais

Na pasta backend, executar `mvnw.cmd verify -Dspring.profiles.active=test` com `RUN_REDIS_TESTS=true`. Para navegador/Postman, iniciar na raiz `./scripts/start-desktop-test-api.ps1 -Port 18081`.

Na pasta desktop:

```powershell
$env:SAMUSCHAT_TEST_API_URL = 'http://127.0.0.1:18081'
npm run build
npm run test:e2e
```

Na raiz:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json `
  -e docs/postman/SamusChat-local.postman_environment.json --working-dir . --bail `
  --timeout-request 15000 --reporters 'cli,json' `
  --reporter-json-export logs/pc-acceptance-newman.json
```

Logs locais, fora do Git: `logs/desktop-acceptance-backend.log`, `logs/desktop-acceptance-android.log`, `logs/pc-acceptance-desktop-build.log`, `logs/pc-acceptance-desktop-e2e.log`, `logs/pc-acceptance-newman.log` e `logs/pc-acceptance-api.log`. O JSON Newman contem dados de execucao e tokens ficticios; nao deve ser publicado. Relatorios de build, APKs e traces continuam fora do Git.
