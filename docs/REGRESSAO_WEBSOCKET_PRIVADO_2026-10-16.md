# Mensagens privadas por WebSocket — 16/10/2026

Android e PC receberam assinatura STOMP privada por usuário, preservando o envio HTTP e o histórico persistente. A mudança visual de navegação por gestos foi aprovada pelo usuário antes desta implementação.

## Protocolo de teste

Testes novos e ajustes do roteiro foram preparados antes de suas execuções. Referências oficiais consultadas:

- [Spring: destinos por usuário](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html).
- [Playwright: assertions com repetição automática](https://playwright.dev/docs/test-assertions).
- [Playwright: inspeção de frames WebSocket](https://playwright.dev/docs/network#websockets).

Critérios: publicação após commit e nenhuma entrega no rollback; recebimento pelos dois participantes; bloqueio de assinaturas arbitrárias, de terceiros e com curingas; JWT revogado bloqueando saída; distribuição por Redis real entre duas instâncias sem duplicação; filtragem da conversa no Android; preservação do histórico paginado e deduplicação REST/STOMP.

O cenário de navegador abre duas contas fictícias, espera o estado Ao vivo, bloqueia GET de histórico do receptor, envia a mensagem e exige tanto sua exibição única quanto um frame MESSAGE com o conteúdo. Depois simula modo offline, envia outra mensagem e verifica sua recuperação após reconectar. Também verifica preservação do rascunho quando o envio retorna 503.

O primeiro teste de desconexão encontrou o status Ao vivo mantido ao entrar offline. O controller passou a reagir ao evento offline, interromper o transporte e indicar reconexão. A rodada posterior passou.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Backend Maven verify | 139 testes; zero falhas, erros ou ignorados |
| Infraestrutura isolada | Redis real em 16379; migração PostgreSQL em 15435 aprovados |
| Android | 44 testes aprovados; assembleDebug e lintDebug aprovados |
| Desktop | TypeScript e build Vite aprovados |
| Chromium | 23 cenários aprovados |
| Edge instalado | Fluxo privado com entrega STOMP, reconexão e erro de envio aprovado |
| Postman/Newman | 143 requisições e 247 verificações; zero falhas |

Logs locais em `logs/direct-websocket-*.log`; relatórios Maven/Gradle nas pastas de build. A API dos testes de navegador e Postman usou H2 descartável em 18082, com contas fictícias. Containers dedicados foram usados para os testes Redis/PostgreSQL; dados de desenvolvimento não foram usados pela regressão.

## Ambiente e aceite manual

A API local foi reiniciada com a implementação atual e o APK debug atualizado por USB com preservação dos dados. PC em `http://127.0.0.1:5173`; celular usando encaminhamento USB para a API 8080.

Teste com contas diferentes: abrir a conversa correspondente nos dois clientes, conferir o destinatário, esperar Ao vivo e enviar mensagens nos dois sentidos. Conferir ausência de duplicatas, alternar conversas, minimizar/reabrir o Android e verificar a recuperação. A troca real PC ↔ celular depende deste aceite; os testes de navegador não a substituem.

Não lidas e notificações continuam como etapas seguintes. Commit e push autorizados pelo usuário em 16/10/2026. O teste manual PC ↔ Android não foi declarado aprovado.
