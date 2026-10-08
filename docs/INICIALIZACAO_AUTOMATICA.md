# Iniciar o SamusChat com um comando

Para abrir o aplicativo Android com os servicos necessarios, execute na raiz:

```powershell
.\iniciar-samuschat.ps1
```

Tambem e possivel dar dois cliques em `iniciar-samuschat.cmd`. O launcher aplica `ExecutionPolicy Bypass` somente ao processo iniciado; nao altera a politica permanente do Windows.

## O que o launcher faz

1. Verifica o SDK local e o AVD `SamusChat_Validacao`.
2. Abre Docker Desktop em segundo plano, se necessario, e espera o engine.
3. Executa `setup.ps1 -NoRun` para preparar PostgreSQL e Redis, preservando os volumes existentes.
4. Reutiliza a API se `/actuator/health` retornar `UP`. Caso a porta 8080 esteja livre, inicia `setup.ps1` em segundo plano. Aguarda a API ficar saudavel antes de continuar.
5. Reutiliza o emulador na porta 5554 ou inicia o AVD e espera `sys.boot_completed=1`.
6. Executa o build incremental do APK debug para incluir as alteracoes atuais, usando a API normal na porta 8080 e sem forcar TURN relay. Compara SHA-256 do APK gerado com o instalado; instala com `adb install -r` somente quando houver diferenca ou o app estiver ausente. Verifica conectividade TCP pelo Android e abre `com.samuschat/.MainActivity`.

O comando pode ser repetido: reutiliza os servicos saudaveis e o emulador existente. Nao apaga dados, nao recria contas e nao realiza login automaticamente. Se uma sessao estiver expirada, sera necessario entrar novamente. Os servicos permanecem ativos ao fechar o aplicativo ou o launcher.

## Opcoes

```powershell
# Forcar a execucao das tarefas de build e reinstalar preservando os dados
.\iniciar-samuschat.ps1 -Rebuild

# Outro AVD no SDK local, em outra porta de emulador
.\iniciar-samuschat.ps1 -Avd SamusChat_Validacao -Port 5556
```

Para compilar, o SDK local precisa conter as ferramentas do projeto; Maven/Gradle podem baixar dependencias. Os pre-requisitos do backend continuam sendo JDK 21, Docker Desktop com containers Linux e `.env` configurado, conforme `setup.ps1`. Nenhuma credencial e copiada para o launcher.

O launcher sempre verifica o build atual, mesmo quando o app ja esta instalado. Sem alteracoes, o Gradle reutiliza suas tarefas e o APK nao e reinstalado. Quando houver atualizacao, a instalacao pode encerrar uma chamada em andamento; os dados da conta sao preservados. Abrir diretamente um APK antigo no emulador nao instala as alteracoes do repositorio.

Logs da API iniciada pelo launcher ficam em `logs/api.stdout.log` e `logs/api.stderr.log` (ignorados pelo Git). Se ela ja estiver em execucao em outro terminal, seus logs continuam nesse terminal. Ha limites de espera para Docker (180 s), API (240 s) e Android (240 s); erros interrompem a abertura do aplicativo com uma mensagem.

## Limites e uso no Android Studio

Abrir diretamente o app no emulador ou clicar em Run no Android Studio nao executa automaticamente este launcher. Use o launcher como ponto de entrada para iniciar o ambiente; depois o Run pode atualizar o aplicativo normalmente. A interface de PC em `samuschat-desktop/` continua independente e e iniciada com `npm run dev`.

Automacao ligada ao Run do Android Studio pode ser adicionada depois como ferramenta externa anterior ao lancamento. Evitamos colocar a inicializacao de Docker/API nas tarefas de build do Gradle, para manter compilacao e CI independentes desses servicos.

Para parar PostgreSQL/Redis sem apagar seus dados: `docker compose stop`. Feche o emulador pela janela. A API iniciada em segundo plano permanece ligada; identifique o processo Java do SamusChat na porta 8080 antes de encerra-lo, para nao afetar outros projetos.

## Validacao realizada

Em 07/10/2026, as consultas de disponibilidade do Docker e de boot do Android foram ajustadas para tratar erros esperados de inicializacao como uma tentativa ainda sem sucesso. No Windows PowerShell, stderr de programas nativos pode interromper o script com `NativeCommandError` quando `ErrorActionPreference` e `Stop`, mesmo com redirecionamento. As consultas agora tratam esses erros localmente, mantendo a interrupcao por falhas nas demais etapas. A sintaxe e os cenarios simulados de falha com stderr e sucesso foram validados; a inicializacao completa com Docker desligado ainda precisa ser conferida.

Em 06/10/2026, a sintaxe PowerShell foi validada e o launcher foi executado com sucesso em dois cenarios: reutilizando API/emulador ativos e iniciando a API desligada em segundo plano. Em ambos, a saude da API foi confirmada, a conectividade TCP pelo Android passou e a atividade SamusChat abriu. Inicializacao com Docker/emulador desligados e recompilacao com `-Rebuild` ainda nao foram exercitadas pelo launcher.

Na correcao da atualizacao automatica, foi identificado que o AVD pessoal ainda usava o APK instalado em 05/10, anterior a previa de tela. O APK atual foi instalado com `adb install -r`, sem limpar os dados. A sintaxe do launcher atualizado foi validada, e uma execucao completa confirmou build incremental, igualdade SHA-256 com o APK instalado, ausencia de reinstalacao desnecessaria e abertura do aplicativo. Isso comprova a atualizacao do APK; a renderizacao da previa em uma chamada requer o teste de compartilhamento no app.
