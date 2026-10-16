# Regressão HTTP com Postman / Newman

Em 16/10/2026, a validação de destinatário privado passou a comparar `publicId` e conferir ausência de emails. A coleção continua com 143 itens; uma consulta auxiliar ao perfil autenticado resulta em **144 requisições e 248 verificações**, aprovadas na API H2 isolada. Veja [privacidade](../PRIVACIDADE_CONTAS_2026-10-16.md).

Atualização de 12/10/2026: coleção compartilhada com **143 requisições e 247 verificações**, aprovada nos ambientes PC e Android. Cada execução cria contas, servidores e canais fictícios. Execute a coleção inteira, na ordem, exclusivamente em uma API descartável. Não exporte tokens preenchidos para o Git.

Na raiz, inicie a API em um terminal:

```powershell
./scripts/start-desktop-test-api.ps1 -Port 18085
```

Em outro terminal, na raiz:

```powershell
npx --yes newman run docs/postman/SamusChat.postman_collection.json -e docs/postman/SamusChat-pc.postman_environment.json --env-var baseUrl=http://127.0.0.1:18085 --working-dir . --bail
npx --yes newman run docs/postman/SamusChat.postman_collection.json -e docs/postman/SamusChat-android.postman_environment.json --env-var baseUrl=http://127.0.0.1:18085 --working-dir . --bail
```

No Postman, importe a coleção e ambos os ambientes; ajuste `baseUrl` para a porta acima. Se necessário, selecione manualmente os arquivos de `fixtures/` nas requisições de upload. Os arquivos são fictícios; `security-attack.html` é um marcador inofensivo para testar rejeição de HTML.

O launcher usa classpath de testes, H2 exclusivo, uploads em target e integrações externas desligadas. O banco conserva os dados mesmo quando todas as conexões fecham e desaparece ao encerrar a JVM. Apenas selecionar o perfil test em spring-boot:run não garante esse isolamento.

| Métodos | Cobertura |
| --- | --- |
| GET | Saúde, autenticação, perfil, servidores, membros, mensagens, contatos, ICE, chamadas, salas e download protegido |
| POST | Cadastro/login/logout, servidor, canais, mensagens, sinalização de chamadas, salas, anexos, aparência e dispositivos |
| PUT | Perfil e personalização |
| DELETE | Mensagens, aparência e anexos com verificação de autoria/permissões |

As pastas de PC e Android exercitam avatar/GIF, capa, fundo, versão estática, remoção, cargos e dispositivos. Os cenários de segurança verificam download anônimo e por não membro, exclusão indevida, conteúdo/MIME perigoso, propriedade do dispositivo, senha fraca, cinco tentativas por conta, troca de cabeçalho IP e revogação após logout.

Cabeçalhos X-Forwarded-For presentes em fixtures não criam identidades confiáveis: o backend só aceita proxies configurados. O perfil H2 aumenta o orçamento global para a regressão local, mantendo o limite de cinco tentativas de login por conta por minuto. A coleção não é um teste de carga nem comprova proteção sob tráfego distribuído.

Estes ambientes exercitam contratos HTTP da API compartilhada, sem executar o aplicativo Android físico. SDP fictício verifica sinalização; áudio, vídeo, captura e FPS exigem WebRTC e testes manuais de hardware. Redis, migração PostgreSQL, WebSocket, recuperação e Google têm testes backend separados.

Consulte o [protocolo e resultados finais](../PROTOCOLO_SEGURANCA_PC_ANDROID_2026-10-12.md) e a [auditoria inicial](../AUDITORIA_SEGURANCA_PC_ANDROID_2026-10-12.md).
