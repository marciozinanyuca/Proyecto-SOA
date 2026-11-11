[CmdletBinding()]
param(
    [string]$BackendImage = 'ecoinvest/backend:azure-local',
    [string]$FrontendImage = 'ecoinvest/frontend:azure-local',
    [string]$OpenSslPath = 'C:\Program Files\Git\usr\bin\openssl.exe'
)

# Isolated smoke test. No inherited backend environment, official API calls or host databases.
# A disposable TLS bridge represents the HTTPS ingress Azure supplies in production.
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $OpenSslPath -PathType Leaf)) {
    throw 'OpenSSL is required for the local test certificate; no tool is installed automatically.'
}

$taskId = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskNetwork = "ecoinvest-smoke-$taskId"
$taskBackend = "ecoinvest-backend-$taskId"
$taskTls = "ecoinvest-tls-$taskId"
$taskFrontend = "ecoinvest-frontend-$taskId"
$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
$taskTemp = [IO.Path]::GetFullPath((Join-Path $taskTempRoot "ecoinvest-smoke-$taskId"))
$taskContainers = [Collections.Generic.List[string]]::new()
$taskNetworkCreated = $false

function Invoke-TestDocker {
    param([string[]]$DockerArguments)
    $taskResult = & docker @DockerArguments
    if ($LASTEXITCODE -ne 0) { throw ('Docker test operation failed: ' + $DockerArguments[0]) }
    return $taskResult
}

function Get-TestStatus {
    param([string]$Url)
    return (Invoke-TestDocker -DockerArguments @('exec', $taskBackend, 'curl', '--silent', '--show-error',
            '--max-time', '15', '--output', '/dev/null', '--write-out', '%{http_code}', $Url)) -join ''
}

