# Login Google no Render

Esta alteração integra o botão oficial Google Identity Services na tela web.
O backend continua validando assinatura, audiência, emissor, expiração e email
verificado. A resposta usa a sessão e a renovação já existentes. Sem Client ID,
o botão permanece oculto e o login por email e senha continua disponível.

## Ativação

1. No Google Cloud, escolha ou crie um projeto do SamusChat. Em Google Auth
   Platform, configure Branding, Audience e os contatos exigidos. Para testes,
   cadastre os emails autorizados quando exigido pela configuração de público.
2. Em Clients, crie um cliente OAuth do tipo **Web application**.
3. Cadastre a origem JavaScript **https://samuschat-beta.onrender.com**, sem
   caminho e sem barra final. Este fluxo usa popup com callback JavaScript;
   não configure a API JSON como redirecionamento do Google.
4. No Render, no serviço samuschat-beta, adicione **GOOGLE_CLIENT_ID** com o
   Client ID Web terminado em `.apps.googleusercontent.com`. O Client ID é
   público. Não inserir Client Secret, senha Google ou token de usuário.
5. Publique o código e implante o serviço. O frontend consulta o Client ID
   em `/api/auth/google/config`, portanto não precisa de variável VITE no build.
6. Valide uma conta Google de teste no endereço HTTPS real, cancelamento do
   popup, login existente e logout. O teste automatizado usa SDK e API simulados
   e não comprova a configuração real da conta Google.

O Google ID token é enviado por JSON à API com proteção de double-submit
cookie; não é salvo em localStorage. Apenas a sessão do SamusChat é persistida,
conforme o comportamento atual. Não há acesso solicitado a Drive, Gmail ou contatos.
Contas existentes com email externo ao Google podem exigir a senha atual para
vinculação; preencha-a antes de usar o botão. Recuperação de senha e push não
são habilitados por esta mudança.

Referências oficiais:
- https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid
- https://developers.google.com/identity/gsi/web/reference/js-reference
- https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
