[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$noticeRoot = Join-Path $projectRoot 'desktop\src-tauri\resources\notices'
New-Item -ItemType Directory -Path $noticeRoot -Force | Out-Null
$manifest = Join-Path $projectRoot 'desktop\src-tauri\Cargo.toml'
$metadataText = & cargo.exe metadata --locked --offline --filter-platform x86_64-pc-windows-msvc --format-version 1 --manifest-path $manifest
if ($LASTEXITCODE -ne 0) { throw 'Cannot obtain the locked Rust dependency inventory.' }
$metadata = $metadataText | ConvertFrom-Json
$inventory = New-Object 'Collections.Generic.List[string]'
$inventory.Add('DeepFind Windows preview: third-party dependency inventory')
$inventory.Add('Java runtime notices are retained in runtime/legal; Maven library notices are also embedded in the backend JAR.')
foreach ($package in ($metadata.packages | Where-Object source | Sort-Object name,version)) {
    $inventory.Add("Rust: $($package.name) $($package.version) | $($package.license) | $($package.repository)")
    $sourceDirectory = Split-Path $package.manifest_path -Parent
    $destination = Join-Path $noticeRoot ("rust\" + $package.name + '-' + $package.version)
    $files = @(Get-ChildItem -LiteralPath $sourceDirectory -File | Where-Object { $_.Name -match '^(LICENSE|LICENCE|COPYING|NOTICE)' })
    if ($package.license_file) {
        $licensePath = [IO.Path]::GetFullPath((Join-Path $sourceDirectory $package.license_file))
        if ($licensePath.StartsWith($sourceDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $licensePath -PathType Leaf)) { $files += Get-Item -LiteralPath $licensePath }
    }
    if ($files.Count -gt 0) {
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        foreach ($file in $files) { Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $destination $file.Name) -Force }
    }
}
foreach ($name in @('react','react-dom','scheduler')) {
    $sourceDirectory = Join-Path $projectRoot "frontend\node_modules\$name"
    $package = Get-Content -LiteralPath (Join-Path $sourceDirectory 'package.json') -Raw | ConvertFrom-Json
    $inventory.Add("Frontend: $name $($package.version) | $($package.license)")
    $destination = Join-Path $noticeRoot "frontend\$name"
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $sourceDirectory 'LICENSE') -Destination $destination -Force
}
Add-Type -AssemblyName System.IO.Compression
$jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $projectRoot 'desktop\src-tauri\resources\deepfind-backend.jar'))
try {
    foreach ($entry in ($jar.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' })) {
        $inventory.Add('Backend: ' + $entry.Name)
        $buffer = New-Object IO.MemoryStream
        $source = $entry.Open()
        try { $source.CopyTo($buffer) } finally { $source.Dispose() }
        $buffer.Position = 0
        $nested = New-Object IO.Compression.ZipArchive($buffer, [IO.Compression.ZipArchiveMode]::Read)
        try {
            $number = 0
            foreach ($notice in ($nested.Entries | Where-Object { $_.FullName -match '^META-INF/(LICENSE|LICENCE|NOTICE|COPYING)([^/]*|/[^/]+)$' -and $_.Length -gt 0 })) {
                $destination = Join-Path $noticeRoot ('backend\' + $entry.Name)
                New-Item -ItemType Directory -Path $destination -Force | Out-Null
                $number++
                $reader = New-Object IO.StreamReader($notice.Open())
                try { [IO.File]::WriteAllText((Join-Path $destination "$number-$($notice.Name).txt"), $reader.ReadToEnd()) } finally { $reader.Dispose() }
            }
        } finally { $nested.Dispose(); $buffer.Dispose() }
    }
} finally { $jar.Dispose() }
[IO.File]::WriteAllLines((Join-Path $noticeRoot 'DEPENDENCIES.txt'), $inventory)
Write-Host 'PASS: third-party inventory and available dependency license/notice files staged.'
