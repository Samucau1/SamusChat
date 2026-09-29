# Etapa 13 — CI/CD e escalabilidade

O workflow em `.github/workflows/ci-cd.yml` valida o backend Java 21 em pushes e
pull requests para `main` e `develop`. Usa Spring Boot 3.5.16 estável, Maven
Wrapper, testes com H2, um teste de fan-out com Redis real e relatórios JaCoCo.
Os relatórios ficam disponíveis como artifacts do GitHub Actions por sete dias.

Depois dos testes, o job `container-smoke` constrói a imagem e sobe uma instalação
descartável com PostgreSQL, Redis, três backends e Nginx. Verifica a migração do
banco, saúde de cada backend, autenticação, roteamento e uploads compartilhados.
Esse job não utiliza credenciais de produção.

## Fluxo das mensagens

REST, WebSocket/STOMP e uploads passam por `MessageService`. O evento já existente
é consumido pelo `MessageBroadcaster` **após o commit da transação**. Ele publica
JSON UTF-8 em `chat:channel:{id}` usando `StringRedisTemplate`; cada instância
escuta esse padrão e entrega a mensagem ao seu broker local. A instância de origem
também recebe pelo Redis, sem um segundo envio local. Anexos e datas são preservados.

Quando o Redis está desativado nos testes, a entrega continua local. Se a
publicação falhar, existe fallback local; clientes das outras instâncias precisam
recuperar o histórico REST. Redis Pub/Sub não é uma fila durável e não garante
replay ou entrega exatamente uma vez em falhas de rede.

## Infraestrutura

- `chatapp-backend/Dockerfile`: build em duas etapas, Java 21, usuário UID 10001,
  diretórios graváveis e healthcheck de readiness. Credenciais Firebase são excluídas
  do contexto Docker. O processo Java recebe diretamente os sinais de encerramento.
- `docker-compose.prod.yml`: três backends, PostgreSQL 16, Redis 7 com senha e
  réplica, Nginx 1.28 e volumes persistentes para banco, Redis e uploads.
- `nginx/nginx.conf`: REST com `least_conn`, WebSocket/SockJS com afinidade por IP,
  upgrade de conexão, DNS dinâmico para recriação de contêineres, rate limiting
  com HTTP 429 e uploads de até 10 MB na aplicação.
- `application-prod.properties`: segredos por ambiente, pool de conexões,
  Hibernate em `validate`, Flyway, desligamento gracioso e logs por instância.
- Readiness inclui aplicação, banco e Redis. Somente `/actuator/health/**` é
  público no backend e fica fora do rate limiter. Nginx não publica Actuator;
  `/health` verifica apenas o proxy. `info` e `metrics` exigem autenticação.

O Compose escala os backends em **um único servidor**. Banco, proxy e Redis master
continuam sendo pontos únicos de falha. A réplica Redis não configura Sentinel,
Cluster, failover automático ou distribuição das leituras da aplicação. Um
healthcheck marca o contêiner como unhealthy; sozinho não reinicia o processo.

## Publicação e deploy

Inicialmente os jobs de publicação/deploy ficam desativados, permitindo CI sem
segredos. Configure as variáveis do repositório em **Settings → Secrets and
variables → Actions → Variables**:

| Variável | Valor |
| --- | --- |
| `ENABLE_DOCKER_PUBLISH` | `true` para publicar pushes da `main` |
| `ENABLE_PRODUCTION_DEPLOY` | `true` depois de preparar o servidor |
| `PRODUCTION_URL` | URL pública HTTPS, sem barra final |

Configure os secrets do repositório (Docker) e do ambiente `production` (deploy):

| Secret | Uso |
| --- | --- |
| `DOCKER_USERNAME` | Usuário Docker Hub |
| `DOCKER_TOKEN` | Token com permissão para publicar a imagem |
| `SERVER_HOST` | IP ou hostname SSH do servidor |
| `SERVER_USER` | Usuário SSH com acesso ao Docker e a `/opt/samuschat` |
| `SERVER_SSH_KEY` | Chave privada SSH de deploy |
| `SERVER_KNOWN_HOSTS` | Linha known_hosts cuja impressão digital foi conferida com o servidor |
| `DB_USER` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Senha do PostgreSQL |
| `JWT_SECRET` | Segredo independente com pelo menos 32 bytes |
| `REDIS_PASSWORD` | Senha do Redis |

Crie **Settings → Environments → production**. Se desejar aprovação antes de cada
deploy, configure os revisores obrigatórios nesse ambiente; a declaração no YAML
não cria essa regra automaticamente.

No servidor Linux:

1. Instale Docker Engine e Compose com suporte a `up --wait` (Compose 2.24+).
2. Prepare `/opt/samuschat/releases` para o usuário de deploy. Para imagem privada,
   faça `docker login` nesse usuário com um token de leitura. O pipeline não envia
   o token de publicação ao servidor.
