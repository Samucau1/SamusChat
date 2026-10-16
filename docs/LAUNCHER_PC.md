# Iniciar o SamusChat no PC

Na raiz do projeto, dê dois cliques em **`iniciar-samuschat-pc.cmd`**. No PowerShell, também pode executar:

```powershell
.\iniciar-samuschat-pc.ps1
```

O launcher espera os serviços ficarem prontos e abre o navegador padrão em **http://127.0.0.1:5173/**. Entre com a conta do PC. Para testar PC ↔ celular, use contas diferentes.

Se o script do celular já iniciou a API, o launcher PC reutiliza essa API e o mesmo banco/Redis. Se os serviços estiverem parados, prepara Docker e inicia `setup.ps1` em segundo plano. A interface PC também é reutilizada se já estiver disponível. Executar novamente abre o navegador sem iniciar novas instâncias dos serviços existentes.

O PC não exige celular conectado, ADB ou Android Studio. Use Node.js 22 ou superior com npm; para iniciar a API do zero, são necessários JDK 21 e Docker Desktop, conforme `setup.ps1`. Se as dependências PC estiverem ausentes, o launcher executa `npm ci`, que pode precisar de internet. O navegador abre a aplicação; o launcher não armazena senha nem realiza login automático.

## Opções e diagnóstico

```powershell
# Preparar os serviços sem abrir o navegador:
.\iniciar-samuschat-pc.ps1 -NoBrowser

# Reinstalar as dependências, com a interface PC parada:
.\iniciar-samuschat-pc.ps1 -ReinstallDependencies
```

Portas: API `8080`, interface PC `5173`. O Vite fica restrito a `127.0.0.1` e usa porta fixa; uma porta ocupada por outra interface gera erro em vez de mudar o endereço ou encerrar processos. Uma API ainda iniciando na porta 8080 é aguardada, sem criar outra instância. Um mutex evita duas inicializações simultâneas pelo launcher PC.

Espera máxima: Docker 180 segundos, API 240 segundos e interface PC 60 segundos. Os serviços iniciados pelo launcher ficam em segundo plano, com janelas ocultas. Fechar o terminal ou o navegador não encerra os serviços e não fecha o aplicativo do celular.

Logs dos processos iniciados por este launcher:

- `logs/pc-api.stdout.log` e `logs/pc-api.stderr.log`.
- `logs/pc-desktop.stdout.log` e `logs/pc-desktop.stderr.log`.

Se a API foi iniciada pelo launcher do celular ou por outro terminal, seus logs permanecem no local original. O script PC não reinstala o aplicativo Android, não altera contas e não modifica o encaminhamento USB.

## Validação em 16/10/2026

O smoke `scripts/test-desktop-launcher.ps1` foi executado com a API do celular ativa: primeiro reutilizando API/interface já disponíveis e depois iniciando a interface com a porta 5173 livre. Foram conferidos HTML do SamusChat, proxy da API exigindo autenticação, mesmo processo da API antes/depois, reutilização do processo PC na segunda execução e preservação das variáveis do terminal chamador.

O teste não interrompeu a API nem alterou contas. A partida da API/Docker do zero não foi exercitada nesta rodada, para preservar o ambiente do celular. Esse caminho usa o `setup.ps1` existente. Não foram necessárias mudanças no launcher do celular.
