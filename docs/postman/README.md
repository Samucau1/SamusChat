# Regressao HTTP com Postman / Newman

Importe `SamusChat.postman_collection.json` e `SamusChat-local.postman_environment.json` no Postman, selecione o ambiente H2 local e execute a colecao inteira, na ordem. A URL padrao e `http://127.0.0.1:18081`; altere a variavel `baseUrl` se necessario, mantendo uma API descartavel.

Use uma API descartavel: a colecao cria duas contas, um servidor e canais novos em cada execucao. IDs e tokens ficam em variaveis de execucao, sem credenciais reais no arquivo. Nao exporte variaveis preenchidas com tokens para o Git.

Para iniciar uma API com H2 em memoria, execute na raiz:

```powershell
.\scripts\start-desktop-test-api.ps1 -Port 18081
```

Com a API pronta, execute na raiz do repositorio:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json `
  -e docs/postman/SamusChat-local.postman_environment.json --working-dir . --bail
```

O limite de autenticacao permite cinco requisicoes por minuto por cliente; a colecao usa quatro. Os cenarios originais e a pasta desktop usam identificadores distintos de clientes simulados via X-Forwarded-For, gerados a partir do ID da execucao, mantendo os limites da API habilitados. Isso permite repetir a suite com novos clientes de teste; nao e um teste de carga. Encerre a API de teste com Ctrl+C quando terminar. O banco em memoria desaparece quando o processo termina.

No Postman, confira o campo `file` da requisicao **Desktop - enviar anexo** e selecione `fixtures/desktop-anexo.txt` caso o caminho relativo nao seja resolvido pelo aplicativo. No Newman, execute da raiz conforme o comando acima; o arquivo fica em `docs/postman/fixtures/desktop-anexo.txt`.

O launcher usa classpath de testes e define H2, driver, usuario, DDL, uploads e integracoes explicitamente. Apenas selecionar o perfil test com spring-boot:run nao garante isolamento quando os recursos de teste nao entram no classpath.

## Metodos e cenarios

| Metodo | Cobertura |
| --- | --- |
| GET | Saude, autenticacao obrigatoria, perfil, servidores, membros, mensagens, contatos, ICE, chamadas e sala |
| POST | Cadastro/login, servidor, entrada de membro, canais, mensagem, convite/aceite/oferta/resposta/encerramento e entrada/saida da sala |
| PUT | Alteracao do nome do perfil |
| DELETE | Exclusao da mensagem criada pela propria colecao |

As 44 requisicoes verificam status HTTP, envelopes de sucesso, estados da chamada, aceite proibido ao chamador, encerramento idempotente e dois participantes na sala. A pasta **MVP Desktop - privados e anexos** acrescenta envio/resposta privada entre duas contas, historicos dos dois lados, rejeicao de mensagem vazia, paginacao, envio anonimo rejeitado, upload/download com conteudo preservado e consulta ao mesmo servidor pelo segundo cliente. A API nao expoe PATCH nesses controladores.

Esta colecao complementa a suite backend; nao cobre todos os endpoints nem substitui seus testes de permissoes, recuperacao de senha, Google, uploads, dispositivos, WebSocket e Redis. SDP de teste verifica sinalizacao; audio, video e compartilhamento real exigem clientes WebRTC.

Execucao aprovada em 09/10/2026: **44 requisicoes e 100 verificacoes**, nenhuma falha. Veja o [relatorio da regressao desktop](../REGRESSAO_ACEITE_DESKTOP_2026-10-09.md).
