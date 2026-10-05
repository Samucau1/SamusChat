# Mensagens privadas

Em Amigos, toque em um contato para abrir diretamente a conversa, com o campo de mensagem disponível. A conversa permite enviar textos de até 2000 caracteres e carregar mensagens anteriores. Voltar retorna à lista de amigos; o telefone continua disponível no topo da conversa.

O histórico fica salvo no banco, separado dos canais de servidores. A API usa a identidade autenticada como remetente e só consulta mensagens entre essa conta e o contato informado. Não exige servidor em comum.

GET `/api/direct-messages?contact=EMAIL&page=0` retorna até 50 mensagens, das mais novas para as mais antigas. POST na mesma rota recebe `{"content":"Olá"}`. Ambas exigem Bearer token. O Android atualiza a conversa a cada três segundos enquanto a tela está visível. Esta versão não inclui anexos, confirmação de leitura ou notificações de mensagens privadas em segundo plano.

Para disponibilizar a mudança, atualize a API e instale o novo APK. O perfil local com `ddl-auto=update` cria `direct_messages` ao iniciar. Em produção, Flyway aplica `V5__direct_messages.sql`.

Os testes `DirectMessageTests` verificam persistência, leitura pelo destinatário, isolamento de uma terceira conta, autenticação obrigatória, limite de texto e contato inexistente.
