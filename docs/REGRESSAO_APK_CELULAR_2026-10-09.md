# APK para celular — 09/10/2026

APK preliminar gerado em `dist/celular/SamusChat-teste.apk`, pacote `com.samuschat`, variante debug, API `http://192.168.1.9:8080/`. Endereco observado no Wi-Fi do computador; conferir novamente se a rede mudar.

- Android: 30 testes, zero falhas e erros; assembleDebug aprovado.
- Lint: zero erros, 16 avisos e uma informacao.
- API: 20 testes direcionados aprovados, sem falhas, erros ou testes ignorados: AccountRecoveryTests, AuthSchemaUpgradeTest, GoogleGisCsrfTests e GoogleTokenValidationTests.
- Script de geracao executado com sucesso; sintaxe PowerShell conferida.
- Logs locais: `logs/apk-celular-build.log`, `logs/apk-celular-entrega.log` e `logs/apk-google-backend-tests.log`.

O Client ID Google nao estava configurado no ambiente consultado. A entrega usa explicitamente `-AllowWithoutGoogle`: login Google continua indisponivel neste APK. Os testes da API verificam regras e tokens assinados com chaves de teste; nao comprovam autenticacao Google real.

A API local nao respondeu na verificacao inicial. Depois, o Moto G7 Play com Android 10 foi conectado e autorizado; o APK 1.1.0 foi instalado com `adb install -r` e aberto com sucesso. Docker, PostgreSQL, Redis e API foram iniciados preservando os dados. Uma requisicao feita pelo proprio celular para `192.168.1.9:8080/actuator/health` retornou HTTP 200 e `status: UP`.

O usuario informou que realizou o teste com sucesso e que o app esta funcionando plenamente no aparelho. Este e um aceite manual relatado pelo usuario no ambiente local; nao foi fornecida uma lista individual de cenarios, por isso nao equivale a aprovar toda a matriz de chamadas, Bluetooth, troca de rede ou estabilidade prolongada. Login Google real continua pendente. Nao foi publicada uma API externa nem um link publico de download.

Para concluir OAuth, cadastrar clientes Web/Android, configurar `GOOGLE_CLIENT_ID` no `.env`, iniciar a API e gerar novamente pelo [guia de instalacao](INSTALACAO_CELULAR.md). O guia inclui a assinatura SHA-1 conferida e os caminhos Wi-Fi/USB.
