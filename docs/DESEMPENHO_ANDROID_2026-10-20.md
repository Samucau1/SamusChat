# Desempenho da captura Android — 20/10/2026

Este documento registra as primeiras medições. O usuário ainda relatou instabilidade depois delas. A continuação e a configuração mais recente estão em [Estabilidade de mídia](ESTABILIDADE_MIDIA_2026-10-20.md).

## Problema

Após a correção de áudio e imagem, o usuário relatou lentidão no Moto G7 Play. Esta etapa reduz o orçamento de vídeo e mede o resultado. A aprovação manual e a autorização para commit/push desta versão ainda estão pendentes. O ajuste dos botões de navegação sem animação também permanece nesta árvore de trabalho.

## Investigação e alteração

O perfil anterior usava teto de 30 fps, 4 Mbit/s e `MAINTAIN_RESOLUTION`. O receptor confirmou 720 × 1512 a 29–30 fps. O capturador Android ignora o argumento de fps de `startCapture`; portanto, o limite agora alcança também `VideoSource.adaptOutputFormat`, antes da codificação, e os parâmetros RTP.

| Perfil | Seleção por sessão | Máximos lado menor/maior | Teto fps | Teto bitrate por conexão |
| --- | --- | --- | --- | --- |
| Econômico | low-RAM, pouca memória disponível reportada pelo sistema ou RAM física até 3 GiB; memória desconhecida também usa este perfil | 720 / 1600 px | 24 | 2 Mbit/s |
| Equilibrado | Demais aparelhos | 900 / 1920 px | 24 | 3 Mbit/s |

Esses limites são escolhas conservadoras do projeto. O Moto mantém 720 × 1512. A primeira build desta etapa usou 15 fps; após o teste, o usuário pediu explicitamente 24 fps. O teto final é 20% menor que os 30 fps anteriores, com bitrate de 2 Mbit/s no aparelho. Não há ampliação da fonte. A política `BALANCED` permite ao WebRTC adaptar resolução e fps sob carga. A memória é consultada apenas ao criar o monitor de compartilhamento; não há novo polling de CPU/memória. Não foi criado um controlador próprio de adaptação.

Chamadas individuais e salas usam a mesma política, inclusive para um participante que conecta durante o compartilhamento. A sala permanece em malha, com custo crescente por destinatário. Não há mudança de áudio, autenticação ou limite de participantes, nem nova dependência.

Referências oficiais consultadas:

