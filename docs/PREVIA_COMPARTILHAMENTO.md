# Prévia da tela compartilhada no Android

## Comportamento

Ao autorizar **Compartilhar tela**, o transmissor vê **Sua tela • prévia** dentro do SamusChat. Em chamadas individuais, a prévia aparece no painel da ligação; em canais de chamada, aparece na linha do próprio participante. As telas dos demais participantes continuam identificadas pelo nome. Os painéis permitem rolagem quando houver várias transmissões.

A prévia recebe exatamente a faixa de vídeo local que é enviada por WebRTC. Não há outra autorização de captura, outra conexão de mídia nem uma cópia recebida pela rede. Na sala, uma única faixa continua ligada a todos os pares, inclusive às conexões criadas para participantes que entram depois do início da transmissão.

Parar o compartilhamento, revogar a autorização pelo Android ou encerrar a chamada remove a prévia. Parar a tela mantém o microfone e a ligação. Se a captura inclui o próprio SamusChat, a prévia pode aparecer repetida dentro da imagem capturada, como ocorre ao compartilhar uma janela que exibe sua própria captura.

## Implementação

- `RtcCall.localVideo` e `RoomRtc.localVideo` disponibilizam as faixas existentes de captura.
- `ScreenShareVideo` é o componente comum para prévia e recepção: preserva a proporção, não espelha a tela, refaz a associação ao mudar a faixa/contexto EGL e remove o sink/libera o renderizador ao sair da composição.
- A imagem usa `TextureView` com `EglRenderer`, compondo dentro do diálogo/rolagem em vez de uma `SurfaceView` em camada separada. A textura é dimensionada pela proporção dos quadros, e o componente mostra **Aguardando imagem…** até o primeiro quadro efetivamente desenhado.
- `CallPanel` exibe a prévia local e, independentemente, o vídeo remoto.
- `CallRoomScreen` escolhe a faixa local para o próprio participante e as faixas recebidas para os demais.
- O log `ScreenPreview`, apenas em debug, registra o primeiro quadro desenhado pelo renderizador EGL e informa se é local. Não registra conteúdo da tela.

O backend, a negociação de chamada, o áudio interno e as permissões de transmissão permanecem com os contratos existentes.

## Validação

As tarefas Android `testDebugUnitTest`, `assembleDebug` e `lintDebug` passaram: 24 testes sem falhas; lint com zero erros e 14 avisos. Foi gerado também um APK de regressão para API isolada na porta 8081, com TURN relay.

Após relato de prévia sem imagem, foram verificadas duas situações: o AVD pessoal ainda usava um APK antigo, corrigido com atualização preservando os dados; depois, no APK com a prévia, o usuário viu o título mas não a imagem, apesar do registro de um quadro local no renderizador anterior. O componente foi trocado para `TextureView`, com indicação de espera e sinal de primeiro desenho via `EglRenderer`. Build e 24 testes Android passaram novamente; lint terminou com zero erros e 16 avisos (dois de construtor de views privadas criadas programaticamente). O APK atualizado foi instalado preservando os dados.

Com essa versão, foi conferida uma captura real do emulador durante o compartilhamento: **Sua tela • prévia** exibiu a imagem do conteúdo transmitido em uma sala com um participante. O log do novo processo confirmou `firstFrame local=true`, e o Android confirmou a projeção ativa. A captura fica apenas em `logs/samus-preview-live.png`, fora do Git, pois contém conteúdo escolhido pelo usuário. Essa verificação comprova a prévia local na sala; não comprova recepção por outro aparelho, prévia no diálogo individual, entrada tardia ou encerramento/reinício.

Os scripts `test-calls-emulator.ps1` e `test-call-rooms-emulator.ps1` passaram a exigir a prévia no transmissor, o primeiro quadro renderizado local/remoto e a remoção da prévia ao parar a transmissão. Usam somente emuladores descartáveis e banco isolado, conforme os guias existentes. Uma execução incompleta não conta como aprovada.

Nesta alteração, a validação de mídia por emuladores **não foi concluída**. O terceiro AVD não pôde criar o disco por falta de espaço. As tentativas com dois AVDs foram interrompidas por falta de resposta do aplicativo/ADB antes de comprovar os quadros da transmissão. A tentativa local em uma sala também não completou o roteiro. Nenhum desses testes conta como aprovado. Os AVDs e a API criados somente para essas tentativas foram encerrados, e seus discos descartáveis removidos; o AVD pessoal não foi limpo. O APK final volta a usar a API padrão `http://10.0.2.2:8080/`, sem forçar relay.

Roteiro adicional de revisão:

1. Conectar uma chamada individual, iniciar compartilhamento e conferir a prévia local e o vídeo do receptor.
2. Parar/reiniciar e verificar que o renderizador volta a mostrar novos quadros.
3. Compartilhar dos dois lados e conferir prévia e vídeo remoto simultaneamente.
4. Na sala, transmitir com três participantes e verificar imagem no transmissor e nos dois receptores; entrar com um participante após a captura começar.
5. Revogar a captura pela interface do Android e encerrar a chamada durante a transmissão; conferir liberação e ausência de crash.

Chamadas com três participantes, entrada tardia, transmissão simultânea, prévia na chamada individual e dispositivos físicos precisam de execução específica para validar a mídia desta alteração; a arquitetura existente e testes anteriores não substituem essa rodada.
