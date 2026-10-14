# Relatorio de autenticacao

Gerado em: 2026-10-05 14:29:54 -03:00

Destinatario: sgfernandes12@gmail.com

Ambiente: testes automatizados com banco H2 isolado. SMTP capturado em memoria e identidade Google simulada; entrega real e login Google em dispositivo ainda nao validados.

Senhas, codigos e tokens omitidos. A senha antiga e verificada como rejeitada, e a nova como aceita.

| Verificacao | Resultado |
| --- | --- |
| Codigo expirado e troca sem autorizacao rejeitados | PASSOU |
| Email destinado a conta de teste; codigo de 4 digitos capturado; validacao concluida; senha antiga rejeitada; nova aceita; sessao antiga revogada; reutilizacao bloqueada | PASSOU |
| Google vincula conta existente e preserva senha | PASSOU |
| Reenvio limitado e conta inexistente sem envio | PASSOU |
| Google cria conta nova; token invalido rejeitado | PASSOU |
| Cinco erros persistidos no banco; codigo correto bloqueado depois do limite | PASSOU |
| Autorizacao expirada e autorizacao anterior ao reenvio rejeitadas | PASSOU |
| Email externo exige senha antes de vincular ao Google | PASSOU |
| Fluxo pelas tres rotas HTTP publicas concluido sem devolver codigo no pedido | PASSOU |
| Falha SMTP desfaz codigo no banco | PASSOU |

Total desta suite: 10; falhas: 0; erros: 0

O aplicativo deve ser testado manualmente com SMTP e OAuth configurados para confirmar entrega na caixa de entrada e seletor de conta Google.

Verificacoes adicionais GIS e token Google:

| Verificacao | Resultado |
| --- | --- |
| CSRF valido nao libera token Google invalido | PASSOU |
| CSRF ausente, divergente, vazio ou cookie duplicado rejeitado antes da identidade | PASSOU |
| GIS JSON: cookie e corpo iguais liberam verificacao Google | PASSOU |
| GIS formulario: token unico no corpo; query string nao substitui corpo | PASSOU |
| Android JSON preservado; formulario rejeitado na rota nativa | PASSOU |
| RSA local: assinatura, audiencia, emissor incorretos e expiracao rejeitados | PASSOU |
| Workspace: email verificado e hd nao vazio; email externo sem autoridade | PASSOU |
| Rejeita sub ausente e tokens malformados | PASSOU |
| RSA local: aceita os dois emissores Google e identifica pelo sub | PASSOU |

Suite API: 110 testes; 0 falhas; 0 erros; 1 ignorados.
Suite Android: 24 testes; 0 falhas; 0 erros.

Estes resultados correspondem aos arquivos JUnit locais. Envio real do relatorio pendente ate configurar SMTP; o script informa aceite SMTP somente depois de Send concluir.
