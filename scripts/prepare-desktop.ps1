[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaCommand = (Get-Command java.exe -ErrorAction Stop).Source
$jdkDirectory = Split-Path (Split-Path $javaCommand -Parent) -Parent
$jlinkCommand = Join-Path $jdkDirectory 'bin\jlink.exe'
if (!(Test-Path -LiteralPath $jlinkCommand)) { throw 'Use a Java 21 JDK with jlink on PATH.' }
$javaVersion = (& $javaCommand -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21[."]') { throw 'DeepFind packaging requires Java 21.' }

Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & npm.cmd run check
    if ($LASTEXITCODE -ne 0) { throw 'Frontend verification failed.' }
} finally { Pop-Location }

Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\mvnw.cmd -Pdesktop clean verify --batch-mode --no-transfer-progress
    if ($LASTEXITCODE -ne 0) { throw 'Desktop backend verification failed.' }
} finally { Pop-Location }

$resources = Join-Path $projectRoot 'desktop\src-tauri\resources'
New-Item -ItemType Directory -Path $resources -Force | Out-Null
$runtime = Join-Path $resources 'runtime'
if (Test-Path -LiteralPath $runtime) {
    $resolvedRuntime = (Resolve-Path -LiteralPath $runtime).Path
    $expectedRuntime = [IO.Path]::GetFullPath((Join-Path $projectRoot 'desktop\src-tauri\resources\runtime'))
    if ($resolvedRuntime -ne $expectedRuntime -or (Get-Item -LiteralPath $runtime).Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) {
        throw 'Refusing to replace a runtime outside the generated resource directory.'
    }
    Remove-Item -LiteralPath $resolvedRuntime -Recurse -Force
}
# Broad Java SE image intentionally retains reflection/service-loaded parser support.
# Do not shrink this list without repeating installed PDF/DOCX/SQLite/watch tests.
& $jlinkCommand --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.zipfs,jdk.localedata --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Java runtime creation failed.' }
Copy-Item -LiteralPath (Join-Path $projectRoot 'backend\target\deepfind-backend-0.0.1-SNAPSHOT.jar') -Destination (Join-Path $resources 'deepfind-backend.jar') -Force
& (Join-Path $runtime 'bin\java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Bundled Java runtime could not launch.' }
Write-Host 'PASS: tested frontend/backend and private Java runtime are ready.'