- [ScreenCapturerAndroid: ignoredFramerate](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/ScreenCapturerAndroid.java).
- [VideoSource: adaptação de quadros e dimensões](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/VideoSource.java).
- [WebRTC: adaptação de vídeo](https://webrtc.googlesource.com/src/+/cd09858f4a3f45e2c60508ababac6031986cdf7d/video/g3doc/adaptation.md).
- [Android: gerenciamento de memória](https://developer.android.com/topic/performance/memory/manage-app-memory).

## Medições iniciais — versão intermediária de 15 fps

O script `scripts/measure-android-performance.mjs` coleta quatro leituras de `top`, a cada três segundos, descarta a primeira e registra PSS, memória disponível, swap, temperatura/status térmico e renderização. Evidências em `logs/android-performance/`, fora do Git. Não grava áudio, vídeo ou credenciais. CPU usa unidades de um núcleo: 100% não representa o processador inteiro.

| Cenário | CPU média antes/depois | PSS antes/depois (KiB) |
| --- | --- | --- |
| Sem chamada | 36,6% / 1,8% | 76.779 / 82.732 |
| Chamada de voz | 68,2% / 63,4% | 125.301 / 110.586 |
| Compartilhando YouTube | 109,0% / 97,5% | 110.210 / 105.384 |

**Limitações:** o primeiro repouso incluiu inicialização. O YouTube variou de 640 × 360 para 1280 × 720 entre execuções. Rede, swap e outros processos também variaram; não é um benchmark controlado nem comprova uma porcentagem de ganho causal. Não houve limitação térmica reportada (status 0).

Após o ajuste, o PC confirmou 720 × 1512 a 15 fps. O observador de áudio registrou 946 blocos decodificados e RMS máximo 0,103; sinal medido não substitui escuta manual.

Com outro aplicativo em primeiro plano, `gfxinfo` não registrou novos quadros do SamusChat. Não há medição válida de latência de toque nesses intervalos. As poucas renderizações em chamada não permitem concluir sobre fluidez. Esta parte exige validação manual e medições adicionais com interação controlada.

Foi feita uma amostra adicional com seis toques no controle de microfone, restaurando seu estado inicial: 170 quadros, 20,59% classificados como janky e P95 de renderização de 38 ms. Isso mede renderização durante interação, não latência exata de toque. Ao receber o vídeo do PC, outra amostra teve 305 quadros, 0,98% janky, P95 de 9 ms e CPU média de 88,1% de um núcleo. São cenários diferentes e não devem ser comparados como ganho antes/depois. Ainda há quadros lentos na interação.

Na transmissão PC → Android, o aparelho usou `OMX.qcom.video.decoder.vp8`, recebeu mais de mil quadros sem descarte observado e reproduziu mais de dois mil pacotes Opus de tela com rota 2 (alto-falante). O PC enviava aproximadamente 30 fps; o perfil novo limita o envio Android, não altera as preferências de captura do PC.

## Testes e reprodução

- 58 testes Android aprovados, incluindo três novos testes de seleção de perfil, preservação de pixels no Moto, rotação e limites de fontes pequenas/grandes.
- Lint e build debug/API 8080 aprovados: `logs/performance-profile-build.log`.
- 11 testes Playwright de áudio, reprodução e política de tela aprovados: `logs/performance-media-regression.log`.
- `scripts/test-android-screen-lifecycle.mjs` verifica no USB liberação da projeção, parada/retorno dos quadros e rotação; restaura as preferências de rotação em `finally`. Exige chamada de teste conectada e transmissão Android ativa.

O roteiro físico passou: dois ciclos completos, projeção liberada nas paradas, contagem de quadros estabilizada e retomada, orientação 1512 × 720 seguida de 720 × 1512 no receptor. Evidência: `logs/android-performance/lifecycle.json`. A inferência de áudio bidirecional usa sinal decodificado e progresso de reprodução; a aprovação auditiva atual segue pendente.

Build intermediária de 15 fps preservada em `dist/celular/desempenho-2026-10-20/SamusChat-desempenho-debug.apk`; SHA-256 `9E50046A7E3D9EA44F0C3DD9D5912353D294B752D436947A141CBE49C583145D`. `build-info.json` registra o commit-base e as alterações ainda sem commit. É uma build debug para API local 8080, não um APK release externo.

```powershell
# Pasta samuschat-android; ANDROID_HOME deve apontar para .android-sdk
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PapiBaseUrl=http://127.0.0.1:8080/
# Pasta samuschat-desktop
npx playwright test tests/device-audio.spec.ts tests/screen-policy.spec.ts tests/media-playback.spec.ts
# Raiz, com aparelho/chamada preparados
node scripts/measure-android-performance.mjs NUMERO_USB identificador-da-fase
node scripts/test-android-screen-lifecycle.mjs NUMERO_USB
```

Uma falha de SDP (`wrong state: stable`) apareceu na primeira preparação da chamada. Uma nova chamada conectou. A sinalização não foi alterada nesta etapa; investigar separadamente se a falha voltar a ocorrer.

## Validação manual pendente

O usuário confirmou PC → celular funcionando bem na primeira build desta etapa. Pediu 24 fps para o celular e relatou que celular → PC ainda trava ao assistir. Essa direção passou a ser o foco da reprodução na build de 24 fps. Não registrar o problema como resolvido sem nova confirmação.

## Repetição em 24 fps

Testes Android (58), lint e build passaram novamente: `logs/performance-24fps-build.log`. A versão de 24 fps foi instalada preservando login e dados. O probe `scripts/test-android-to-pc-playback.mjs` acompanhou o YouTube no celular e, no PC, os contadores RTP e o elemento de vídeo, inclusive seu relógio de reprodução.

Em aproximadamente 20 segundos, a fonte avançou 19,66 segundos e o receptor decodificou/exibiu 472 quadros, aproximadamente 24 fps. Não foi detectada amostra congelada enquanto a fonte avançava. O áudio apresentou RMS máximo 0,349. Evidências: `logs/performance-24fps-playback.log` e `logs/android-performance/playback-24fps.json`. Esse teste curto não exclui falha intermitente ou lentidão em sessões longas.

A amostra adicional durante envio em 24 fps registrou CPU média de 93,5% de um núcleo e PSS de 106.139 KiB, sem alerta térmico. As limitações de comparação descritas acima permanecem; não concluir uma redução causal de consumo apenas por essas amostras.

A confirmação manual do sentido celular → PC foi solicitada novamente com a transmissão ativa. Sala e sessão prolongada ainda precisam de validação manual. Nenhum commit/push foi feito nesta etapa.

- [ ] Escutar YouTube e microfone simultâneos nos dois sentidos.
- [ ] Comparar fluidez e legibilidade, incluindo tela cheia.
- [ ] Conferir rotação e início/parada em chamada individual e sala.
- [ ] Avaliar resposta da interface e aquecimento numa sessão prolongada.
- [ ] Registrar aprovação ou defeitos, preservar build/hash e solicitar autorização antes do commit/push.
