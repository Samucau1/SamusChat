# Personalização de perfil e servidor — 12/10/2026

PC e Android usam a mesma API e a mesma conta. O perfil pode ter avatar estático ou GIF, capa, fundo, cor de destaque e texto “Sobre mim”. O servidor pode ter ícone, capa, fundo e cor. Administradores podem alterar a aparência do servidor; membros podem consultá-la.

No PC, abrir **Editar** no rodapé para personalizar o perfil. No servidor, usar **Personalizar servidor**. No Android, usar a aba **Perfil** ou o botão **Personalizar servidor**. Uploads e remoções são salvos imediatamente; texto e cor têm botão de salvar. A mudança aparece no outro cliente após atualizar a lista ou recarregar o perfil.

As fotos locais da versão Android anterior não são enviadas automaticamente. Escolher novamente a imagem a salva na conta. Não há assinatura ou cobrança para esses recursos. Esta entrega implementa as personalizações descritas aqui; não reproduz todos os recursos comerciais do Discord Nitro, como emojis globais, boosts, efeitos de avatar ou perfis distintos por servidor.

## Limites para manter a interface leve

- PNG/JPG até 4 MB; GIF até 2 MB. A API verifica o conteúdo da imagem, sem confiar na extensão enviada.
- Imagens estáticas: no máximo 4096 px por dimensão e 16 megapixels na entrada. A API reduz avatar/ícone para até 256 px e capa/fundo para até 1600 px, sem ampliar imagens menores.
- Fotos opacas são recodificadas em JPEG para reduzir o arquivo; transparência permanece em PNG. URLs relativas usam o endereço da API configurado em cada cliente, sem obrigar o celular a acessar o localhost do servidor.
- GIF: até 60 quadros, avatar/ícone até 512×512 e capa/fundo até 960×540. O total de pixels dos quadros não pode superar 16 milhões. GIFs acima dos limites precisam ser reduzidos antes do upload; não há transcodificador de GIF nesta entrega.
- Cada GIF recebe uma versão PNG do primeiro quadro. **Economizar animações** fica habilitado por padrão. Fundos usam sempre a versão estática. O Android decodifica imagens no tamanho do componente e reutiliza o cache Coil; o PC usa carregamento tardio e decodificação assíncrona.
- As imagens substituídas são removidas depois do commit no banco. Em rollback, a nova mídia é removida. As URLs não contêm nomes de arquivos enviados pelo usuário.

## Contratos compartilhados

| Método | Caminho | Uso |
| --- | --- | --- |
| PUT | `/api/users/me/appearance` | `{bio, accentColor}`; bio até 190 caracteres, cor `#RRGGBB` |
| POST / DELETE | `/api/users/me/media/{slot}` | Multipart `file` ou remoção; `avatar`, `banner`, `background` |
| PUT | `/api/servers/{id}/appearance` | `{accentColor}`; exige administrador |
| POST / DELETE | `/api/servers/{id}/media/{slot}` | Multipart `file` ou remoção; `icon`, `banner`, `background` |
| GET | `/api/users/me`, `/api/servers`, `/api/servers/{id}` | Retorna URLs e aparência salvas |

A migração Flyway V6 acrescenta os campos sem remover contas, servidores ou mensagens. Clientes antigos podem ignorar os campos opcionais novos. A edição do nome aceita de 1 a 50 caracteres aparados, sem tags ou caracteres de controle, e não falha quando o nome não mudou. O cadastro conserva sua regra original de 3–30 caracteres.

## Desktop: API e chamadas

O repositório TypeScript cobre os contratos REST dos controladores existentes: autenticação, recuperação, Google nativo/GIS, perfil, servidores, canais, membros/cargos, mensagens, anexos, dispositivos, chamadas e salas. A interface inclui membros, alteração de cargos, remoção confirmada, saída do servidor e exclusão confirmada de mensagens próprias. Google e dispositivos têm adaptadores REST; configuração OAuth/FCM e notificações web não fazem parte da interface desta entrega.

Os canais de chamada do PC usam o mesmo contrato de sala do Android, até seis participantes e um par WebRTC por destinatário. A captura de microfone e tela é compartilhada entre os pares; cada destinatário ainda exige sua própria codificação/transmissão. Chamadas individuais continuam disponíveis.

Tela econômica: até 960×540, 10 fps e 700 kbit/s de vídeo. Equilibrada, em chamada individual: até 1280×720, 15 fps e 1,5 Mbit/s. No grupo, o orçamento econômico é dividido entre os pares. Esses limites não incluem áudio nem overhead de rede. A trilha de vídeo anterior ao compartilhamento usa um canvas de 16×9 desabilitado. A prévia local individual é opcional e começa oculta; grupos não reproduzem a própria captura.

Os testes automatizados verificam limites configurados, sinalização e decodificação de tela sintética. A redução real de CPU/GPU e a qualidade de áudio físico precisam de medição na máquina e nos dispositivos. Captura exige localhost/HTTPS e redes externas exigem TURN acessível.

Referências técnicas: [limites de codificação WebRTC](https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpSender/setParameters), [captura de tela](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Capture_API/Using_Screen_Capture), [decodificadores de GIF do Coil](https://coil-kt.github.io/coil/gifs/). O Android mantém Coil 2.5.0 e usa os nomes de decodificadores dessa versão.
