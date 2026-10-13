# Auditoria de segurança — PC, Android e API

Este relatório registra o estado anterior às correções. Consulte o [protocolo de correções e resultados finais](PROTOCOLO_SEGURANCA_PC_ANDROID_2026-10-12.md), incluindo as pendências de Spring MVC e implantação HTTPS.

Data: 12/10/2026. Código avaliado: `8d81b3dd9d31b330bb6a56dee7e86eaaf22e8a44`, branch `feature/call-channels`. As falhas abaixo permanecem abertas; esta entrega acrescenta evidências e estratégias de correção, sem alterar o comportamento da aplicação.

**Há falhas reproduzidas que afetam as duas versões pela API compartilhada.** O PC também permite executar HTML enviado como anexo. Corrigir uploads, autorização de arquivos, revogação de acesso WebSocket e proteção de login tem prioridade antes de uma publicação com dados reais.

## Escopo e resultados

Os ataques usaram uma API Spring Boot descartável, H2 em memória na porta 18085, contas fictícias, arquivos pequenos e um PC web em localhost na porta 5175. Foram executadas duas matrizes HTTP, identificadas como PC e Android. Ambas usam os contratos reais compartilhados; alterar o User-Agent não equivale a testar o APK fisicamente. O PC foi exercitado também em Chromium real. No Android foram inspecionados código, dependências e manifestos resolvidos de debug, validation e release.

| Verificação | Resultado |
| --- | --- |
| Roteiro de ataques à API/PC | 73 verificações: 55 controles observados e 18 ocorrências de falhas, incluindo repetições entre clientes |
| Regressões existentes de autenticação, JWT, recuperação e WebSocket | 45 testes aprovados, zero falhas/erros |
| Dependências do PC, `npm audit` | Zero avisos conhecidos na consulta; 75 dependências no inventário npm |
| Dependências resolvidas consultadas no OSV | 174 coordenadas do backend e 151 do Android |
| Correspondências do OSV | 14 coordenadas com 35 avisos distintos; não são 35 explorações comprovadas |
| Manifesto Android release | Tarefa Gradle aprovada; target SDK 35, backup desativado, sem habilitação explícita de debug ou HTTP |
| Busca limitada de segredos | 313 arquivos de texto rastreados, 20 itens ignorados; nenhum candidato nos quatro padrões examinados |

O roteiro encerra com código **1** porque encontrou falhas. Os 45 testes existentes aprovados confirmam suas proteções específicas; não tornam a auditoria de segurança aprovada.

Evidências sem senhas, tokens ou conteúdo de usuários reais: [matriz de ataques](security/2026-10-12/api-results.json), [inventário e avisos de dependências](security/2026-10-12/dependency-results.json), [revisão estática](security/2026-10-12/static-results.json), [resultado npm](security/2026-10-12/npm-audit.json) e [regressão do backend](security/2026-10-12/backend-regression-results.json). A [análise dos pacotes](security/2026-10-12/DEPENDENCIAS.md) apresenta os alertas e as limitações de aplicabilidade.

## Falhas reproduzidas e correções

As prioridades são qualitativas para este projeto, não pontuações CVSS calculadas. P0 deve ser corrigido primeiro; P1 contém riscos elevados; P2 contém riscos menores ou dependentes de condições adicionais.

| ID | Prioridade | Resultado | Abrangência |
| --- | --- | --- | --- |
| SEC-01 | P0 | HTML disfarçado executa JavaScript na origem do PC | Upload compartilhado; execução comprovada no PC |
| SEC-02 | P1 | Outra conta apaga anexos sem ser membro do servidor | PC e Android |
| SEC-03 | P1 | Anexo de canal acessível sem autenticação ao conhecer a URL | PC e Android |
| SEC-04 | P1, condicionada à entrada | Trocar `X-Forwarded-For` contorna o limite de login da API direta | PC e Android; Nginx descrito abaixo mitiga esse caminho |
| SEC-05 | P1 | Assinatura WebSocket continua recebendo mensagens após remoção do membro | PC e Android |
| SEC-06 | P1 quando usado HTTP | Senha/token observáveis e token reutilizável em transporte HTTP | Prova local; HTTPS não foi quebrado |
| SEC-07 | P2, ampliada por roubo de token | Logout local mantém o JWT válido | PC reproduzido; Android identificado no código |
| SEC-08 | P2 | Upload não autorizado grava arquivo antes de negar a mensagem | PC e Android |
| SEC-09 | P2 | Cadastro informa se o email já existe | PC e Android |

