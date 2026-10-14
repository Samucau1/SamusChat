# APK e teste no celular

O APK de teste usa o pacote `com.samuschat`, Android 8 ou superior e assinatura debug. Atualiza uma instalacao com o mesmo pacote e assinatura; nao desinstale a versao existente para contornar erro de assinatura sem antes avaliar os dados locais.

## Iniciar pelo USB

Ative Depura??o USB, conecte o celular e aceite a autoriza??o. Na raiz, execute `./iniciar-samuschat.ps1`. O launcher prepara os servi?os e instala o app preservando os dados. Com v?rios aparelhos, informe `-Serial SERIAL_DO_CELULAR`. Veja [inicializa??o autom?tica](INICIALIZACAO_AUTOMATICA.md).

## Configurar Google

No Google Cloud, use o mesmo projeto para os dois clientes OAuth:

1. Configure a tela de consentimento em Google Auth Platform. Se estiver em teste, cadastre os emails dos testadores.
2. Crie um cliente **Android**, pacote `com.samuschat`, com o SHA-1 da variante **debug** mostrado por `samuschat-android/gradlew.bat :app:signingReport` (execute dentro da pasta Android).
3. Crie um cliente **Aplicativo da Web**. Coloque seu ID, terminado em `.apps.googleusercontent.com`, em `GOOGLE_CLIENT_ID=` no `.env` da raiz. Nao e necessario colocar Client Secret no app.
4. Reinicie a API com `./setup.ps1`. Uma API que ja estava executando nao recebe a nova variavel automaticamente.
5. Gere novamente o APK com o script abaixo, que le esse mesmo ID do `.env`. O ID Android nao substitui o ID Web.

Referencia: [implementacao Android do login Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

Assinatura debug conferida em 09/10/2026 neste computador:

```text
Pacote: com.samuschat
SHA-1: 6F:D8:8E:62:6B:A0:EE:55:71:40:68:E3:16:B7:0A:3E:06:E2:0D:80
```

Se a chave debug mudar ou o APK for assinado para release, obtenha e cadastre a assinatura correspondente.

## Gerar e instalar pelo Wi-Fi

Na raiz do projeto, substitua o IP pelo IPv4 atual do computador mostrado por `ipconfig`:

```powershell
./scripts/build-apk-celular.ps1 -ApiBaseUrl 'http://192.168.1.9:8080/'
```

O script executa testes Android, lint, build e relatorio de assinatura; entrega `dist/celular/SamusChat-teste.apk` e seu hash em `build-info.json`. Para gerar apenas uma versao preliminar enquanto OAuth nao foi configurado, acrescente `-AllowWithoutGoogle`; essa versao nao permite login Google.

1. Mantenha computador e celular no mesmo Wi-Fi, com a API executando por `./setup.ps1`.
2. Para anexos, configure no `.env` `FILE_BASE_URL=http://192.168.1.9:8080` antes de iniciar a API.
3. Abra no navegador do celular `http://192.168.1.9:8080/actuator/health`. Espere `status: UP`. Se nao responder, verifique a API, isolamento de clientes no roteador e permissao do Java/porta 8080 no firewall para a rede privada.
4. Transfira o APK para Downloads do celular usando cabo, Drive ou outro meio de sua escolha. Abra o arquivo e permita a instalacao por essa origem quando o Android solicitar. Depois toque em Instalar e Abrir.

Este endereco exige a mesma rede e o computador ligado. Para acesso externo, gere o APK com a URL HTTPS da API hospedada e configure os enderecos publicos de anexos e TURN.

## Fluxo padr?o por USB

Ative Opcoes do desenvolvedor e Depuracao USB, conecte o cabo e aceite a autorizacao no celular. Gere com `-ApiBaseUrl 'http://127.0.0.1:8080/'`. Depois, na raiz:

```powershell
./.android-sdk/platform-tools/adb.exe devices
./.android-sdk/platform-tools/adb.exe -s SERIAL_DO_CELULAR reverse tcp:8080 tcp:8080
./.android-sdk/platform-tools/adb.exe -s SERIAL_DO_CELULAR install -r ./dist/celular/SamusChat-teste.apk
```

Substitua `SERIAL_DO_CELULAR` pelo identificador mostrado em `devices`. Reaplique `reverse` ao reconectar o aparelho. O cabo deve permanecer conectado para a API nesse modo. Para anexos por USB, configure `FILE_BASE_URL=http://127.0.0.1:8080` no backend.

## Testes no aparelho

- Google: entrar com conta de teste, sair e entrar novamente; confirmar retorno ao login ao cancelar. Testar conta nova e conta existente. Email externo ao Gmail/Workspace pode exigir a senha atual para vincular a conta.
- Sessao: fechar e reabrir o app; confirmar que a conta permanece conectada; sair pelo Perfil.
- Chat: enviar mensagem e anexo e conferir no segundo cliente.
- Chamadas: com um segundo cliente, testar convite, audio, viva-voz, microfone, tela compartilhada e encerramento. Conferir tambem segundo plano e tela bloqueada.

Testes automatizados da API usam identidades e chaves de teste. A aprovacao deles nao comprova login com uma conta Google real; esse passo exige OAuth configurado, Play Services e acesso a internet no celular.
