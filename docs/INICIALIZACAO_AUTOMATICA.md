# Inicializa??o pelo celular USB

Na raiz do projeto:

```powershell
.\iniciar-samuschat.ps1
# Com mais de um celular conectado:
.\iniciar-samuschat.ps1 -Serial 'SERIAL_DO_CELULAR'
# For?ar recompila??o e instala??o:
.\iniciar-samuschat.ps1 -Rebuild
```

Ative Op??es do desenvolvedor e Depura??o USB, conecte um cabo de dados e aceite a autoriza??o no celular. Confira os identificadores com `.\.android-sdk\platform-tools\adb.exe devices -l`. O launcher seleciona um ?nico aparelho f?sico. Aparelhos sem autoriza??o, offline ou sele??o amb?gua geram uma orienta??o antes de iniciar os servi?os. N?o precisa de AVD nem inicia emulador.

O launcher prepara Docker, PostgreSQL, Redis e API; aguarda o Android, aplica `adb reverse tcp:8080 tcp:8080`, gera o APK debug com `http://127.0.0.1:8080/` e instala com `adb install -r` quando o hash difere. Preserva os dados da conta e abre o app. Execute novamente ap?s reconectar o cabo.

A API iniciada pelo launcher recebe `FILE_BASE_URL=http://127.0.0.1:8080` para anexos. Se a API j? estiver executando com outra URL de anexos, reinicie-a com essa configura??o. Google depende dos clientes OAuth descritos em [instala??o no celular](INSTALACAO_CELULAR.md).

Mantenha o cabo conectado para REST, WebSocket e anexos. Chamadas WebRTC e transmiss?o de tela precisam de conectividade de rede entre participantes ou TURN: o encaminhamento USB da API n?o transporta a m?dia UDP.

No Android Studio, selecione o celular como destino do Run ap?s executar o launcher. O Run n?o inicia Docker/API. O desktop continua com `npm run dev` em `samuschat-desktop/`.

Logs da API: `logs/api.stdout.log` e `logs/api.stderr.log`. Limites de espera: Docker 180 s, API 240 s e Android 240 s. Servi?os permanecem ativos ao fechar o app. Para parar PostgreSQL/Redis sem apagar dados: `docker compose stop`.

Desde 14/10/2026 o fluxo padr?o usa celular USB. Relat?rios anteriores de emulador permanecem como hist?rico; scripts de chamadas entre emuladores s?o ferramentas legadas fora do launcher.

Valida??o em 14/10/2026: moto g(7) play com Android 10 reconhecido por USB; testes do seletor e dos probes aprovados; 35 testes Android sem falhas; build e lint aprovados. Execu??o completa do launcher confirmou Docker, PostgreSQL, Redis, API, encaminhamento USB, instala??o com preserva??o de dados, conectividade TCP a partir do celular e abertura da MainActivity. Login, chamadas e transmiss?o no aparelho n?o foram exercitados nesta rodada.

## Computador

Dê dois cliques em `iniciar-samuschat-pc.cmd`, na raiz, ou execute `./iniciar-samuschat-pc.ps1`. O launcher PC reutiliza os serviços do celular, prepara o que falta e abre o navegador. Veja [instruções e validação](LAUNCHER_PC.md).