try {
    New-Item -ItemType Directory -Path $taskTemp | Out-Null
    $taskCertificate = Join-Path $taskTemp 'test.crt'
    $taskKey = Join-Path $taskTemp 'test.key'
    $taskPreviousErrorAction = $ErrorActionPreference
    try {
        # OpenSSL writes key-generation progress to stderr even on success (Windows PowerShell 5).
        $ErrorActionPreference = 'Continue'
        & $OpenSslPath req -x509 -newkey rsa:2048 -sha256 -days 1 -nodes -keyout $taskKey -out $taskCertificate `
            -subj '/CN=backend-tls' -addext 'subjectAltName=DNS:backend-tls' 2>$null
    } finally { $ErrorActionPreference = $taskPreviousErrorAction }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the disposable local TLS certificate.' }

    # Single-quoted here-string: Nginx variables are literal, not PowerShell expressions.
    $taskTlsConfiguration = @'
pid /tmp/nginx.pid;
error_log /dev/stderr warn;
events {}
http {
    access_log off;
    client_body_temp_path /tmp/client_body;
    proxy_temp_path /tmp/proxy;
    server {
        listen 8443 ssl;
        ssl_certificate /test/test.crt;
        ssl_certificate_key /test/test.key;
        location / {
            proxy_pass http://backend:8081;
            proxy_set_header Host $host;
            proxy_next_upstream off;
        }
    }
}
'@
    # The test bridge uses unprivileged port 8443 instead of Azure ingress port 443.
    [IO.File]::WriteAllText((Join-Path $taskTemp 'tls.conf'), $taskTlsConfiguration, [Text.UTF8Encoding]::new($false))

    Invoke-TestDocker -DockerArguments @('network', 'create', '--internal', $taskNetwork) | Out-Null
    $taskNetworkCreated = $true
    Invoke-TestDocker -DockerArguments @('run', '--detach', '--name', $taskBackend, '--network', $taskNetwork,
        '--network-alias', 'backend', '--env', 'SPRING_PROFILES_ACTIVE=azure', '--env', 'SENACE_BASE_URL=',
        '--env', 'SENACE_AUTH_KEY=', $BackendImage) | Out-Null
    $taskContainers.Add($taskBackend)

    $taskReady = $false
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $taskDeadline) {
        $taskProbe = & docker exec $taskBackend curl --silent --max-time 2 --fail http://127.0.0.1:8081/actuator/health/readiness 2>$null
        if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $taskReady) { throw 'Backend did not become ready within the isolated test deadline.' }
    Write-Output 'PASS backend readiness (no provider query)'

    Invoke-TestDocker -DockerArguments @('run', '--detach', '--name', $taskTls, '--network', $taskNetwork,
        '--network-alias', 'backend-tls', '--mount', "type=bind,source=$taskTemp,target=/test,readonly",
        '--entrypoint', 'nginx', $FrontendImage, '-c', '/test/tls.conf', '-g', 'daemon off;') | Out-Null
    $taskContainers.Add($taskTls)

    # The generated certificate is trusted explicitly for this test, not via TLS bypass.
    # Change only the upstream port in a disposable template; keep hostname/SNI verification intact.
    $taskTestTemplate = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../../frontend/nginx/default.conf.template') -Raw
    $taskTestTemplate = $taskTestTemplate.Replace('proxy_pass https://${BACKEND_FQDN};', 'proxy_pass https://${BACKEND_FQDN}:8443;')
    [IO.File]::WriteAllText((Join-Path $taskTemp 'default.conf.template'), $taskTestTemplate, [Text.UTF8Encoding]::new($false))
    Invoke-TestDocker -DockerArguments @('run', '--detach', '--name', $taskFrontend, '--network', $taskNetwork,
        '--network-alias', 'frontend', '--env', 'BACKEND_FQDN=backend-tls',
        '--mount', "type=bind,source=$taskCertificate,target=/etc/ssl/certs/ca-certificates.crt,readonly",
        '--mount', "type=bind,source=$taskTemp/default.conf.template,target=/etc/nginx/templates/default.conf.template,readonly",
        $FrontendImage) | Out-Null
    $taskContainers.Add($taskFrontend)

    $taskFrontendReady = $false
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(20)
    while ([DateTime]::UtcNow -lt $taskDeadline) {
        $taskProbe = & docker exec $taskBackend curl --silent --max-time 2 --fail http://frontend:8080/healthz 2>$null
        if ($LASTEXITCODE -eq 0) { $taskFrontendReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $taskFrontendReady) { throw 'Frontend did not become ready.' }
    Write-Output 'PASS frontend healthz'

    foreach ($taskPath in @('/', '/api/estado-servicios')) {
        if ((Get-TestStatus -Url "http://frontend:8080$taskPath") -ne '200') { throw ('Unexpected status for ' + $taskPath) }
        Write-Output ('PASS HTTP 200 ' + $taskPath)
    }
    $taskMonitorText = (Invoke-TestDocker -DockerArguments @('exec', $taskBackend, 'curl', '--silent', '--show-error',
        '--fail', '--max-time', '15', 'http://frontend:8080/api/estado-servicios')) -join ''
    $taskMonitor = $taskMonitorText | ConvertFrom-Json
    foreach ($taskService in @($taskMonitor.senace, $taskMonitor.bcrp)) {
        if ($taskService.disponibilidadExterna -ne 'NO_VERIFICADA' -or $taskService.respaldoLocal -ne 'NO_IMPLEMENTADO' -or
            $taskService.alcanceMetricas -ne 'LLAMADAS_LOGICAS') { throw 'Unexpected Monitor semantics.' }
    }
    Write-Output 'PASS Monitor via frontend -> verified TLS bridge -> backend (no provider health checks)'

    foreach ($taskPath in @('/api/senace/datastream', '/actuator/health')) {
        if ((Get-TestStatus -Url "http://frontend:8080$taskPath") -ne '404') { throw ('Public route not blocked: ' + $taskPath) }
        Write-Output ('PASS blocked ' + $taskPath)
    }
    if ((Get-TestStatus -Url 'http://backend:8081/actuator/health/liveness') -ne '200') { throw 'Liveness failed.' }

    $taskInternal = (Invoke-TestDocker -DockerArguments @('network', 'inspect', $taskNetwork, '--format', '{{.Internal}}')) -join ''
    if ($taskInternal -ne 'true') { throw 'Test network has external egress.' }
    foreach ($taskContainer in @($taskBackend, $taskFrontend)) {
        $taskUser = (Invoke-TestDocker -DockerArguments @('exec', $taskContainer, 'id', '-u')) -join ''
        if ($taskUser -eq '0') { throw 'Application runs as root.' }
        Write-Output ('PASS non-root UID ' + $taskUser)
    }
    Invoke-TestDocker -DockerArguments @('exec', $taskBackend, 'sh', '-c',
        'test ! -e /app/ecoinvest_fallback.db && test ! -e /app/ecoinvest.db && test ! -e /app/.env.apis.local') | Out-Null
    # Docker diff inspects all writable-layer paths without requiring find in the slim image.
    $taskChanges = Invoke-TestDocker -DockerArguments @('diff', $taskBackend)
    $taskPersistence = @($taskChanges | Where-Object { $_ -match '(?i)(/[^/]+\.(db|sqlite|sqlite3)|/\.env[^/]*)$' })
    if ($taskPersistence.Count -gt 0) { throw 'Unexpected database or local configuration in the backend writable layer.' }
    Write-Output 'PASS internal network, no host database mounts, no database/.env paths in the writable layer'
    Write-Output 'CONTAINER SMOKE TEST PASSED. No claim about provider availability or Azure deployment.'
}
finally {
    for ($taskIndex = $taskContainers.Count - 1; $taskIndex -ge 0; $taskIndex--) {
        & docker rm --force $taskContainers[$taskIndex] | Out-Null
    }
    if ($taskNetworkCreated) { & docker network rm $taskNetwork | Out-Null }
    if ($taskTemp.StartsWith($taskTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $taskTemp -Leaf) -eq "ecoinvest-smoke-$taskId" -and (Test-Path -LiteralPath $taskTemp)) {
        Remove-Item -LiteralPath $taskTemp -Recurse -Force
    }
}
