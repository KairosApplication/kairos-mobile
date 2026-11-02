$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$integration = Join-Path $workspace '.integration'
$spring = Join-Path $integration 'kairos-springboot'
$docker = 'D:\KairosRuntime\DockerDesktop\resources\bin\docker.exe'
if (-not (Test-Path -LiteralPath $docker)) {
    $docker = (Get-Command docker -ErrorAction Stop).Source
}
$credential = Get-ChildItem (Join-Path $integration 'secrets') -Filter '*.json' -File |
    Where-Object {
        $metadata = Get-Content $_.FullName -Raw | ConvertFrom-Json
        $metadata.type -eq 'service_account' -and $metadata.project_id -eq 'germinimobile'
    } | Select-Object -First 1
if (-not $credential) { throw 'Credencial Firebase germinimobile ausente em .integration\secrets.' }

& $docker --context desktop-linux info --format '{{.ServerVersion}}' 2>$null
if ($LASTEXITCODE -ne 0) { throw 'Abra o Docker Desktop e aguarde o mecanismo iniciar; depois execute novamente.' }
$credentialPath = $credential.FullName.Replace('\', '/')
$override = Join-Path $integration 'compose.firebase.local.yaml'
@"
services:
  api:
    restart: unless-stopped
    environment:
      FIREBASE_PROJECT_ID: germinimobile
      GOOGLE_APPLICATION_CREDENTIALS: /run/firebase/firebase-admin.json
    volumes:
      - type: bind
        source: '$credentialPath'
        target: /run/firebase/firebase-admin.json
        read_only: true
        bind:
          create_host_path: false
  postgres:
    restart: unless-stopped
  redis:
    restart: unless-stopped
  redis-history:
    restart: unless-stopped
  redis-history-api:
    restart: unless-stopped
"@ | Set-Content -LiteralPath $override

$compose = @('--context', 'desktop-linux', 'compose', '-f', (Join-Path $spring 'compose.dev.yaml'), '-f', $override)
$previousTtl = $env:DEV_AUTH_ACCESS_TTL
try {
    $env:DEV_AUTH_ACCESS_TTL = '15m'
    & $docker @compose up -d --wait api
    if ($LASTEXITCODE -ne 0) { throw 'A API local não iniciou corretamente.' }
    & $docker @compose run --rm --no-deps seed
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível preparar o banco local.' }
} finally {
    $env:DEV_AUTH_ACCESS_TTL = $previousTtl
}
Write-Output 'Backend local disponível em http://127.0.0.1:8081. Para conectar o celular, execute tools\connect-local-services.ps1.'