### SEC-01 — conteúdo ativo em anexo

**Prova:** um arquivo chamado `audit.html`, declarado como `text/plain`, foi aceito por `POST /api/channels/{id}/upload`. Ao acessá-lo, a resposta passou a `Content-Type: text/html`, sem `Content-Disposition`. O Chromium executou seu script. Na mesma aba e origem, esse script leu um valor sentinela na chave usada pelo app em `sessionStorage`. Não houve envio de tokens para terceiros.

**Causa:** [FileStorageService.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/service/FileStorageService.java) confia no MIME informado pelo cliente, conserva a extensão enviada e permite que o manipulador estático escolha o tipo pela extensão. [FileStorageConfig.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/config/FileStorageConfig.java) serve o resultado em `/uploads/`, origem também encaminhada pelo proxy do PC. A validação mais forte de fotos de perfil não se aplica aos anexos comuns.

**Impacto:** código controlado por um usuário é executado na origem da aplicação. Ler a sessão exige uma aba com a sessão disponível. O link normal de anexo usa `noopener noreferrer`; não foi demonstrado roubo do token da aba original por esse link. A prova usou navegação na mesma aba. O Android abre anexos externamente e não tem WebView neste fluxo; execução dentro do aplicativo Android não foi demonstrada.

**Correção:** validar extensão, conteúdo e MIME real em conjunto; recodificar imagens; bloquear HTML/SVG e formatos ativos fora do escopo. Servir anexos não visuais com `Content-Disposition: attachment`, tipo seguro e `nosniff`. Isolar mídia em origem separada sem credenciais da aplicação e aplicar CSP restritiva nesse host. Sanitizar nomes de arquivo e resolver caminhos dentro da pasta permitida. Só autenticação, troca para HTTPS ou `nosniff` com o tipo já definido como HTML não corrigem esta falha. A estratégia de validação e isolamento segue a [orientação de uploads da OWASP](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html).

**Aceite:** anexos HTML disfarçados rejeitados; conteúdo ativo não executa na origem do app; PNG/JPG/GIF e arquivos permitidos continuam funcionando nos dois clientes.

### SEC-02 — exclusão sem autorização por arquivo

**Prova:** a conta A enviou um anexo; uma conta B, fora do servidor, chamou `DELETE /api/files?fileUrl=...` e recebeu HTTP 200. O arquivo foi removido fisicamente da pasta de teste. A leitura anterior era 200; após a remoção retornou 500 pelo tratamento atual de recursos ausentes.

**Causa:** [FileController.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/controller/FileController.java) não recebe a identidade para a exclusão e não consulta autoria, mensagem ou servidor. `FileStorageService.deleteFile` usa o nome retirado de uma URL fornecida pelo cliente.

**Correção:** registrar cada arquivo por ID, proprietário, mensagem e canal. Autorizar exclusão pelo autor ou por moderador autorizado daquele servidor, conforme a regra escolhida. Usar o ID interno em vez de URL arbitrária, validar o caminho resolvido e garantir consistência entre banco e disco. Mapear recursos ausentes para 404.

**Aceite:** outra conta recebe 403/404 e o arquivo continua existindo; autor autorizado consegue remover somente seu arquivo; tentativas de caminhos fora do armazenamento são rejeitadas.

### SEC-03 — download sem vínculo com a permissão do canal

**Prova:** o anexo de um canal com leitura REST restrita a membros foi baixado sem JWT: HTTP 200 e conteúdo idêntico ao original.

