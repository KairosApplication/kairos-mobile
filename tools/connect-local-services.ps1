param([string]$Serial)
$ErrorActionPreference = 'Stop'

# Development-only loopback bridge. Does not install, reset or uninstall apps and never accesses production data.
$sdkProperties = Join-Path (Split-Path $PSScriptRoot -Parent) 'local.properties'
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot -and (Test-Path -LiteralPath $sdkProperties)) {
    $sdkLine = Get-Content -LiteralPath $sdkProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) { $sdkRoot = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
}
if (-not $sdkRoot) { throw 'SDK Android não encontrado. Configure ANDROID_HOME ou local.properties.' }
$adbPath = Join-Path $sdkRoot 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'adb.exe não encontrado no SDK Android.' }

foreach ($endpoint in @('http://127.0.0.1:8081/health', 'http://127.0.0.1:8000/health')) {
    try {
        $response = Invoke-WebRequest -Uri $endpoint -TimeoutSec 5 -MaximumRedirection 0
        if ($response.StatusCode -ne 200) { throw 'Health check failed' }
    } catch {
        if ($endpoint -eq 'http://127.0.0.1:8081/health') { throw "A API real ainda não respondeu em $endpoint. Inicie-a antes de conectar o aplicativo." }
        Write-Warning 'O chatbot ainda não está disponível. Isso não impede conectar a API de estoque.'
    }
}

$adbArguments = @()
if ($Serial) { $adbArguments += @('-s', $Serial) }
$existingMappings = (& $adbPath @adbArguments reverse --list) -join "`n"
foreach ($port in @(8081, 8000)) {
    if ($existingMappings -notmatch "(?m)\btcp:$port\s+tcp:$port(?:\s|$)") {
        & $adbPath @adbArguments reverse "tcp:$port" "tcp:$port"
        if ($LASTEXITCODE -ne 0) { throw "Não foi possível conectar a porta $port. Confira o cabo USB e a depuração USB." }
    }
}
$watchSerial = $Serial
if (-not $watchSerial) {
    $watchSerial = (& $adbPath get-serialno).Trim()
    if ($LASTEXITCODE -ne 0 -or $watchSerial -eq 'unknown') { throw 'Selecione o aparelho usando -Serial.' }
}
$runtime = Join-Path (Split-Path $PSScriptRoot -Parent) '.integration'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$watchScript = Join-Path $PSScriptRoot 'watch-local-services.ps1'
$powershell = (Get-Process -Id $PID).Path
$watchRunning = $false
$watchPidFile = Join-Path $runtime 'bridge-watch.pid'
if (Test-Path -LiteralPath $watchPidFile) {
    $watchPidValue = 0
    if ([int]::TryParse((Get-Content -LiteralPath $watchPidFile -Raw).Trim(), [ref]$watchPidValue)) {
        $watchProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$watchPidValue" -ErrorAction SilentlyContinue
        $watchRunning = $watchProcess -and $watchProcess.CommandLine -and
            $watchProcess.CommandLine.IndexOf($watchScript, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
            $watchProcess.CommandLine.Contains($watchSerial)
    }
}
if (-not $watchRunning) {
    Start-Process -FilePath $powershell -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
        ('"' + $watchScript + '"'), '-Serial', $watchSerial, '-AdbPath', ('"' + $adbPath + '"')) -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $runtime 'bridge-watch.log') `
        -RedirectStandardError (Join-Path $runtime 'bridge-watch-error.log') | Out-Null
}
Write-Output 'API verificada e ponte USB conectada. A reconexão automática está ativa enquanto este computador estiver ligado.'
