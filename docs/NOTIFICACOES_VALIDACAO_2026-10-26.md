# Validação de notificações — 26/10/2026

## Ambiente

- PC: Windows, interface React servida pelo Vite e navegador controlado pelo Playwright.
- Celular físico: Moto G7 Play, Android 10 (API 29), conectado por USB autorizado.
- APK atualizado de validação: `com.samuschat.validation`, nome “SamusChat Teste”. A instalação principal foi preservada.
- API descartável na porta 18082, H2 em memória, Redis e Firebase desativados; contas criadas exclusivamente para os testes.
- Encaminhamento USB: `adb reverse tcp:18082 tcp:18082`.

## Evidências obtidas

- Execução final conjunta: **7 testes aprovados e 1 ignorado**, em 59,4 segundos. TypeScript e verificação de whitespace aprovados.
- Compilação Kotlin debug e geração do APK validation aprovadas.
- Quatro cenários de inbox/tempo real aprovados: deduplicação e exclusão de eventos próprios, leitura em outro dispositivo, preservação de mensagens novas e recuperação após desconexão.
- Troca real PC → celular → PC aprovada. O envio inicial usou o endpoint REST autenticado do PC; a resposta foi digitada e enviada pela interface Android.
- O Android exibiu o aviso interno “Nova mensagem de…”, abriu a conversa e apresentou a mensagem recebida.
- O PC exibiu o aviso interno e o contador de não lidas após a resposta do celular; abrir a conversa removeu o contador.
- Feedback de permissão bloqueada aprovado em Chromium e Microsoft Edge.
- Feedback de permissão concedida aprovado: a interface informa que o SamusChat deve permanecer aberto.

## Limites do aceite

Aviso dentro do aplicativo e notificação do sistema são fluxos diferentes. A evidência de troca USB não comprova push com a tela bloqueada.

O arquivo `samuschat-android/app/google-services.json` está ausente. O usuário confirmou que ainda não criou o projeto Firebase. Portanto, o push Android real ainda precisa de configuração do aplicativo, credencial do backend e validação em dispositivo com app em segundo plano e tela bloqueada. A API descartável usa estratégia de log, sem enviar push.

O aviso nativo do Windows não foi comprovado. Nesta sessão automatizada, Chromium e Edge mantiveram `document.hidden=false` mesmo após minimizar a janela. O teste registra a impossibilidade como cenário ignorado com justificativa explícita; isso não é um aceite do recurso nativo. O aplicativo atualmente depende de `document.hidden` para produzir esse aviso.

O PC ainda não possui push com o site fechado. Os testes também não comprovam execução em redes externas ou Android 13+, no qual a permissão de notificações é solicitada em tempo de execução.

## Repetição

Iniciar `scripts/start-desktop-test-api.ps1`. Para o teste USB, usar o APK validation com URL `http://127.0.0.1:18082/`, encaminhamento USB e sua tela inicial de login sem campos preenchidos. A API de teste deve continuar ligada durante a execução.

```powershell
cd samuschat-desktop
$env:SAMUSCHAT_ANDROID_SERIAL = 'SERIAL_USB_AUTORIZADO'
npx playwright test tests/direct-inbox.spec.ts tests/direct-realtime.spec.ts tests/notifications.spec.ts tests/notifications-android.spec.ts --headed
```

Sem `SAMUSCHAT_ANDROID_SERIAL`, o cenário USB é ignorado. O teste nativo exige uma sessão capaz de apresentar segundo plano real; os testes de permissão continuam independentes dessa condição.

## Próxima validação necessária

Configurar Firebase e repetir a entrega com o Android aberto, em segundo plano e bloqueado. Verificar também o toque no aviso, retorno ao app e ausência de entrega após logout. No Windows, verificar manualmente o aviso com o navegador minimizado e a permissão concedida.
