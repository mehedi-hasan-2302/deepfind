[CmdletBinding()]
param([string]$ResourceDirectory)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$ResourceDirectory) { $ResourceDirectory = Join-Path $projectRoot 'desktop\src-tauri\resources' }
$ResourceDirectory = (Resolve-Path -LiteralPath $ResourceDirectory).Path
$caseRoot = Join-Path $projectRoot ('artifacts\runtime-smoke-' + [guid]::NewGuid().ToString('N'))
$fixtureRoot = Join-Path $caseRoot 'documents'
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $fixtureRoot 'notes.txt'), 'DeepFind packaged runtime text canary: cedarwalnut')

# Small real DOCX/PDF fixtures, generated locally without Office or external documents.
Add-Type -AssemblyName System.IO.Compression
$docx = [IO.Compression.ZipFile]::Open((Join-Path $fixtureRoot 'report.docx'), 'Create')
try {
    $parts = @{
        '[Content_Types].xml' = '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
        '_rels/.rels' = '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
        'word/document.xml' = '<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>DeepFind DOCX canary: maplequartz</w:t></w:r></w:p></w:body></w:document>'
    }
    foreach ($entry in $parts.GetEnumerator()) {
        $writer = New-Object IO.StreamWriter($docx.CreateEntry($entry.Key).Open())
        try { $writer.Write($entry.Value) } finally { $writer.Dispose() }
    }
} finally { $docx.Dispose() }
$pdfText = "BT /F1 18 Tf 40 740 Td (DeepFind PDF canary: birchsapphire) Tj ET`n"
$objects = @(
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    ("<< /Length " + $pdfText.Length + " >>`nstream`n" + $pdfText + 'endstream')
)
$pdf = New-Object Text.StringBuilder
[void]$pdf.Append("%PDF-1.4`n")
$offsets = @(0)
for ($i = 0; $i -lt $objects.Count; $i++) {
    $offsets += $pdf.Length
    [void]$pdf.Append("$($i + 1) 0 obj`n$($objects[$i])`nendobj`n")
}
$xrefOffset = $pdf.Length
[void]$pdf.Append("xref`n0 6`n0000000000 65535 f `n")
foreach ($offset in $offsets[1..5]) { [void]$pdf.Append(('{0:0000000000} 00000 n ' -f $offset) + "`n") }
[void]$pdf.Append("trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$xrefOffset`n%%EOF`n")
[IO.File]::WriteAllText((Join-Path $fixtureRoot 'report.pdf'), $pdf.ToString(), [Text.Encoding]::ASCII)

function Start-TestBackend {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = Join-Path $ResourceDirectory 'runtime\bin\java.exe'
    $jar = Join-Path $ResourceDirectory 'deepfind-backend.jar'
    $stateDirectory = Join-Path $caseRoot 'state'
    $info.Arguments = "-Xmx512m -jar `"$jar`" --deepfind.desktop=true --server.address=127.0.0.1 --server.port=0 --spring.main.banner-mode=off `"--deepfind.storage.data-directory=$stateDirectory`""
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($name in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) { $info.EnvironmentVariables.Remove($name) }
    $child = New-Object Diagnostics.Process
    $child.StartInfo = $info
    [void]$child.Start()
    $stderr = $child.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    $pending = $child.StandardOutput.ReadLineAsync()
    try {
        while ([DateTime]::UtcNow -lt $deadline -and !$child.HasExited) {
            if (!$pending.Wait(500)) { continue }
            $line = $pending.Result
            if ($line -match '^DEEPFIND_READY ([1-9][0-9]{0,4})$') {
                $port = [int]$Matches[1]
                if ($port -gt 65535) { throw 'Invalid handshake port.' }
                # Continue draining stdout so later indexing cannot fill the pipe.
                return @{ Process = $child; Port = $port; Stdout = $child.StandardOutput.ReadToEndAsync(); Stderr = $stderr }
            }
            if ($null -eq $line) { break }
            $pending = $child.StandardOutput.ReadLineAsync()
        }
        throw 'Packaged backend did not report readiness within 120 seconds.'
    } catch {
        $child.StandardInput.Close()
        if (!$child.WaitForExit(5000)) { $child.Kill(); $child.WaitForExit() }
        $child.Dispose()
        throw
    }
}

function Stop-TestBackend($instance) {
    $instance.Process.StandardInput.Close()
    if (!$instance.Process.WaitForExit(45000)) {
        $instance.Process.Kill()
        $instance.Process.WaitForExit()
        throw 'Packaged backend did not shut down within 45 seconds.'
    }
    if ($instance.Process.ExitCode -ne 0) { throw "Packaged backend exited with code $($instance.Process.ExitCode)." }
    $instance.Process.Dispose()
}

function Assert-Search($baseUrl, $query, $filename) {
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    do {
        $result = Invoke-RestMethod "$baseUrl/api/search?query=$query"
        if (@($result.results | Where-Object filename -eq $filename).Count -gt 0) { return }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Search did not find fixture $filename for canary $query."
}

$instance = $null
try {
    $instance = Start-TestBackend
    $baseUrl = "http://127.0.0.1:$($instance.Port)"
    if ((Invoke-RestMethod "$baseUrl/api/health").status -ne 'UP') { throw 'Health failed.' }
    $page = Invoke-WebRequest "$baseUrl/" -UseBasicParsing
    if ($page.Content -notmatch '/assets/index-.*\.js' -or $page.Headers['Content-Security-Policy'] -notmatch "connect-src 'self'") { throw 'Packaged UI or CSP missing.' }
    $headers = @{ 'X-DeepFind-Client' = 'browser' }
    Invoke-RestMethod "$baseUrl/api/index/start" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{ root = $fixtureRoot } | ConvertTo-Json) | Out-Null
    Assert-Search $baseUrl 'cedarwalnut' 'notes.txt'
    Assert-Search $baseUrl 'maplequartz' 'report.docx'
    Assert-Search $baseUrl 'birchsapphire' 'report.pdf'
    $deadline = [DateTime]::UtcNow.AddSeconds(40)
    do {
        $status = Invoke-RestMethod "$baseUrl/api/index/status"
        if ($status.state -eq 'COMPLETED') { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($status.state -ne 'COMPLETED' -or $status.failures -ne 0) { throw 'Fixture indexing did not complete cleanly.' }
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'watched.txt'), 'Live watcher canary: junipertopaz')
    Assert-Search $baseUrl 'junipertopaz' 'watched.txt'
    Stop-TestBackend $instance
    $instance = $null
    $instance = Start-TestBackend
    $baseUrl = "http://127.0.0.1:$($instance.Port)"
    Assert-Search $baseUrl 'maplequartz' 'report.docx'
    Assert-Search $baseUrl 'junipertopaz' 'watched.txt'
    Stop-TestBackend $instance
    $instance = $null
    Write-Host 'PASS: bundled Java, UI/CSP, PDF/DOCX/text extraction, watcher, SQLite/Lucene restart, and graceful pipe shutdown.'
    Write-Host "Retained isolated test data: $caseRoot"
} finally {
    if ($null -ne $instance) {
        if (!$instance.Process.HasExited) { $instance.Process.Kill(); $instance.Process.WaitForExit() }
        $instance.Process.Dispose()
    }
}
