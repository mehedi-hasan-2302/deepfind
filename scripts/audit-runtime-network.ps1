param(
    [string]$JarPath = "backend\target\deepfind-backend-0.0.1-SNAPSHOT.jar",
    [ValidateRange(1, 65535)]
    [int]$Port = 18082,
    [ValidateRange(1, 100)]
    [int]$Samples = 8
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($env:OS -ne "Windows_NT") {
    throw "This audit uses Windows connection inspection and must run on Windows."
}

$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$auditRoot = Join-Path ([IO.Path]::GetTempPath()) ("deepfind-network-audit-" + [guid]::NewGuid().ToString("N"))
$stdoutPath = Join-Path $auditRoot "stdout.log"
$stderrPath = Join-Path $auditRoot "stderr.log"
$process = $null

function Test-LoopbackAddress {
    param([string]$Address)

    return $Address -eq "127.0.0.1" -or $Address -eq "::1"
}

try {
    New-Item -ItemType Directory -Path $auditRoot | Out-Null
    $arguments = @(
        "-jar",
        ('"' + $resolvedJar + '"'),
        "--server.port=$Port",
        ('--deepfind.storage.data-directory="' + $auditRoot + '"')
    )
    $process = Start-Process `
        -FilePath "java" `
        -ArgumentList $arguments `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if ($process.HasExited) {
            break
        }
        try {
            $response = Invoke-WebRequest `
                -UseBasicParsing `
                -Uri "http://127.0.0.1:$Port/api/health" `
                -TimeoutSec 1
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            # Startup polling is expected to fail until the loopback server is ready.
        }
        Start-Sleep -Milliseconds 250
    }

    if (-not $ready) {
        throw "The packaged runtime did not become healthy on the audit port."
    }

    $tcp = @()
    $udp = @()
    for ($sample = 0; $sample -lt $Samples; $sample++) {
        $tcp += Get-NetTCPConnection -OwningProcess $process.Id -ErrorAction SilentlyContinue |
            Select-Object State, LocalAddress, LocalPort, RemoteAddress, RemotePort
        $udp += Get-NetUDPEndpoint -OwningProcess $process.Id -ErrorAction SilentlyContinue |
            Select-Object LocalAddress, LocalPort
        Start-Sleep -Milliseconds 250
    }

    $uniqueTcp = @($tcp | Sort-Object State, LocalAddress, LocalPort, RemoteAddress, RemotePort -Unique)
    $uniqueUdp = @($udp | Sort-Object LocalAddress, LocalPort -Unique)
    $unexpectedTcp = @($uniqueTcp | Where-Object {
        ($_.State -eq "Listen" -and -not (Test-LoopbackAddress $_.LocalAddress)) -or
        ($_.State -ne "Listen" -and -not (Test-LoopbackAddress $_.RemoteAddress))
    })

    Write-Output "DeepFind runtime network audit"
    Write-Output "Health: HTTP $($response.StatusCode)"
    Write-Output "Observed TCP endpoints:"
    $uniqueTcp | Format-Table -AutoSize
    Write-Output "Observed UDP endpoints:"
    $uniqueUdp | Format-Table -AutoSize

    if ($unexpectedTcp.Count -gt 0 -or $uniqueUdp.Count -gt 0) {
        throw "Unexpected non-loopback TCP or UDP activity was observed."
    }
    if (-not ($uniqueTcp | Where-Object { $_.State -eq "Listen" -and $_.LocalAddress -eq "127.0.0.1" })) {
        throw "The expected IPv4 loopback listener was not observed."
    }

    Write-Output "PASS: only the expected loopback TCP listener was observed."
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id
        $process.WaitForExit(5000) | Out-Null
    }

    if (Test-Path -LiteralPath $auditRoot) {
        $resolvedAuditRoot = [IO.Path]::GetFullPath($auditRoot)
        $resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (-not $resolvedAuditRoot.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove an audit directory outside the temporary directory."
        }
        Remove-Item -LiteralPath $resolvedAuditRoot -Recurse -Force
    }
}
