param(
    [string]$ResultsPath = 'logs/publication-regression-2026-10-08.json',
    [string]$OutputPath = 'docs/SamusChat_Relatorio_2026-10-08.docx'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$results = Get-Content -Raw -Encoding UTF8 (Join-Path $root $ResultsPath) | ConvertFrom-Json
if (!$results.passed) { throw 'A successful regression result is required before exporting.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
function EscapeXml([string]$Text) { [System.Security.SecurityElement]::Escape($Text) }
function Paragraph([string]$Text, [string]$Style = 'Normal') {
    '<w:p><w:pPr><w:pStyle w:val="' + $Style + '"/></w:pPr><w:r><w:t xml:space="preserve">' + (EscapeXml $Text) + '</w:t></w:r></w:p>'
}
function PageBreak { '<w:p><w:r><w:br w:type="page"/></w:r></w:p>' }
function Picture([string]$Name, [int]$Id) {
    $path = Join-Path $root "docs/images/validacao-midia-2026-10-08/$Name"
    $bitmap = [Drawing.Image]::FromFile($path)
    try { $height = [long](2651760 * $bitmap.Height / $bitmap.Width) } finally { $bitmap.Dispose() }
    $label = EscapeXml $Name
    @"
<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="2651760" cy="$height"/><wp:docPr id="$Id" name="$label" descr="$label"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$Id" name="$label"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="image$Id"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="2651760" cy="$height"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
"@
}
$body = [Text.StringBuilder]::new()
function Add([string]$Xml) { [void]$body.Append($Xml) }
Add (Paragraph 'SamusChat' 'Title')
Add (Paragraph 'Relatório de desenvolvimento e validação' 'Subtitle')
Add (Paragraph '08 de outubro de 2026 | America/Sao_Paulo' 'Subtitle')
Add (Paragraph 'O que foi concluído hoje' 'Heading1')
Add (Paragraph 'Após liberar espaço no computador, retomamos a validação de chamadas privadas, transmissão de tela e estabilidade em dois emuladores Android. A execução completa passou em 19 verificações, com evidências reais de vídeo e áudio.')
Add (Paragraph 'Preparamos o pacote separado com.samuschat.validation, identificado como SamusChat Teste. Ele permite preparar contas fictícias sem limpar os dados do aplicativo pessoal. A API de teste usa H2 em memória; o TURN local foi exercitado com relay obrigatório.')
Add (Paragraph 'O roteiro recebeu verificações de viva-voz, segundo plano, bloqueio de tela e interrupção abrupta do outro participante. Corrigimos os seletores dos controles e a codificação UTF-8 com BOM para Windows PowerShell. As primeiras tentativas falharam no roteiro; a execução completa posterior passou.')
Add (Paragraph 'O design escuro e os controles aprovados anteriormente foram exercitados nesta rodada. Não houve nova alteração visual nem correção do código de mídia durante a retomada de hoje.')
Add (Paragraph 'Documentação e preparação para a próxima etapa' 'Heading1')
Add (Paragraph 'Consolidamos o resultado da mídia com capturas e atualizamos o índice dos documentos. O estudo de hospedagem, celulares reais e atualizações, iniciado em 07/10, foi incluído neste conjunto para revisão antes das etapas 4 e 5. Nenhum servidor foi contratado ou publicado.')
Add (Paragraph 'Escopo da versão' 'Heading1')
Add (Paragraph 'Branch: feature/call-channels. O commit reúne a variante de testes, o roteiro de regressão, os estudos, este documento Word e as imagens. Builds, logs, credenciais, dados pessoais e o SDK local não integram a versão do projeto.')
Add (PageBreak)
Add (Paragraph 'Resultados comprovados' 'Heading1')
foreach ($line in @(
    'Chamadas: autenticação, contatos, convite, aceite nos dois clientes, convite inverso e recusa.',
    'Controles: viva-voz e 25 ciclos de silenciar/ativar o microfone, totalizando 50 toques; os processos permaneceram ativos.',
    'Estabilidade: segundo plano e bloqueio de tela; o processo do chamador permaneceu o mesmo após retornar.',
    'Vídeo: maior amostra observada de 277 quadros decodificados; primeiro quadro renderizado na prévia local e no receptor.',
    'Áudio interno: 650 pacotes recebidos e 196.217 amostras não nulas com captura permitida pelo app auxiliar.',
    'Privacidade da captura: quando o app auxiliar proibiu a captura, os pacotes continuaram chegando, sem aumento das amostras não nulas após estabilização.',
    'Ciclo de transmissão: parar removeu a prévia; reiniciar retomou a captura; encerrar remotamente liberou os recursos.',
    'Interrupção abrupta: encerramos o processo do receptor de teste; o chamador saiu da chamada dentro da janela de 90 segundos do roteiro.',
    'Encerramento: serviço de mídia ausente nos dois pacotes ao final; nenhum FATAL EXCEPTION nos logs examinados.'
)) { Add (Paragraph $line) }
Add (Paragraph 'Ambiente e limites da medição' 'Heading1')
Add (Paragraph 'Android API 35, emulator-5554 e emulator-5556. Contas fictícias, API na porta 8081, banco H2 descartável e Coturn local. O emulador adicional confirmou boot em 38 segundos. Ao terminar, o ambiente adicional e os encaminhamentos foram encerrados, preservando o aplicativo normal.')
Add (Paragraph 'Os números de vídeo e áudio são evidências desta execução, não metas de desempenho ou teste de carga. O tom de 440 Hz veio de um aplicativo auxiliar. Viva-voz e microfone foram validados como controles e estados, sem medição da qualidade acústica física.')
$images = @(
    @{ Name = 'call-connected.png'; Title = 'Chamada privada conectada'; Caption = 'Captura da rodada aprovada: interface escura e controles de microfone, viva-voz, compartilhamento e encerramento.' },
    @{ Name = 'screen-local-preview.png'; Title = 'Prévia local da transmissão'; Caption = 'A tela compartilhada é renderizada no próprio cliente. A prévia foi removida ao parar e reapareceu após reiniciar a transmissão.' },
    @{ Name = 'screen-received.png'; Title = 'Transmissão recebida pelo outro cliente'; Caption = 'O receptor exibe a tela do aplicativo auxiliar de teste. Vídeo decodificado e áudio interno recebido foram confirmados pelos registros da rodada.' }
)
for ($i = 0; $i -lt $images.Count; $i++) {
    Add (PageBreak)
    Add (Paragraph $images[$i].Title 'Heading1')
    Add (Picture $images[$i].Name ($i + 1))
    Add (Paragraph $images[$i].Caption 'Caption')
    Add (Paragraph 'Captura real em emulador; somente contas fictícias.' 'Caption')
}
Add (PageBreak)
Add (Paragraph 'Regressão antes da publicação' 'Heading1')
Add (Paragraph ('Execução: ' + $results.completedAt + '. Resultados conferidos nos relatórios das ferramentas.'))
foreach ($check in $results.checks) { Add (Paragraph ($check.name + ': ' + $check.result)) }
Add (Paragraph 'A suíte completa de mídia aprovada hoje às 00:09:10 foi preservada como evidência. A regressão anterior à publicação complementou essa rodada com testes automatizados, builds e análise estática. Não se afirma uma segunda execução da mídia.')
Add (Paragraph 'Documento: estrutura ZIP/XML e três imagens incorporadas conferidas. A automação local do Word não concluiu a abertura; a paginação visual e a prévia PDF não foram verificadas.')
Add (Paragraph 'Próxima validação' 'Heading1')
Add (Paragraph 'Ainda precisamos dos dois celulares reais para medir microfone e alto-falante, eco, Bluetooth, chamadas longas e diferenças entre fabricantes. Também falta validar operadoras e a troca Wi-Fi/dados móveis. Encerrar o processo do receptor não equivale a perder a rede nem comprova reconexão automática.')
Add (Paragraph 'Antes das etapas 4 e 5, definir uma API pública HTTPS e TURN alcançável. O estudo propõe avaliar uma VPS Linux de staging com PostgreSQL, Redis, uploads persistentes e backups; não promete capacidade sem testes de carga.')
Add (Paragraph 'Atualizações previstas: APK para desenvolvimento; distribuição de testes com assinatura e versão controladas; posteriormente canais de teste da loja. Para o servidor, imagem versionada, staging, compatibilidade da API e migrações revisadas antes da produção.')
Add (Paragraph 'Fontes no repositório' 'Heading1')
foreach ($name in @('docs/REGRESSAO_MIDIA_ESTABILIDADE_2026-10-08.md', 'docs/REGRESSAO_PUBLICACAO_2026-10-08.md', 'docs/VALIDACAO_CHAMADAS_TRANSMISSAO_ESTABILIDADE.md', 'docs/ESTUDO_HOSPEDAGEM_CELULARES_ATUALIZACOES.md', 'docs/images/validacao-midia-2026-10-08/')) { Add (Paragraph $name 'Caption') }
$document = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:footerReference w:type="default" r:id="footer"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567"/></w:sectPr></w:body></w:document>
"@
$styles = '<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/><w:lang w:val="pt-BR"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="140" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:color w:val="1558B0"/><w:sz w:val="56"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:rPr><w:color w:val="555555"/><w:sz w:val="26"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="160"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:color w:val="1558B0"/><w:sz w:val="30"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Caption"><w:name w:val="Caption"/><w:basedOn w:val="Normal"/><w:rPr><w:color w:val="555555"/><w:sz w:val="18"/></w:rPr></w:style></w:styles>'
$contentTypes = '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>'
$rootRels = '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="document" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
$docRels = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="footer" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>'
for ($i = 0; $i -lt $images.Count; $i++) { $docRels += '<Relationship Id="image' + ($i + 1) + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/' + $images[$i].Name + '"/>' }
$docRels += '</Relationships>'
$footer = '<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:sz w:val="18"/></w:rPr><w:t>SamusChat | 08/10/2026 | Página </w:t></w:r><w:fldSimple w:instr="PAGE"/></w:p></w:ftr>'
$target = Join-Path $root $OutputPath
$file = [IO.File]::Open($target, [IO.FileMode]::Create)
$zip = [IO.Compression.ZipArchive]::new($file, [IO.Compression.ZipArchiveMode]::Create)
try {
    $parts = @{ '[Content_Types].xml' = $contentTypes; '_rels/.rels' = $rootRels; 'word/document.xml' = $document; 'word/styles.xml' = $styles; 'word/_rels/document.xml.rels' = $docRels; 'word/footer1.xml' = $footer }
    foreach ($part in $parts.GetEnumerator()) {
        [void][xml]$part.Value
        $entry = $zip.CreateEntry($part.Key)
        $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
        try { $writer.Write($part.Value) } finally { $writer.Dispose() }
    }
    foreach ($image in $images) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $root "docs/images/validacao-midia-2026-10-08/$($image.Name)"), "word/media/$($image.Name)")
    }
} finally { $zip.Dispose(); $file.Dispose() }
Write-Output "DOCX created: $target"
