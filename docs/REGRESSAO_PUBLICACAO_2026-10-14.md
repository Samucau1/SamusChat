# Regressão antes da publicação — 14/10/2026

Resultado: **APROVADO**. Execução concluída às 14:28:29 (America/Sao_Paulo). Branch: `feature/call-channels`. Base anterior ao conjunto: `209c75a`.

## Escopo

Destino Android por USB, sessão persistente, seleção de microfone, áudio de transmissão, identificação de contatos, tela cheia e captura adaptada à rotação. Fontes, scripts e relatórios relacionados integram o conjunto. Artigo e arquivos de LinkedIn ficaram fora da publicação. SDK, APKs, logs locais, dados de contas e chaves não integram o commit.

## Resultados

| Verificação | Resultado |
| --- | --- |
| Maven verify e integração Redis/PostgreSQL | 132 testes aprovados; nenhum ignorado |
| Android | 42 testes aprovados; APK debug/validation e lint aprovados |
| Chromium | 23 cenários aprovados em 49,2 s |
| Edge instalado | 23 cenários aprovados em 49,4 s |
| Celular USB | 50 ciclos / 200 toques, 136,2 s, processo preservado |
| Fechar/reabrir Android | Identidade restaurada e armazenamento criptografado preservado |
| JaCoCo — linhas | 90,10% — 1.101 cobertas / 121 não cobertas |
| Lint Android | Zero erros / 16 avisos |
| Revisão de credenciais | Zero candidatos; senhas pessoais ausentes do conteúdo preparado |

Total: 220 execuções de testes aprovadas, mais testes PowerShell e stress de navegação no aparelho.

## Execução

- API: `mvnw.cmd verify` com `RUN_REDIS_TESTS=true`, `REDIS_TEST_PORT=16379` e `RUN_PG_MIGRATION_TESTS=true`. Redis e PostgreSQL descartáveis nas portas 16379 e 15435. A cobertura foi recalculada após remover apenas o arquivo gerado `target/jacoco.exec`.
- Android: `testDebugUnitTest`, `assembleDebug`, `assembleValidation` e `lintDebug`. Manifesto release gerado separadamente com URL HTTPS fictícia; não houve distribuição release.
- Desktop: build TypeScript/Vite e Playwright completo, sequencialmente em Chromium e Edge. API H2 descartável na porta 18082 e navegador de testes em 5174.
- USB: probes do launcher, seleção de aparelho e navegação com 50 ciclos. Restauração da identidade e preservação do armazenamento verificadas ao encerrar/reabrir o processo, exigindo um dump novo da interface.
- Credenciais: revisão de arquivos preparados no Git. Sem candidatos na varredura e sem as senhas pessoais fornecidas na conversa.

## Ajustes do roteiro

A primeira invocação do Maven no PowerShell foi interrompida por um aviso normal do Mockito em stderr. A invocação foi corrigida e a rodada completa passou.

O roteiro de navegação foi atualizado para o rótulo atual de foto/GIF, esperar os painéis e tocar nos ancestrais clicáveis. O progresso normal do `adb push` também passou a ser tratado pelo código de saída. A rodada final passou por 50 ciclos e 200 toques.

## Evidências

A [evidência JSON](evidence/regression-2026-10-14.json) contém totais, cobertura, resultados por suíte, checks de sessão, SHA-256 do APK e hashes das fontes testadas. Logs completos permanecem em `logs/regression-2026-10-14-*.log`, relatórios JUnit nas pastas de build e cobertura em `chatapp-backend/target/site/jacoco/`.

As imagens em `docs/images/validacao-2026-10-14/` foram produzidas pelos testes do Edge com fontes sintéticas, sem contas reais na tela.

## Limites

Os cenários de navegador usam contas fictícias e fontes sintéticas. O teste de jogo/vídeo real girando o aparelho, a avaliação acústica da ligação física, OAuth Google e redes externas não foram declarados aprovados nesta rodada. O smoke da pilha de produção será verificado pelo workflow remoto acionado pelo push.
