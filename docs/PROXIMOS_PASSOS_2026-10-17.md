# Proximos passos do SamusChat ? atualizado em 20/10/2026

As quatro etapas locais foram executadas: validacao de midia, caixa privada global por WebSocket, nao lidas/avisos e preparacao da beta local. Detalhes, testes, limitacoes e build em [Beta local 1.2.0](BETA_LOCAL_2026-10-20.md).

## Concluido nesta versao

- Sala com tres participantes por cinco minutos em navegadores de teste, com voz, tela e audio.
- Sala real Android para PC a 24 fps com YouTube; o usuario confirmou imagem e audio bons.
- Mensagens privadas recebidas mesmo fora da conversa, recuperacao apos reconexao, deduplicacao e ordem do historico.
- Contadores persistentes de nao lidas e reconhecimento limitado as mensagens recebidas carregadas em primeiro plano.
- Avisos internos PC/Android, permissao opcional do navegador e envio privado generico integrado ao FCM existente.
- Regressao backend/Android/desktop, guia local e empacotamento do APK debug/web com SHA-256.

## Proxima etapa: uso continuado da beta local

- Avaliar sessoes de horas, consumo de bateria e comportamento em outros aparelhos.
- Conferir sala fisica tambem no sentido PC para Android e com mais aparelhos reais. O teste de tres participantes desta versao usa navegadores sinteticos.
- Registrar relatos de notificacoes e contadores durante o uso; leitura em outro dispositivo atualiza no foco/reconexao, sem recibo de leitura em tempo real.
- Configurar Firebase para validar push Android com app fechado, caso essa funcionalidade seja necessaria na distribuicao externa. O funcionamento de avisos internos nao comprova entrega FCM.

## Beta externa ? depende de infraestrutura

O usuario informou que ainda nao tem servidor/dominio e escolheu preparar a beta local. Quando definir o ambiente:

1. Configurar servidor, dominio, HTTPS/WSS e TURN.
2. Conferir persistencia de PostgreSQL/Redis/anexos, backup e restauracao.
3. Preparar chave release fora do Git, URL publica e transicao da assinatura debug.
4. Validar mensagens, anexos, chamadas e compartilhamento em Wi-Fi/dados moveis entre redes distintas.
5. Obter autorizacao para distribuir o APK identificado por hash aos testadores.

O [plano beta original](PLANO_BETA_2026-10-12.md) conserva o contexto historico; suas datas anteriores nao sao um novo prazo.
