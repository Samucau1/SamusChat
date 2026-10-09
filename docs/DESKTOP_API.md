# SamusChat desktop integrado — 09/10/2026

O front desktop agora utiliza a mesma API Spring Boot, PostgreSQL, Redis, contratos REST e STOMP do Android. A interface mantém a paleta e usa barra de servidores, lista lateral e conversa em colunas. Nenhuma alteração de contrato do backend foi necessária.

## Funcionalidades

- Login/cadastro por email e senha, restauração de sessão na mesma aba, logout e edição do nome no backend.
- Recuperação de senha em três etapas pelos endpoints existentes; entrega de email depende de SMTP configurado.
- Listagem, criação e entrada em servidores por ID; criação de canais de texto e busca lateral.
- Histórico paginado, envio por Enter, quebra de linha com Shift+Enter e preservação do rascunho em falhas.
- Mensagens de canal recebidas por STOMP, reconexão automática e deduplicação por ID. Conversas privadas usam leitura REST a cada três segundos, conforme o contrato existente sem destino STOMP privado.
- Upload de anexo em canal até 10 MB e abertura de imagens/arquivos pelo proxy de uploads. O backend não oferece upload privado no contrato atual.
- Chamadas individuais: convite, aceite/recusa, microfone, encerramento e compartilhamento de tela com prévia. SDP e credenciais ICE vêm da mesma API do Android.

O token fica em `sessionStorage`, separado do app Android. A sessão persiste ao recarregar a mesma aba e é removida ao sair; não há senha armazenada. Ao receber HTTP 401 em uma sessão conectada, retorna ao login. A sessão de navegador não usa cookies de autenticação.

## Executar

Na raiz, iniciar a API com `./setup.ps1`. Em outro terminal:

```powershell
cd samuschat-desktop
npm ci
npm run dev
```

Abrir `http://127.0.0.1:5173`. Usar a conta do celular. O proxy Vite mantém API e WebSocket na mesma origem do navegador; não exige ampliar CORS. `SAMUSCHAT_API_URL` seleciona a API no servidor Vite, sem colocar segredos no bundle.

O build `npm run build` gera `dist/`. Hospedagem precisa servir esses arquivos e encaminhar `/api`, `/uploads` e `/ws` para a API, incluindo upgrade WebSocket. Para revisão local, `npm run preview` reutiliza o proxy Vite configurado; esse comando não é a hospedagem de produção. Não há instalador Electron/Tauri nesta entrega.

## Organização MVC

`models/apiRepository.ts` centraliza contratos HTTP, erros, anexos e ordenação de mensagens. `controllers/useSession.ts`, `useChatController.ts` e `useCallController.ts` mantêm o estado e os recursos de rede/mídia. `views/AuthScreen.tsx` e `ChatApp.tsx` adaptam os fluxos a mouse e teclado. O repositório fictício original permanece como referência histórica, sem entrar na composição da aplicação conectada.

## Validação

Para preparar uma API descartável, na raiz:

```powershell
./scripts/start-desktop-test-api.ps1
```

O script usa explicitamente H2 em memória, classpath de testes, uploads em `target` e integrações externas desligadas. Encerrar esse processo descarta o banco. Não usar somente `spring-boot:run` com perfil test como garantia de isolamento: os recursos de teste precisam estar no classpath e as propriedades de banco devem ser explícitas.

Em outro terminal, na pasta desktop:

```powershell
npx playwright install chromium
npm run test:e2e
```

A suíte abre clientes Chromium e exercita a API real com contas fictícias. Os seis cenários passaram em 09/10/2026, em 22,6 segundos: cadastro/sessão/perfil/logout; servidores/STOMP/anexo/canal; mensagens privadas/falha de envio; paginação; chamada/microfone/tela/encerramento; erro de login e largura de 1024 px. TypeScript e build Vite aprovados.

O microfone é simulado pelo navegador e a fonte de tela é um canvas sintético, transmitido pelo WebRTC e decodificado no segundo cliente. Isso não mede qualidade acústica nem testa o seletor nativo de compartilhamento. O cabeçalho de clientes de teste distingue as origens simuladas para não desativar os limites de requisições do backend. Logs ficam em `logs/desktop-e2e-final.log`; capturas e traces ficam fora do Git em `samuschat-desktop/test-results`.

Na primeira tentativa, o perfil test via Maven não carregou os recursos H2 e utilizou PostgreSQL local. A instância foi encerrada; os três usuários fictícios, um servidor, seu canal, duas mensagens e um arquivo criados por essa tentativa foram identificados e removidos com critérios exatos. As dez contas anteriores permaneceram no banco. A suíte aprovada foi repetida em H2; o launcher agora define explicitamente banco e integrações externas.

## Limites da entrega

Chamadas em grupo, instalador, push desktop e login Google ainda não integram este MVP. Recuperação exige SMTP disponível. Compartilhamento transmite vídeo da tela; áudio interno do dispositivo Android usa um canal de dados próprio que ainda não foi portado ao desktop. Microfone continua disponível na chamada.

Chamadas e captura de tela exigem localhost ou HTTPS e permissões do navegador. Acesso entre redes diferentes depende de TURN alcançável. Validação de áudio físico, escolha real da tela/janela e interoperabilidade desktop–Android precisa ser realizada com os dispositivos; sucesso entre navegadores com mídia simulada não substitui esses testes.
