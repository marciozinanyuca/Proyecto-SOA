#requires -Version 5.1
<#
.SYNOPSIS
Arranca el backend local con autenticacion SQLite y LKG JSON opt-in.
.DESCRIPTION
No compila, instala, abre el frontend, consulta APIs ni importa snapshots de laboratorio.
Preparar previamente el JAR mediante Maven offline, con autorizacion independiente.
-CargarSenace autoriza leer SOLO SENACE_BASE_URL y SENACE_AUTH_KEY de .env.apis.local.
La credencial se entrega al entorno del hijo Java, nunca al entorno padre o argumentos.
-InicializarUsuarios pide las dos contrasenas iniciales mediante entrada oculta.
Solo crea usuarios inexistentes segun el inicializador actual; no cambia claves existentes.
La base backend/ecoinvest_fallback.db debe existir con esquema valido. No se crea ni migra.
Las consultas posteriores de usuarios al backend pueden contactar SENACE/BCRP.
Ctrl+C termina el lanzador; finally espera al hijo y lo termina si sigue activo.
No se eliminan snapshots. La terminacion forzada no equivale a un cierre graceful.
.EXAMPLE
.\Iniciar-EcoInvestLocal.ps1 -Lkg -CargarSenace
.EXAMPLE
.\Iniciar-EcoInvestLocal.ps1 -Lkg -CargarSenace -InicializarUsuarios
# Solo cuando faltan los usuarios iniciales; requiere autorizar escrituras de usuarios.
.EXAMPLE
.\Iniciar-EcoInvestLocal.ps1 -CargarSenace
# LKG desactivado; se conservan los JSON previos.
.EXAMPLE
.\Iniciar-EcoInvestLocal.ps1 -Lkg
# Sin credencial SENACE: permite leer un respaldo previo ante configuracion ausente.
# BCRP continua siendo un cliente real; no realizar consultas sin autorizacion.
#>
[CmdletBinding()]
param(
    [switch]$Lkg,
    [switch]$CargarSenace,
    [switch]$InicializarUsuarios,
    [string]$SnapshotDir,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$backendRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$repositoryRoot = [IO.Directory]::GetParent($backendRoot).FullName
$senaceValues = $null
$authValues = $null
$javaProcess = $null
$startInfo = $null
$safeFailure = $null

function Fail-Local([string]$Code) {
    # Never propagate exception messages that may include file contents or a credential.
    $failure = [InvalidOperationException]::new($Code)
    $failure.Data['EcoInvest.SafeFailure'] = $true
    throw $failure
}

function Assert-NoReparsePoint([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                Fail-Local 'RUTA_CON_ENLACE_NO_ADMITIDA'
            }
        }
        $parent = [IO.Directory]::GetParent($cursor)
        if ($null -eq $parent) { break }
        $cursor = $parent.FullName
    }
}

function Normalize-Config([string]$Text) {
    return (($Text -replace '^\uFEFF', '') -replace '\r\n', "`n").Trim()
}

function Assert-LocalProfile([string]$Text) {
    $actual = @($Text -split '\r?\n' | ForEach-Object { ($_ -replace '#.*$', '').Trim() } |
        Where-Object { $_.Length -gt 0 }) -join "`n"
    $expected = @(
        'server:', 'address: 127.0.0.1', 'port: 8081', 'spring:', 'datasource:',
        'url: jdbc:sqlite:${ECOINVEST_AUTH_DB_PATH}', 'driver-class-name: org.sqlite.JDBC',
        'jpa:', 'open-in-view: false', 'hibernate:', 'ddl-auto: validate'
    ) -join "`n"
    if ($actual -cne $expected) { Fail-Local 'PERFIL_LOCAL_CAMBIO: requiere revision antes de arrancar' }
}

