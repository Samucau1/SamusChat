# Beta por APK — meta de 12 a 16/10/2026

Objetivo escolhido pelo usuario: entregar uma beta para um pequeno grupo com acesso pela internet. O teste no Moto G7 Play foi aprovado pelo usuario em 09/10; a proxima etapa e preparar o ambiente externo. A data e uma meta de trabalho, condicionada a disponibilidade do servidor e ao resultado dos testes externos.

## Escopo da entrega

- API HTTPS e WebSocket WSS acessiveis sem depender do computador pessoal.
- PostgreSQL, Redis e anexos persistentes, com backup e restauracao conferidos.
- TURN acessivel nas redes dos testadores, com credenciais temporarias emitidas pela API.
- APK assinado com chave preservada para atualizacoes, URL externa e versionCode maior que 2.
- Distribuicao controlada para os testadores, com instrucoes de instalacao e canal para registrar problemas.
- Login por email/senha como acesso inicial; login Google depende de concluir OAuth e teste real. Sua ausencia deve estar explicita na entrega.

## Sequencia proposta

| Data | Trabalho | Evidencia de conclusao |
| --- | --- | --- |
| 12/10 | Definir e preparar servidor, endereco publico e persistencia | Ambiente acessivel e dados mantidos apos reinicio |
| 13/10 | Configurar HTTPS/WSS, TURN e backup | Saude externa, relay de midia e restauracao conferidos |
| 14/10 | Preparar assinatura, versao e APK beta | APK instalavel e atualizacao com assinatura compativel |
| 15/10 | Testar em dois clientes, Wi-Fi e dados moveis; corrigir falhas | Registro por cenario de cadastro, sessao, chat, anexos e chamadas |
| 16/10 | Regressao final e entrega ao pequeno grupo | Artefato identificado por hash, guia e limitacoes conhecidas |

O dia 12 e feriado nacional; ajustar a disponibilidade de trabalho e dos testadores conforme necessario.

## Preparacao ja identificada no repositorio

O Compose de producao inclui tres APIs, PostgreSQL, Redis e proxy HTTP. HTTPS e Coturn ainda precisam de configuracao propria. O APK atual usa assinatura debug e endereco da rede local. Nao distribuir esse artefato como beta externa.

Servidor e dominio ainda nao foram adquiridos. O usuario pediu alternativas gratuitas. Avaliar primeiro uma VM Oracle Always Free se cadastro e capacidade estiverem disponiveis; alternativa para testes privados e usar o computador com rede privada Tailscale, explicando que exige cliente adicional nos celulares e computador ligado. Nenhuma conta, recurso ou cobranca foi criada.

Referencias a conferir durante a escolha: [Oracle Free Tier](https://www.oracle.com/cloud/free/), [recursos Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm), [Tailscale](https://tailscale.com/pricing). Render Free nao resolve a infraestrutura completa atual: nao oferece disco persistente para o servico gratuito e requer adaptar os anexos; TURN precisa de hospedagem apropriada. [Limites Render](https://render.com/docs/free).

## Criterios para liberar

Cadastro/login, sessao, mensagens e anexos precisam funcionar externamente; chamadas e compartilhamento precisam ser conferidos em redes diferentes. Verificar reinicio dos servicos, backup, atualizacao do APK e ausencia de erros impeditivos nos cenarios exercitados. Registrar limites e falhas conhecidas. A aprovacao local nao substitui essas verificacoes.

Uma assinatura release diferente da debug nao atualiza diretamente o APK instalado hoje; definir a transicao com os testadores antes da distribuicao, considerando os dados locais. Guardar chaves e segredos fora do Git.
