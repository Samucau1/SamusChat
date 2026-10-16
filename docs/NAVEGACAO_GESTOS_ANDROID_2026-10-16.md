# Navegação por gestos no Android — 16/10/2026

A faixa de fechamento da lista de servidores foi removida, incluindo o X. A barra com SamusChat, criar servidor (+) e busca ocupa agora o topo desse conteúdo.

As áreas usam HorizontalPager na ordem Servidores → Amigos → Perfil. Arrastar para a esquerda avança uma página; para a direita retorna. A barra inferior continua oferecendo acesso por toque. O botão de servidores dentro de um canal retorna à lista. O painel de chamada permanece fora das páginas.

## Critérios e referências

Os testes foram preparados antes da execução da alteração. O roteiro USB usa seletores de texto/semântica, coordenadas proporcionais ao display e consultas novas à interface. Exercita gestos de 250 e 500 ms, avanço e retorno, limites sem circular entre a primeira e a última página, seleção da navegação, botão Voltar, ausência do X e navegação repetida sem reinício do processo.

Referências oficiais consultadas em 16/10/2026:

- [Pager no Compose](https://developer.android.com/develop/ui/compose/layouts/pager): componente de páginas com gestos nativos.
- [APIs de testes Compose](https://developer.android.com/develop/ui/compose/testing/apis): localizar elementos por semântica, executar ações e verificar resultados. O roteiro deste projeto usa UI Automator via ADB no celular físico; não é uma suíte de instrumentação Compose.

## Validação

- 42 testes unitários Android aprovados, sem falhas.
- `:app:assembleDebug` e `:app:lintDebug` aprovados.
- APK debug atualizado por USB com `adb install -r`, preservando os dados.
- Roteiro físico aprovado no Moto G7 Play: gestos nos dois sentidos com 250/500 ms, limites, botão Voltar e ausência do X. Stress com 10 ciclos / 40 toques, mesmo processo e interface responsiva ao final; nenhum crash detectado pelo roteiro.
- Evidência local: `samuschat-android/app/build/navigation-regression/result.json`; logs em `logs/android-pager-build.log` e `logs/android-pager-device.log`. Artefatos gerados não integram o Git.

Comandos: executar as tarefas Gradle acima com o SDK local e depois `powershell -File scripts/test-android-navigation.ps1 -Cycles 10`. O roteiro exige aparelho USB desbloqueado e app autenticado; não envia mensagens reais nem altera o perfil.

Aceite visual confirmado pelo usuário em 16/10/2026. Mensagens privadas por WebSocket foram implementadas em seguida; não lidas e notificações continuam como etapas futuras. Commit e push autorizados pelo usuário em 16/10/2026.
