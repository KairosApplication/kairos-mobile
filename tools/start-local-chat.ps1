$ErrorActionPreference='Stop'
$runtime=Join-Path (Split-Path $PSScriptRoot -Parent) '.integration'
$docker='D:\KairosRuntime\DockerDesktop\resources\bin\docker.exe'
if(-not(Test-Path $docker)) { $docker=(Get-Command docker -ErrorAction Stop).Source }
& $docker --context desktop-linux compose -f (Join-Path $PSScriptRoot 'chat-local.compose.yaml') up -d
if($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o MongoDB local. Verifique o Docker Desktop.' }
$python=Join-Path $runtime 'kairos-inteligencia-artificial/.venv/Scripts/python.exe'
& $python -c "from pymongo import MongoClient; d=MongoClient('mongodb://127.0.0.1:27018',serverSelectionTimeoutMS=30000)['kairos_chat_local']; names=['conversations','user_memory','feedback','agent_traces','guardrail_events','judge_evaluations','rag_chunks','ai_executions','rag_documents']; [d.create_collection(n) for n in names if n not in d.list_collection_names()]; d.conversations.create_index([('user_id',1),('updated_at',-1)])"
if($LASTEXITCODE -ne 0) { throw 'MongoDB local ainda não está pronto. Aguarde e tente novamente.' }
$secretPath=Join-Path $runtime 'secrets/chat-runtime.json'
if(-not(Test-Path $secretPath)) {
    $bytes=New-Object byte[] 48
    $generator=[System.Security.Cryptography.RandomNumberGenerator]::Create(); $generator.GetBytes($bytes); $generator.Dispose()
    @{apiToken=[Convert]::ToBase64String($bytes)} | ConvertTo-Json | Set-Content $secretPath
}
$env:API_ACCESS_TOKEN=(Get-Content $secretPath -Raw | ConvertFrom-Json).apiToken
$env:API_PORT='8082'
$env:SERVER_ADDRESS='127.0.0.1'
$env:MONGODB_URI='mongodb://127.0.0.1:27018/kairos_chat_local'
$env:MONGODB_DATABASE='kairos_chat_local'
$env:APP_MONGODB_WRITES_ENABLED='true'
$env:APP_MONGODB_VERIFY_ON_STARTUP='true'
$java='C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
$jar=Join-Path $runtime 'kairos-api-ia/target/kairos-api-mongodb-0.0.1-SNAPSHOT.jar'
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
Write-Output 'Serviços de conversas iniciados; consulte os health checks para confirmar.'

