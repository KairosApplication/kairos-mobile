param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$AdbPath
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'adb.exe não encontrado.' }
$runtime = Join-Path (Split-Path $PSScriptRoot -Parent) '.integration'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$mutex = [System.Threading.Mutex]::new($false, ('Local\KairosUsbBridge-' + ($Serial -replace '[^a-zA-Z0-9]', '')))
$owned = $false
try {
    try { $owned = $mutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) { exit 0 }
    Set-Content (Join-Path $runtime 'bridge-watch.pid') $PID
    while ($true) {
        try {
            $state = & $AdbPath -s $Serial get-state 2>$null
            if ($LASTEXITCODE -eq 0 -and $state -eq 'device') {
                $mappings = (& $AdbPath -s $Serial reverse --list 2>$null) -join "`n"
                foreach ($port in @(8081, 8000)) {
                    # Do not reset existing mappings: doing so could interrupt an active request.
                    if ($mappings -notmatch "(?m)\btcp:$port\s+tcp:$port(?:\s|$)") {
                        & $AdbPath -s $Serial reverse "tcp:$port" "tcp:$port" | Out-Null
                        if ($LASTEXITCODE -eq 0) { Write-Output "$(Get-Date -Format s) Ponte USB restaurada: $port" }
                    }
                }
            }
        } catch {
            # Disconnecting USB or restarting adb is expected; retry only the local bridge.
        }
        Start-Sleep -Seconds 3
    }
} finally {
    if ($owned) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
