# Regressão de notificações e Microsoft Edge — 26/10/2026

## Entrega

- PC: informa o resultado da permissão de notificações e orienta como liberar avisos bloqueados.
- Android: texto expandido e identificação dos avisos no serviço de mensagens.
- Playwright e scripts de PC usam o Microsoft Edge instalado. Os downloads locais de Chromium e headless shell foram removidos.
- Testes adicionais de permissão e troca de mensagens entre PC e Android físico.
- Cenário de foto de perfil ajustado para rolar o contato até a área visível antes de verificar uma imagem com `loading="lazy"`. A lista acumulada de contas de teste expôs essa dependência de visibilidade.

## Verificações

| Área | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw.cmd -B -ntp verify` | Aprovado: 147 testes, 3 ignorados, zero falhas/erros |
| Android | `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline` | Aprovado: 60 testes, zero falhas/erros; APK gerado |
| Android lint | Relatório debug | Zero erros, 6 avisos e 2 informações |
| PC | `npm run build` | TypeScript e Vite aprovados |
| PC Edge | `npx playwright test`, seguido da repetição do cenário corrigido | 44 cenários aprovados no conjunto das execuções; 2 ignorados |
| Scripts | `node --check` nos dois scripts alterados | Aprovado |

Os três testes ignorados no backend exigem PostgreSQL ou Redis: migração V7, limite distribuído e fan-out Redis. Essas integrações não foram exercitadas nesta execução local.

A execução completa do Edge durou 2,1 minutos: 43 aprovados, 1 falha e 2 ignorados. A falha era a verificação da imagem fora da área visível; após acrescentar a rolagem, somente esse cenário foi repetido e aprovado em 3,3 segundos. Não houve nova execução integral após essa correção no teste.

## Teste físico e limites

A troca PC → Moto G7 Play → PC foi aprovada em execução USB anterior nesta mesma entrega, incluindo aviso interno, resposta digitada no Android e confirmação de leitura. Consulte [evidências e configuração](NOTIFICACOES_VALIDACAO_2026-10-26.md).

O cenário USB é opt-in, exige a variável `SAMUSCHAT_ANDROID_SERIAL`, APK validation e tela inicial de login. O aviso nativo do Windows exige segundo plano real; nesta sessão a automação manteve `document.hidden=false` mesmo minimizada. O teste ignora explicitamente essa condição em vez de tratá-la como sucesso.

O push Android com tela bloqueada permanece pendente: o projeto Firebase ainda não foi criado. O PC não implementa push com o site fechado. Não houve publicação de aplicação nem implantação de infraestrutura nesta entrega.
