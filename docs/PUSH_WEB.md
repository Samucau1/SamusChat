# Push do PC com a aba fechada

O PC pode se inscrever no Firebase Cloud Messaging (FCM). O backend compartilha o cadastro de dispositivos já usado pelo Android e envia as mensagens ao navegador; `public/push-sw.js` recebe o evento sem depender de uma aba aberta. Não há polling em segundo plano nem cache de conversas no service worker.

## Configuração necessária

O projeto Firebase ainda precisa ser criado. Código implementado não significa que a entrega externa já está ativada.

1. Criar um projeto no [Firebase Console](https://console.firebase.google.com/) e adicionar um aplicativo Web. Usar o mesmo projeto do backend e, futuramente, do Android.
2. Nas configurações de Cloud Messaging, gerar o certificado Web Push (VAPID). O navegador usa somente a chave **pública**. Conferir a habilitação da FCM Registration API conforme o [guia oficial](https://firebase.google.com/docs/cloud-messaging/web/get-started).
3. Copiar `samuschat-desktop/.env.example` para `.env.local` na mesma pasta e preencher os cinco campos públicos. Essas variáveis são incorporadas ao build; reiniciar o Vite em desenvolvimento ou gerar e publicar um novo build.
4. Configurar a credencial de serviço **somente no backend**, por exemplo com `--app.firebase.credentials=file:/caminho/seguro/firebase-service-account.json`. Nunca colocar o JSON privado nas variáveis `VITE_`, nos arquivos públicos ou no Git.
5. Executar a API com `app.notifications.strategy=push` e Firebase habilitado. O endpoint autenticado `GET /api/devices/web-push/status` retorna `enabled=false` quando a estratégia é log ou o Firebase não foi inicializado.
6. Servir a interface por HTTPS, ou localhost para desenvolvimento. Publicar `/push-sw.js` como JavaScript na raiz da mesma origem. Não substituir essa rota pelo HTML da SPA nem mantê-la permanentemente em cache no host/CDN.

Exemplo dos campos públicos:

```dotenv
VITE_FIREBASE_API_KEY=chave-publica-do-app-web
VITE_FIREBASE_PROJECT_ID=seu-projeto
VITE_FIREBASE_MESSAGING_SENDER_ID=id-do-remetente
VITE_FIREBASE_APP_ID=id-do-app-web
VITE_FIREBASE_VAPID_KEY=chave-publica-web-push
```

## Uso e ciclo de vida

- Entrar na conta e clicar em **Ativar avisos de mensagens**. A permissão é solicitada por ação do usuário. Sem configuração, a interface informa que somente os avisos com o site aberto estão disponíveis.
- Após o registro no servidor, a interface confirma os avisos com a aba fechada. O token é renovado ao entrar novamente na mesma conta. O JWT não é guardado no service worker nem usado para receber push.
- **Desativar push neste navegador** remove o registro remoto antes de cancelar a inscrição local. Se a API falhar, a interface mantém o estado e permite tentar novamente.
- Sair da conta revoga todas as sessões e remove seus dispositivos no backend. O navegador também cancela sua inscrição e fecha os avisos existentes. Registro de dispositivo e logout usam o mesmo bloqueio de conta, com revalidação do JWT, para não restaurar um registro revogado por uma requisição em andamento.
- A inscrição pertence a uma conta por perfil do navegador. Ativar em outra conta cancela a inscrição local anterior e solicita um novo token; o servidor não transfere a propriedade de um token existente.
- Enquanto há uma janela visível do app, o service worker não exibe aviso do sistema. A interface e o WebSocket continuam cuidando dos avisos internos. Com push ativo, o aviso legado do WebSocket é suprimido para evitar duas notificações.
- Os avisos do sistema mostram somente “SamusChat” e “Você tem uma nova mensagem”. O clique foca uma janela do site ou abre `/`. Com a sessão encerrada, o usuário entra novamente; a notificação não contém credenciais nem contorna o login.
- O backend limita a vida do push Web a cinco minutos. Isso não altera o histórico de mensagens.

## Testes

`npx playwright test tests/browser-push.spec.ts tests/notifications.spec.ts --headed` usa somente o Microsoft Edge instalado.

O teste do service worker fecha todas as abas do SamusChat e injeta um evento por `ServiceWorker.deliverPushMessage`, ferramenta do próprio navegador. Ele verifica a notificação nativa registrada, a privacidade do texto, a supressão com janela visível e o descarte de payloads inválidos. A lógica do clique é verificada isoladamente com clientes simulados; o clique físico no aviso ainda exige aceite manual.

O teste **não valida transporte pela rede Firebase**, obtenção de um token real nem execução após encerrar todos os processos do Edge. Esses cenários precisam das configurações reais. A [documentação da Microsoft sobre serviços em segundo plano](https://learn.microsoft.com/en-us/microsoft-edge/devtools/javascript/background-services) descreve a ferramenta usada para injetar o evento.

### Regressão de 26/10/2026

| Verificação | Resultado |
| --- | --- |
| `npm run build` | TypeScript e Vite aprovados; `push-sw.js` incluído no build com hash igual ao fonte |
| Regressão completa no Edge instalado | 48 aprovados, 2 ignorados, zero falhas, em 2,1 minutos |
| Backend `mvnw verify` | 149 testes: 146 aprovados, 3 ignorados, zero falhas/erros, em 1 min 39 s |
| `npm audit --omit=dev` | Zero vulnerabilidades conhecidas |

No Edge, foram ignorados o teste USB opt-in e o teste legado de notificação via WebSocket que exige `document.hidden=true` na sessão automatizada. O **novo teste de push no service worker com todas as abas fechadas foi aprovado**. No backend, os três ignorados exigem Redis ou PostgreSQL externos; o status autenticado, a remoção de dispositivos no logout e a revalidação após adquirir o bloqueio de conta passaram. Não houve alteração no código Android nesta etapa.

## Aceite com Firebase real

Com duas contas, ativar o push no Edge da conta destinatária, fechar todas as suas abas do SamusChat e enviar uma mensagem pela outra conta. Conferir um único aviso genérico, clicar e verificar o acesso à tela do app. Repetir após desativar o push e após logout, sem novo aviso para mensagens posteriores. Verificar também permissão bloqueada, rede interrompida e o comportamento ao fechar o navegador inteiro. As permissões do Edge e do Windows podem afetar a apresentação dos avisos; consultar [notificações de sites no Edge](https://support.microsoft.com/en-us/edge/manage-website-notifications-in-microsoft-edge).
