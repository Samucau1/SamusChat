# Beta gratuita no Render

Esta configuração publica React, API Spring Boot e WebSocket no mesmo endereço. Um container executa Nginx e uma API Java; PostgreSQL fica no serviço de banco do Render. O perfil `prod,render` mantém Flyway, validação de esquema, JWT e proteção dos anexos. A instância única usa o broadcaster STOMP local e dispensa Redis.

## Limites da entrega gratuita

- O PostgreSQL Free expira 30 dias após a criação. Exportar os dados antes do prazo ou migrar o banco. Ele não oferece backups gerenciados.
- Os arquivos enviados, fotos e capas ficam no disco temporário do container e desaparecem em reinícios, novos deploys ou suspensão. Não usar esta configuração para guardar arquivos importantes. Armazenamento externo ou disco persistente pago exige uma adaptação posterior.
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

### Códigos de servidor e atualização dos clientes

A alteração de convites adiciona a migração `V10__server_invite_codes.sql`. A base local já incorpora a `V9__friends_and_persistent_appearance.sql` da versão mais recente, sem alterar seu conteúdo. A V10 adiciona uma coluna e uma tabela de coordenação, sem renumerar servidores, canais ou mensagens. Servidores existentes recebem um código persistente quando um administrador carrega sua lista ou abre o servidor.

O código tem exatamente três caracteres, diferencia maiúsculas de minúsculas e aceita letras, números e os símbolos `!@#$%*+-_`. A geração usa `SecureRandom`, coordenação por bloqueio no banco e índice único. Apenas respostas para usuários com cargo `ADMIN` incluem `inviteCode`; `myRole` informa o cargo do usuário autenticado. Alterações de aparência continuam restritas a administradores.

A interface atualizada usa `POST /api/servers/join` com JSON `{"code":"A#%"}` e sessão autenticada. A rota antiga `POST /api/servers/{id}/join` continua aceitando o ID numérico para preservar os clientes já instalados, com o mesmo envelope de resposta e entrada como membro `USER`. Não é exigida atualização imediata do Android para continuar entrando em servidores. As associações existentes permanecem válidas. O código curto é um convite, não uma senha: há limite específico de dez tentativas por minuto por IP, e ele não substitui controles de acesso para servidores confidenciais. A remoção futura da rota antiga exige uma decisão separada de compatibilidade.

As alterações estão preparadas no código local; nenhuma migração foi aplicada ao banco hospedado por esta tarefa. Os testes de API usam H2 isolado, e a atualização do esquema é ensaiada também em PostgreSQL 16 descartável. O Android SDK foi localizado em `.android-sdk`; com `ANDROID_HOME` apontando para essa pasta, a compilação Kotlin e os 70 testes de unidade do Android passaram. O artefato de distribuição ainda precisa ser gerado, versionado e validado antes da publicação.

### Preservação de dados na próxima publicação

O backend local foi reconciliado com a base recente de amizades, perfis públicos e imagens persistidas no banco. A migração adicional `V11__persistent_attachments.sql` cria `attachment_blobs`, sem remover nem reescrever usuários ou mensagens. Novos anexos são salvos na mesma transação da mensagem e seus bytes passam a fazer parte do backup do PostgreSQL. Downloads mantêm a exigência de sessão e participação no servidor. As URLs existentes continuam válidas. Imagens de perfil usam a persistência introduzida pela V9.

