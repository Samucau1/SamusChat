# Envio de anexos no Android

Primeira funcionalidade desta sequência: envio de arquivos nos canais de texto, usando a API existente. Implementada em 06/10/2026.

## Como usar

1. Abra um canal de texto e toque no **+** ao lado do campo da mensagem.
2. O menu abre com **Imagem** (ícone de foto), **Documento** (folha escrita) e **Vídeo** (câmera). Escolha uma opção para abrir o seletor do Android filtrado por categoria.
3. Confira o nome do arquivo. **Remover** cancela a seleção; uma legenda é opcional.
4. Toque em **Enviar mensagem**. Durante o envio, o aplicativo mostra o andamento e bloqueia outro envio.

O limite por arquivo é 10 MiB (exibido como 10 MB) e a legenda aceita até 2.000 caracteres. Arquivos vazios, formatos não aceitos e arquivos maiores são rejeitados. A validação considera também os bytes efetivamente lidos, mesmo quando o provedor omite ou informa incorretamente o tamanho.

Formatos aceitos: imagens JPG, PNG, GIF e WebP; documentos PDF, TXT, DOC e DOCX; vídeos MP4, WebM e 3GP. O botão e o menu usam as cores, tipografia e componentes do tema do SamusChat.

Imagens aparecem na conversa; documentos e vídeos usam **Abrir anexo**, com o aplicativo escolhido pelo Android. Em caso de falha no envio, o arquivo selecionado e o texto permanecem para nova tentativa.

## Implementação

- `OpenDocument` seleciona um documento sem solicitar acesso geral ao armazenamento.
- Cada opção do menu fornece seus tipos MIME ao seletor. O backend aceita os mesmos formatos; vídeos e documentos mantêm `attachmentType=FILE` para compatibilidade com os clientes existentes.
- O ViewModel mantém a seleção durante a navegação ao seletor e sua volta à conversa; a inspeção do documento não depende da atualização do histórico.
- O cliente envia `POST /api/channels/{id}/upload`, com JWT e multipart `file` e `content` opcional.
- Um arquivo temporário limitado no cache representa o documento durante o upload e é removido após sucesso, erro ou cancelamento.
- A mensagem da resposta é unificada por ID com histórico e STOMP, evitando duplicatas.
- Para o emulador configurado em `10.0.2.2`, URLs de `/uploads/` que apontam para `localhost` ou `127.0.0.1`, na mesma porta da API, são adaptadas. URLs externas continuam intactas. Em dispositivos físicos, configure `FILE_BASE_URL` com endereço acessível.
- O backend mantém o limite de arquivo em 10 MB. O limite da requisição multipart passou para 11 MB para acomodar cabeçalhos, separadores e legenda.

Conversas privadas ainda não têm contrato de upload. A seleção é temporária: após encerramento do processo, selecione o arquivo novamente.

## Validação

- Android: **30 testes aprovados**, incluindo tipos, limites reais do arquivo, URLs e contrato multipart com e sem legenda.
- `assembleDebug`: APK gerado.
- `lintDebug`: **0 erros e 16 avisos**.
- API: **5 testes aprovados** (`FileControllerTests` e `Stage10ApiRegressionTests`), com upload de imagem com legenda, TXT sem legenda, leitura dos arquivos e presença no histórico. Execução no perfil `test`, com H2, sem utilizar o PostgreSQL de desenvolvimento.
- Regressão completa antes do commit: **111 testes da API aprovados**, zero falhas, erros ou testes ignorados, incluindo integração Redis real. Os **30 testes Android**, geração do APK e lint foram repetidos com `--rerun-tasks`: zero falhas, zero erros de lint e 16 avisos.
- Após adicionar o menu por categoria, DOC/DOCX e vídeos: **30 testes Android e 6 testes da API aprovados**. O teste adicional verifica envio, recuperação dos bytes e presença no histórico dos cinco novos tipos MIME. Esses arquivos usam bytes de teste; isso não valida a reprodução de vídeo em um player externo.
- Regressão consolidada do menu e ajustes de teclado antes do commit: **112 testes da API** (incluindo Redis real) e **30 testes Android** aprovados, sem falhas, erros ou testes ignorados. APK e lint aprovados; teclado validado no emulador conforme [guia Android](ANDROID.md).
- Sem emulador conectado ao concluir esta etapa: instalação e teste visual de seleção, envio e recebimento entre dois dispositivos permanecem pendentes.

Comandos, a partir de cada subprojeto:

```powershell
# samuschat-android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

# chatapp-backend
.\mvnw.cmd test '-Dspring.profiles.active=test' '-Dtest=FileControllerTests,Stage10ApiRegressionTests'

# Regressão completa antes do commit; Redis local disponível na porta 6379
$env:RUN_REDIS_TESTS='true'
.\mvnw.cmd test '-Dspring.profiles.active=test'
# Em samuschat-android:
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --rerun-tasks
```

Próxima funcionalidade da sequência: sincronização da foto de perfil com o backend; depois, gerenciamento de membros e cargos.

Para utilizar DOC/DOCX e vídeos, execute também a versão atualizada do backend: uma API já em execução precisa ser reiniciada com o código novo.
