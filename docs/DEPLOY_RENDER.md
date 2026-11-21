# Beta gratuita no Render

Esta configuração publica React, API Spring Boot e WebSocket no mesmo endereço. Um container executa Nginx e uma API Java; PostgreSQL fica no serviço de banco do Render. O perfil `prod,render` mantém Flyway, validação de esquema, JWT e proteção dos anexos. A instância única usa o broadcaster STOMP local e dispensa Redis.

## Limites da entrega gratuita

- O PostgreSQL Free expira 30 dias após a criação. Exportar os dados antes do prazo ou migrar o banco. Ele não oferece backups gerenciados.
- Fotos, capas, fundos e suas prévias estáticas são salvos no PostgreSQL desde a migração V9 e sobrevivem a reinícios, novos deploys e suspensão da aplicação. Consomem a capacidade do banco gratuito (1 GB), que continua sujeito à expiração. Arquivos já apagados pelo disco temporário antes desta versão precisam ser enviados novamente.
- Anexos das mensagens continuam no disco temporário do container; a persistência V9 cobre apenas imagens de aparência. Armazenamento durável de anexos exige uma adaptação separada.
- O serviço Free pode suspender após 15 minutos sem tráfego e levar cerca de um minuto para iniciar novamente. A memória é limitada; validar o consumo antes de convidar mais pessoas.
- Chamadas usam WebRTC entre os participantes. Esta configuração não hospeda TURN. Sem configurar um provedor TURN externo, algumas redes não conseguirão estabelecer mídia.
- Firebase, Google e envio de email não são configurados automaticamente. Render Free bloqueia as portas SMTP 25, 465 e 587; recuperação de senha exige um provedor por HTTPS ou outra solução compatível.
- O Nginx substitui cabeçalhos de encaminhamento externos. Os limites por IP podem agregar usuários que chegam pelo mesmo proxy do Render; o limite de login por conta continua ativo. Validar concorrência e acesso externo antes de ampliar a beta.

Regras verificadas em 20/11/2026: [Render Free](https://render.com/docs/free), [TLS](https://render.com/docs/tls), [WebSocket](https://render.com/docs/websocket) e [Blueprint](https://render.com/docs/blueprint-spec).

## Publicação pelo Blueprint

1. Enviar a versão preparada para uma branch do repositório GitHub. O Render só recebe arquivos publicados no Git, não alterações locais sem commit.
2. No Render, usar **New → Blueprint**, conectar o repositório **Samucau1/SamusChat** e selecionar a branch da entrega. O arquivo é `render.yaml`, na raiz.
3. Conferir os dois recursos: `samuschat-beta` com plano **Free** e `samuschat-beta-db` com plano **Free**. Não adicionar disco, plano pago ou cartão para concluir esta beta.
4. Aplicar o Blueprint. A senha do PostgreSQL é referenciada automaticamente e `JWT_SECRET` é gerado pelo Render. Não inserir essas credenciais em arquivos do Git.
5. Acompanhar o build e os logs. A verificação de saúde usa `/actuator/health/readiness` e só passa depois da conexão com o banco e das migrações.
6. Abrir o endereço HTTPS gerado pelo serviço. Cadastro, login, chat e arquivos usam esse mesmo endereço; não é necessário criar um Static Site separado.

Os deploys automáticos estão desabilitados (`autoDeployTrigger: off`). Para publicar uma nova versão, usar **Manual Deploy → Deploy latest commit** na branch selecionada. O domínio final pode receber um sufixo se o nome já estiver ocupado.

## Se estiver na tela New Web Service

O Blueprint é preferível porque conecta o banco automaticamente. Para preencher manualmente a tela do Web Service, primeiro criar o PostgreSQL **Free** na mesma região e usar:

| Campo | Valor |
| --- | --- |
| Repository | Samucau1/SamusChat |
| Branch | Branch que contém os arquivos desta entrega |
| Name | samuschat-beta |
| Region | Virginia, a mesma do PostgreSQL |
| Language / Runtime | Docker |
| Root Directory | Vazio, raiz do repositório |
| Dockerfile Path | `./deploy/render/Dockerfile` |
| Docker Build Context | `.` |
| Instance Type | Free |
| Health Check Path | `/actuator/health/readiness` |
| Auto-Deploy | Off |

Adicionar as variáveis `DB_HOST`, `DB_PORT`, `DB_NAME`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` com os dados internos do banco. Gerar um `JWT_SECRET` aleatório com pelo menos 32 caracteres. `RENDER_EXTERNAL_URL` é fornecido pelo Render; o script deriva `PUBLIC_BASE_URL` e a origem autorizada de WebSocket dessa URL. Não preencher Build Command ou Start Command: o Dockerfile e seu entrypoint já os definem.

## Android

Depois de o serviço ficar saudável, gerar um APK com `-PapiBaseUrl=https://ENDERECO_REAL.onrender.com/`. A barra final é obrigatória. O APK local que aponta para USB/localhost não acessa a nova hospedagem. Seguir o processo de assinatura e distribuição já usado na beta; a URL deve ser a do serviço efetivamente criado.

## Validação local

Construir com `docker build -f deploy/render/Dockerfile -t samuschat-render-validation:local .`. O contexto usa uma lista de arquivos permitidos e exclui credenciais Firebase, arquivos `.env`, chaves e projetos que não pertencem ao SamusChat.

Executar a imagem em uma rede Docker isolada com PostgreSQL 16 descartável, publicar a porta `10000` apenas em `127.0.0.1:19080` e definir `RENDER_EXTERNAL_URL=http://127.0.0.1:19080`, as variáveis de conexão do banco e um `JWT_SECRET` exclusivo de teste. O serviço não foi projetado para exposição HTTP direta fora desse teste local: no Render, TLS é terminado na borda e o Nginx é a única entrada do Java, vinculado a loopback.

`node scripts/smoke-render.mjs` confere a interface React, service worker, saúde com PostgreSQL/Flyway, autenticação, mensagens STOMP sem Redis, anexos protegidos e revogação da sessão no logout. O script recusa destinos fora de localhost. A validação local não substitui o aceite no endereço público, incluindo Wi-Fi/dados móveis, cold start, restart e consumo de memória.

### Resultado local em 20/11/2026

A imagem foi construída com sucesso. O smoke acima passou com PostgreSQL 16 isolado, limite de 512 MiB e 0,5 CPU para o aplicativo. O Java iniciou em aproximadamente 30 segundos e uma amostra após o teste registrou cerca de 328 MiB de memória do container. Isso valida o cenário pequeno exercitado, sem garantir capacidade para mais usuários. Logs locais em `logs/render-build.log` e `logs/render-smoke.log`.