**Causa:** `/uploads/**` é público em [SecurityConfig.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/config/SecurityConfig.java). A URL contém UUID e não foi adivinhada: o teste conhecia a URL. O risco aparece quando ela é compartilhada, registrada em logs ou obtida por outra falha. O UUID dificulta descoberta, mas não revoga acesso.

**Correção:** separar mídia deliberadamente pública de anexos de canais. Para anexos, autenticar e verificar acesso atual ao canal, ou gerar URLs assinadas com validade curta e escopo limitado. A autorização deve se aplicar ao download, ao HEAD e à troca de servidor/membro. Adaptar imagens no PC e Coil/OkHttp no Android para o contrato protegido, sem colocar JWT na URL.

**Aceite:** visitantes e contas fora do canal não baixam anexos protegidos; membros autorizados continuam vendo imagens; URLs temporárias expiram; remoção do membro impede novos downloads.

### SEC-04 — proteção de login contornável e senha comum

**Prova:** seis tentativas com o mesmo cabeçalho de IP produziram `[400,400,400,400,400,429]`. Sem trocar de conexão/máquina, três novas tentativas com cabeçalhos diferentes produziram `[400,400,200]`. A última usou uma senha comum aceita no cadastro de uma conta sintética. Não foi quebrado BCrypt nem descoberta uma senha forte.

**Causa:** [RateLimitFilter.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/security/RateLimitFilter.java) confia diretamente em `X-Forwarded-For`. [RateLimitConfig.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/config/RateLimitConfig.java) limita por IP em mapas locais sem expiração das entradas. Não há limite independente por conta nem compartilhamento desse estado entre as três instâncias. [InputSanitizer.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/security/InputSanitizer.java) aceita seis caracteres, incluindo a senha comum usada na prova.

**Condição de produção:** [nginx.conf](../nginx/nginx.conf) já substitui `X-Forwarded-For` pelo endereço observado e aplica seu próprio limite; o Compose de produção não publica as portas das APIs. Esses controles mitigam o ataque de cabeçalho quando todo o tráfego passa por esse Nginx. Não foi reproduzido bypass do Nginx. API publicada diretamente ou um proxy que conserve cabeçalhos do cliente expõe o problema. `server.forward-headers-strategy=framework` também exige fronteira de confiança corretamente configurada.

