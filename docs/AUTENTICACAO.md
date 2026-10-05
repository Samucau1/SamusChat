# Recuperacao de senha e login Google

O login oferece **Esqueci minha senha**: informar email, validar quatro digitos, escolher e confirmar nova senha. Funciona para contas existentes e novas, inclusive criadas pelo Google.

O codigo e aleatorio, preserva zeros a esquerda, expira em 10 minutos e permite cinco tentativas por envio. Reenvio a cada 10 minutos evita renovar indefinidamente as tentativas. Codigo correto libera uma autorizacao de 256 bits por cinco minutos, usada uma unica vez. Banco guarda hashes BCrypt dos segredos. Transacoes e bloqueio da conta coordenam as tres instancias da API. Troca incrementa a versao de autenticacao e invalida tokens anteriores nas novas requisicoes HTTP e autenticacoes WebSocket; conexoes WebSocket ja abertas precisam ser encerradas separadamente.

## Configuracao

### Atualizacao de bancos locais existentes

O perfil local usa `ddl-auto=update` e Flyway desabilitado. A coluna obrigatoria
`users.auth_version` precisa de `DEFAULT 0` para preservar contas ja cadastradas.
O mapeamento de `User` declara esse padrao, assim como a migracao V4 usada em
producao. Uma inicializacao antiga pode ter criado `google_subject` e
`password_recoveries`, mas falhado ao adicionar `auth_version` por existirem
usuarios. Nesse caso, o login apresenta erro de coluna inexistente.

Em 05/10/2026, o banco local `chatapp` foi corrigido adicionando somente
`auth_version INTEGER NOT NULL DEFAULT 0`, sem apagar contas nem trocar senhas.
O teste `AuthSchemaUpgradeTest` agora reproduz a atualizacao de uma tabela com
usuario existente e confirma a preservacao da senha e inicializacao da versao.
A regressao direcionada executou 25 testes sem falhas ou erros.
Em producao, mantenha Flyway habilitado e `ddl-auto=validate`; nao execute a V4
inteira manualmente sobre um banco local parcialmente atualizado.

### SMTP e Google

Preencha SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS e AUTH_MAIL_FROM no arquivo local .env. Use senha de aplicativo do provedor quando necessario. setup.ps1 e Compose de producao repassam essas variaveis. Nao envie credenciais pelo chat nem versionamento.

Crie credenciais OAuth no Google Cloud: cliente Android para pacote com.samuschat com SHA-1 das assinaturas de debug/release, e cliente Web para a audiencia do servidor. Configure GOOGLE_CLIENT_ID no backend e googleClientId no Gradle com o mesmo Client ID Web. Exemplo: ./gradlew.bat assembleDebug -PgoogleClientId=SEU_ID.apps.googleusercontent.com. Para producao, use HTTPS na API.

O Android usa Credential Manager; a API verifica assinatura, audiencia, emissor, expiracao e email verificado usando GoogleIdTokenVerifier. Identidade permanente usa sub. Para Gmail/Workspace, primeiro login vincula a conta pelo email. Email externo ao Google exige senha atual no campo Senha antes de clicar Entrar com Google. Novos usuarios recebem nome unico editavel no Perfil. Tokens invalidos nao criam contas.

Referencia: [validacao Google](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token), [Credential Manager Android](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

## API

- POST /api/auth/password/request: {email}; resposta generica para conta inexistente e reenvio no periodo de espera.
- POST /api/auth/password/verify: {email, code}; devolve {resetToken}.
- POST /api/auth/password/reset: {email, resetToken, password}; nao autentica automaticamente.
- POST /api/auth/google: {idToken, password?}; devolve token de sessao.

## Testes e relatorio

Execute ./mvnw.cmd test no backend e ./gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin no Android com ANDROID_HOME apontando para SDK instalado. AccountRecoveryTests utiliza H2 e captura email em memoria, sem entregar emails reais; Google e simulado para testar regras de vinculacao.

Na raiz: ./scripts/auth-report.ps1 gera docs/RELATORIO_AUTENTICACAO.md a partir dos resultados JUnit. ./scripts/auth-report.ps1 -Send envia esse relatorio exclusivamente para sgfernandes12@gmail.com via SMTP configurado. Relatorio informa a simulacao, valida envio destinado a conta de teste e formato do codigo, validacao, rejeicao da senha antiga e sucesso da nova. Nunca inclui senhas, hashes, codigos ativos ou tokens. Senhas existentes nao podem ser recuperadas de BCrypt.

Teste real no celular: entrar numa conta de teste, pedir recuperacao, conferir email (incluindo spam), digitar codigo, alterar senha, confirmar que senha antiga falha e nova entra; testar Google com conta existente e nova, cancelamento e senha de email externo. Entrega na caixa de entrada nao e garantida apenas por aceite SMTP.

## POST Google Identity Services (GIS) no navegador

Configure o login_uri do GIS para /api/auth/google/gis. Aceita JSON ou application/x-www-form-urlencoded com credential, g_csrf_token e password opcional. Antes de validar credential, compara exatamente o token do corpo com o cookie g_csrf_token, rejeitando tokens ausentes, vazios, divergentes ou cookies duplicados. Formulario exige um unico token no corpo; parametro de URL nao substitui o corpo. Rejeicao CSRF retorna HTTP 401 e nao chama o servico Google.

/api/auth/google continua sendo o contrato JSON do Credential Manager Android com idToken e password opcional. Nao aceita formulario e nao cria sessao por cookie. O navegador deve usar a rota GIS. A API nao habilita CORS amplo nem autenticacao por cookies.

GoogleIdTokenVerifier e reutilizado por instancia, preservando o cache de chaves publicas da biblioteca conforme Cache-Control. Assinatura, audiencia configurada, emissores accounts.google.com e https://accounts.google.com e validade temporal sao verificados; tolerancia temporal configurada em zero. Email deve ser verificado e sub presente. hd vazio nao torna o Google autoridade; nao ha restricao de organizacao porque o aplicativo aceita contas pessoais e Workspace.

GoogleGisCsrfTests verifica os dois formatos e rejeicoes antes de validar identidade. GoogleTokenValidationTests usa tokens RSA assinados localmente para exercitar a verificacao criptografica real com chaves de teste, audiencia/emissor incorretos, expiracao e regras de hd; nao valida uma conta Google real.
