# Mensagens privadas

Atualização de privacidade em 16/10/2026: o parâmetro `contact` e as identidades dos eventos usam o identificador público do contato; os nomes de campos legados não indicam mais emails públicos. Veja [privacidade e validação](PRIVACIDADE_CONTAS_2026-10-16.md).

Em Amigos, toque em um contato para abrir diretamente a conversa, com o campo de mensagem disponível. A conversa permite enviar textos de até 2000 caracteres e carregar mensagens anteriores. Voltar retorna à lista de amigos; o telefone continua disponível no topo da conversa.

O histórico fica salvo no banco, separado dos canais de servidores. A API usa a identidade autenticada como remetente e só consulta mensagens entre essa conta e o contato informado. Não exige servidor em comum.

GET `/api/direct-messages?contact=EMAIL&page=0` retorna até 50 mensagens, das mais novas para as mais antigas. POST na mesma rota recebe `{"content":"Olá"}`. Ambas exigem Bearer token. Android e PC recebem novas mensagens por STOMP em `/user/queue/direct-messages`, autenticando o CONNECT com JWT. O envio e o histórico continuam REST.

A publicação ocorre após o commit, para remetente e destinatário, incluindo suas outras sessões conectadas. Redis Pub/Sub em `chat:direct` distribui o evento às instâncias da API. Sem Redis, ou se a publicação falhar, há entrega local; isso não garante entrega entre instâncias durante a falha. JWT revogado impede a entrega de novos eventos. Assinar filas de outras sessões ou destinos arbitrários é proibido.

Os clientes filtram a conversa selecionada e unificam os resultados por ID. Na reconexão consultam a página mais recente do histórico; sem WebSocket mantêm consultas de recuperação a cada três segundos. No Android a conexão acompanha a tela visível e a renovação do token. Uma lacuna de mais de 50 mensagens pode exigir carregar páginas anteriores. Esta versão não inclui anexos privados, confirmação de leitura, contagem de não lidas ou notificações de mensagens privadas em segundo plano.

Para disponibilizar a mudança, atualize a API e instale o novo APK. O perfil local com `ddl-auto=update` cria `direct_messages` ao iniciar. Em produção, Flyway aplica `V5__direct_messages.sql`.

Os testes `DirectMessageTests` verificam persistência, leitura pelo destinatário, isolamento de uma terceira conta, autenticação obrigatória, limite de texto e contato inexistente.

Veja a [regressão WebSocket de 16/10/2026](REGRESSAO_WEBSOCKET_PRIVADO_2026-10-16.md), com rollback, isolamento das filas, Redis entre instâncias, filtragem Android, deduplicação e teste de navegador bloqueando o histórico REST.
