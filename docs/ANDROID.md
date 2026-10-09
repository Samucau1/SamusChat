# SamusChat Android

Aplicativo Kotlin/Jetpack Compose integrado ao back-end deste repositório. Inclui cadastro, login, restauração de sessão, logout, criação e entrada em servidores, seleção de canais de texto, histórico paginado, mensagens REST com atualização STOMP, exibição de anexos e registro de dispositivos Firebase.

A navegação no celular usa uma barra inferior com **Servidores**, **Amigos** (ao centro) e **Perfil**. Amigos é a tela inicial; Servidores abre o painel lateral com os servidores da conta e seus canais. A barra inferior também permanece disponível nas conversas. Crie servidores no **+ superior esquerdo** do painel e abra a busca na **lupa superior direita**.

Ao abrir o teclado, a barra inferior fica oculta para manter o campo de mensagem logo acima dele; ela reaparece ao fechar o teclado. O espaço do teclado é aplicado no layout principal e consumido pelas telas internas para evitar afastamento adicional. Ajuste validado visualmente no emulador em 06/10/2026, com teclado aberto e fechado; APK e lint aprovados.

Tocar fora do campo em foco fecha o teclado e remove o foco, preservando o rascunho. O comportamento reutilizável está em `ui/components/KeyboardDismiss.kt`: observa o gesto sem consumir os eventos dos botões e compara o toque com os limites do campo em foco. Tocar dentro mantém a edição. Validado no emulador com toque interno, toque externo, preservação de texto e envio local na conversa de demonstração. Regressão antes do commit: 30 testes Android e 112 testes da API aprovados, incluindo Redis real; nenhuma falha, erro ou teste ignorado. APK gerado e lint com zero erros e 16 avisos.

A tela Amigos inclui busca e Alex, um contato de demonstração. A conversa aceita mensagens locais para experimentar a interface; não cria usuários no servidor nem envia mensagens diretas reais. O histórico de teste é temporário.

Em Perfil, o nome é carregado da conta e salvo pelo endpoint `PUT /api/users/me/username`. A foto é selecionada pelo seletor de documentos do Android e sua referência é persistida por conta neste dispositivo; ainda não há sincronização de avatar com o back-end. A opção de sair da conta está no Perfil.

## Executar

Abra a pasta `samuschat-android/` no Android Studio. Execute os comandos abaixo a partir dessa pasta. Use JDK 17 ou 21, SDK Android 35 e Build Tools 34.0.0. Configure `ANDROID_HOME` ou `sdk.dir` em `local.properties` (arquivo local ignorado pelo Git).

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`. O emulador acessa o back-end por `http://10.0.2.2:8080/`. Para celular físico na mesma rede:

```powershell
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://192.168.1.10:8080/
```

A URL deve terminar com `/`. HTTP é permitido apenas na variante debug. Para release, use uma URL HTTPS; o WebSocket será WSS. No back-end configure `file.base-url` com um endereço acessível ao dispositivo para exibir os anexos.

## Firebase opcional

Cadastre o aplicativo `com.samuschat` no seu projeto Firebase e coloque `google-services.json` em `app/`. O plugin Google Services só é aplicado quando esse arquivo existe. Configure também `firebase-service-account.json` no classpath do back-end, conforme `FirebaseConfig`. Não versione credenciais. Sem esses arquivos, login e chat funcionam, mas push fica desabilitado. Android 13+ solicita permissão de notificações após login.

## Contratos e decisões

- REST usa `{ success, data, message, error }`; erros HTTP são lidos do corpo de erro.
- O email usado nas mensagens próprias vem de `/api/users/me` e é salvo junto do JWT. DataStore é privado ao app, sem criptografia adicional; backup está desativado.
- STOMP conecta em `/ws/websocket`, envia JWT no frame CONNECT e assina `/topic/channel/{id}`. O back-end verifica autenticação e participação no servidor.
- O back-end publica mensagens REST, uploads e STOMP após o commit da transação. Mensagens são unificadas por ID no cliente para evitar duplicação entre histórico, REST e WebSocket.
- Histórico mantém ordenação cronológica e páginas de 50 mensagens. Erros de envio preservam o texto digitado. A conexão acompanha o ciclo de vida da tela; há reconexão manual com recarga do histórico.
- As dependências são fornecidas por um container na Application e factories de ViewModel, sem necessidade de Hilt.
- Os canais de texto permitem [selecionar e enviar anexos](ANEXOS_ANDROID.md), com legenda opcional e limite de 10 MB. Moderação e gerenciamento de membros ainda não possuem telas próprias.

## Validação manual em dois dispositivos

Validação automatizada realizada em 28/09/2026: 14 testes Maven aprovados (incluindo REST → STOMP via SockJS e WebSocket nativo, e rejeição de acesso anônimo); 3 testes Android aprovados; `assembleDebug` e `lintDebug` concluídos. Lint: 0 erros e 9 avisos de atualização de dependências/configuração de backup. Não havia dispositivo ou emulador conectado; o roteiro abaixo e o push Firebase real ainda precisam ser executados com dispositivos e credenciais do projeto.

1. Inicie PostgreSQL/Redis e o back-end. Cadastre duas contas.
2. Na primeira conta crie um servidor; na segunda entre usando o ID exibido.
3. Abra o canal geral em ambos e envie mensagens; confirme chegada imediata sem duplicatas e identificação do remetente.
4. Gere mais de 50 mensagens, reabra o canal e carregue páginas anteriores.
5. Desative a rede durante envio: o texto deve permanecer. Reative e reconecte.
6. Feche/reabra o aplicativo para verificar a sessão. Saia da conta e confirme retorno ao login.
7. Com Firebase configurado, teste push em segundo plano e remoção do dispositivo ao sair.

Referências: [compatibilidade AGP 8.7 / Gradle 8.9](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [protocolo STOMP](https://stomp.github.io/stomp-specification-1.2.html).
