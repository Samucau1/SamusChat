# Ligação PC: interface, qualidade e prévia

Entrega de 12/10/2026. Ligações individuais e canais de chamada usam um palco escuro, participantes em blocos, avatares e controles com ícones na parte inferior. Microfone, compartilhar/parar tela, prévia, encerrar e engrenagem têm nomes acessíveis e indicação de estado.

## Comportamento

- A engrenagem abre **Qualidade da transmissão** em janela modal. Resolução, FPS, sugestão para a máquina e ajuste automático ficam nessa janela. Escape, fechar e Concluído retornam à ligação.
- As opções continuam 750/900/1080/1440/2160p e 30/60 fps, salvas no navegador. Durante a captura, resolução/FPS ficam bloqueados; parar e reiniciar aplica outra seleção. O ajuste automático pode ser alterado durante o envio.
- A prévia local começa visível nas duas modalidades, usando a mesma captura e áudio silenciado. Ampliar permite assistir à própria transmissão; ocultar a prévia preserva o envio ao outro participante.
- Qualidade configurada, FPS/dimensões medidos, bitrate e avisos permanecem no palco, fora da janela de configurações. Uma fonte parada ou sem destinatários pode ainda não fornecer estatísticas.
- Cancelamento do seletor mantém a chamada. Falhas de captura orientam a tentar outra fonte, qualidade, navegador ou permissão; o áudio continua disponível para tentar novamente.

## Compatibilidade e diagnóstico

O código anterior exigia `maintain-framerate-and-resolution` e propagava rejeições do codificador. Agora tenta essa preferência e, apenas para TypeError/NotSupportedError, consulta parâmetros novos e tenta `maintain-resolution`. Se as preferências opcionais não forem aceitas, mantém limites e usa a indicação de conteúdo `detail`. Cada tentativa preserva teto de bitrate, FPS e escala, sem alterar resolução da captura.

A primeira alternativa experimental priorizava FPS. O teste de recepção observou queda para 225p; essa alternativa foi removida. A versão final prioriza resolução e passou a verificação de vídeo recebido com pelo menos 750p. A preferência compatível não garante simultaneamente FPS constante sob carga: os dados medidos são apresentados separadamente, e as opções/ajustes do app conservam o piso configurado de 750p/30 fps.

O usuário confirmou Edge com tela inteira. Uma sondagem adicional no Edge 155.0.4283.45, com perfil isolado e sem dispositivo sintético, capturou um monitor via API nativa: captura configurada em 1600×900/30 fps e vídeo recebido/decodificado em 1600×900. Foram exercitadas captura, restrições, negociação, substituição da trilha, parâmetros do emissor e recepção local. Não foram gravados imagens ou vídeo; apenas metadados. A automação usou modo sem interface e seleção automática do monitor, portanto não reproduz o seletor manual nem as configurações do perfil habitual do usuário. O erro original não foi reproduzido nessa sondagem; esse perfil ainda exige reteste. Não foi confirmado que sua falha veio da preferência de codificação: Edge 155 e Chromium 156 disponíveis aceitaram a preferência estrita no teste de codificador. A rejeição foi reproduzida por injeção de NotSupportedError em teste, e o fluxo continuou com codificação real e vídeo recebido. Chrome não estava instalado no caminho pesquisado pelo Playwright.

Referências de contrato: [preferências de degradação — W3C](https://w3c.github.io/mst-content-hint/#degradation-preference-when-encoding) e [captura de tela e restrições — MDN](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia).

## Validação

| Verificação | Resultado |
| --- | --- |
| TypeScript e build Vite | Aprovados; JavaScript 313,06 kB, gzip 95,94 kB |
| Regressão desktop — Chromium | 18 testes aprovados em 39,1 s |
| Regressão desktop — Edge instalado | 18 testes aprovados em 41,1 s |
| Monitor real — Edge, perfil isolado sem interface | Captura configurada 900p/30; substituição do vídeo e decodificação 900p aprovadas |
| Ligação individual com dois clientes | Convite/aceite, áudio, erro de captura recuperável, compatibilidade do emissor, prévia, recepção, encerramento |
| Canal com dois clientes | Prévia silenciada, recepção a 1080p, ajuste 60 → 30 fps por feedback simulado, nova transmissão no piso 750p/30 |
| Captura nativa Chromium, fonte sintética | 900p/30 e ajuste 750p/30 aceitos; trilha encerrada no fim |
| Prévia ampliada em 1024×768 | Sem transbordamento horizontal ou corte por altura da grade |
| Revisão visual local | Palco, modal e prévia individual/canal inspecionados |

Os testes de integração usam a API H2 real em localhost:18085. Microfone e fontes de vídeo são sintéticos; o teste nativo exercita o pipeline de captura do navegador com dispositivo de teste, sem gravar a tela pessoal. O teste de pressão de CPU injeta estatísticas, sem sobrecarregar a máquina. Não comprovam economia percentual, seletor físico do Windows, áudio real, interoperabilidade Android ou estabilidade de FPS na rede pública.

Logs locais: `logs/desktop-call-regression-18-tests.log`, `logs/desktop-call-design-build-final.log` e sondagens `logs/desktop-native-encoder-probe.log`/`logs/desktop-native-capture-probe.log`. Capturas locais: `logs/desktop-call-settings.png`, `logs/desktop-call-sharing.png` e `logs/desktop-room-sharing.png`. Logs/capturas permanecem fora do Git.

## Repetir no Edge

Na pasta PC, com a API H2 em 18085:

```powershell
$env:SAMUSCHAT_TEST_BROWSER_CHANNEL = 'msedge'
$env:SAMUSCHAT_TEST_API_URL = 'http://127.0.0.1:18085'
npm run test:e2e
```

Sem a variável de canal, a suíte usa o Chromium do Playwright. O Edge precisa estar instalado. Na raiz, com o PC em 5175, `node scripts/probe-desktop-edge.mjs` usa fonte sintética. `node scripts/probe-desktop-edge.mjs --physical-monitor` captura brevemente um monitor real e envia somente entre pares locais do mesmo navegador, sem salvar imagens/vídeo; não executar esse modo durante a exibição de informações sensíveis. Resultado apenas de metadados em `logs/desktop-edge-physical-probe.json`. A [evidência desta execução](security/2026-10-12/correcoes/edge-monitor-results.json) conserva somente dimensões, versão e resultado das etapas. FPS configurado não equivale a uma medição de FPS entregue sob carga.

## Teste manual

1. Abrir http://127.0.0.1:5175 e atualizar com Ctrl+F5. Entrar com a conta fictícia do PC.
2. Iniciar uma ligação com outro cliente ou entrar em um canal de chamada.
3. Abrir a engrenagem, escolher 750p/30 ou outra qualidade e concluir.
4. Compartilhar uma tela, janela ou aba. Confirmar prévia local, ampliar e voltar à grade; verificar recepção no segundo cliente.
5. Ocultar a prévia e confirmar que o envio continua. Abrir a engrenagem durante a transmissão e verificar o bloqueio de resolução/FPS.
6. Parar, mudar qualidade e reiniciar. Cancelar o seletor e confirmar que a chamada continua.
7. Se a captura falhar, registrar navegador/versão, tipo de fonte e aviso exibido; repetir com outra fonte no piso 750p/30.
