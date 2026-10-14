# Protocolo de testes e correções — PC e Android

Entrega de 12/10/2026: correções implementadas e regressão final aprovada. O relatório da [auditoria inicial](AUDITORIA_SEGURANCA_PC_ANDROID_2026-10-12.md) conserva as provas anteriores às correções.

## Alterações

- Anexos exigem autenticação e associação ao canal para download; exclusão exige autoria ou moderação autorizada. Fotos de aparência permanecem públicas. O PC baixa com Bearer e usa blobs; o Android usa download autenticado e FileProvider, sem JWT na URL.
- Upload valida extensão/MIME e conteúdo, limita tamanho/pixels e recodifica PNG/JPG. Texto é UTF-8; formatos binários têm verificações de assinatura. DOCX tem limite de expansão e rejeição de macro. Isso não substitui antivírus.
- Permissão é verificada antes da gravação; rollback remove o arquivo. Quota: até 500 anexos e 250 MB por conta para o fluxo novo. V7 acrescenta identidade/tamanho do anexo e preserva mensagens anteriores. Arquivos antigos sem tamanho registrado não entram retroativamente na soma de bytes; continuam contando na quantidade.
- Login limita cinco tentativas por conta por minuto, com contador atômico Redis em ambientes configurados e contador local limitado no teste sem Redis. Cabeçalhos de IP só são aceitos de proxies explicitamente confiáveis.
- Cadastro e recuperação exigem 15 caracteres e no máximo 72 bytes, com lista inicial de senhas comuns. Login de contas antigas continua possível. Erros de cadastro foram uniformizados, mas a eliminação completa de enumeração exige um fluxo de confirmação de email.
- Logout revoga todas as sessões da conta no servidor. PC/Android explicam esse alcance. Falha de rede impede confirmar revogação; Android apresenta erro em vez de afirmar que saiu.
- WebSocket verifica JWT e associação nas entregas, além de CONNECT/SUBSCRIBE/SEND. Remoção do membro bloqueia mensagens na conexão já aberta. Origens e tamanho de mensagens foram limitados.
- Token Android passa a AES-GCM com chave do Android Keystore; sessões locais antigas são migradas quando utilizadas. Backup permanece desativado. A migração criptográfica não foi exercitada em aparelho físico nesta rodada.
- Produção exige HTTPS, com confiança explícita em proxy; HTTP de desenvolvimento continua disponível em localhost. Release Android exige URL HTTPS ao gerar pacote. Não foi implantado nem validado um certificado público.
- Dependências foram atualizadas. A migração de Spring Framework 6.2 para a linha corrigida 7.x requer atualização compatível de Spring Boot. Os avisos de XSLT e fragmentos SSE têm análise de alcance pendente de revisão contínua; esses fluxos não foram encontrados no código principal.

- Banco H2 do launcher conserva tabelas e dados após fechar todas as conexões (`DB_CLOSE_DELAY=-1`). A conexão substituta foi exercitada por teste de regressão. Encerrar a JVM continua descartando os dados. Erros de persistência retornam HTTP 500 genérico, sem SQL ou nomes de tabelas.

## Últimas verificações concluídas

| Verificação | Resultado |
| --- | --- |
| Backend, com Redis e PostgreSQL descartáveis | 128 testes, zero falhas/erros/skips |
| Migração PostgreSQL V6 → V7 | Mensagem e URL antigas preservadas; nome do anexo preenchido |
| PC, Playwright | 15 testes aprovados; download protegido, chat, GIF, salas, chamadas e compartilhamento |
| Ataques após correções | 75 verificações: 74 controles observados, zero falhas no roteiro, uma limitação ambiental de HTTP local |
| Postman/Newman — ambiente PC | 143 requisições e 247 verificações; zero falhas |
| Postman/Newman — ambiente Android | 143 requisições e 247 verificações; zero falhas |
| Android | Testes debug/validation, APK debug e lint aprovados; contagens nos artefatos abaixo |
| PC build | TypeScript/Vite aprovados |

A regressão final foi repetida com `httpclient5=5.6.3`, `httpcore5=5.4.3` e `opentelemetry=1.62.0`: 128 testes aprovados e inventário Maven resolvido. A nova consulta OSV verificou 207 coordenadas backend e 152 Android: restam dois avisos em `spring-webmvc=6.2.19`, sem avisos Android nessa consulta. A correção compatível de Spring/Boot permanece pendente; ausência de aviso não prova ausência de vulnerabilidades.

