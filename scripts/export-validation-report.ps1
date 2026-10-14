[CmdletBinding()]
param(
    [string]$ResultsPath = 'docs/evidence/regression-2026-10-14.json',
    [string]$SummaryPath = 'docs/RELATORIO_DESENVOLVIMENTO_2026-10-14.md',
    [string]$RegressionPath = 'docs/REGRESSAO_PUBLICACAO_2026-10-14.md',
    [string]$OutputPath = 'docs/SamusChat_Relatorio_2026-10-14.docx'
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskResults = Get-Content -LiteralPath (Join-Path $taskRoot $ResultsPath) -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $taskResults.passed) { throw 'A passing regression is required before exporting.' }
$taskTarget = [IO.Path]::GetFullPath((Join-Path $taskRoot $OutputPath))
if (-not $taskTarget.StartsWith($taskRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must remain inside the repository.' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
function Escape-Xml([string]$Value) { [Security.SecurityElement]::Escape($Value) }
function Paragraph([string]$Value, [string]$Style = 'Normal') {
    '<w:p><w:pPr><w:pStyle w:val="' + $Style + '"/></w:pPr><w:r><w:t xml:space="preserve">' + (Escape-Xml $Value) + '</w:t></w:r></w:p>'
}
function Plain-Markdown([string]$Value) {
    ([regex]::Replace($Value, '\[([^\]]+)\]\(([^)]+)\)', '$1 ($2)')).Replace('**', '').Replace('`', '')
}
$taskBody = [Text.StringBuilder]::new()
foreach ($taskPath in @($SummaryPath, $RegressionPath)) {
    foreach ($taskLine in Get-Content -LiteralPath (Join-Path $taskRoot $taskPath) -Encoding UTF8) {
        if (-not $taskLine.Trim() -or $taskLine -match '^\|[\s|:\-]+\|$' -or $taskLine -match '^```') { continue }
        $taskStyle = if ($taskLine.StartsWith('# ')) { 'Title' } elseif ($taskLine.StartsWith('## ')) { 'Heading1' } elseif ($taskLine.StartsWith('### ')) { 'Heading2' } else { 'Normal' }
        $taskText = Plain-Markdown ($taskLine -replace '^#{1,3}\s+', '' -replace '^[-*]\s+', '' -replace '^\|\s*', '' -replace '\s*\|$', '')
        [void]$taskBody.Append((Paragraph $taskText $taskStyle))
    }
    [void]$taskBody.Append('<w:p><w:r><w:br w:type="page"/></w:r></w:p>')
}
$taskImages = @(
    @{ Name = 'desktop-paisagem-tela-cheia.png'; Caption = 'Fonte horizontal em tela cheia nativa; vídeo sintético no teste do Edge.' },
    @{ Name = 'desktop-preencher-tela.png'; Caption = 'Preenchimento remove faixas cortando bordas, sem esticar a imagem.' },
    @{ Name = 'desktop-retrato-tela-cheia.png'; Caption = 'Retorno ao modo retrato preserva a proporção da fonte e a chamada.' }
)
for ($taskIndex = 0; $taskIndex -lt $taskImages.Count; $taskIndex++) {
    $taskImage = $taskImages[$taskIndex]
    $taskImagePath = Join-Path $taskRoot ('docs/images/validacao-2026-10-14/' + $taskImage.Name)
    $taskBitmap = [Drawing.Image]::FromFile($taskImagePath)
    try { $taskHeight = [long](5943600 * $taskBitmap.Height / $taskBitmap.Width) } finally { $taskBitmap.Dispose() }
    $taskId = $taskIndex + 1
    [void]$taskBody.Append((Paragraph $taskImage.Caption 'Heading1'))
    [void]$taskBody.Append(@"
<w:p><w:r><w:drawing><wp:inline><wp:extent cx="5943600" cy="$taskHeight"/><wp:docPr id="$taskId" name="Synthetic test $taskId"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$taskId" name="Test $taskId"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="image$taskId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5943600" cy="$taskHeight"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
"@)
    [void]$taskBody.Append((Paragraph 'Fonte sintética. Esta captura não mostra um jogo real do Android nem comprova qualidade acústica do áudio físico.'))
    if ($taskIndex -lt $taskImages.Count - 1) { [void]$taskBody.Append('<w:p><w:r><w:br w:type="page"/></w:r></w:p>') }
}
$taskDocument = '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>' + $taskBody.ToString() + '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080"/></w:sectPr></w:body></w:document>'
$taskTypes = '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>'
$taskRootRelationships = '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="document" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
$taskRelationships = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
for ($taskIndex = 0; $taskIndex -lt $taskImages.Count; $taskIndex++) { $taskRelationships += '<Relationship Id="image' + ($taskIndex + 1) + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/' + $taskImages[$taskIndex].Name + '"/>' }
$taskRelationships += '</Relationships>'
$taskStyles = '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr><w:pPr><w:spacing w:after="120"/></w:pPr></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="34"/><w:color w:val="243B75"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="Heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="Heading 2"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style></w:styles>'
$taskFile = [IO.File]::Open($taskTarget, [IO.FileMode]::Create)
$taskZip = [IO.Compression.ZipArchive]::new($taskFile, [IO.Compression.ZipArchiveMode]::Create)
try {
    $taskParts = @{ '[Content_Types].xml' = $taskTypes; '_rels/.rels' = $taskRootRelationships; 'word/document.xml' = $taskDocument; 'word/styles.xml' = $taskStyles; 'word/_rels/document.xml.rels' = $taskRelationships }
    foreach ($taskPart in $taskParts.GetEnumerator()) {
        [void][xml]$taskPart.Value
        $taskWriter = [IO.StreamWriter]::new($taskZip.CreateEntry($taskPart.Key).Open(), [Text.UTF8Encoding]::new($false))
        try { $taskWriter.Write($taskPart.Value) } finally { $taskWriter.Dispose() }
    }
    foreach ($taskImage in $taskImages) {
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip, (Join-Path $taskRoot ('docs/images/validacao-2026-10-14/' + $taskImage.Name)), ('word/media/' + $taskImage.Name))
    }
} finally { $taskZip.Dispose(); $taskFile.Dispose() }
Write-Host "DOCX created: $taskTarget"
