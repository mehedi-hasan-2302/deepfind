[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][int]$AppProcessId,
    [ValidateRange(1,120)][int]$Samples = 20
)
$ErrorActionPreference = 'Stop'
$root = Get-Process -Id $AppProcessId -ErrorAction Stop
if ($root.ProcessName -ne 'DeepFind') { throw 'Choose the DeepFind shell process, not an arbitrary process.' }
$owned = New-Object 'Collections.Generic.HashSet[int]'
[void]$owned.Add($AppProcessId)
$observed = New-Object 'Collections.Generic.HashSet[string]'
$unexpected = New-Object 'Collections.Generic.HashSet[string]'
$listenerSeen = $false
for ($sample = 0; $sample -lt $Samples; $sample++) {
    $processes = @(Get-CimInstance Win32_Process)
    do {
        $added = $false
        foreach ($child in $processes) {
            if ($owned.Contains([int]$child.ParentProcessId) -and $child.Name -in @('java.exe','msedgewebview2.exe','DeepFind.exe')) {
                if ($owned.Add([int]$child.ProcessId)) { $added = $true }
            }
        }
    } while ($added)
    $javaProcesses = @($processes | Where-Object { $_.Name -eq 'java.exe' -and $owned.Contains([int]$_.ProcessId) } | ForEach-Object { [int]$_.ProcessId })
    foreach ($connection in (Get-NetTCPConnection -ErrorAction Stop)) {
        if (!$owned.Contains([int]$connection.OwningProcess)) { continue }
        $description = "TCP $($connection.LocalAddress):$($connection.LocalPort) -> $($connection.RemoteAddress):$($connection.RemotePort) $($connection.State)"
        [void]$observed.Add($description)
        # Windows reports non-listening, unconnected socket reservations as wildcard Bound rows.
        # They are not network traffic or listening sockets; subsequent connections are still audited.
        if ($connection.State -eq 'Bound' -and $connection.RemotePort -eq 0 -and $connection.RemoteAddress -in @('0.0.0.0','::')) { continue }
        if ($connection.State -eq 'Listen' -and $connection.LocalAddress -eq '127.0.0.1' -and [int]$connection.OwningProcess -in $javaProcesses) { $listenerSeen = $true }
        $localAllowed = $connection.LocalAddress -in @('127.0.0.1','::1')
        $remoteAllowed = $connection.RemoteAddress -in @('127.0.0.1','::1','0.0.0.0','::')
        if (!$localAllowed -or !$remoteAllowed) { [void]$unexpected.Add($description) }
    }
    foreach ($endpoint in (Get-NetUDPEndpoint -ErrorAction Stop)) {
        if ($owned.Contains([int]$endpoint.OwningProcess)) { [void]$unexpected.Add("UDP $($endpoint.LocalAddress):$($endpoint.LocalPort)") }
    }
    Start-Sleep -Milliseconds 500
}
$observed | Sort-Object
if (!$listenerSeen) { throw 'No loopback backend listener was observed; audit is inconclusive.' }
if ($unexpected.Count -gt 0) { $unexpected | Sort-Object; throw 'Unexpected desktop process network endpoints observed.' }
Write-Host "PASS: $Samples samples of the shell, Java backend, and discovered WebView2 descendants showed only loopback TCP and no UDP. This is sampled evidence, not proof of all future behavior."
