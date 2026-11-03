# Diagnostico temporal SENACE: sin -Consultar no lee configuracion ni realiza HTTP.
# Cada ejecucion requiere autorizacion independiente del recurso y volumen.
[CmdletBinding()]
param(
    [ValidateSet('CarteraProyectos', 'ConsultorasAmbientales')]
    [string]$Recurso = 'ConsultorasAmbientales',
    [switch]$Consultar
)
& {
    param([string]$Resource, [bool]$RunHttp, [string]$ProjectRoot)
    $ErrorActionPreference = 'Stop'
    if (-not $RunHttp) {
        'Modo seguro: no se leyo configuracion ni se realizo HTTP.'
        'Consulta previamente autorizada: -Recurso <recurso aprobado> -Consultar'
        return
    }
    $key=$null; $config=$null; $uri=$null; $body=$null
    $handler=$null; $client=$null; $response=$null; $stream=$null; $memory=$null; $cancel=$null

    function Protect-Text([string]$Text) {
        if ($null -eq $Text) { return '' }
        $safe=$Text
        if (-not [string]::IsNullOrEmpty($key)) {
            $safe=$safe.Replace($key,'[CREDENCIAL OMITIDA]').Replace([Uri]::EscapeDataString($key),'[CREDENCIAL OMITIDA]')
        }
        $safe=[regex]::Replace($safe,'(?i)https?://\S+','[URL OMITIDA]')
        $safe=[regex]::Replace($safe,'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT OMITIDO]')
        $safe=[regex]::Replace($safe,'(?i)(auth_key|token|password|secret|authorization)\s*[=:]\s*\S+','[DATO OMITIDO]')
        $safe=[regex]::Replace($safe,'[\w.+-]+@[\w.-]+','[EMAIL OMITIDO]')
        $safe=[regex]::Replace($safe,'[A-Za-z0-9_-]{50,}|\b\d{8,}\b','[VALOR OMITIDO]')
        $safe=[regex]::Replace($safe,'[\x00-\x1f\x7f]',' ')
        return $safe.Substring(0,[Math]::Min(160,$safe.Length))
    }

    function Read-LocalConfig([string]$Path) {
        if (-not [IO.File]::Exists($Path)) { throw 'CONFIG_AUSENTE' }
        if ([IO.FileInfo]::new($Path).Length -gt 65536) { throw 'CONFIG_TAMANO_NO_ADMITIDO' }
        $values=@{}
        # Solo KEY=VALUE como texto: no dot-sourcing, Invoke-Expression o expansion.
        foreach ($line in [IO.File]::ReadLines($Path,[Text.Encoding]::UTF8)) {
            $entry=$line.Trim()
            if ($entry.Length -eq 0 -or $entry.StartsWith('#')) { continue }
            if ($entry -notmatch '^(SENACE_BASE_URL|SENACE_AUTH_KEY)\s*=\s*(.*)$') { continue }
            $name=$Matches[1]; $value=$Matches[2].Trim()
            if ($values.ContainsKey($name)) { throw 'CONFIG_DUPLICADA' }
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                if ($value.Length -lt 2 -or $value[$value.Length-1] -ne $value[0]) { throw 'CONFIG_COMILLAS_INVALIDAS' }
                $value=$value.Substring(1,$value.Length-2)
            }
            if ([string]::IsNullOrWhiteSpace($value)) { throw 'CONFIG_INCOMPLETA' }
            $values[$name]=$value
        }
        if (-not $values.ContainsKey('SENACE_BASE_URL') -or -not $values.ContainsKey('SENACE_AUTH_KEY')) { throw 'CONFIG_INCOMPLETA' }
        if ($values['SENACE_BASE_URL'].TrimEnd('/') -cne 'https://datosabiertos.senace.gob.pe/Api/datastreams') { throw 'CONFIG_DESTINO_NO_PERMITIDO' }
        if ($values['SENACE_AUTH_KEY'] -match '\s|[\x00-\x1f\x7f]') { throw 'CONFIG_CLAVE_INVALIDA' }
        return $values
    }

    function Get-ValueType($Value) {
        if ($null -eq $Value) { return 'null' }
        if ($Value -is [string]) { return 'string' }
        if ($Value -is [bool]) { return 'boolean' }
        if ($Value -is [System.Array]) { return 'array' }
        if ($Value -is [System.Management.Automation.PSCustomObject]) { return 'object' }
        if ($Value -is [DateTime]) { return 'datetime-convertido-por-parser' }
        if ($Value -is [byte] -or $Value -is [int16] -or $Value -is [int32] -or $Value -is [int64]) { return 'integer' }
        if ($Value -is [decimal]) { return 'decimal' }
        if ($Value -is [double] -or $Value -is [single]) { return 'floating-point' }
        return 'other'
    }

    function Get-Property($Row,[string]$Name) { return $Row.PSObject.Properties[$Name] }

    function Get-IdentityProfile([object[]]$Rows,[string]$Field) {
        $counts=[Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
        $missing=0; $empty=0; $notScalar=0
        foreach ($row in $Rows) {
            $p=Get-Property $row $Field
            if ($null -eq $p) { $missing++; continue }
            if ($null -eq $p.Value -or [string]::IsNullOrWhiteSpace([string]$p.Value)) { $empty++; continue }
            if ((Get-ValueType $p.Value) -in @('object','array')) { $notScalar++; continue }
            $identity=(Get-ValueType $p.Value)+':'+[string]$p.Value
            if (-not $counts.ContainsKey($identity)) { $counts[$identity]=0 }
            $counts[$identity]++
        }
        $groups=0; $excess=0
        foreach ($n in $counts.Values) { if ($n -gt 1) { $groups++; $excess+=$n-1 } }
        return [pscustomobject]@{
            campo=(Protect-Text $Field); faltantes=$missing; nulosOVacios=$empty; noEscalares=$notScalar
            valoresDistintos=$counts.Count; gruposRepetidos=$groups; repeticionesAdicionales=$excess
            criterio='igualdad exacta de tipo y representacion; no demuestra unicidad de negocio'
        }
    }

    function Get-Distribution([object[]]$Rows,[string]$Field) {
        $counts=[Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
        foreach ($row in $Rows) {
            $p=Get-Property $row $Field
            $label=if ($null -eq $p) { '[FALTANTE]' }
                   elseif ($null -eq $p.Value) { '[NULL]' }
                   elseif ($p.Value -is [string] -and [string]::IsNullOrWhiteSpace($p.Value)) { '[VACIO]' }
                   elseif ((Get-ValueType $p.Value) -in @('object','array')) { '[NO ESCALAR]' }
                   else { Protect-Text ([string]$p.Value) }
            if (-not $counts.ContainsKey($label)) { $counts[$label]=0 }
            $counts[$label]++
        }
        $ordered=@($counts.GetEnumerator() | Sort-Object -Property @{Expression='Value';Descending=$true},Key)
        $top=@($ordered | Select-Object -First 25 | ForEach-Object { [pscustomobject]@{ categoriaSaneada=$_.Key; registros=$_.Value } })
        $omitted=0
        foreach ($pair in ($ordered | Select-Object -Skip 25)) { $omitted+=$pair.Value }
        return [pscustomobject]@{ campo=$Field; categoriasSaneadas=$counts.Count; primeras25=$top; registrosEnCategoriasOmitidas=$omitted }
    }

    function Get-DateShape($Value) {
        if ($null -eq $Value) { return 'NULL' }
        if ($Value -is [DateTime]) { return 'DATETIME_CONVERTIDO_POR_PARSER' }
        if ($Value -isnot [string]) { return 'NO_STRING' }
        $text=$Value.Trim()
        if ($text.Length -eq 0) { return 'VACIO' }
        $formats=if ($text -match '^\d{2}/\d{2}/\d{4}$') { @('dd/MM/yyyy','MM/dd/yyyy') }
                 elseif ($text -match '^\d{4}-\d{2}-\d{2}$') { @('yyyy-MM-dd') }
                 elseif ($text -match '^\d{4}-\d{2}-\d{2}T') { @('yyyy-MM-ddTHH:mm:ss','yyyy-MM-ddTHH:mm:ss.FFFFFFFK','yyyy-MM-ddTHH:mm:ssK') }
                 else { @() }
        $matched=[Collections.Generic.List[string]]::new()
        foreach ($format in $formats) {
            $date=[DateTime]::MinValue
            if ([DateTime]::TryParseExact($text,$format,[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::None,[ref]$date)) { $matched.Add($format) | Out-Null }
        }
        if ($matched.Count -gt 1) { return 'AMBIGUA_DIA_MES' }
        if ($matched.Count -eq 1) { return $matched[0] }
        if ($text -match '^/Date\(-?\d+(?:[+-]\d{4})?\)/$') { return 'PATRON_DOTNET_DATE_NO_CONVERTIDO' }
        if ($formats.Count -gt 0) { return 'FECHA_NO_VALIDA_EN_FORMATOS_COMPROBADOS' }
        return 'FORMATO_NO_RECONOCIDO'
    }

    function Get-Coordinate($Property,[double]$Limit) {
        if ($null -eq $Property) { return [pscustomobject]@{ estado='FALTANTE'; valor=$null } }
        if ($null -eq $Property.Value) { return [pscustomobject]@{ estado='NULL'; valor=$null } }
        $value=$Property.Value; $number=0.0
        $valid=(Get-ValueType $value) -in @('integer','decimal','floating-point')
        if ($valid) { $number=[double]$value }
        elseif ($value -is [string]) {
            $valid=[double]::TryParse($value,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$number)
        }
        if (-not $valid -or [double]::IsNaN($number) -or [double]::IsInfinity($number)) { return [pscustomobject]@{ estado='NO_NUMERICA_FINITA'; valor=$null } }
        $state=if ([Math]::Abs($number) -gt $Limit) { 'FUERA_RANGO_GRADOS' } else { 'DENTRO_RANGO_GRADOS' }
        return [pscustomobject]@{ estado=$state; valor=$number }
    }

    function Get-RecordProfile([object[]]$Rows,[string]$SelectedResource) {
        $objects=@($Rows | Where-Object { $_ -is [System.Management.Automation.PSCustomObject] })
        $fields=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach ($row in $objects) {
            foreach ($p in $row.PSObject.Properties) {
                if ($p.Name -match '(?i)auth|token|password|secret|cookie') { continue }
                if (-not $fields.ContainsKey($p.Name)) { $fields[$p.Name]=@{ presentes=0; nulos=0; vacios=0; tipos=@{} } }
                $stat=$fields[$p.Name]; $stat.presentes++
                if ($null -eq $p.Value) { $stat.nulos++ }
                if ($p.Value -is [string] -and [string]::IsNullOrWhiteSpace($p.Value)) { $stat.vacios++ }
                $type=Get-ValueType $p.Value
                if (-not $stat.tipos.ContainsKey($type)) { $stat.tipos[$type]=0 }
                $stat.tipos[$type]++
            }
        }
        $fieldRows=@($fields.Keys | Sort-Object | Select-Object -First 100 | ForEach-Object {
            $name=$_; $s=$fields[$name]; $denominator=$objects.Count
            [pscustomobject]@{
                campo=(Protect-Text $name); presentes=$s.presentes; faltantes=($denominator-$s.presentes)
                nulos=$s.nulos; stringsVacios=$s.vacios; tiposObservados=$s.tipos
                porcentajeNulosSobreObjetos=$(if ($denominator) { [Math]::Round(100.0*$s.nulos/$denominator,4) } else { 0 })
                semantica='PENDIENTE; nombre y tipo no prueban equivalencia de negocio'
            }
        })
        $profile=[ordered]@{
            elementosRecibidos=$Rows.Count; registrosObjeto=$objects.Count; elementosNoObjeto=($Rows.Count-$objects.Count)
            camposDistintosNoSensibles=$fields.Count; camposMostrados=$fieldRows; camposOmitidosPorLimite=[Math]::Max(0,$fields.Count-100)
            denominadorPorcentajes='registros objeto; faltante y null se cuentan por separado'; coberturaDataset='NO_VERIFICADA'
        }
        $candidates=@($fields.Keys | Where-Object { $_ -match '^(?i:ID|RUC|REGISTRO|NUMERO_REGISTRO|NRO_REGISTRO|CODIGO)$' })
        $profile.identificadoresCandidatos=@($candidates | ForEach-Object { Get-IdentityProfile $objects $_ })
        if ($SelectedResource -eq 'ConsultorasAmbientales') {
            $profile.camposCandidatosConsultoras=@($fields.Keys | Where-Object { $_ -match '(?i)RAZON|SOCIAL|RUC|REGISTR|ESPECIAL|SECTOR|ACTIVIDAD|NIVEL|CLASIF|ESTADO|VIGEN|FECHA' } | Sort-Object | ForEach-Object { Protect-Text $_ })
            $profile.notaConsultoras='Candidatos por nombre; sin valores personales. Repetir RUC puede corresponder a distintas especialidades.'
        } else {
            $profile.distribuciones=@('ACTIVIDAD','TIPO','ESTADO' | ForEach-Object { Get-Distribution $objects $_ })
            $dateShapes=@{}; $resolutionShapes=@{}
            $latSummary=@{ estados=@{}; minimo=$null; maximo=$null; ceros=0 }
            $lonSummary=@{ estados=@{}; minimo=$null; maximo=$null; ceros=0 }
            $pairs=@{ paresNumericos=0; paresDentroRangoGrados=0; paresCeroCero=0; soloUnaNumerica=0; ningunaNumerica=0 }
            foreach ($row in $objects) {
                $dateProperty=Get-Property $row 'FECHA_INICIO'
                $shape=if ($null -eq $dateProperty) { 'FALTANTE' } else { Get-DateShape $dateProperty.Value }
                if (-not $dateShapes.ContainsKey($shape)) { $dateShapes[$shape]=0 }; $dateShapes[$shape]++
                $resolution=Get-Property $row 'RESOLUCION'
                $rshape=if ($null -eq $resolution) { 'FALTANTE' }
                        elseif ($null -eq $resolution.Value) { 'NULL' }
                        elseif ($resolution.Value -isnot [string]) { Get-ValueType $resolution.Value }
                        elseif ([string]::IsNullOrWhiteSpace($resolution.Value)) { 'STRING_VACIO' }
                        elseif ($resolution.Value -match '^https?://') { 'STRING_CON_FORMA_URL_NO_PUBLICADA' }
                        elseif ($resolution.Value -match '^\d+$') { 'STRING_SOLO_DIGITOS' }
                        else { 'STRING_TEXTO_NO_PUBLICADO' }
                if (-not $resolutionShapes.ContainsKey($rshape)) { $resolutionShapes[$rshape]=0 }; $resolutionShapes[$rshape]++
                $lat=Get-Coordinate (Get-Property $row 'LATITUD') 90
                $lon=Get-Coordinate (Get-Property $row 'LONGITUD') 180
                foreach ($item in @(@{coordinate=$lat;summary=$latSummary},@{coordinate=$lon;summary=$lonSummary})) {
                    $c=$item.coordinate; $s=$item.summary
                    if (-not $s.estados.ContainsKey($c.estado)) { $s.estados[$c.estado]=0 }; $s.estados[$c.estado]++
                    if ($null -ne $c.valor) {
                        if ($null -eq $s.minimo -or $c.valor -lt $s.minimo) { $s.minimo=$c.valor }
                        if ($null -eq $s.maximo -or $c.valor -gt $s.maximo) { $s.maximo=$c.valor }
                        if ($c.valor -eq 0) { $s.ceros++ }
                    }
                }
                if ($null -ne $lat.valor -and $null -ne $lon.valor) {
                    $pairs.paresNumericos++
                    if ($lat.estado -eq 'DENTRO_RANGO_GRADOS' -and $lon.estado -eq 'DENTRO_RANGO_GRADOS') { $pairs.paresDentroRangoGrados++ }
                    if ($lat.valor -eq 0 -and $lon.valor -eq 0) { $pairs.paresCeroCero++ }
                } elseif ($null -ne $lat.valor -or $null -ne $lon.valor) { $pairs.soloUnaNumerica++ }
                else { $pairs.ningunaNumerica++ }
            }
            $profile.fechaInicio=$dateShapes; $profile.resolucion=$resolutionShapes
            $profile.coordenadas=@{ latitud=$latSummary; longitud=$lonSummary; pares=$pairs; nota='Solo rangos globales si fueran grados; no valida CRS ni ubicacion Peru. Fecha de inicio no equivale a aprobacion.' }
        }
        return [pscustomobject]$profile
    }

    $result=[ordered]@{ recurso=$Resource; http=$null; clasificacion='NO_DISPONIBLE'; solicitudesMaximas=1; limiteBytes=6291456; timeoutSegundos=25 }
    try {
        try {
            $config=Read-LocalConfig (Join-Path $ProjectRoot 'backend/.env.apis.local')
            $key=$config['SENACE_AUTH_KEY']
        } catch {
            $result.clasificacion='CONFIGURACION_RECHAZADA'
            $result.motivo='Archivo ausente, incompleto, duplicado, formato no admitido o destino no autorizado. No se realizo HTTP.'
            return
        }
        Add-Type -AssemblyName System.Net.Http
        $handler=[System.Net.Http.HttpClientHandler]::new()
        $handler.AllowAutoRedirect=$false; $handler.UseCookies=$false
        $client=[System.Net.Http.HttpClient]::new($handler)
        $client.Timeout=[TimeSpan]::FromSeconds(25)
        $cancel=[Threading.CancellationTokenSource]::new(); $cancel.CancelAfter(25000)
        # Destino fijo validado; nunca imprimir la URI autenticada.
        $uri=[Uri]::new('https://datosabiertos.senace.gob.pe/Api/datastreams/'+$Resource+'?auth_key='+[Uri]::EscapeDataString($key))
        $response=$client.GetAsync($uri,[System.Net.Http.HttpCompletionOption]::ResponseHeadersRead,$cancel.Token).GetAwaiter().GetResult()
        $status=[int]$response.StatusCode; $result.http=$status
        $contentType=$response.Content.Headers.ContentType
        if ($null -ne $contentType) { $result.contentType=Protect-Text $contentType.MediaType }
        $result.contentLengthDeclarado=$response.Content.Headers.ContentLength
        if ($status -ge 300 -and $status -lt 400) { $result.clasificacion='REDIRECCION_NO_SEGUIDA'; return }
        $stream=$response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $memory=[IO.MemoryStream]::new(); $buffer=New-Object byte[] 8192; $ended=$false
        while ($memory.Length -lt $result.limiteBytes) {
            $size=[Math]::Min($buffer.Length,[int]($result.limiteBytes-$memory.Length))
            $n=$stream.ReadAsync($buffer,0,$size,$cancel.Token).GetAwaiter().GetResult()
            if ($n -eq 0) { $ended=$true; break }
            $memory.Write($buffer,0,$n)
        }
        $result.bytesLeidos=$memory.Length; $result.cuerpoCompleto=$ended
        if (-not $ended) { $result.clasificacion='PARCIAL_NO_INTERPRETADO'; return }
        if ($memory.Length -eq 0) { $result.clasificacion='CUERPO_VACIO'; return }
        $encoding=[Text.Encoding]::UTF8
        if ($null -ne $contentType -and -not [string]::IsNullOrWhiteSpace($contentType.CharSet)) { $encoding=[Text.Encoding]::GetEncoding($contentType.CharSet.Trim('"')) }
        $body=$encoding.GetString($memory.ToArray()).TrimStart([char]0xFEFF).TrimStart()
        try {
            # Envoltorio para preservar arrays vacios y de un elemento en PowerShell 5.1.
            $wrapper=ConvertFrom-Json -InputObject ('{"payload":'+$body+'}') -ErrorAction Stop
            $json=$wrapper.payload
        } catch {
            $result.clasificacion=if ($body.StartsWith('<')) { 'HTML_O_XML_NO_JSON' } else { 'JSON_INVALIDO_U_OTRO_FORMATO' }
            if ($status -eq 401 -or $status -eq 403) { $result.acceso='RECHAZADO_HTTP' }
            return
        }
        $result.jsonValido=$true; $result.tipoRaiz=Get-ValueType $json
        if (-not $response.IsSuccessStatusCode) {
            $result.clasificacion=if ($status -eq 401 -or $status -eq 403) { 'ACCESO_RECHAZADO_HTTP' } else { 'ERROR_HTTP_CON_JSON' }
            if ($json -is [System.Management.Automation.PSCustomObject]) {
                $result.camposError=@($json.PSObject.Properties | Select-Object -First 25 | ForEach-Object {
                    if ($_.Name -notmatch '(?i)auth|token|password|secret|cookie') { [pscustomobject]@{ campo=(Protect-Text $_.Name); tipo=(Get-ValueType $_.Value) } }
                })
            }
            return
        }
        if ($body.StartsWith('[') -and $json -is [System.Array]) {
            $result.clasificacion=if ($json.Count -eq 0) { 'ARRAY_VACIO' } else { 'JSON_ARRAY_PERFILADO_NO_CERTIFICA_SEMANTICA' }
            $result.perfil=Get-RecordProfile -Rows $json -SelectedResource $Resource
        } else {
            $result.clasificacion='JSON_CON_ESTRUCTURA_NO_ACORDADA'
            if ($json -is [System.Management.Automation.PSCustomObject]) {
                $result.camposRaiz=@($json.PSObject.Properties | Select-Object -First 30 | ForEach-Object {
                    if ($_.Name -notmatch '(?i)auth|token|password|secret|cookie') { [pscustomobject]@{ campo=(Protect-Text $_.Name); tipo=(Get-ValueType $_.Value) } }
                })
            }
        }
        $result.nota='HTTP 200 no prueba que auth_key sea obligatoria; cuerpo completo no prueba cobertura total. No se almacenaron filas.'
    } catch {
        $result.clasificacion='ERROR_LOCAL_TRANSPORTE_LECTURA_O_ANALISIS'
        $result.tipoExcepcion=$_.Exception.GetType().FullName
        # No imprimir Message, ErrorRecord, stack trace, URI o causas con cuerpo.
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $memory) { $memory.Dispose() }
        if ($null -ne $cancel) { $cancel.Dispose() }
        if ($null -ne $client) { $client.Dispose() }
        if ($null -ne $handler) { $handler.Dispose() }
        $config=$null; $key=$null; $uri=$null; $body=$null; $json=$null; $wrapper=$null
        ConvertTo-Json -InputObject $result -Depth 15
    }
} $Recurso $Consultar.IsPresent $PSScriptRoot
