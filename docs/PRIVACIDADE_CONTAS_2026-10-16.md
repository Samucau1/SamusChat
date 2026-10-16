# Privacidade e limpeza local — 16/10/2026

## Identidades públicas

Contatos, mensagens REST/STOMP, donos de servidores, membros e sinalização de chamadas usam identificadores públicos assinados `usr_...`, em lugar dos emails de login. Android e PC usam esses identificadores para comparar participantes, conversar, ligar e administrar membros. As telas mostram nomes de usuário.

Somente as operações `/api/users/me` preservam o email da própria conta autenticada, acrescentando `publicId`. O email próprio aparece na área de Perfil. Os nomes históricos de campos como `email`, `senderEmail`, `recipientEmail`, `ownerEmail` e `userEmail` continuam por compatibilidade, mas seus valores públicos agora são identificadores. Os endpoints também aceitam esses identificadores; a entrada por email conhecido continua compatível.

A tradução acontece na fronteira HTTP e na saída WebSocket, depois da autorização. Banco e Redis mantêm referências internas. Os identificadores usam HMAC-SHA256 com separação de contexto e assinatura de 128 bits; não substituem as permissões. Sua assinatura deriva da chave JWT compartilhada entre as APIs. Rotacionar essa chave altera os identificadores e exige recarregar a sessão dos clientes.

Senhas dos DTOs e segredos das requisições de autenticação foram retirados de `toString`. O nível de log web padrão foi limitado, logs locais foram sanitizados e a ausência das senhas fornecidas foi conferida nos logs atuais. Nenhuma senha pessoal integra fontes, documentação ou Git.

Referência para os critérios: [OWASP API3: exposição indevida de propriedades](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/).

## Limpeza autorizada

Dez contas foram removidas do banco de desenvolvimento, restando apenas as duas indicadas pelo usuário. Uma foi criada e a outra preservada. Ambas receberam as senhas solicitadas, armazenadas com BCrypt; sessões anteriores da conta existente foram revogadas.

A operação foi transacional e conferiu exatamente duas contas ao final. Foram removidos dados vinculados às contas excluídas em conversas, mensagens, chamadas, presenças, membros, dispositivos e recuperação. Seis servidores exclusivos foram excluídos. Dois servidores ligados à conta preservada foram mantidos, com transferência de administração, para conservar seus dados.

Backup PostgreSQL custom com cabeçalho validado: `logs/samus-before-account-cleanup-2026-10-16.dump`, fora do Git. Ele contém dados anteriores à limpeza e deve permanecer privado. A operação não é aplicada automaticamente a outros bancos nem impede novos cadastros.

## Validação

| Verificação | Resultado |
| --- | --- |
| Backend Maven verify | 144 testes; zero falhas, erros ou ignorados |
| Redis e PostgreSQL | Integração e migrações em containers descartáveis aprovadas |
| Android | 44 testes, build e lint aprovados; APK final instalado por USB |
| Desktop | TypeScript e build Vite aprovados |
| Chromium | 23 cenários aprovados |
| Edge | Fluxo privado com privacidade e reconexão aprovado |
| Newman | 144 requisições, incluindo consulta auxiliar; 248 verificações, zero falhas |
| Banco local | Duas contas; login aceito nas duas; um contato por conta, sem emails expostos |

`PublicIdentityTests` cobre contatos, email próprio, histórico privado, chamadas, servidores, membros, atualização de cargo, adulteração de identificador e proteção de segredos em logs. O teste de canal verifica identidade pública no frame WebSocket real. O cenário privado inspeciona frames MESSAGE, exige ausência dos emails e entrega com o GET de histórico bloqueado.

Expectativas anteriores de emails públicos foram atualizadas. As fixtures de tela cheia passaram a fornecer os nomes dos contatos. A rodada final passou integralmente.

Testes automatizados usaram contas fictícias em H2. No banco local, apenas login e contatos foram consultados para validação; nenhuma mensagem real foi enviada. O aceite manual PC ↔ Android continua pendente. As contas precisam de novo login. O usuário autorizou commit e push em 16/10/2026; essa autorização não declara aprovado um teste manual que não foi relatado.
