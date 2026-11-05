# Atualização local do SamusChat — 04/11/2026

- Android: versão `1.2.1-beta-local`, código `4`, pacote `com.samuschat`, assinatura debug existente.
- API: histórico de canais carrega os autores em lote para obter avatares, evitando consultas individuais por mensagem. Não altera o contrato usado pelo Android. A melhoria exige executar o backend atualizado.
- Testes do backend: `MessageMapperTests` e `MessageControllerTests`, três testes aprovados; cobrem lote, autores sem avatar, histórico vazio e fluxo de envio/paginação/exclusão.
- APK de teste para USB: `dist/celular/SamusChat-teste.apk`, API `http://127.0.0.1:8080/`; exige backend local e `adb reverse tcp:8080 tcp:8080`.
- Login Google não configurado neste build, assim como no APK anterior. Não é um APK de produção.
- Artefatos APK e metadados permanecem fora do Git conforme `.gitignore`.
- Validação Android: 60 testes unitários sem falhas; `assembleDebug`, `lintDebug` e `signingReport` concluídos com sucesso.
- SHA-256 do APK: `9D790018854027A29B54D92671D32CB52F8A3DD943F25B77E7FA28BFF1EAA5A2`.
- O aparelho conectado foi identificado como `unauthorized`; instalação e teste manual no celular não foram realizados.

Atualização posterior: o aparelho foi autorizado, o APK instalado e os resultados registrados em [Teste no celular — 04/11/2026](TESTE_CELULAR_2026-11-04.md).

Geração e validação:

```powershell
./scripts/build-apk-celular.ps1 -ApiBaseUrl 'http://127.0.0.1:8080/' -AllowWithoutGoogle
```
