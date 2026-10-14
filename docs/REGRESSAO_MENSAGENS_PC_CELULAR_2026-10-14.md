# Mensagens privadas PC ↔ celular — 14/10/2026

O usuário relatou mensagens ausentes nos dois sentidos. A conta conectada no Android foi confirmada como `testecell@gmail.com`; o usuário inicialmente informou `amigo.regressao.0510@test.com` no PC e depois corrigiu: o PC estava em `samus.regressao.0510@test.com`.

A leitura dos metadados do PostgreSQL encontrou duas mensagens de `testecell@gmail.com` para `amigo.regressao.0510@test.com` e uma de `samus.regressao.0510@test.com` para `testecell@gmail.com`. A conversa aberta no celular era com `samus_regressao_0510`, compatível com a conta posteriormente confirmada no PC. As duas mensagens enviadas pelo celular haviam sido direcionadas a amigo, e não a samus. Não foi necessário ler ou copiar o conteúdo das mensagens para o diagnóstico.

Consultas autenticadas pela API 8080 e pelo proxy desktop 5173 retornaram as mesmas duas mensagens na conversa celular ↔ amigo. Portanto, os clientes consultam o mesmo backend e essas mensagens estão persistidas; a entrega real na tela exige selecionar a conversa correta.

## Ajuste de interface

- Android mostra **Conectado como [email]** em Amigos, email abaixo do nome em cada contato real e email do destinatário no cabeçalho da conversa.
- Desktop mostra email nos contatos e permite buscar tanto pelo nome quanto pelo email. A conta conectada já aparece no perfil da barra lateral.
- O envio e o histórico continuam usando o email do contato como identidade. Não houve alteração ou transferência das mensagens existentes entre conversas.

## Verificação

Build desktop aprovado; APK Android e lint aprovados. Regressão de entrega de mensagem privada e preservação de rascunho em falha aprovada no Edge, com duas contas fictícias em API H2 descartável. Nenhuma nova mensagem foi enviada para as contas reais durante o diagnóstico.

Para repetir: no PC com `samus.regressao.0510@test.com`, abrir o contato `testecell@gmail.com`; no celular com `testecell@gmail.com`, abrir `samus.regressao.0510@test.com`. Manter as conversas abertas e aguardar a atualização periódica de aproximadamente três segundos. Conferir o remetente conectado e o destinatário no cabeçalho antes de enviar.
