# Auditoria da Etapa 10 — Clean Code e Patterns

Verificação realizada em 28/09/2026 contra o roteiro fornecido, preservando a integração Android da Etapa 11.

## Checklist conferido

| Requisito | Implementação encontrada | Resultado |
| --- | --- | --- |
| Result / resposta padronizada | `dto/ApiResponse.java`, controllers REST e `GlobalExceptionHandler` | Já presente; completado nos erros dos filtros de segurança |
| Mapper | `UserMapper`, `MessageMapper`, `ServerMapper` | Já presentes e usados pelos serviços |
| Builder | `UserResponse`, `MessageResponse`, `ServerResponse`, `ChannelResponse` | Já presente nos quatro DTOs |
| Factory | `MessageFactory`, `ServerFactory` | Já presentes e usados na criação de mensagens, servidores, canal geral e membros |
| Strategy | `NotificationStrategy`, `PushNotificationStrategy`, `LogNotificationStrategy` | Já presentes; seleção agora configurável |
| Service | `MessageService`, `ServerService`, `UserService` | Já utilizam os padrões do roteiro; validações e transações preservadas |
| Repository | Interfaces Spring Data JPA e histórico paginado | Já presentes |
| Controllers de autenticação e mensagens | Respostas com `success`, `data`, `message`, `error`, `timestamp` | Já presentes; campos nulos omitidos conforme o roteiro |

## Complementos implementados

`ApiErrorWriter` centraliza a serialização de `ApiResponse.error` usando o ObjectMapper da aplicação. JWT inválido, ausência de autenticação, acesso negado pelo Spring Security e limite de requisições usam o mesmo envelope. Os códigos 401/403/429, textos de erro e cabeçalhos de rate limit foram mantidos. O Android continua lendo o campo `error`.

`NotificationConfig` seleciona a estratégia por `app.notifications.strategy`, configurável pela variável `NOTIFICATION_STRATEGY`:

- `push`: padrão, mantendo o envio Firebase usado pela Etapa 11. Sem configuração Firebase, o comportamento existente de ignorar o envio permanece.
- `log`: opção de desenvolvimento sem envio Firebase. Registra os dados da notificação nos logs.

Um valor diferente impede a inicialização com uma mensagem de configuração inválida. A escolha é explícita; não há troca automática para logs quando Firebase está indisponível.

## Compatibilidade preservada

As melhorias da Etapa 11 foram mantidas: servidores dos quais o usuário é membro aparecem na listagem; participação no servidor é verificada para ler/enviar mensagens; histórico é retornado em ordem cronológica; mensagens REST, STOMP e anexos são publicados após a transação; payload de mensagem mantém `type: CHAT`. Não foi feita substituição literal pelos exemplos antigos que eliminaria esses comportamentos.

## Testes realizados

Antes das mudanças: 14 testes do back-end aprovados.

Após as mudanças:

- **20 testes do back-end aprovados**, sem falhas ou testes ignorados, com `mvnw.cmd -o test`.
- **4 testes Android aprovados**, sem falhas ou testes ignorados, incluindo consumo dos erros padronizados 401/429.
- `gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` concluído; APK gerado e lint com **0 erros e 9 avisos** preexistentes de dependências/backup.
- `git diff --check` sem erros.

A cobertura de API usa Spring Boot, MockMvc, JWT real, serviços/repositórios reais e H2 em modo PostgreSQL. Inclui cadastro/login, perfil sem senha, alteração de nome, criação de servidor e canal geral, entrada/listagem/remoção de membros, alteração de cargo, mensagens com espaços, paginação, anexo sem texto, exclusão, validações e rejeição de acesso de não membros. As suítes anteriores continuam cobrindo arquivos, dispositivos, rate limit e segurança.

Os testes WebSocket abrem conexões reais em uma porta local aleatória, tanto via SockJS quanto em `/ws/websocket`, e verificam envio REST e recebimento STOMP. Os testes Android usam MockWebServer para verificar o contrato HTTP e a unificação de mensagens.

Esta validação não executou PostgreSQL/Redis de produção, interface em emulador/celular nem entrega Firebase real. Esses ambientes continuam exigindo infraestrutura, dispositivo e credenciais próprios.
