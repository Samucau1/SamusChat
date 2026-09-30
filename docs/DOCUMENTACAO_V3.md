# SamusChat — documentação v3.0

Esta revisão consolida o backend, Android, testes, CI/CD e a interface da etapa 14. “v3.0” identifica a documentação; não implica uma release publicada nas lojas.

## Fluxo de uso

1. Execute `setup.ps1` na raiz e abra o Android no emulador.
2. Crie uma conta ou faça login.
3. Toque em **+**, no canto superior esquerdo, informe um nome e crie o servidor.
4. Selecione o servidor na barra lateral e abra um canal de texto.
5. Use a **lupa**, no canto superior direito, para filtrar seus servidores por nome/ID ou entrar pelo ID de outro servidor.
6. Envie mensagens; o histórico é paginado e eventos REST/STOMP são unificados por ID.

A busca ignora maiúsculas e acentos. A lista vem dos servidores dos quais a conta participa; não há catálogo público de comunidades. Erros de criação/entrada permanecem visíveis no formulário para permitir correção.

## Configuração local

| Variável em `.env` | Uso |
| --- | --- |
| `DB_USER`, `DB_PASSWORD` | Usuário/senha usados pelo Compose local e API |
| `JWT_SECRET` | Assinatura dos tokens; mínimo 32 caracteres no setup |
| `NOTIFICATION_STRATEGY` | `log` para desenvolvimento; `push` para Firebase configurado |
| `FILE_BASE_URL` | Endereço da API acessível ao Android para anexos |

O script aceita `CHAVE=valor` e aspas simples/duplas externas. Não executa interpolação, comandos ou conteúdo do arquivo. Variáveis já definidas no processo têm precedência; as que o script carrega são restauradas ao terminar. PostgreSQL e Redis ficam disponíveis apenas em localhost nas portas 5432/6379. A API atende na porta 8080.

O `.env` não é carregado automaticamente pelo Spring ao executar Maven manualmente: use `setup.ps1` ou exporte as variáveis no terminal. Para alterar credenciais de um banco existente, ajuste o usuário no PostgreSQL; não apague volumes com dados para trocar senhas.

Produção usa `.env.prod.example`, Flyway e `application-prod.properties`. Consulte [ETAPA_13.md](ETAPA_13.md) para secrets, HTTPS, backups e deploy. Os arquivos de exemplo e wrappers são versionados; credenciais locais, keystores, SDK, logs e builds são ignorados.

## Contratos principais da API

Respostas da API usam o envelope `{ success, message, data, error }`. Endpoints `/api/**`, exceto cadastro/login, requerem `Authorization: Bearer <token>`. Autorização adicional depende do recurso e cargo. Healthcheck e arquivos em `/uploads/**` são públicos; o handshake WebSocket é público, mas STOMP exige autenticação.

| Método | Endpoint | Finalidade |
| --- | --- | --- |
| POST | `/api/auth/register` | Cadastro: `username`, `email`, `password` |
| POST | `/api/auth/login` | Login: `email`, `password`; retorna JWT |
| GET | `/api/users/me` | Conta autenticada |
| PUT | `/api/users/me/username?newUsername=...` | Alterar nome |
| GET / POST | `/api/servers` | Listar participações / criar servidor |
| POST | `/api/servers/{id}/join` | Entrar no servidor |
| POST | `/api/servers/{id}/channels` | Criar canal com permissão |
| GET | `/api/channels/{id}/messages?page=0&size=50` | Histórico paginado |
| POST | `/api/channels/{id}/messages` | Enviar `{ "content": "Olá!" }` |
| DELETE | `/api/channels/{id}/messages/{messageId}` | Remover mensagem autorizada |
| POST | `/api/channels/{id}/upload` | Upload multipart |
| GET | `/api/servers/{id}/members` | Consultar membros |
| POST | `/api/devices/register` | Registrar token FCM |

O Android conecta em `/ws/websocket` com STOMP nativo e JWT no frame `CONNECT`; SockJS usa `/ws`. A assinatura é `/topic/channel/{id}` e o destino de envio é `/app/channel/{id}`. O backend verifica a participação no servidor antes do acesso ao canal.

## Decisões de arquitetura

- **Camadas:** controllers validam entradas, services aplicam regras e repositories persistem dados. DTOs definem os contratos externos.
- **JWT e BCrypt:** autenticação por token e hash de senha com salt. JWT stateless facilita múltiplas instâncias, mas exige proteger e expirar tokens.
- **Strategy:** notificações usam a estratégia configurada (`log` ou Firebase `push`).
- **Eventos após commit:** somente depois de persistir a mensagem o broadcaster publica no Redis; cada instância encaminha ao seu broker local.
- **Recuperação:** Pub/Sub não persiste eventos. Falhas de distribuição exigem recarga do histórico REST; o cliente elimina duplicatas por ID.
- **Android:** ViewModels expõem estado; Compose renderiza a interface. O token fica em DataStore privado, sem criptografia adicional; backup do app está desativado.
- **Infraestrutura:** Nginx distribui REST e mantém afinidade nas conexões WebSocket. Rate limiting reduz abuso, mas não substitui autenticação ou infraestrutura contra DDoS.

## Validação e evolução

Backend: `mvnw verify`, incluindo fan-out real com `RUN_REDIS_TESTS=true`. Android: `testDebugUnitTest`, `lintDebug` e `assembleDebug`. A busca tem casos para acentos, maiúsculas, espaços, ID exato e ausência de resultados.

No CI, o smoke test sobe a stack de produção e valida autenticação, migrações, roteamento e uploads compartilhados. Veja o checklist da [etapa 14](ETAPA_14.md) e os artifacts da execução correspondente no GitHub Actions.

Próximas evoluções possíveis: telas de upload/moderação, descoberta pública com regras de visibilidade, reconexão automática e observabilidade. Alta disponibilidade entre hosts, failover Redis e chamadas de voz/vídeo não fazem parte da implementação atual.
