[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRoot = Join-Path $projectRoot 'desktop\src-tauri\target\'
$runningBuild = @(Get-Process DeepFind -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase) })
if ($runningBuild.Count -gt 0) { throw 'Close the development/release test window before rebuilding its runtime files.' }
$previousPath = $env:PATH
try {
    if (!(Get-Command cargo.exe -ErrorAction SilentlyContinue)) {
        $rustBin = Join-Path $env:USERPROFILE '.cargo\bin'
        if (!(Test-Path -LiteralPath (Join-Path $rustBin 'cargo.exe'))) { throw 'Install Rust (MSVC) and Microsoft C++ Build Tools first.' }
        $env:PATH = $rustBin + ';' + $env:PATH
    }
    & (Join-Path $PSScriptRoot 'prepare-desktop.ps1')
    & (Join-Path $PSScriptRoot 'test-desktop-runtime.ps1')
    Push-Location (Join-Path $projectRoot 'desktop')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Desktop dependency installation failed.' }
        & cargo.exe fmt --manifest-path src-tauri/Cargo.toml -- --check
        if ($LASTEXITCODE -ne 0) { throw 'Rust formatting check failed.' }
        & cargo.exe fetch --locked --target x86_64-pc-windows-msvc --manifest-path src-tauri/Cargo.toml
        if ($LASTEXITCODE -ne 0) { throw 'Rust dependency download failed.' }
        & (Join-Path $PSScriptRoot 'prepare-desktop-notices.ps1')
        & cargo.exe test --release --locked --features custom-protocol --manifest-path src-tauri/Cargo.toml
        if ($LASTEXITCODE -ne 0) { throw 'Desktop supervisor tests failed.' }
        & npm.cmd run build -- --bundles nsis
        if ($LASTEXITCODE -ne 0) { throw 'Windows installer build failed.' }
        $installer = @(Get-ChildItem -LiteralPath 'src-tauri\target\release\bundle\nsis' -Filter '*-setup.exe')
        if ($installer.Count -ne 1) { throw 'Expected exactly one installer; inspect the generated NSIS directory.' }
        $artifactDirectory = Join-Path $projectRoot 'artifacts'
        New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
        $destination = Join-Path $artifactDirectory 'DeepFind-Setup.exe'
        Copy-Item -LiteralPath $installer[0].FullName -Destination $destination -Force
        Write-Host "PASS: unsigned preview installer created at $destination"
        Get-FileHash -LiteralPath $destination -Algorithm SHA256
    } finally { Pop-Location }
} finally { $env:PATH = $previousPath }
