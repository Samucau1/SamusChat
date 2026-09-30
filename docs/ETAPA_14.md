# Etapa 14 — interface Android e apresentação do portfólio

## Interface

- Tema escuro com tons de cinza e destaque violeta, inspirado no Discord.
- Ação de criar servidor no **+ superior esquerdo**; busca na **lupa superior direita**.
- Barra lateral com avatares dos servidores, seleção destacada e lista de canais.
- Formulários de criação e busca em painéis inferiores, com erros e estados de carregamento.
- Busca dos servidores da conta por nome/ID, ignorando acentos e caixa; entrada em outro servidor por ID.
- Login/cadastro com identidade visual comum; chat com avatar, remetente, horário e campo de mensagem.

Os fluxos de autenticação, histórico, anexos e WebSocket continuam integrados à API. Criar ou entrar em um servidor seleciona o resultado e fecha o painel após sucesso.

## Portfólio e execução

- README com badges, arquitetura Mermaid, comandos para PC/celular e limitações reais.
- Documentação consolidada [v3.0](DOCUMENTACAO_V3.md) e licença MIT.
- `.env.example` local separado do template de produção GHCR.
- `.gitignore` ampliado para credenciais, builds e arquivos de IDE, preservando templates e wrappers.
- `setup.ps1`: valida JDK/Docker, preserva `.env` existente, aguarda PostgreSQL/Redis e inicia a API.
- Compose local com healthchecks e portas de banco/Redis restritas a localhost.
- CI Android com testes, lint, APK debug e relatórios; publicação GHCR depende também desse job.

## Verificação

Validação local em 29/09/2026:

- **65 testes do backend** aprovados, sem falhas ou testes ignorados, incluindo Redis real.
- **7 testes Android** aprovados; build do APK e lint concluídos. Lint sem erros, com 9 avisos existentes de dependências/configuração.
- `setup.ps1 -Check`, `-NoRun` e inicialização completa executados; `.env` existente preservado e API respondendo `UP`.
- Emulador API 35: login, criação pelo +, busca sem diferenciar caixa, erro de ID inexistente, entrada em servidor válido, seleção de canal, envio e recebimento ao vivo de outra conta.
- Testes visuais feitos em perfil separado; a sessão original do emulador foi preservada. [Capturas](images/) usam apenas dados fictícios.

Esses resultados são locais; o status de cada execução remota está no GitHub Actions.

```powershell
.\setup.ps1 -Check
.\setup.ps1 -NoRun
cd chatapp-backend
$env:RUN_REDIS_TESTS = 'true'
.\mvnw.cmd verify
cd ../samuschat-android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Roteiro visual: fazer login, criar pelo +, alternar servidores, abrir a lupa, filtrar por nome/ID, entrar com um ID e abrir um canal para enviar/receber mensagens. Testar erro com ID inexistente e voltar sem perder a lista. Testes automatizados de busca cobrem consultas vazias, acentos, caixa, ID exato e nenhum resultado.

Push Firebase real exige as credenciais do projeto. Deploy público exige servidor e secrets descritos na etapa 13. Não há alteração desses requisitos nesta etapa.
