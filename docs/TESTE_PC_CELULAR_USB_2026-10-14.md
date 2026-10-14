# Ligação e transmissão PC ↔ celular — 14/10/2026

Objetivo: confirmar voz e imagem recebidas nas duas versões com celular físico. O resultado manual permanece pendente até observar e ouvir os dois lados.

## Preparação

- Na raiz: `./iniciar-samuschat.ps1` com o celular conectado por USB e depuração autorizada.
- Desktop: `npm run dev -- --port 5173 --strictPort` em `samuschat-desktop/`; abrir http://127.0.0.1:5173.
- Entrar com **duas contas diferentes**, uma no PC e outra no celular. Manter ambos os clientes abertos. Alex é demonstração e não recebe chamadas.
- Manter PC e celular na mesma rede Wi-Fi. O celular foi encontrado em `192.168.1.11` durante a preparação. USB encaminha a API, não a mídia WebRTC.
- Permitir microfone no navegador e no Android. Usar fones em um lado para reduzir eco entre os aparelhos próximos.

## Rodada 1 — voz e tela do PC

1. Em Amigos, abrir o contato real da conta do celular e iniciar pelo telefone; aceitar no celular.
2. Aguardar conexão. Falar no PC e confirmar voz no celular; falar no celular e confirmar voz no PC. Se aparecer **Ativar áudio** no PC, clicar.
3. No PC, abrir a engrenagem e escolher 750p/30 fps para a primeira rodada.
4. Clicar **Compartilhar tela**, escolher uma tela/janela e confirmar no seletor do navegador.
5. Confirmar a prévia no PC e a mesma imagem recebida no celular. Mover uma janela ou rolar uma página por 30 segundos para conferir atualização, legibilidade e travamentos.
6. Parar a transmissão; confirmar que a imagem sai do celular e que a conversa de voz continua.

## Rodada 2 — tela do celular

1. Na mesma chamada, tocar no controle de compartilhar tela do Android e autorizar **Iniciar agora**.
2. Confirmar a prévia no celular e a tela recebida no PC. No PC, ampliar a transmissão recebida se necessário.
3. Abrir uma tela sem dados privados e rolar por 30 segundos. Confirmar movimento, orientação e legibilidade no PC.
4. Parar a transmissão e confirmar que o áudio da chamada continua nos dois lados.
5. Encerrar e confirmar fechamento nos dois clientes. Repetir uma chamada iniciada pelo celular para conferir convite no sentido inverso.

## Registro de feedback

| Verificação | Resultado observado |
| --- | --- |
| Convite PC → celular e aceite | Pendente |
| Voz PC → celular | Pendente |
| Voz celular → PC | Pendente |
| Prévia da tela do PC | Pendente |
| Tela PC → celular: movimento/legibilidade/atraso | Pendente |
| Parar tela do PC preserva voz | Pendente |
| Prévia da tela do celular | Pendente |
| Tela celular → PC: movimento/legibilidade/atraso | Pendente |
| Parar tela do celular preserva voz | Pendente |
| Encerramento nas duas versões | Pendente |
| Convite celular → PC | Pendente |

Na primeira rodada, o desktop não capturava o áudio da tela nem reproduzia o canal de som interno do Android. O usuário confirmou imagem nos dois sentidos e microfone do celular, mas relatou ausência de voz do PC e de som das transmissões. A [correção de áudio e o novo roteiro](REGRESSAO_AUDIO_PC_CELULAR_2026-10-14.md) adicionam seleção de microfone no PC e áudio de transmissão para a ligação individual. O reteste deve incluir o som de um aplicativo, além da voz.

Para diagnóstico Android, na raiz: `./.android-sdk/platform-tools/adb.exe -s 0054124296 logcat -d -s CallStats:I '*:S'`. Não é necessário limpar o log. No PC, registrar o aviso exibido e os dados de transmissão no palco. Em caso de falha, anotar a direção, se a voz continua, se a prévia funciona e a mensagem exata.

Preparação confirmada: celular moto g(7) play reconhecido; launcher completo aprovado com API saudável, APK atual e atividade aberta. Desktop Vite iniciado na porta 5173. Esses checks não substituem ouvir e observar a chamada real.