3. Configure um proxy TLS na frente de `127.0.0.1:8080`, permitindo WebSocket e
   uploads de 11 MB. O Compose não abre uma porta HTTPS sem certificado.
4. Ajuste o bloco `http` do Nginx para confiar **somente** no IP/CIDR desse proxy
   (`set_real_ip_from`, `real_ip_header X-Forwarded-For`, `real_ip_recursive on`).
   Sem esse ajuste, clientes atrás do proxy compartilham IP, rate limit e afinidade.
   Não confie em `0.0.0.0/0`. Configure o proxy externo para substituir headers
   de encaminhamento enviados pelo cliente.
5. Prepare backups do banco e uploads. O pool permite até 60 conexões somando os
   três backends; reserve também conexões para manutenção.

O workflow gera um bundle privado com a configuração do **mesmo commit** da imagem,
transfere por SSH com verificação da chave do host e usa
`/opt/samuschat/releases/<sha>`. Segredos não são interpolados em comandos remotos.
O `.env.prod` fica com modo 0600 e o diretório com acesso restrito. Releases antigas
também contêm segredos: gerencie retenção e rotação no servidor.

`scripts/deploy.sh` baixa as imagens, sobe os serviços de infraestrutura e substitui
um backend de cada vez, aguardando seu healthcheck. Uma falha interrompe a sequência.
Deploys do ambiente são serializados pelo GitHub Actions. A imagem implantada usa
a tag SHA, embora `latest` também seja publicada para conveniência.

Conexões WebSocket da instância substituída são encerradas e precisam reconectar.
Compose não oferece garantia de zero downtime. Para rollback, após conferir a
compatibilidade do banco, entre no diretório de uma release anterior e execute
`bash scripts/deploy.sh`. O processo não reverte migrações e não apaga imagens antigas.

## Primeiro banco e bancos existentes

Flyway executa `V1__initial_schema.sql` antes de o Hibernate validar as entidades.
Uma instalação nova funciona sem criação manual de tabelas; o lock do Flyway
coordena as inicializações concorrentes das instâncias.

Um banco existente criado por `ddl-auto=update` precisa de backup e comparação com
V1 antes de receber um baseline Flyway de versão 1. O baseline automático fica
desativado para evitar assumir que um esquema desconhecido já corresponde a V1.
Não aponte o primeiro deploy para um banco existente sem essa preparação.
Trocar `DB_PASSWORD` no Compose não altera a senha de um banco já inicializado:
faça a rotação no PostgreSQL e na configuração de forma coordenada.

Notificações usam `log` por padrão no Compose. Para ativar push, monte o arquivo
Firebase somente para leitura em `/app/secrets/firebase-service-account.json` e
adicione às três instâncias as variáveis `FIREBASE_ENABLED=true`,
`NOTIFICATION_STRATEGY=push` e
`APP_FIREBASE_CREDENTIALS=file:/app/secrets/firebase-service-account.json`.
Use uma configuração de deploy local para esse volume; nunca inclua a credencial
na imagem ou no Git.

## Execução manual

Na raiz do repositório, em um servidor preparado:

```bash
cp .env.prod.example .env.prod
# Preencha .env.prod com credenciais próprias, URL e tag SHA existente no Docker Hub.
chmod 600 .env.prod
bash scripts/deploy.sh
```

Para validar o backend localmente:

```bash
cd chatapp-backend
./mvnw -B -ntp verify
# Opcional: Redis local na porta 16379 para incluir a integração real.
RUN_REDIS_TESTS=true REDIS_TEST_PORT=16379 ./mvnw -B -ntp verify
```

O teste Redis é ignorado quando `RUN_REDIS_TESTS` não está habilitado. No CI ele
sempre executa. O perfil de testes mantém H2 isolado e Firebase desativado.

## Validação desta implementação

- `mvn verify` com Redis real: 65 testes, nenhuma falha, erro ou teste ignorado;
  JAR e relatório JaCoCo gerados.
- Imagem Docker construída e stack isolada iniciada: sete serviços saudáveis.
  Smoke test passou com autenticação, mensagens e o mesmo upload acessível nas
  três instâncias sobre PostgreSQL.
- Workflow validado com actionlint, script de deploy com ShellCheck e segredos
  fictícios com caracteres especiais conferidos no parser do Compose.

Publicação no Docker Hub, execução no GitHub Actions e deploy SSH remoto dependem
da configuração da conta e do servidor; não foram executados nesta validação local.

Referências: [Compose up e --wait](https://docs.docker.com/reference/cli/docker/compose/up/),
[Actuator e probes](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html),
[resolução dinâmica de upstreams Nginx](https://nginx.org/en/docs/http/ngx_http_upstream_module.html).
