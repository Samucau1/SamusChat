# SamusChat · Android

[← Apresentação do projeto](../README.md) · [Desktop](../samuschat-desktop/README.md) · [API](../chatapp-backend/README.md) · [Documentação](../docs/README.md)

Aplicativo nativo em Kotlin e Jetpack Compose, conectado à mesma API utilizada pela versão para computador. Inclui servidores, canais, mensagens privadas, anexos, chamadas individuais, salas de chamada e compartilhamento de tela.

<p align="center">
  <a href="../docs/images/chat.png"><img src="../docs/images/chat.png" width="240" alt="Conversa em canal no SamusChat Android"></a>
  <a href="../docs/images/validacao-midia-2026-10-08/screen-local-preview.png"><img src="../docs/images/validacao-midia-2026-10-08/screen-local-preview.png" width="240" alt="Compartilhamento de tela com prévia local"></a>
</p>

## Organização

Compose desenha as telas; ViewModels coordenam estado e ações; repositórios concentram os contratos REST, a sessão e a comunicação. As dependências são fornecidas por um container na aplicação e factories de ViewModel.

| Área | Implementação |
| --- | --- |
| Interface | Jetpack Compose, navegação Servidores / Amigos / Perfil |
| Dados | Retrofit, OkHttp e Coroutines |
| Sessão | JWT e DataStore privado ao aplicativo |
| Tempo real | STOMP/WebSocket, atualização por ID e histórico paginado |
| Chamadas | WebRTC, microfone, transmissão e recepção da tela |
| Notificações | Firebase Messaging opcional |

As conversas reais utilizam a API. A tela Amigos também possui o contato **Alex**, que é uma demonstração local e não cria uma conta no servidor. A foto de perfil é selecionada e persistida por conta neste dispositivo; a sincronização de avatar com o backend ainda é uma etapa futura.

## Abrir e executar

Pré-requisitos: Android Studio, JDK 17 ou 21, SDK Android 35 e Build Tools 34.0.0. O aplicativo requer **Android 8.0/API 26 ou superior**. Configure `ANDROID_HOME` ou `sdk.dir` no arquivo local `local.properties`.

Inicie a API com `.\setup.ps1` na raiz do repositório. Depois abra esta pasta no Android Studio e execute no emulador. A URL padrão `http://10.0.2.2:8080/` aponta para a máquina hospedeira a partir do emulador Android padrão.

Para usar o launcher que inicia o ambiente e abre o aplicativo, consulte [inicialização automática](../docs/INICIALIZACAO_AUTOMATICA.md).

## Gerar e instalar o APK de desenvolvimento

Na pasta `samuschat-android/`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

Para um celular físico, defina o IP da máquina que executa a API. O endereço abaixo é um exemplo; substitua pelo IP da sua rede:

```powershell
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://192.168.1.9:8080/
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O celular precisa estar conectado, com depuração USB autorizada, e conseguir acessar a API. A URL deve terminar com `/`. Para acessar pelo Wi-Fi, os dispositivos precisam de conectividade entre si e a porta da API deve estar acessível. `FILE_BASE_URL` no backend também deve usar um endereço alcançável pelo celular.

HTTP é permitido na variante de desenvolvimento. A distribuição de produção exige API HTTPS e WebSocket WSS; assinatura e hospedagem da beta precisam ser configuradas para a entrega externa.

## Testes e validação

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Regressão de 09/10/2026: **30 testes unitários aprovados**, zero falhas, erros ou ignorados. Lint: **zero erros**, 16 avisos e 1 informação. Consulte o [relatório consolidado](../docs/REGRESSAO_ACEITE_DESKTOP_2026-10-09.md).

A [regressão de mídia e estabilidade de 08/10/2026](../docs/REGRESSAO_MIDIA_ESTABILIDADE_2026-10-08.md) registra 19 cenários em dois emuladores, com capturas da tela transmitida e recebida. Áudio interno depende de Android compatível e da permissão de captura do aplicativo reproduzido. A validação entre desktop e Android e em redes externas ainda precisa de uma rodada específica.

## Integrações opcionais

| Integração | Configuração |
| --- | --- |
| Login Google | Cliente OAuth Android para `com.samuschat`/SHA-1 e Client ID Web correspondente no backend e em `-PgoogleClientId=...` |
| Recuperação por email | SMTP configurado na API |
| Push Firebase | `app/google-services.json` local e credencial de serviço no backend |
| STUN/TURN | Configuração ICE fornecida pela API para chamadas |

O login Google está implementado no Android, mas sua configuração real está pendente. Arquivos locais e credenciais das integrações ficam fora do Git. Veja [autenticação](../docs/AUTENTICACAO.md) e [guia Android](../docs/ANDROID.md).

## Guias detalhados

- [Interface, contratos e configuração Android](../docs/ANDROID.md)
- [Mensagens privadas](../docs/MENSAGENS_PRIVADAS.md)
- [Envio de anexos](../docs/ANEXOS_ANDROID.md)
- [Chamadas individuais, tela e áudio](../docs/CHAMADAS_INDIVIDUAIS.md)
- [Salas de chamada e permissões](../docs/CANAIS_DE_CHAMADA.md)
- [Prévia da tela compartilhada](../docs/PREVIA_COMPARTILHAMENTO.md)
- [Validação de mídia e estabilidade](../docs/REGRESSAO_MIDIA_ESTABILIDADE_2026-10-08.md)
- [Índice completo](../docs/README.md)