function Read-ZipText($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) { Fail-Local 'JAR_INCOMPLETO: empaquetar antes de arrancar' }
    $reader = [IO.StreamReader]::new($entry.Open(), [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Assert-ProductionJar([string]$Jar) {
    if (-not [IO.File]::Exists($Jar)) { Fail-Local 'JAR_AUSENTE: ejecutar Maven package offline con autorizacion' }
    Assert-NoReparsePoint $Jar
    $jarDate = [IO.File]::GetLastWriteTimeUtc($Jar)
    $inputs = @(Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src\main') -Recurse -File)
    $inputs += Get-Item -LiteralPath (Join-Path $backendRoot 'pom.xml')
    if (@($inputs | Where-Object { $_.LastWriteTimeUtc -gt $jarDate }).Count -gt 0) {
        Fail-Local 'JAR_DESACTUALIZADO: empaquetar cambios antes de arrancar'
    }
    # Only the reviewed authentication repository/bootstrap may introduce persistence/startup work.
    $reviewedAuthenticationSources = @(
        [IO.Path]::GetFullPath((Join-Path $backendRoot 'src\main\java\com\ecoinvest\soa\repository\UsuarioRepository.java')),
        [IO.Path]::GetFullPath((Join-Path $backendRoot 'src\main\java\com\ecoinvest\soa\config\UsuariosInicialesConfig.java'))
    )
    $sensitivePattern = '\b(DataSource|EntityManagerFactory|EntityManager|JpaRepository|JdbcTemplate|ApplicationRunner|CommandLineRunner|InitializingBean|SmartLifecycle)\b|@(EnableJpaRepositories|PostConstruct|Scheduled|EventListener)\b|initMethod\s*='
    foreach ($source in @($inputs | Where-Object { $_.Extension -eq '.java' })) {
        if ([regex]::IsMatch([IO.File]::ReadAllText($source.FullName), $sensitivePattern)) {
            if ($reviewedAuthenticationSources -notcontains $source.FullName) {
                Fail-Local 'COMPONENTE_DE_ARRANQUE_O_PERSISTENCIA_REQUIERE_REVISION'
            }
        }
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $manifest = Read-ZipText $archive 'META-INF/MANIFEST.MF'
        if ($manifest -notmatch '(?m)^Start-Class: com\.ecoinvest\.soa\.SoaApplication\s*$') {
            Fail-Local 'JAR_NO_ES_LA_APLICACION_PRODUCTIVA'
        }
        foreach ($name in @('application.yml', 'application-local-auth.yml')) {
            $packed = Read-ZipText $archive ('BOOT-INF/classes/' + $name)
            $source = [IO.File]::ReadAllText((Join-Path $backendRoot ('src\main\resources\' + $name)))
            if ((Normalize-Config $packed) -cne (Normalize-Config $source)) {
                Fail-Local 'CONFIGURACION_JAR_DESACTUALIZADA'
            }
            if ($name -eq 'application-local-auth.yml') { Assert-LocalProfile $packed }
        }
        foreach ($type in @('snapshot/SnapshotConfiguration', 'snapshot/JsonFileSnapshotRepository',
                'snapshot/SnapshotStatusService', 'client/SenaceSnapshotCoordinator',
                'client/BcrpSnapshotCoordinator', 'controller/LkgPublicMapper')) {
            if ($null -eq $archive.GetEntry('BOOT-INF/classes/com/ecoinvest/soa/' + $type + '.class')) {
                Fail-Local 'JAR_SIN_COMPONENTES_LKG_ACTUALES'
            }
        }
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '(?i)(LkgLocalHarness|SenaceRealApiTest|BOOT-INF/classes/com/ecoinvest/soa/integration/|BOOT-INF/lib/(mockito-|spring-test-|junit-))') {
                Fail-Local 'ARTEFACTO_DE_LABORATORIO_NO_ADMITIDO'
            }
        }
    } finally { $archive.Dispose() }
}

function Resolve-SnapshotDirectory([string]$Requested) {
    $ignoredRoot = [IO.Path]::GetFullPath((Join-Path $backendRoot 'snapshots'))
    if ([string]::IsNullOrWhiteSpace($Requested)) { $Requested = Join-Path $ignoredRoot 'real-local' }
    if ($Requested -notmatch '^[A-Za-z]:[\\/]' -or $Requested -match '["\x00-\x1f]') {
        Fail-Local 'SNAPSHOT_DIR_DEBE_SER_RUTA_LOCAL_ABSOLUTA'
    }
    $resolved = [IO.Path]::GetFullPath($Requested).TrimEnd('\', '/')
    if ($resolved -eq [IO.Path]::GetPathRoot($resolved).TrimEnd('\')) { Fail-Local 'SNAPSHOT_DIR_NO_DEDICADO' }
    if ($resolved -match '(?i)(ecoinvest-lkg-b4-|ecoinvest-lkg-qa-|ecoinvest-lkg-interactions-|[\\/](target|src|tests|mocks|node_modules|temp|tmp)([\\/]|$))') {
        Fail-Local 'SNAPSHOT_DIR_TEMPORAL_O_DE_LABORATORIO_NO_ADMITIDO'
    }
    Assert-NoReparsePoint $resolved
    $insideRepository = $resolved.StartsWith($repositoryRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $resolved.Equals($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)
    if ($insideRepository) {
        if (-not ($resolved.StartsWith($ignoredRoot + '\', [StringComparison]::OrdinalIgnoreCase))) {
            Fail-Local 'DENTRO_DEL_REPOSITORIO_USAR_UN_SUBDIRECTORIO_DE_BACKEND_SNAPSHOTS'
        }
        $ignoreFile = Join-Path $repositoryRoot '.gitignore'
        if (-not [IO.File]::Exists($ignoreFile) -or
                @([IO.File]::ReadAllLines($ignoreFile) | Where-Object { $_.Trim() -ceq '/backend/snapshots/' }).Count -eq 0) {
            Fail-Local 'EXCLUSION_GIT_DE_SNAPSHOTS_REQUERIDA'
        }
    } else {
        # External directories require explicit operator preparation; never create arbitrary external trees.
        if (-not [IO.Directory]::Exists($resolved)) { Fail-Local 'DIRECTORIO_EXTERNO_DEBE_EXISTIR_PREVIAMENTE' }
        $cursor = $resolved
        while ($cursor) {
            if (Test-Path -LiteralPath (Join-Path $cursor '.git')) { Fail-Local 'DIRECTORIO_EXTERNO_DENTRO_DE_OTRO_REPOSITORIO' }
            $parent = [IO.Directory]::GetParent($cursor)
            if ($null -eq $parent) { break }
            $cursor = $parent.FullName
        }
    }
    return $resolved
}

function Read-SenaceConfiguration {
    $path = Join-Path $backendRoot '.env.apis.local'
    Assert-NoReparsePoint $path
    if (-not [IO.File]::Exists($path)) { Fail-Local 'CONFIG_SENACE_AUSENTE' }
    if ([IO.FileInfo]::new($path).Length -gt 65536) { Fail-Local 'CONFIG_SENACE_TAMANO_INVALIDO' }
    $values = @{}
    foreach ($line in [IO.File]::ReadLines($path, [Text.Encoding]::UTF8)) {
        $entry = $line.Trim()
        if ($entry -notmatch '^(SENACE_BASE_URL|SENACE_AUTH_KEY)\s*=\s*(.*)$') { continue }
        $name = $Matches[1]; $value = $Matches[2].Trim()
        if ($values.ContainsKey($name)) { Fail-Local 'CONFIG_SENACE_DUPLICADA' }
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            if ($value.Length -lt 2 -or $value[$value.Length - 1] -ne $value[0]) {
                Fail-Local 'CONFIG_SENACE_COMILLAS_INVALIDAS'
            }
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ([string]::IsNullOrWhiteSpace($value)) { Fail-Local 'CONFIG_SENACE_INCOMPLETA' }
        $values[$name] = $value
    }
    if (-not $values.ContainsKey('SENACE_BASE_URL') -or -not $values.ContainsKey('SENACE_AUTH_KEY')) {
        Fail-Local 'CONFIG_SENACE_INCOMPLETA'
    }
    if ($values['SENACE_BASE_URL'].TrimEnd('/') -cne 'https://datosabiertos.senace.gob.pe/Api/datastreams') {
        Fail-Local 'CONFIG_SENACE_DESTINO_NO_PERMITIDO'
    }
    if ($values['SENACE_AUTH_KEY'].Length -gt 8192 -or $values['SENACE_AUTH_KEY'] -match '\s|[\x00-\x1f\x7f]') {
        Fail-Local 'CONFIG_SENACE_CLAVE_INVALIDA'
    }
    return $values
}

function Read-InitialPasswords {
    $values = @{}
    foreach ($account in @(
        @{ Name = 'admin'; Variable = 'ECOINVEST_ADMIN_PASSWORD' },
        @{ Name = 'analista'; Variable = 'ECOINVEST_ANALISTA_PASSWORD' }
    )) {
        $secure = $null; $confirmation = $null; $text = $null; $confirmed = $null
        $pointer = [IntPtr]::Zero; $confirmationPointer = [IntPtr]::Zero
        try {
            $secure = Read-Host -Prompt ('Contrasena inicial de ' + $account.Name) -AsSecureString
            $confirmation = Read-Host -Prompt ('Confirmar contrasena de ' + $account.Name) -AsSecureString
            $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
            $confirmationPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmation)
            $text = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
            $confirmed = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmationPointer)
            if ([string]::IsNullOrWhiteSpace($text)) { Fail-Local 'CONTRASENA_INICIAL_VACIA' }
            if (-not $text.Equals($confirmed, [StringComparison]::Ordinal)) { Fail-Local 'CONTRASENAS_NO_COINCIDEN' }
            if ([Text.Encoding]::UTF8.GetByteCount($text) -gt 72) { Fail-Local 'CONTRASENA_SUPERA_LIMITE_BCRYPT_72_BYTES' }
            $values[$account.Variable] = $text
        } finally {
            if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
            if ($confirmationPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmationPointer) }
            if ($null -ne $secure) { $secure.Dispose() }
            if ($null -ne $confirmation) { $confirmation.Dispose() }
            $text = $null; $confirmed = $null
        }
    }
    return $values
}

function New-JavaStartInfo([string[]]$Arguments) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $JavaHome 'bin\java.exe'
    $info.WorkingDirectory = $backendRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $quoted = foreach ($argument in $Arguments) {
        if ($argument -match '["\x00-\x1f]') { Fail-Local 'ARGUMENTO_NO_ADMITIDO' }
        # No shell: quote Windows argv, doubling trailing backslashes before the closing quote.
        '"' + [regex]::Replace($argument, '(\\+)$', '$1$1') + '"'
    }
    $info.Arguments = $quoted -join ' '
    $info.EnvironmentVariables.Clear()
    foreach ($name in @('SystemRoot', 'windir', 'ComSpec', 'TEMP', 'TMP', 'USERPROFILE', 'LOCALAPPDATA', 'APPDATA',
            'ProgramData', 'SystemDrive', 'OS', 'NUMBER_OF_PROCESSORS', 'PROCESSOR_ARCHITECTURE', 'USERNAME')) {
        $value = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
        if ($null -ne $value) { $info.EnvironmentVariables[$name] = $value }
    }
    $info.EnvironmentVariables['JAVA_HOME'] = $JavaHome
    $info.EnvironmentVariables['PATH'] = (Join-Path $JavaHome 'bin') + ';' + (Join-Path $env:SystemRoot 'System32')
    return $info
}

function Write-SafeJavaLine([string]$Line) {
    if ($null -ne $senaceValues) {
        $secret = $senaceValues['SENACE_AUTH_KEY']
        $Line = $Line.Replace($secret, '[CREDENCIAL OMITIDA]').Replace([Uri]::EscapeDataString($secret), '[CREDENCIAL OMITIDA]')
    }
    if ($null -ne $authValues) {
        foreach ($secret in $authValues.Values) {
            $Line = $Line.Replace($secret, '[CONTRASENA OMITIDA]').Replace([Uri]::EscapeDataString($secret), '[CONTRASENA OMITIDA]')
        }
    }
    $Line = [regex]::Replace($Line, '(?i)https?://\S+', '[URL OMITIDA]')
    $Line = [regex]::Replace($Line, 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[JWT OMITIDO]')
    $Line = [regex]::Replace($Line, '(?i)(auth_key|authorization|password|secret|token)\s*[=:]\s*\S+', '[DATO OMITIDO]')
    $Line = [regex]::Replace($Line, '[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '')
    Write-Host $Line.Substring(0, [Math]::Min(4096, $Line.Length))
}

try {
    Assert-NoReparsePoint $backendRoot
    $JavaHome = [IO.Path]::GetFullPath($JavaHome)
    Assert-NoReparsePoint $JavaHome
    if (-not [IO.File]::Exists((Join-Path $JavaHome 'bin\java.exe'))) { Fail-Local 'JDK_17_0_12_NO_ENCONTRADO' }
    # Reject conflicting launch overrides by NAME only. Values are neither displayed nor used.
    foreach ($name in [Environment]::GetEnvironmentVariables([EnvironmentVariableTarget]::Process).Keys) {
        if ($name -match '^(?i:SPRING_|EXTERNAL_|SERVER_|LOGGING_|MANAGEMENT_|LOADER_|ECOINVEST_)' -or
                $name -match '^(?i:JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|MAVEN_OPTS)$') {
            Fail-Local 'OVERRIDES_DE_ENTORNO_NO_ADMITIDOS: usar una consola sin configuracion adicional'
        }
    }
    $versionProcess = [Diagnostics.Process]::new()
    try {
        $versionProcess.StartInfo = New-JavaStartInfo -Arguments @('-version')
        if (-not $versionProcess.Start()) { Fail-Local 'NO_SE_PUDO_VERIFICAR_JAVA' }
        $versionOut = $versionProcess.StandardOutput.ReadToEndAsync()
        $versionErr = $versionProcess.StandardError.ReadToEndAsync()
        if (-not $versionProcess.WaitForExit(5000)) {
            $versionProcess.Kill(); $versionProcess.WaitForExit()
            Fail-Local 'VERIFICACION_JAVA_TIMEOUT'
        }
        $versionText = $versionOut.GetAwaiter().GetResult() + $versionErr.GetAwaiter().GetResult()
        if ($versionProcess.ExitCode -ne 0 -or $versionText -notmatch 'java version "17\.0\.12"' -or
                $versionText -notmatch 'Java\(TM\)') { Fail-Local 'SE_REQUIERE_ORACLE_JDK_17_0_12' }
    } finally { $versionProcess.Dispose() }

    $jarPath = Join-Path $backendRoot 'target\soa-0.0.1-SNAPSHOT.jar'
    Assert-ProductionJar $jarPath
    $snapshotPath = Resolve-SnapshotDirectory $SnapshotDir
    $authDatabasePath = Join-Path $backendRoot 'ecoinvest_fallback.db'
    Assert-NoReparsePoint $authDatabasePath
    if (-not [IO.File]::Exists($authDatabasePath)) {
        Fail-Local 'BASE_USUARIOS_AUSENTE_EN_BACKEND: no se crea ni selecciona otra base automaticamente'
    }
    if (@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
            Where-Object { $_.Port -eq 8081 }).Count -gt 0) { Fail-Local 'PUERTO_8081_OCUPADO: no se detienen procesos ajenos' }

    if ($CargarSenace) { $senaceValues = Read-SenaceConfiguration }
    if ($InicializarUsuarios) { $authValues = Read-InitialPasswords }
    if ($Lkg) {
        [IO.Directory]::CreateDirectory($snapshotPath) | Out-Null
        Assert-NoReparsePoint $snapshotPath
        # Only this new disposable probe is removed; never delete snapshots or an owner lock.
        $probePath = Join-Path $snapshotPath ('.write-probe-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        $probe = [IO.FileStream]::new($probePath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None, 4096, [IO.FileOptions]::DeleteOnClose)
        try { $probe.WriteByte(0); $probe.Flush($true) } finally { $probe.Dispose() }
    }
    $javaArguments = @(
        '-jar', $jarPath,
        '--spring.profiles.active=local-auth', '--spring.profiles.include=',
        '--spring.config.location=classpath:/application.yml', '--spring.config.additional-location=', '--spring.config.import=',
        '--server.address=127.0.0.1', '--server.port=8081', '--spring.autoconfigure.exclude=',
        '--spring.jpa.hibernate.ddl-auto=validate', '--spring.jpa.open-in-view=false', ('--ecoinvest.snapshots.enabled=' + $Lkg.IsPresent.ToString().ToLowerInvariant()),
        '--ecoinvest.snapshots.senace-stale-after=PT24H', '--ecoinvest.snapshots.senace-max-age=P90D',
        '--ecoinvest.snapshots.bcrp-stale-after=P7D', '--ecoinvest.snapshots.bcrp-max-age=P365D',
        '--external.apis.bcrp-base-url=https://estadisticas.bcrp.gob.pe/estadisticas/series/api',
        '--logging.level.org.apache.hc.client5.http=OFF', '--logging.level.org.apache.hc.core5.http=OFF'
    )
    $startInfo = New-JavaStartInfo $javaArguments
    $startInfo.EnvironmentVariables['ECOINVEST_SNAPSHOT_DIR'] = $snapshotPath
    $startInfo.EnvironmentVariables['ECOINVEST_AUTH_DB_PATH'] = $authDatabasePath.Replace('\', '/')
    if ($InicializarUsuarios) {
        foreach ($name in @('ECOINVEST_ADMIN_PASSWORD', 'ECOINVEST_ANALISTA_PASSWORD')) {
            $startInfo.EnvironmentVariables[$name] = $authValues[$name]
        }
    }
    if ($CargarSenace) {
        $startInfo.EnvironmentVariables['SENACE_BASE_URL'] = $senaceValues['SENACE_BASE_URL']
        $startInfo.EnvironmentVariables['SENACE_AUTH_KEY'] = $senaceValues['SENACE_AUTH_KEY']
    }
    Write-Host 'EcoInvest real local: Oracle JDK 17.0.12; perfil unico local-auth; bind 127.0.0.1:8081.'
    Write-Host 'Autenticacion: SQLite backend/ecoinvest_fallback.db; esquema validate, sin actualizacion automatica.'
    Write-Host ('Usuarios iniciales: ' + $(if ($InicializarUsuarios) { 'claves suministradas solo al hijo Java; no se cambian usuarios existentes.' } else { 'sin claves iniciales; si falta un usuario, reiniciar con -InicializarUsuarios.' }))
    Write-Host ('LKG: ' + $(if ($Lkg) { 'HABILITADO; SENACE PT24H/P90D, BCRP P7D/P365D.' } else { 'DESACTIVADO; JSON existentes conservados.' }))
    if ($Lkg) { Write-Host ('Directorio JSON: ' + $snapshotPath + '; publicacion atomica pendiente de una escritura SAVED.') }
    Write-Host ('Configuracion SENACE: ' + $(if ($CargarSenace) { 'suministrada exclusivamente al hijo Java.' } else { 'no cargada; no se hereda del entorno.' }))
    Write-Host 'Sin consultas de precarga. React y consultas manuales se autorizan por separado. Ctrl+C para detener.'

    $javaProcess = [Diagnostics.Process]::new()
    $javaProcess.StartInfo = $startInfo
    if (-not $javaProcess.Start()) { Fail-Local 'NO_SE_PUDO_INICIAR_JAVA' }
    $stdoutTask = $javaProcess.StandardOutput.ReadLineAsync()
    $stderrTask = $javaProcess.StandardError.ReadLineAsync()
    while ($null -ne $stdoutTask -or $null -ne $stderrTask -or -not $javaProcess.HasExited) {
        if ($null -ne $stdoutTask -and $stdoutTask.IsCompleted) {
            $line = $stdoutTask.GetAwaiter().GetResult()
            if ($null -eq $line) { $stdoutTask = $null }
            else { Write-SafeJavaLine $line; $stdoutTask = $javaProcess.StandardOutput.ReadLineAsync() }
        }
        if ($null -ne $stderrTask -and $stderrTask.IsCompleted) {
            $line = $stderrTask.GetAwaiter().GetResult()
            if ($null -eq $line) { $stderrTask = $null }
            else { Write-SafeJavaLine $line; $stderrTask = $javaProcess.StandardError.ReadLineAsync() }
        }
        [Threading.Thread]::Sleep(30)
    }
    if ($javaProcess.ExitCode -ne 0) { Fail-Local 'BACKEND_TERMINO_CON_ERROR: revisar salida sanitizada' }
} catch {
    if ($_.Exception.Data.Contains('EcoInvest.SafeFailure')) { $safeFailure = $_.Exception.Message }
    else { $safeFailure = 'ARRANQUE_LOCAL_NO_COMPLETADO: revisar requisitos y permisos; no se muestra la excepcion original' }
} finally {
    if ($null -ne $javaProcess) {
        try {
            if (-not $javaProcess.HasExited) {
                if (-not $javaProcess.WaitForExit(5000)) {
                    Write-Host 'Terminando exclusivamente el hijo Java del lanzador; no es un cierre graceful.'
                    $javaProcess.Kill()
                    $javaProcess.WaitForExit(5000) | Out-Null
                }
            }
        } catch {
            Write-Host 'NO_SE_PUDO_CONFIRMAR_CIERRE_DEL_HIJO_JAVA: verificar el proceso propio antes de reintentar.'
            $safeFailure = 'CIERRE_JAVA_REQUIERE_VERIFICACION'
        } finally { $javaProcess.Dispose() }
    }
    if ($null -ne $startInfo) { $startInfo.EnvironmentVariables.Clear() }
    if ($null -ne $senaceValues) { $senaceValues.Clear(); $senaceValues = $null }
    if ($null -ne $authValues) { $authValues.Clear(); $authValues = $null }
    # No global environment edits, snapshots deletion, imports or persistent credential copies.
}
if ($null -ne $safeFailure) { throw [InvalidOperationException]::new($safeFailure) }
