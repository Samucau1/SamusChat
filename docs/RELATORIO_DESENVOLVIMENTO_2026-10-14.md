# SamusChat — desenvolvimento e validação em 14/10/2026

Regressão concluída às 14:28:29, horário de São Paulo. O conjunto foi preparado para publicação na branch `feature/call-channels`.

## Entregas

- O celular USB passou a ser o destino padrão. O launcher seleciona o aparelho, encaminha a API, atualiza o APK preservando os dados e abre o app. Foram removidos 9,20 GiB de arquivos locais do emulador, mantendo SDK de compilação e ADB.
- O Android recebeu renovação automática de sessão por até 30 dias após o login, com tokens criptografados e sem guardar a senha. Falhas temporárias de conexão preservam a sessão; sair da conta ou trocar a senha revoga a renovação.
- O desktop permite escolher a entrada de microfone, testar seu nível e trocar de entrada durante a chamada. O áudio da transmissão passou a ser enviado e reproduzido pelo canal compatível com Android nas ligações individuais.
- Contatos e conversas mostram o email. O relato de mensagens ausentes foi explicado pelo destinatário escolhido: as mensagens estavam persistidas em outra conversa. O usuário corrigiu a identificação do PC para a conta samus.
- As transmissões receberam tela cheia nas duas versões. A captura Android acompanha a proporção real do display e as mudanças retrato/paisagem, incluindo Android 10. O espectador pode mostrar toda a imagem ou preencher a tela com corte de bordas.

## Resultado da regressão

| Verificação | Resultado |
| --- | --- |
| API, Redis e PostgreSQL | 132 testes; zero falhas, erros ou ignorados |
| Android | 42 testes; builds debug/validation e lint aprovados |
| Chromium | 23 cenários em 49,2 s |
| Edge instalado | 23 cenários em 49,4 s |
| Celular USB | 50 ciclos / 200 toques; mesmo processo após o stress |
| Sessão no celular | Identidade restaurada após encerrar e reabrir o processo |
| Cobertura de linhas da API | 90,10% — 1.101 de 1.222 linhas |
| Lint Android | Zero erros / 16 avisos |
| Varredura estática | Zero candidatos a credenciais; senhas pessoais ausentes |

Foram aprovadas **220 execuções de testes**, além dos probes PowerShell e da navegação no aparelho. A cobertura foi recalculada usando um arquivo JaCoCo novo.

## Evidências

A navegação no moto g(7) play permaneceu responsiva após 200 toques, com o mesmo processo e sem crash detectado. A restauração do login foi confirmada por um dump novo da interface após encerrar e reabrir o app; o armazenamento permaneceu igual e continha tokens criptografados.

Os navegadores exercitaram tela cheia nativa, vídeo sintético alternando retrato/paisagem, preenchimento e retorno à chamada. O áudio foi verificado com tom sintético, transporte por RTCDataChannel, pacotes de 20 ms e limites de fila.

O relatório Word inclui capturas de fontes sintéticas dos testes do Edge. Elas não mostram jogos reais do aparelho nem comprovam qualidade acústica de microfones e alto-falantes físicos.

## Limites e continuidade

O aceite com jogo/vídeo real girando o aparelho e a avaliação acústica PC ↔ celular permanecem testes manuais. A rodada local não comprova login Google real, redes externas ou hospedagem em produção. O smoke da pilha de produção integra o workflow remoto acionado pelo push.

O celular pode ser desconectado após a rodada. No modo atual, a API depende do encaminhamento USB; desconectar interrompe essa conexão, preservando o login salvo. Para retomar, conectar o cabo e executar o launcher.

## Documentos

- [Relatório Word com imagens](SamusChat_Relatorio_2026-10-14.docx)
- [Regressão para publicação](REGRESSAO_PUBLICACAO_2026-10-14.md)
- [Evidência estruturada](evidence/regression-2026-10-14.json)
- [Sessão persistente](SESSAO_ANDROID.md)
- [Áudio entre PC e celular](REGRESSAO_AUDIO_PC_CELULAR_2026-10-14.md)
- [Mensagens e identificação das contas](REGRESSAO_MENSAGENS_PC_CELULAR_2026-10-14.md)
- [Tela cheia e rotação](TELA_CHEIA_ROTACAO_PC_ANDROID.md)
