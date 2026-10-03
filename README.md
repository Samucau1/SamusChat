# SamusChat

Aplicativo Android em Kotlin/Jetpack Compose e API Java/Spring Boot para conversar em servidores e canais.

A navegação no celular tem **Servidores**, **Amigos** e **Perfil** na barra inferior. Servidores abre o painel lateral; Amigos inclui uma conversa de demonstração; Perfil permite alterar o nome da conta e escolher uma foto local.

Qualquer conta cadastrada pode ligar para outra conta pelo telefone no perfil do contato, sem precisar de um servidor em comum. O botão de câmera compartilha a tela. Servidores oferecem canais de chat e salas de chamada com vários participantes. Os dois usuários precisam manter o aplicativo aberto para receber um convite individual.

## Documentação

- [Chamadas individuais, tela e áudio do dispositivo](docs/CHAMADAS_INDIVIDUAIS.md)
- [Nova etapa: perfis, permissões e canais de chamada](docs/CANAIS_DE_CHAMADA.md)

- [Índice de todos os documentos](docs/README.md)
- [Instalação, execução e arquitetura](docs/GUIA_PROJETO.md)
- [Aplicativo Android](docs/ANDROID.md)
- [Regressão e teste de toques do novo design](docs/REGRESSAO_NAVEGACAO.md)

Na raiz, execute `./setup.ps1` para iniciar os serviços e a API. Abra `samuschat-android/` no Android Studio para executar o aplicativo.
