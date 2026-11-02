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
foreach ($port in @(8081, 8000)) {
    & $adbPath @adbArguments reverse "tcp:$port" "tcp:$port"
    if ($LASTEXITCODE -ne 0) { throw "Não foi possível conectar a porta $port. Confira o cabo USB e a depuração USB." }
}
Write-Output 'API de estoque verificada e portas locais encaminhadas ao Android por USB. Execute o build debug no Android Studio.'
