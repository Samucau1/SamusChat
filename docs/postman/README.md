# Regressao HTTP com Postman / Newman

Importe `SamusChat.postman_collection.json` no Postman e execute a colecao inteira, na ordem. A URL padrao e `http://127.0.0.1:18081`; altere a variavel `baseUrl` se necessario.

Use uma API descartavel: a colecao cria duas contas, um servidor e canais novos em cada execucao. IDs e tokens ficam em variaveis de execucao, sem credenciais reais no arquivo. Nao exporte variaveis preenchidas com tokens para o Git.

Para iniciar uma API com H2 em memoria, execute em `chatapp-backend`:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.profiles=test' '-Dspring-boot.run.arguments=--server.port=18081 --spring.datasource.url=jdbc:h2:mem:postman-regression;MODE=PostgreSQL;DB_CLOSE_DELAY=-1'
```

Com a API pronta, execute na raiz do repositorio:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json --bail
```

Aguarde pelo menos um minuto entre execucoes: o limite de autenticacao permite cinco requisicoes por minuto por cliente; a colecao usa quatro. Encerre a API de teste com Ctrl+C quando terminar. O banco em memoria desaparece quando o processo termina.

## Metodos e cenarios

| Metodo | Cobertura |
| --- | --- |
| GET | Saude, autenticacao obrigatoria, perfil, servidores, membros, mensagens, contatos, ICE, chamadas e sala |
| POST | Cadastro/login, servidor, entrada de membro, canais, mensagem, convite/aceite/oferta/resposta/encerramento e entrada/saida da sala |
| PUT | Alteracao do nome do perfil |
| DELETE | Exclusao da mensagem criada pela propria colecao |

As 34 requisicoes verificam status HTTP, envelopes de sucesso, estados da chamada, aceite proibido ao chamador, encerramento idempotente e dois participantes na sala. A API nao expoe PATCH nesses controladores.

Esta colecao complementa a suite backend; nao cobre todos os endpoints nem substitui seus testes de permissoes, recuperacao de senha, Google, uploads, dispositivos, WebSocket e Redis. SDP de teste verifica sinalizacao; audio, video e compartilhamento real exigem clientes WebRTC.