Evidências: [API de segurança](security/2026-10-12/correcoes/api-results.json), [backend](security/2026-10-12/correcoes/backend-results.json) [Android](security/2026-10-12/correcoes/android-results.json), [dependências finais](security/2026-10-12/correcoes/dependency-results.json) e [inspeção estática](security/2026-10-12/correcoes/static-results.json). A configuração H2 eleva o orçamento global de requisições dos clientes locais, mantendo o limite de tentativas por conta. Não equivale a validar limitação de volume em produção.

## Implantação HTTPS pendente

O Nginx fornecido ainda escuta HTTP. Com o perfil `prod`, a API rejeita esse transporte com HTTP 426. Antes de implantar, configurar TLS no proxy e os IPs exatos desse proxy em `TRUSTED_PROXY_IPS`; encaminhar `X-Forwarded-Proto` a partir do transporte real, nunca de um cabeçalho livre do cliente. Configurar `WEB_ORIGINS` com a origem HTTPS do PC. O Compose passa ambas as variáveis para os backends. Certificado, proxy TLS e fluxo público ainda precisam de validação em homologação.

## Reexecutar

Usar somente bancos descartáveis e os serviços temporários criados para estes testes. Na pasta backend, com Redis local de teste em 16385 e PostgreSQL de teste em 15435:

```powershell
$env:RUN_REDIS_TESTS = 'true'
$env:REDIS_TEST_PORT = '16385'
$env:RUN_PG_MIGRATION_TESTS = 'true'
./mvnw.cmd test
./mvnw.cmd dependency:list '-DincludeScope=runtime' '-DoutputFile=target/security-runtime-dependencies.txt'
```

O teste PostgreSQL usa exclusivamente o banco fictício `samus_audit`, usuário `audit`, senha de teste `audit_test_only`, em localhost:15435. Os testes Redis usam a porta acima. Não redirecionar essas portas para bancos reais.

Na raiz, iniciar a API H2 em um terminal:

```powershell
./scripts/start-desktop-test-api.ps1 -Port 18085
```

Na pasta PC, executar `npm run build` e, com `SAMUSCHAT_TEST_API_URL=http://127.0.0.1:18085`, executar `npm run test:e2e`. Para o roteiro de ataques, manter o PC em 5175 conforme abaixo e executar, na raiz, `node scripts/security-audit.mjs --disposable-local-api`.

```powershell
cd samuschat-desktop
$env:SAMUSCHAT_API_URL = 'http://127.0.0.1:18085'
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5175 --strictPort
```

Postman/Newman, na raiz:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json -e docs/postman/SamusChat-pc.postman_environment.json --env-var baseUrl=http://127.0.0.1:18085 --working-dir . --bail
npx --yes newman run docs/postman/SamusChat.postman_collection.json -e docs/postman/SamusChat-android.postman_environment.json --env-var baseUrl=http://127.0.0.1:18085 --working-dir . --bail
```

Na pasta Android, com SDK configurado, executar `:app:testDebugUnitTest :app:testValidationUnitTest :app:assembleDebug :app:lintDebug`. Para inspecionar release, usar `:app:processReleaseMainManifest -PapiBaseUrl=https://api.example.test/`; esse domínio é fictício e não serve para publicar um APK funcional. Consultar pacotes com `node scripts/security-dependencies.mjs` após atualizar os inventários Maven/Gradle.

## Teste manual do PC

Abrir `http://127.0.0.1:5175/`. Criar uma conta fictícia com nome de 3–30 caracteres e senha de pelo menos 15 caracteres. Para duas pessoas/clientes, usar uma janela normal e outra anônima com contas diferentes.

1. Criar um servidor, entrar com a segunda conta e enviar mensagens/anexos.
2. Verificar GIF, foto, capa e fundo de perfil/servidor.
3. Abrir chamada individual e canal de chamada; permitir microfone.
4. Compartilhar uma tela/janela com 750p/30 fps e depois 1080p/60 fps, se a fonte e o hardware suportarem. Observar o ajuste de desempenho e a parada.
5. Remover o segundo membro e confirmar que novas mensagens/anexos não chegam ou são bloqueados.
6. Sair e confirmar que a conta pede novo login nas outras sessões.

A configuração anterior apagava o banco ao fechar sua última conexão; os dados já perdidos não podem ser recuperados. A configuração corrigida preserva o banco durante a vida da JVM. O ambiente usa H2 descartável: contas/dados somem quando a API é encerrada. Não usar informações reais. Captura física, FPS entregue sob carga, áudio/dispositivos, HTTPS público, push real e criptografia no aparelho ainda precisam de validação manual.
