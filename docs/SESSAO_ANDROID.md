# Login persistente no Android

O app restaura a sessão ao abrir novamente, inclusive após encerramento do processo. Fechar o app, trocar de aplicativo e remover a tela dos recentes não executam logout.

Após um login por email/senha, cadastro ou Google, a API devolve um token de acesso e um token de renovação. O acesso conserva validade de 24 horas. A renovação vale 30 dias a partir daquele login; esse prazo não é estendido por cada renovação. Após o prazo, é necessário entrar novamente.

Os dois tokens são salvos no DataStore com AES/GCM usando uma chave do Android Keystore. A senha não é armazenada. Quando uma chamada autenticada recebe 401, o Android renova o acesso em `POST /api/auth/refresh`, salva o novo token e repete a solicitação uma única vez. Solicitações concorrentes compartilham a renovação. A troca de token não recria a navegação nem a chamada ativa.

Falhas temporárias de rede, timeout, limite de solicitações e indisponibilidade da API preservam os tokens salvos. Renovação recusada por expiração ou revogação exige novo login. O botão **Sair da conta** continua sendo logout explícito: a API revoga as sessões da conta, incluindo renovações; troca de senha também revoga essas sessões. Logout no PC com a mesma conta afeta o celular, por isso o teste PC ↔ celular deve usar contas diferentes.

Sessões anteriores à atualização não possuem token de renovação. É necessário fazer login uma vez na versão atual para habilitar o período de 30 dias. Reinstalar com `adb install -r` preserva os dados; desinstalar, limpar dados ou perder a chave do Keystore exige novo login.

## Validação de 14/10/2026

- Android: 39 testes aprovados, incluindo renovação e repetição, recusa de sessão revogada, prevenção de restauração durante logout e indisponibilidade temporária. Build e lint aprovados.
- API: 32 testes aprovados na rodada inicial, incluindo emissão, separação entre acesso/renovação, rejeição de entradas inválidas e revogação por logout, além das regressões de autenticação, Google e segurança.
- API local reiniciada e APK atualizado no moto g(7) play com preservação de dados.
- A primeira senha informada recebeu **Credenciais inválidas**. Após o usuário atualizar as credenciais, a API aceitou o login. Na regressão para publicação, encerrar o processo e reabrir o app no celular restaurou a identidade conectada, preservou o armazenamento e confirmou tokens criptografados. A inspeção exigiu um dump novo da interface para evitar resultados baseados em uma tela antiga. Nenhuma senha foi adicionada a arquivos do projeto.

Teste manual: entrar, conferir Amigos/Perfil, fechar o app, abrir novamente e confirmar que o Perfil continua na mesma conta. Depois tirar o cabo, abrir o app sem API disponível e confirmar que a sessão é preservada; reconectar e reaplicar o encaminhamento USB. Para testar a renovação sem esperar 24 horas, os testes automatizados usam acesso expirado e renovação válida.
