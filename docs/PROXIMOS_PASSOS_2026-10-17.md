# Próximos passos do SamusChat — 17/10/2026

## Ponto de partida

Código publicado na branch `feature/call-channels`, commit `e552cc996c316c2e14f88cd62e785ac31500102f`.

O usuário confirmou áudio de transmissão nos dois sentidos entre PC e Android e melhora da imagem. Depois, relatou queda de desempenho no celular. O editor de avatar e a indicação visual de fala foram implementados; incluir sua aprovação manual na próxima regressão.

Últimas verificações: 55 testes Android aprovados, lint e builds Android/desktop concluídos; quatro testes de áudio no navegador aprovados; backend com 145 testes, sem falhas e três ignorados por condições de ambiente. Esses resultados não comprovam fluidez no aparelho.

Build de referência preservada em `dist/celular/validada-2026-10-16/`, com APK, pacote web e hashes. Os artefatos são locais e ignorados pelo Git. Detalhes em [Recorte e áudio Opus](RECORTE_E_AUDIO_OPUS_2026-10-16.md).

## 1. Investigar a lentidão no Android — prioridade atual

- [ ] Comparar o mesmo aparelho em repouso, com o app aberto sem chamada, em chamada de voz, recebendo tela e transmitindo tela com áudio.
- [ ] Repetir os cenários com a mesma fonte de vídeo, rede e duração; registrar processos concorrentes para evitar atribuir ao app a carga de outros serviços.
- [ ] Medir CPU, memória, swap, estado térmico, quadros enviados/recebidos e resposta da interface. Identificar o encoder e se há uso de software.
- [ ] Verificar liberação de captura, encoder, decoder, áudio, filas, listeners e tarefas após parar a transmissão ou sair da chamada.
- [ ] Comparar chamada individual e sala, aumentando participantes dentro do limite de seis.

Hipóteses a confirmar: captura com aproximadamente 2,5 vezes mais pixels por quadro, limite de fps duplicado, pressão de memória e processos concorrentes. A amostra anterior mostrou swap em uso, mas não isolou uma causa única nem indicou limitação térmica.

**Conclusão desta etapa:** relatório comparável antes/depois, com causa sustentada por medições. Não reduzir a qualidade sem avaliar nitidez e áudio.

## 2. Equilibrar qualidade e consumo

- [ ] A partir das medições, avaliar um perfil padrão equilibrado de resolução, fps e bitrate para aparelhos com menos recursos.
- [ ] Avaliar adaptação à carga do encoder e à rede, evitando mudanças constantes de qualidade.
- [ ] Preservar proporção da tela, legibilidade, áudio e independência do microfone.
- [ ] Conferir o efeito de preservar resolução quando a rede ou o aparelho precisam reduzir a carga.
- [ ] Comparar o resultado com a build preservada; registrar qualidade, fluidez e consumo.

**Conclusão desta etapa:** melhora medida de fluidez e aprovação manual do usuário, sem retorno da falha de áudio. Valores de qualidade serão definidos após a investigação.

## 3. Fortalecer a regressão e validar no aparelho

Criar ou ajustar os testes antes de executar a próxima validação da correção. Usar documentação oficial atualizada quando houver mudança de API, codec ou estratégia de captura.

- [ ] Manter testes de negociação Opus, compatibilidade PCM, sinal decodificado, filas limitadas e parada do envio.
- [ ] Atualizar a matriz de captura para os perfis escolhidos: retrato, paisagem, rotação, fonte pequena e tela grande, sem ampliar ou deformar.
- [ ] Acrescentar cobertura de ciclo de vida e reconexão onde a investigação identificar risco de recursos acumulados.
- [ ] Executar testes Android, lint, build; testes relevantes do backend; build e regressão desktop.
- [ ] Testar YouTube PC → Android e Android → PC, com áudio e microfone simultâneos, em chamada individual e sala.
- [ ] Testar tela cheia, compartilhamento recursivo, início/parada repetidos, troca de aplicativo, rotação, fone/alto-falante e interrupções de rede.
- [ ] Conferir avatares entre clientes, recorte salvo sem deformação, cancelamento e indicador de fala com microfone silenciado.
- [ ] Obter validação manual de áudio, nitidez e fluidez; registrar resultado por cenário.

**Limite:** testes sintéticos não substituem escuta real, comportamento de saída de áudio e avaliação de fluidez no celular. Testes reduzem o risco de regressão; não garantem ausência de falhas.

## 4. Revisar as funcionalidades de mensagens

- [ ] Conferir o estado atual das mensagens privadas por WebSocket e validar entrega entre PC e Android sem depender da consulta periódica.
- [ ] Testar reconexão, recuperação do histórico, duplicação e ordem das mensagens.
- [ ] Definir e implementar contagem de não lidas e notificações conforme o que ainda estiver pendente no código.
- [ ] Conferir privacidade: respostas públicas não devem expor e-mails de outros usuários.

**Conclusão desta etapa:** comportamento verificado no código e em dois clientes; não reimplementar recursos já concluídos.

## 5. Preparar a beta externa

Retomar o [plano beta](PLANO_BETA_2026-10-12.md), revisando as datas históricas e o que ainda falta. A aprovação local não equivale à aprovação externa.

- [ ] Definir hospedagem e endereço público conforme as escolhas do usuário.
- [ ] Preparar HTTPS/WSS, persistência de PostgreSQL/Redis/anexos e backup com restauração testada.
- [ ] Configurar TURN e testar chamadas entre redes diferentes, incluindo dados móveis.
- [ ] Preparar assinatura release, guardar a chave fora do Git, incrementar a versão e definir a transição da instalação debug.
- [ ] Gerar APK com URL externa; conferir login, sessão, mensagens, anexos e chamadas fora da rede local.
- [ ] Documentar instalação, limitações e como os testadores devem relatar problemas.

**Conclusão desta etapa:** APK identificado por hash, atualização conferida, cenários externos aprovados e autorização do usuário para distribuição.

## 6. Fechar cada nova versão

- [ ] Documentar problema, causa confirmada, alteração, evidências e limitações.
- [ ] Preservar APK e build web com hashes, configuração pública e referência ao commit correspondente.
- [ ] Após os testes e a aprovação manual, fazer commit e push quando autorizados para essa nova versão.
- [ ] Conferir igualdade entre o commit local e o remoto, incluindo todos os novos arquivos necessários ao código.
- [ ] Manter credenciais, chaves, logs e arquivos gerados fora do Git.

## Próxima ação concreta

Medir a queda de desempenho no Moto G7 Play com cenários controlados e comparar perfis de captura. A build funcional atual permanece como referência para detectar perda de áudio ou qualidade durante a otimização.
