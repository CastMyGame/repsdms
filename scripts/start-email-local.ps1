[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
foreach ($name in @('REPS_LOCAL_MONGODB_URI','REPS_LOCAL_DATABASE_CONFIRM',
    'REPS_LOCAL_SMTP_USERNAME','REPS_LOCAL_SMTP_PASSWORD','REPS_LOCAL_GOOGLE_CLIENT_ID',
    'REPS_LOCAL_GOOGLE_CLIENT_SECRET','REPS_LOCAL_GMAIL_ENCRYPTION_KEY')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Set $name explicitly. No development/production credential fallback is used."
    }
}
$match = [regex]::Match($env:REPS_LOCAL_MONGODB_URI,
    '^mongodb(?:\+srv)?://(?:[^/@]+@)?(?<hosts>[^/?]+)/(?<database>[^?/#]+)(?:\?[^#]*)?$')
if (-not $match.Success) { throw 'Use an explicit MongoDB URI with a database name; credential characters must be URI-encoded.' }
$database = $match.Groups['database'].Value
$target = $match.Groups['hosts'].Value + '/' + $database
if ($database -in @('admin','local','config') -or $env:REPS_LOCAL_DATABASE_CONFIRM -cne $target) {
    throw 'Set REPS_LOCAL_DATABASE_CONFIRM to exactly hosts/database from your approved NONPRODUCTION URI. Shared dev/prod data is prohibited.'
}
try { $key = [Convert]::FromBase64String($env:REPS_LOCAL_GMAIL_ENCRYPTION_KEY) }
catch { throw 'REPS_LOCAL_GMAIL_ENCRYPTION_KEY must be base64 encoding of 32 random bytes.' }
if ($key.Length -ne 32) { throw 'The Gmail encryption key must contain exactly 32 bytes.' }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Set JAVA_HOME to your JDK 17 installation.' }
$javaVersion = & (Join-Path $env:JAVA_HOME 'bin/java.exe') -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "17\.') { throw 'JDK 17 is required.' }
$maven = Get-Command mvn.cmd -CommandType Application -ErrorAction Stop
# Prevent inherited configuration from reintroducing Secret Manager or dev/prod.
$names = @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','MAVEN_OPTS','SPRING_APPLICATION_JSON',
    'SPRING_PROFILES_ACTIVE','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION',
    'SPRING_CONFIG_IMPORT','PATH')
$names += @(Get-ChildItem Env: | Where-Object Name -Match '^(SPRING_|REPS_EMAIL_|MAIL_SERVICE_|AUTH_SSO_)' | Select-Object -ExpandProperty Name)
$names = @($names | Select-Object -Unique)
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $previous['PATH']
    $env:SPRING_CONFIG_LOCATION = 'classpath:/application-email-local.properties'
    $env:SPRING_PROFILES_ACTIVE = 'email-local'
    Write-Host "MANUAL EMAIL TEST: MongoDB target $target (must contain synthetic records only)"
    Write-Host 'Profile=email-local; backend=http://localhost:8080; frontend=http://localhost:3000'
    Write-Host 'Routing/connection/capture/dispatch=true. Real provider calls occur on your manual actions.'
    Push-Location $repositoryRoot
    try {
        & $maven.Source -o '-DskipTests' spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'Manual email backend startup failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