**Correção:** confiar em cabeçalhos encaminhados somente de proxies explicitamente confiáveis; impedir acesso externo direto ao backend; limitar por conta normalizada, origem e janela global, com estado distribuído e TTL. Aplicar atraso progressivo, detecção de tentativas e MFA opcional, evitando bloqueio permanente que permita negar acesso à vítima. Recusar senhas comuns/vazadas e usar comprimento adequado; sem MFA, a OWASP recomenda um mínimo de 15 caracteres. Conservar limites consistentes em bytes com BCrypt, sem truncamento silencioso. Referência: [autenticação e tentativas de login — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

**Aceite:** variar apenas o cabeçalho não reinicia o limite; várias origens não liberam tentativas ilimitadas na mesma conta; o limite funciona entre instâncias; senha comum é recusada; recuperação legítima permanece acessível.

### SEC-05 — permissão revogada não encerra assinatura ativa

**Prova:** um membro autenticado assinou `/topic/channel/{id}`; o administrador o removeu; uma nova mensagem do canal ainda chegou pela conexão existente (`MESSAGE`). Uma nova assinatura de um não membro foi bloqueada corretamente.

**Causa:** [WebSocketAuthInterceptor.java](../chatapp-backend/src/main/java/com/chatapp/chatapp_backend/websocket/WebSocketAuthInterceptor.java) verifica JWT no CONNECT e associação ao servidor no SUBSCRIBE/SEND. Isso não retira assinaturas antigas nem verifica a autorização de cada entrega. O broker simples conserva a assinatura ativa.

**Correção:** rastrear sessões/assinaturas por usuário e canal; na remoção ou mudança de permissão, cancelar assinaturas ou desconectar as sessões afetadas, incluindo todas as instâncias. Verificar autorização nas entregas quando necessário. Expiração/revogação de token também precisa alcançar WebSocket. Restringir origens e limitar conexões, mensagens e tamanho de quadros separadamente de REST. Orientação: [segurança WebSocket — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html).

**Aceite:** a primeira mensagem enviada após remoção não chega ao antigo membro; nova inscrição também é recusada; membros atuais continuam recebendo; a revogação vale entre instâncias.

### SEC-06 — interceptação e reutilização em HTTP

**Prova:** um proxy exclusivamente local observou a senha enviada no login e o cabeçalho Bearer de uma conta fictícia. Reutilizar o token em outra requisição retornou 200. A senha/token não estão nas evidências publicadas. Isso demonstra exposição em HTTP; não demonstra quebra de TLS nem interceptação de um celular físico.

**Configuração:** o Nginx fornecido escuta HTTP, sem TLS próprio; pode estar atrás de um terminador TLS externo, que não foi auditado. Android debug/validation permitem HTTP. Release target 35 não habilita HTTP explicitamente, seguindo o bloqueio padrão da plataforma; a URL padrão de build ainda é HTTP local e precisa ser substituída por HTTPS para uma versão pública. Referência: [configuração de segurança de rede Android](https://developer.android.com/privacy-and-security/security-config).

**Correção:** publicar API/mídia via HTTPS e WebSocket via WSS; não aceitar login sensível em HTTP externo; configurar certificados, encaminhamento de protocolo e HSTS no ponto que termina TLS. Gerar release Android com URL HTTPS obrigatória e sem depuração. Conservar verificação de certificados/hostname e evitar TrustManagers permissivos. Pinning, se adotado, precisa de chave de reserva e rotação; não substitui TLS nem autorização.

**Aceite:** em captura de tráfego da versão release/PC publicados, não há senha/JWT em HTTP; certificados inválidos/hostname incorreto são rejeitados; WSS e mídia continuam funcionando.

### SEC-07 — logout sem revogação no servidor

**Prova:** após logout no PC, a chave de sessão foi removida do navegador, mas o JWT anterior ainda acessou `/api/users/me` com HTTP 200. O Android também limpa sua sessão local e remove registro de push; não chama uma revogação de JWT. Esse último fluxo foi identificado no código, sem execução do logout em APK.

**Correção:** implementar revogação de sessão por `jti`/registro de sessão ou tokens de acesso curtos com refresh rotativo e revogável. Distinguir sair deste dispositivo e sair de todos. A estratégia precisa invalidar WebSocket e funcionar entre instâncias. No PC, avaliar BFF/cookie HttpOnly, Secure e SameSite, com proteção CSRF apropriada caso a autenticação passe a usar cookie. Corrigir SEC-01 continua necessário. Referência: [gerenciamento de sessão — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

**Aceite:** token da sessão encerrada recebe 401; sessão de outro dispositivo segue a política definida; sessões WebSocket correspondentes são encerradas; troca de senha conserva a revogação já existente.

### SEC-08 — gravação antes da autorização

**Prova:** um usuário fora do servidor recebeu HTTP 400 ao enviar anexo ao canal, mas um arquivo com o conteúdo de teste já estava gravado. A negativa impediu a mensagem, não a gravação.

**Correção:** verificar associação e permissões antes de persistir o arquivo; usar temporário/limpeza compensatória quando uma etapa posterior falhar. Acrescentar quota por conta/servidor, limites de operações caras e limpeza de órfãos. MIME e extensão também precisam da correção SEC-01.

**Aceite:** upload proibido deixa zero arquivos; rollback/erro não deixa órfão; limites não podem ser contornados por mudar cabeçalhos. Não foi realizado enchimento de disco ou ataque volumétrico.

### SEC-09 — enumeração de contas

**Prova:** cadastro repetido retorna `Email ja cadastrado`, distinguindo a existência da conta. Login com senha inválida usa mensagem genérica, um controle positivo.

**Correção:** padronizar respostas públicas de cadastro/recuperação, com confirmação por email quando apropriado; limitar por conta/IP e monitorar exploração. Evitar diferença de status, conteúdo ou tempo que revele existência desnecessariamente.

**Aceite:** o cadastro público não permite confirmar em massa quais emails existem; usuários legítimos têm um fluxo claro para entrar ou recuperar a conta.

## Riscos adicionais identificados por revisão

| Área | Evidência e condição | Estratégia |
| --- | --- | --- |
| Segredo JWT padrão | `application.properties` contém fallback público. Prod exige variável de ambiente; a API de teste usou chave própria. Não foi comprovado uso do fallback em produção. | Falhar na inicialização pública se a chave estiver ausente, fraca ou igual a exemplo; armazenar em gerenciador de segredos; rotacionar se o fallback tiver sido usado. |
| Token Android em disco | `TokenManager` guarda JWT em DataStore comum. Há sandbox e backup desativado; não foi extraído token de aparelho. | Reduzir vida útil, usar chave do Android Keystore para proteção em repouso quando necessária e tratar dispositivo comprometido no modelo de ameaça. |
| Tokens FCM e proprietário | `DeviceController.unregister`/`DeviceService.unregisterDevice` não vinculam remoção à conta; `registerDevice` pode transferir um token conhecido para outra conta. Requer conhecer o token. Identificado por código, sem prova de entrega de push real. | Vincular operações à conta atual e ao dispositivo; exigir prova adequada para transferência; proteger token em logs/URLs; testar registro e remoção entre contas. |
| Erros de negócio e arquivos ausentes | Negativas de permissão aparecem como 400; arquivo ausente virou 500. Exceções Runtime retornam sua mensagem. | Exceções específicas: 401/403/404; resposta pública genérica para falha inesperada; detalhes somente em logs sem segredos. |
| Origens/volume WebSocket | Origem `*` aceita; REST rate limit não limita todos os frames de uma conexão já aberta. JWT inicial continua obrigatório. | Allowlist de origens web, tratamento compatível de clientes nativos, quotas de conexões/frames/tamanho e testes de revogação. |
| Endpoints operacionais | Prod expõe health/info/metrics; as regras gerais aceitam qualquer usuário autenticado para endpoints fora de health. | Restringir métricas a rede operacional/identidade administrativa; manter detalhes de health ocultos. |
| Privacidade de chamadas | ICE pode informar endereços entre participantes; Android tem opção relay no build, PC não a aplica. Mídia WebRTC não foi tratada como HTTP em texto claro. | Se ocultar IP entre participantes for requisito, configurar TURN e relay, medir custo/latência; avaliar E2EE de conteúdo separadamente de TLS e autorização. |

A busca de segredos cobre somente padrões de chave privada, token GitHub, ID de chave AWS e chave Google em arquivos de texto rastreados. Não cobre histórico Git, arquivos ignorados, segredos arbitrários, artefatos binários ou painel de configuração de produção. O resultado vazio não certifica ausência de segredos.

## Pacotes e controles que funcionaram

Os avisos de pacotes requerem atualização e análise de alcance, conforme o [anexo de dependências](security/2026-10-12/DEPENDENCIAS.md). Android resolveu Gson 2.8.5 e Java-WebSocket 1.3.6 com avisos conhecidos; o fluxo STOMP atual usa OkHttp, portanto o alerta de hostname de Java-WebSocket não comprova falha de TLS nesse fluxo. O aviso Gson de desserialização Java também não comprova ataque no uso comum de `fromJson`. No backend, alertas incluem Tomcat, Spring MVC, Jackson, PostgreSQL JDBC, Protobuf, gRPC, Guava e Netty. Não foram executados exploits volumétricos desses pacotes.

Foram observados: 401 sem token e com JWT inválido/adulterado/expirado/`alg:none`; bloqueio de consulta/envio/exclusão de mensagens de não membros; bloqueio de promoção de cargo e personalização não autorizada; isolamento de mensagens privadas e chamadas; bloqueio inicial de WebSocket sem JWT e de inscrição por não membro; paginação limitada; foto falsa rejeitada; mensagem HTML exibida como texto no PC; ausência de CORS permissivo no cenário REST testado. Android mascara Authorization nos logs HTTP, desativa esses logs em release e desativa backup.

Os 45 testes reexecutados também verificaram bloqueio após cinco erros no código de recuperação, expiração/uso único de autorização, revogação de JWT após troca de senha e validação de Google/CSRF. SMTP e Google foram simulados nos testes correspondentes; nenhuma mensagem foi enviada a pessoa real.

## Ordem de correção e nova validação

1. Corrigir conteúdo ativo, autorização de download/exclusão e gravação de anexos (SEC-01/02/03/08). Exercitar as duas interfaces e a API direta, com dono, membro, moderador, não membro e visitante.
2. Garantir HTTPS/WSS externo e geração release segura; validar cabeçalhos confiáveis e limites de login distribuídos/por conta (SEC-04/06). Testar também a entrada Nginx, não apenas a API direta.
3. Implementar sessão revogável e remoção de assinaturas ativas (SEC-05/07), incluindo troca de senha e mudança de cargo em múltiplas instâncias.
4. Atualizar dependências por BOM/SDK compatíveis, retirar pacotes sem uso, repetir OSV/npm e registrar a análise de alcance dos avisos restantes. Executar regressão Android, PC, REST, Postman e chamadas.
5. Corrigir enumeração, operações FCM, erros e controles operacionais. Fazer teste físico de release Android e implantação HTTPS, com captura de tráfego e dispositivos próprios.

A API deve verificar a permissão em cada operação. Um usuário pode observar as próprias requisições ou chamar a API por ferramentas externas; User-Agent, segredo embutido no APK ou CORS não substituem autorização. A auditoria não demonstrou acesso remoto ao Windows/Android nem quebra criptográfica.

## Reproduzir no ambiente descartável

Pré-requisitos: dependências npm/Playwright já instaladas, Java/Maven e Android SDK/Gradle configurados. Na raiz, em um terminal:

```powershell
./scripts/start-desktop-test-api.ps1 -Port 18085
```

Em outro terminal, iniciar o PC:

```powershell
cd samuschat-desktop
$env:SAMUSCHAT_API_URL = 'http://127.0.0.1:18085'
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5175 --strictPort
```

Na raiz, executar:

```powershell
node scripts/security-audit.mjs --disposable-local-api
```

O alvo e a origem web são fixos em localhost, há orçamento de até 260 chamadas contabilizadas pelo helper e as contas são criadas com nomes aleatórios. Não apontar esse roteiro para dados reais. A saída esperada no código auditado é 1 por falhas encontradas. As regras do roteiro devem evoluir junto com contratos corrigidos, preservando os ataques proibidos e os controles positivos.

Para a revisão de pacotes e manifestos, gerar primeiro os inventários:

```powershell
cd chatapp-backend
./mvnw.cmd dependency:list '-DincludeScope=runtime' '-DoutputFile=target/security-runtime-dependencies.txt'
cd ../samuschat-android
./gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath > ../logs/security-android-dependencies.log
./gradlew.bat :app:processReleaseMainManifest
cd ..
node scripts/security-dependencies.mjs
node scripts/security-static.mjs
```

O scanner estático requer também manifestos debug/validation já gerados; se ausentes, executar suas tarefas `processDebugMainManifest` e `processValidationMainManifest`. A consulta OSV envia apenas nomes e versões públicos, não código/credenciais.

## Limites desta auditoria

Não houve ataque a produção, contas reais ou serviços de terceiros; não houve negação de serviço volumétrica, enchimento de disco ou malware. Nginx/TLS públicos, múltiplas instâncias, PostgreSQL/Redis de produção, captura física de pacotes no celular, APK release assinado, MITM de HTTPS, componentes nativos internos de WebRTC e todos os formatos de fuzzing permanecem fora da validação dinâmica desta rodada. Esses limites precisam ser considerados antes de concluir que a implantação pública está segura.
