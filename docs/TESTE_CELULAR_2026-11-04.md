# Teste no celular — 04/11/2026

Ambiente: Moto G7 Play físico por USB, pacote `com.samuschat`, APK `1.2.1-beta-local` (`versionCode=4`). API local atualizada em `http://127.0.0.1:8080/`, PostgreSQL e Redis locais; encaminhamento `adb reverse tcp:8080 tcp:8080`.

## Resultados observados

- Instalação com `adb install -r`: aprovada, sem limpar os dados do app.
- Conta temporária de teste autorizada pelo usuário: login aprovado.
- Mensagem enviada por um segundo cliente HTTP: recebida ao vivo no Android, com aviso e conteúdo renderizado.
- Resposta enviada pela interface Android: confirmada no histórico da API do segundo participante, uma ocorrência.
- Encerramento forçado e reabertura do processo: sessão mantida e histórico das duas direções recuperado.
- Regressão de API `MessageControllerTests`, `DirectMessageRealtimeTests` e `SessionRefreshTests`: nove testes, nenhuma falha.
- Navegação no aparelho: aprovada com `test-android-navigation.ps1 -Cycles 5`; perfil, conversa de demonstração, servidores, gestos a 250/500 ms, limites de página e botão Voltar. Cinco ciclos de estresse (20 toques), mantendo o mesmo processo e sem crash no buffer consultado.
- Chamada individual Android/web: conexão WebRTC confirmada nos dois lados. O cliente web recebeu mais 19.696 bytes RTP de áudio em uma janela de dez segundos; o microfone do navegador era sintético. Não foi avaliada a qualidade audível nem demonstrada voz humana inteligível nos dois sentidos.
- Compartilhamento da tela Android: quadros decodificados no cliente web e progressão confirmada em uma janela adicional de cinco segundos.
- Encerramento da chamada: painel encerrado no celular; `dumpsys media_projection` confirmou captura inativa (`null`). Nenhum crash no buffer consultado para o processo.

## Problema encontrado

A sessão que estava salva antes dos testes recebeu HTTP 401 em `GET /api/users/me`. A interface exibiu “Não foi possível carregar sua conta. Confira a conexão com a API.”, apesar de a API estar acessível. O botão “Tentar novamente” repetiu o 401. Não foi determinada a causa da rejeição da sessão antiga; o aplicativo deve diferenciar sessão inválida de indisponibilidade da rede. Foi possível sair dessa sessão pela interface e entrar com a conta temporária.

## Evidências locais

- `logs/device-message-result-2026-11-04.json`
- `logs/device-api-regression-2026-11-04.log`
- `logs/device-navigation-2026-11-04.log`
- `samuschat-android/app/build/navigation-regression/result.json`
- `logs/device-call-result-2026-11-04.json`

Esses logs ficam fora do Git. Contas e conversas de teste foram criadas separadamente; não houve envio de mensagens a contatos existentes. O celular permaneceu conectado à conta temporária. Este teste não avalia login Google, anexos, áudio interno da tela, chamadas em sala, push com o app fechado, acesso fora do USB ou sessões prolongadas.
