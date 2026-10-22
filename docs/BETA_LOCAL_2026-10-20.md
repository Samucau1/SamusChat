# Beta local 1.2.0 — 20/10/2026

## Escopo

O usuario escolheu preparar a beta local porque ainda nao tem servidor e dominio. Esta entrega nao publica infraestrutura nem distribui uma release externa.

## Mensagens e leitura

- Mensagens privadas ja tinham STOMP/WebSocket na conversa aberta. A nova caixa privada acompanha a sessao autenticada, inclusive nas outras telas; nao abre um socket por contato.
- Ao conectar/reconectar, os clientes recuperam historico e contadores do servidor. O historico usa merge por ID e ordem deterministica; consultas de tres segundos ficam como alternativa quando o socket da conversa esta desconectado.
- `GET /api/direct-messages/unread` agrupa mensagens recebidas ainda nao lidas. As identidades publicas continuam substituindo emails de login na resposta.
- `POST /api/direct-messages/read?contact=...&throughId=...` marca apenas mensagens recebidas daquele contato ate uma mensagem que pertence a conversa. Chamadas repetidas sao idempotentes. Uma mensagem nova acima desse limite continua nao lida.
- A migracao Flyway V8 adiciona `read_at` e indice de consulta. Mensagens existentes sem registro de leitura aparecem como nao lidas; o estado anterior nao permitia inferir se foram lidas.
- Abrir a conversa em primeiro plano reconhece as mensagens carregadas. Abrir o app, outra conversa ou uma pagina adjacente do pager nao reconhece mensagens desse contato.
- Contadores sao recuperados no foco/reconexao. Uma leitura realizada em outro dispositivo nao e um recibo de leitura em tempo real: atualiza no proximo foco/reconexao.

## Avisos

- PC e Android mostram aviso dentro do app para mensagens fora da conversa aberta. Eventos repetidos sao deduplicados em uma janela limitada.
- O desktop oferece permissao opcional para notificacoes do navegador quando a pagina esta oculta. O aviso mostra o nome do remetente, sem reproduzir o corpo privado. Nao existe push web com o navegador fechado nesta entrega.
- A integracao FCM Android existente recebe notificacao privada generica apenas para o destinatario, depois do commit da mensagem. O envio usa fila limitada fora da resposta HTTP e sua falha nao interrompe a mensagem.
- Sem configuracao Firebase, o teste local comprova avisos internos e contratos de envio, nao entrega push pelo Google com o app fechado. As permissoes Android existentes continuam aplicadas.

## Como executar

1. Iniciar Docker Desktop e executar `iniciar-samuschat-pc.cmd` para API e interface PC.
2. Conectar o celular com depuracao USB e executar `iniciar-samuschat.cmd`. Reutilizar as contas existentes; nao limpar dados.
3. Para reconstruir: `scripts/build-apk-celular.ps1 -AllowWithoutGoogle` e `npm run build` em `samuschat-desktop`. O parametro permite ausencia de Google OAuth; se configurado no ambiente local, o script preserva o client ID.
4. Apos os testes, executar `scripts/package-local-beta.ps1`. O pacote local contem APK debug, web e manifesto SHA-256 em `dist/celular/beta-local-2026-10-20/` (ignorado pelo Git).

Esta versao usa versionCode 3 e versionName 1.2.0-beta-local. A assinatura debug existente permite atualizar o APK debug sem limpar a sessao. Ainda faltam hospedagem, HTTPS/WSS, TURN externo, assinatura release e validacao em redes diferentes para uma beta externa.

## Validacao e evidencias

| Verificacao | Resultado | Evidencia local |
| --- | --- | --- |
| Backend completo, incluindo privacidade, autorizacao, transacoes e migracoes V1–V8 | 147 testes, zero falhas/erros, tres ignorados por ambiente | `logs/beta-local-backend-tests.log` |
| Android | 60 testes aprovados, lint e APK debug aprovados | `logs/beta-local-android-build.log`, `logs/beta-local-android-package-build.log` |
| Regressao desktop | 42 testes aprovados, sem ignorados ou flakiness; inclui mensagens, reconexao, avatares, sala, tela cheia e audio | `logs/beta-local-desktop-regression.log` |
| Sala sintetica com tres navegadores | Cinco minutos, voz nos tres clientes, video e audio de tela nos dois receptores; liberacao da captura ao parar | `logs/beta-local-room-soak.log` |
| Mensagens nas duas contas reais via USB | PC → Android e Android → PC; aviso, nao lidas e reconhecimento de leitura nos dois clientes | `logs/beta-local-usb-messages.json` |
| Sala real Android → PC com YouTube | Um minuto: 1.446 quadros, 640 × 1344, 24 fps, 3.010 inicios de audio, nenhum novo congelamento/lacuna/sobreposicao; RTP de voz progrediu | `logs/beta-local-room-usb.json` |

O usuario confirmou nesta sala: **“Imagem e audio estao bons”**. A aprovacao manual anterior da chamada individual nos dois sentidos permanece registrada em [Estabilidade de midia](ESTABILIDADE_MIDIA_2026-10-20.md). A sala fisica desta etapa transmitiu Android → PC; os tres participantes simultaneos foram navegadores com fontes sinteticas, nao tres celulares fisicos.

A primeira tentativa da sala fisica foi descartada porque o YouTube voltou silenciado apos navegar. O roteiro passou a exigir fonte sem mute e sinal de audio antes da janela de medicao, sem corrigir silenciosamente interrupcoes durante essa janela. A primeira regressao SQL reprovou um indice parcial incompativel com H2; foi substituido por indice composto portavel e a regressao foi repetida. A primeira regressao desktop detectou atraso na exibicao de reconexao; o indicador passou a acompanhar diretamente o estado do socket global.

A revisao acrescentou cobertura de snapshots concorrentes: uma resposta antiga nao pode apagar uma chegada posterior, e uma consulta autoritativa deve limpar mensagens ja lidas em outro dispositivo. O teste isolado tambem cobre repeticao de eventos, eco do remetente e mensagens de terceiros.

Depois de parar a fonte e sair da sala, o Android ficou sem chamada/captura ativa. Amostra em repouso: CPU media 3,2% de um nucleo, PSS 83.284 KiB e estado termico 0. Nao houve quadros novos de interface nessa janela; portanto ela nao mede latencia de toque. Nao e comparacao controlada antes/depois nem promessa de consumo em outros aparelhos. Evidencia: `logs/beta-local-idle.log`.

Testes sinteticos e janelas de cinco minutos/um minuto nao garantem estabilidade em todas as redes ou sessoes de horas. Push FCM real, navegador fechado, redes distintas e distribuicao release externa continuam fora da validacao local.

## Referencias usadas

- [STOMP: reconectar e refazer inscricoes em onConnect](https://stomp-js.github.io/guide/stompjs/2018/06/28/using-stompjs-v5.html).
- [Spring Data JPA: transacoes nas consultas declaradas](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).
- [Android: permissao de notificacoes](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Artefatos preservados

- `SamusChat-beta-local-debug.apk`: 58886790 bytes; SHA-256 `59BB9D3B07F8BAC3B7E48EA887E267C6E7A4AA260375EE74068E55B3FE814F90`.
- `SamusChat-desktop.zip`: 110973 bytes; SHA-256 `6EABAE4E0120F82162B3D8AF389E4ACBDBF3C5641FC333682B343F0BF8B00E81`.
