# Regressão da navegação mobile

Validação local em 30/09/2026, Windows, JDK 21, Android API 35 (emulador Pixel 5).

## Alterações

- Barra inferior com Servidores, Amigos e Perfil, inclusive nas conversas.
- Servidores abre o painel lateral; Amigos inicia a navegação e oferece Alex como contato demonstrativo.
- Perfil carrega o usuário da API, atualiza o nome e guarda a referência da foto por conta no dispositivo.
- Validação de envio também no callback: toques repetidos não devem enviar uma mensagem que ficou vazia após o envio anterior.
- Documentos centralizados em `docs/`, com índice no README da raiz e links relativos atualizados.

## Testes automatizados

| Verificação | Resultado |
| --- | --- |
| Maven `verify`, incluindo Redis fan-out real | 65 testes, 0 falhas, 0 erros, 0 ignorados |
| Android `testDebugUnitTest` | 13 testes, 0 falhas, 0 erros |
| Android `assembleDebug` | APK gerado |
| Android `lintDebug` | 0 erros, 10 avisos |
| Regressão de interface + stress no emulador | 50 ciclos, 200 toques, aprovado |
| Links locais da documentação | Todos os destinos encontrados |

O stress e suas verificações finais levaram 60,1 segundos. O processo do aplicativo permaneceu o mesmo; Amigos e Perfil responderam após a sequência. Nenhum crash ou ANR do pacote foi encontrado nos buffers consultados. A primeira tentativa do script encontrou a hierarquia de UI ainda indisponível durante a abertura; foi adicionada uma espera antes da leitura e a execução completa seguinte passou.

Os quatro novos testes unitários de `HomeRulesTest` verificam busca sem distinção de maiúsculas e espaços externos, rejeição de mensagens vazias e acima de 2.000 caracteres, alteração de nome apenas após carregar o perfil e limite de 50 caracteres no nome normalizado. `ApiContractTest` também verifica autenticação, método HTTP e codificação do parâmetro de atualização do nome.

Os testes JVM validam regras; o script de regressão abaixo exercita a interface real. Não são testes instrumentados de Compose.

## Reproduzir

Na pasta `chatapp-backend`:

```powershell
$env:RUN_REDIS_TESTS = 'true'
.\mvnw.cmd verify -Dspring.profiles.active=test
```

Redis deve estar disponível em localhost:6379. Os testes Spring usam H2 isolado; não apagam o PostgreSQL de desenvolvimento.

Na pasta `samuschat-android`, com SDK configurado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Na raiz, com o aplicativo atualizado aberto no emulador e uma conta de teste autenticada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-android-navigation.ps1 -Cycles 50
```

O script verifica Amigos, conversa com Alex, Perfil e painel de servidores. Depois alterna Perfil → Amigos → Servidores → fechar painel, usando coordenadas obtidas da hierarquia real da tela. Ao terminar, exige o mesmo processo vivo e telas responsivas; consulta o buffer de crashes. O resultado JSON fica em `samuschat-android/app/build/navigation-regression/result.json`.

Logs completos permanecem fora do Git: `chatapp-backend/target/regression-design.log`, `samuschat-android/regression-design.log` e `samuschat-android/touch-regression.log`. Relatórios JUnit ficam nas pastas padrão de build de cada módulo.

## Limites

O stress é uma sequência determinística de toques, sem multitouch ou garantia de ausência de falhas em todo aparelho. O teste não cobre upload remoto de avatar, push Firebase nem mensagens diretas reais: a foto é local e Alex é uma demonstração. A suíte não substitui testes em dispositivos físicos.
