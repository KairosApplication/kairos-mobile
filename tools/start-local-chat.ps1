param([switch]$Restart)
$ErrorActionPreference='Stop'
$runtime=Join-Path (Split-Path $PSScriptRoot -Parent) '.integration'
$providerFile=Join-Path $runtime 'secrets/groq.env'
if(Test-Path -LiteralPath $providerFile) {
    foreach($line in Get-Content -LiteralPath $providerFile) {
        if($line -match '^(GROQ_API_KEY|LLM_PROVIDER|LLM_MAX_TOKENS|MAX_TENTATIVAS_JUIZ|GROQ_MODEL|GROQ_FAST_MODEL|VISION_PROVIDER|GROQ_VISION_MODEL)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
        }
    }
}
$atlasFile=Join-Path $runtime 'secrets/mongodb-atlas.json'
if(-not(Test-Path -LiteralPath $atlasFile)) { throw 'Configure .integration/secrets/mongodb-atlas.json no servidor. Não há fallback para MongoDB local.' }
try { $atlas=Get-Content -LiteralPath $atlasFile -Raw | ConvertFrom-Json } catch { throw 'Configuração privada do MongoDB Atlas inválida.' }
if($atlas.uri -notmatch '^mongodb\+srv://' -or $atlas.database -ne 'kairos_ai') { throw 'Configure a URI SRV do Atlas e o banco kairos_ai.' }
$secretPath=Join-Path $runtime 'secrets/chat-runtime.json'
if(-not(Test-Path $secretPath)) {
    $bytes=New-Object byte[] 48
    $generator=[System.Security.Cryptography.RandomNumberGenerator]::Create(); $generator.GetBytes($bytes); $generator.Dispose()
    @{apiToken=[Convert]::ToBase64String($bytes)} | ConvertTo-Json | Set-Content $secretPath
}
$env:API_ACCESS_TOKEN=(Get-Content $secretPath -Raw | ConvertFrom-Json).apiToken
$env:API_PORT='8082'
$env:SERVER_ADDRESS='127.0.0.1'
$env:MONGODB_URI=$atlas.uri
$env:MONGODB_DATABASE=$atlas.database
$env:APP_MONGODB_WRITES_ENABLED='true'
$env:APP_MONGODB_VERIFY_ON_STARTUP='true'
$java='C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
$jar=Join-Path $runtime 'kairos-api-ia/target/kairos-api-mongodb-0.0.1-SNAPSHOT.jar'
if($Restart) {
    foreach($service in @(@{Port=8000;Pattern='-m\s+uvicorn\s+api:app'},@{Port=8082;Pattern=[regex]::Escape($jar)})) {
        $listeners=@(Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue)
        foreach($listener in $listeners) {
            $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
            if(-not $process -or $process.CommandLine -notmatch $service.Pattern) { throw 'Uma porta está ocupada por outro processo. O serviço não foi interrompido.' }
            Stop-Process -Id $listener.OwningProcess -ErrorAction Stop
        }
    }
}
elseif(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) {
    throw 'A API de persistência já está em execução. Execute tools/start-local-chat.ps1 -Restart para aplicar a configuração Atlas.'
}
if(-not(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue)) {
 Start-Process $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory (Split-Path $jar -Parent) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'mongo-api.log') -RedirectStandardError (Join-Path $runtime 'mongo-api-error.log')
}
$env:AI_API_TOKEN=$env:API_ACCESS_TOKEN
$env:AI_API_URL='http://127.0.0.1:8082'
$env:PERSISTENCE_BACKEND='api'
$env:SPRING_AUTH_URL='http://127.0.0.1:8081'
$env:FIREBASE_PROJECT_ID='germinimobile'
$env:GOOGLE_APPLICATION_CREDENTIALS=(Get-ChildItem (Join-Path $runtime 'secrets') -Filter '*firebase*.json' | Select-Object -First 1).FullName
$env:POSTGRESQL_HOST='127.0.0.1'
$env:POSTGRESQL_PORT='5433'
$env:POSTGRESQL_DB='kairos_dev'
$env:POSTGRESQL_USER='kairos_dev'
$env:POSTGRESQL_PASSWORD='kairos-local-only'
$python=Join-Path $runtime 'kairos-inteligencia-artificial/.venv/Scripts/python.exe'
if(-not(Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue)) {
 Start-Process $python -ArgumentList @('-m','uvicorn','api:app','--host','127.0.0.1','--port','8000') -WorkingDirectory (Join-Path $runtime 'kairos-inteligencia-artificial') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'chat-api.log') -RedirectStandardError (Join-Path $runtime 'chat-api-error.log')
}
foreach($endpoint in @('http://127.0.0.1:8082/health','http://127.0.0.1:8000/health')) {
    $ready=$false
    $deadline=(Get-Date).AddSeconds(30)
    while((Get-Date) -lt $deadline) {
        try { if((Invoke-WebRequest -Uri $endpoint -TimeoutSec 2).StatusCode -eq 200) { $ready=$true; break } } catch { }
        Start-Sleep -Milliseconds 250
    }
    if(-not $ready) { throw 'Um serviço não confirmou saúde. Consulte os logs privados em .integration; nenhum fallback local foi ativado.' }
}
Write-Output 'Chat e persistência saudáveis, usando MongoDB Atlas (kairos_ai), sem fallback local.'

