# Amigos, perfis e imagens persistentes

A central Amigos do cliente web mostra apenas amizades aceitas. Participar do mesmo
servidor ou existir no cadastro não cria uma amizade. A lupa Encontrar amigos abre
a busca por nome (a partir de dois caracteres, até 20 resultados) ou ID público
completo. O email de login não faz parte dos resultados nem dos perfis de terceiros.

O fluxo tem convite enviado, convite recebido, aceite, recusa, cancelamento e
remoção da amizade. Convites simultâneos criam uma única relação pendente; o
destinatário precisa aceitá-la. Conversas antigas permanecem na barra lateral e
seu histórico não é removido quando a amizade termina. A API de chamadas mantém
os contratos existentes, mas sua lista de contatos passa a listar somente amigos.

Clicar na foto do cabeçalho da conversa, na foto de uma mensagem, em um resultado
da busca ou no nome/foto de um membro abre o perfil de consulta: foto, capa, fundo,
cor, descrição e ID copiável. Essa visualização não permite editar dados alheios.

O seletor de mídia informa compatibilidade, envio em andamento, aceite e recusa
com motivo. Arquivos inválidos não substituem a foto anterior. O backend continua
validando o conteúdo real, dimensões, tamanho e quantidade de quadros dos GIFs;
as verificações do navegador não substituem essa validação.

## Persistência e implantação

A migração aditiva V9 cria `friendships` e `appearance_blobs`. Os bytes das imagens
de aparência e as prévias dos GIFs são armazenados no PostgreSQL como `bytea`.
As URLs existentes continuam com o formato `/uploads/appearance/<uuid>.<ext>`.
A aplicação consulta primeiro o banco, com fallback para arquivos locais antigos
que ainda existam. Imagens que já desapareceram de uma instância antiga não podem
ser recuperadas por esta alteração e precisam de novo envio.

O plano Free continua sem disco persistente. A persistência das imagens depende
da disponibilidade e da capacidade do PostgreSQL, cuja instância beta expira em
20/12/2026. Anexos de mensagens não foram migrados para o banco nesta entrega.

## Validação

- Regressão backend: 159 testes executados, 157 aprovados, dois opcionais de Redis
  ignorados. Inclui migração PostgreSQL em schema descartável e validação Hibernate.
- Navegador: 14 testes aprovados, com API real e PostgreSQL local; inclui Google,
  sessões, convites, busca, perfil alheio, mensagens, mídia aceita/recusada e celular.
- Uma foto foi enviada, seu arquivo local removido e a API reiniciada mantendo o
  PostgreSQL. Um novo login confirmou a mesma URL e os mesmos bytes da imagem.

Os testes e as capturas usam contas sintéticas separadas dos usuários reais.
