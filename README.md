# SamusChat

O MVP para computador está em [samuschat-desktop/](samuschat-desktop/README.md), com TypeScript, React e CSS integrado à mesma API do Android. Inclui autenticação, servidores, chat, anexos e chamadas individuais. Consulte o [plano MVC](docs/DESKTOP_MVC.md) e o [guia da integração desktop](docs/DESKTOP_API.md).

Para iniciar Docker, banco, API, emulador e abrir o app Android com um comando, execute `.\iniciar-samuschat.ps1` ou abra `iniciar-samuschat.cmd`. Veja a [inicialização automática](docs/INICIALIZACAO_AUTOMATICA.md).

Aplicativo Android em Kotlin/Jetpack Compose e API Java/Spring Boot para conversar em servidores e canais.

A navegação no celular tem **Servidores**, **Amigos** e **Perfil** na barra inferior. Servidores abre o painel lateral; Amigos inclui uma conversa de demonstração; Perfil permite alterar o nome da conta e escolher uma foto local.

Ao tocar em um amigo, a conversa privada abre com o campo de mensagem disponível e o telefone no topo para ligar, sem precisar de um servidor em comum. O botão de câmera compartilha a tela. Servidores oferecem canais de chat e salas de chamada com vários participantes. Os dois usuários precisam manter o aplicativo aberto para receber um convite individual.

## Documentação

- [Mensagens privadas entre amigos](docs/MENSAGENS_PRIVADAS.md)

- [Chamadas individuais, tela e áudio do dispositivo](docs/CHAMADAS_INDIVIDUAIS.md)
- [Nova etapa: perfis, permissões e canais de chamada](docs/CANAIS_DE_CHAMADA.md)

- [Índice de todos os documentos](docs/README.md)
- [Instalação, execução e arquitetura](docs/GUIA_PROJETO.md)
- [Aplicativo Android](docs/ANDROID.md)
- [Regressão e teste de toques do novo design](docs/REGRESSAO_NAVEGACAO.md)

Na raiz, execute `./setup.ps1` para iniciar os serviços e a API. Abra `samuschat-android/` no Android Studio para executar o aplicativo.

- [Recuperacao de senha, login Google e relatorio de testes](docs/AUTENTICACAO.md)