Isso não recupera automaticamente arquivos antigos que existam apenas em `/app/uploads`. O backend mantém leitura compatível desses arquivos enquanto estiverem presentes. Um redeploy no Render Free substitui o sistema de arquivos temporário: é necessário obter uma cópia dos bytes antigos **antes** de substituir o container e importá-los para o armazenamento persistente. Uma URL ou registro de mensagem não é uma cópia do arquivo. Arquivos já ausentes precisam ser identificados; não podem ser declarados preservados só porque a referência está no banco. [Persistência no Render Free](https://render.com/docs/free#local-files-lost-on-redeploy).

Sequência para a publicação, ainda não executada:

1. Confirmar no painel o serviço, banco vinculado, branch, commit efetivamente ativo e política de deploy automático. Preparar um commit revisável com a base recente e somente as mudanças do SamusChat; não publicar a árvore local inteira, que contém outros projetos e trabalho em andamento. A interface local já foi reconciliada com a central de amigos e o histórico recentes, preservando temas, configurações e códigos de convite.
2. Manter o banco existente, seu volume de dados e as configurações atuais de autenticação. Preservar `JWT_SECRET`: o código também usa essa chave para assinar IDs públicos dos usuários. Não trocar credenciais, URLs de OAuth, endpoint público ou variáveis de conexão como parte desta atualização.
3. Executar `scripts/sql/preflight-atualizacao-render.sql` no banco atual, com acesso autorizado. Guardar a saída privada fora do Git. O inventário inclui contagens, histórico Flyway, bytes de mídia e quantidade de referências sem bytes persistidos. O espaço necessário para anexos deve caber na cota do banco; a mudança não amplia a capacidade do plano.
4. Fazer `pg_dump` completo do banco, com ferramentas compatíveis com a versão de origem, e uma cópia independente dos arquivos que não estão no banco. Usar credenciais por mecanismo seguro, sem colocá-las no histórico, nesta documentação ou em argumentos de comandos. Registrar horário e SHA-256 das cópias, e verificar cobertura de todas as referências, inclusive capas, fundos, ícones, GIFs e versões estáticas. O Free Postgres não oferece a recuperação automática dos planos pagos. [Backup manual no Render](https://render.com/docs/postgresql-backups#exporting-with-pg_dump).
5. Restaurar a cópia em um banco separado, aplicar V10/V11 e importar ali os bytes antigos obtidos no passo anterior. Comparar contagens, chaves e hashes de arquivos. Validar login, mensagens privadas e de servidor, amizades, perfis, anexos e código administrativo. Não usar o banco em produção como ambiente de ensaio.
6. Validar os dois contratos de entrada antes da publicação: código para a web/Android atualizados e ID numérico para clientes instalados. A API mantém ambos. O código Android foi adaptado e compilado; a geração e distribuição do novo APK é uma etapa independente e não deve bloquear os usuários do APK anterior.
7. Coordenar uma janela curta para o backup final e a transferência de arquivos que tenham mudado desde a primeira cópia. Bloquear escritas de forma efetiva em REST, uploads, WebSocket/STOMP e tarefas; uma tela de manutenção na web não impede gravações do Android. Capturar a última cópia e verificar a restauração antes de trocar a versão.
8. Publicar o commit validado no mesmo serviço, mantendo o mesmo banco e domínio. O Flyway deve validar o histórico já aplicado e executar somente as migrações pendentes. Se houver divergência de checksum, investigar; não usar `clean`, `repair` ou recriação do esquema para contornar o erro. Importar os bytes preservados e verificar que as referências necessárias têm conteúdo persistente.
9. Conferir readiness, login de uma conta já existente, IDs públicos, contagens e download de arquivos antigos e novos. Verificar que o criador vê o código e membros comuns não o recebem. Reabrir escritas após o aceite.

Se a versão candidata falhar, preferir reverter o aplicativo ao commit conhecido mantendo o banco intacto. As novas estruturas são aditivas; o retorno ao código anterior precisa ser ensaiado com a configuração Flyway efetiva antes de prometer rollback automático. Não restaurar um dump antigo sobre produção após usuários voltarem a gravar: isso descartaria dados novos. A cópia do banco e a cópia de arquivos são preservadas para recuperação verificada, e não como comandos automáticos de substituição.

O ensaio de migração usa dados simulados e valida preservação de contas e hashes de senha, cargos, amizades, mensagens, referências de anexos e bytes de imagens. Ele não substitui a restauração da cópia real. A publicação permanece pendente do inventário e das cópias reais; ainda não há evidência suficiente para garantir que todos os anexos antigos em produção estão recuperáveis.

### Consolidação local em 25/11/2026

A versão local foi reconciliada com `deploy/render-free-beta` no commit `2d20e18db489f3b6a9298bd167dd124dd1273ef2`, confirmado também por consulta somente de leitura à referência remota. Os 264 arquivos de execução dessa base estão presentes nesta cópia. As diferenças restantes incluem as mudanças intencionais de temas, configurações, perfis, convites administrativos e persistência de anexos; não houve reset da árvore de trabalho nem substituição dos projetos vizinhos.

A web recuperou busca de amigos por nome e ID, solicitações e aceite, ações de amizade no perfil, cópia do ID, perfil pelos membros do servidor, histórico de conversas após remover uma amizade e retorno explícito para imagem salva ou recusada. Esses fluxos usam os quatro temas e os controles padronizados de configurações.

Validação realizada:

- Build web aprovado; 19 testes de contratos, login Google, temas, perfis e configurações aprovados, além de três testes de integração real para amizades/imagens e entrada no servidor com duas contas, mensagens e anexos.
- Compilação Kotlin e 70 testes de unidade Android aprovados com o SDK local, sem alterar a versão instalada dos usuários.
- Imagem `samuschat-consolidated:local` construída com `deploy/render/Dockerfile`. Smoke com PostgreSQL 16 descartável, limite de 512 MiB e 0,5 CPU aprovado: interface, service worker, Flyway/readiness, autenticação, STOMP, anexos protegidos e logout.
- `scripts/smoke-render-restart.mjs` removeu os arquivos sintéticos do cache de um container local identificado por label, reiniciou a aplicação e confirmou preservação de login, código do servidor, foto e bytes do anexo no banco. Esse script recusa endereços externos e não reinicia o Render.

O teste de imagem executado logo após vários cenários atingiu o limite de requisições por IP da configuração de produção. O cenário passou isoladamente após o reinício da instância descartável; os limites da aplicação não foram alterados para aprovar o teste. Isso não é uma medição de capacidade do serviço real.

Logs e capturas locais estão em `logs/consolidated-image-build.log`, `logs/social-consolidation/` e `samuschat-android/build-consolidation.log`, fora da publicação. Nenhum push ou deploy foi realizado. O aceite dos dados reais e a obtenção das cópias descritas acima continuam necessários antes da atualização no Render.

### Resultado local em 20/11/2026

A imagem foi construída com sucesso. O smoke acima passou com PostgreSQL 16 isolado, limite de 512 MiB e 0,5 CPU para o aplicativo. O Java iniciou em aproximadamente 30 segundos e uma amostra após o teste registrou cerca de 328 MiB de memória do container. Isso valida o cenário pequeno exercitado, sem garantir capacidade para mais usuários. Logs locais em `logs/render-build.log` e `logs/render-smoke.log`.
