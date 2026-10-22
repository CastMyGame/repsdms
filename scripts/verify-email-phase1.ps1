[CmdletBinding()]
param([switch]$Phase2, [switch]$Phase3)

# Phase 1 uses only reviewed Mockito/unit tests and intercepted HTTP. Never starts Spring or MongoDB.
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$testNames = @(
    'EmailRoutingServiceTest', 'EmailSenderServiceTest', 'EmailNotificationServiceTest',
    'EmailServiceTest', 'EmailReferralServiceTest', 'UserContextServiceTest',
    'GoogleMailServiceTest', 'PunishmentCreationServiceTest', 'OfficeReferralServiceTest',
    'PunishmentClosureServiceImplTest', 'JwtUtilsLoggingTest'
)
if ($Phase2 -or $Phase3) { $testNames += @('GoogleOAuthTokenStoreTest', 'GmailConnectionControllerTest', 'OAuth2LoginSuccessHandlerTest') }
if ($Phase3) { $testNames += @('RequiredNotificationServiceTest', 'GuidanceServiceTest', 'PunishmentUtilsTest', 'PunishmentServiceTest#deletePunishment_*') }
$javaDirectory = Join-Path $env:ProgramFiles 'Zulu\zulu-17'
if (-not (Test-Path (Join-Path $javaDirectory 'bin\java.exe'))) {
    throw 'JDK 17 is required. This script does not install runtimes.'
}
$maven = Get-Command mvn.cmd -CommandType Application -ErrorAction Stop
$mockitoAgent = Join-Path $env:USERPROFILE '.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar'
if (-not (Test-Path -LiteralPath $mockitoAgent)) {
    throw 'The project Mockito 5.17.0 agent must already be cached locally. No download is performed.'
}
# Avoid Maven-wrapper bootstrap downloads; dependency resolution is offline as well.
$environmentNames = @('JAVA_HOME', 'PATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'MAVEN_OPTS',
    'SPRING_APPLICATION_JSON', 'SPRING_PROFILES_ACTIVE', 'SPRING_CONFIG_LOCATION',
    'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT')
$previousValues = @{}
foreach ($name in $environmentNames) { $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    foreach ($name in $environmentNames | Where-Object { $_ -notin @('JAVA_HOME', 'PATH') }) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    $env:JAVA_HOME = $javaDirectory
    $env:PATH = (Join-Path $javaDirectory 'bin') + [IO.Path]::PathSeparator + $env:PATH
    Push-Location $repositoryRoot
    try {
        # Preload the existing Mockito dependency instead of relying on runtime JVM attachment.
        # This focused run does not collect JaCoCo coverage.
        & $maven.Source -o -B ('-Dtest=' + ($testNames -join ',')) ('-DargLine=-javaagent:"' + $mockitoAgent + '"') '-DfailIfNoTests=true' test
        if ($LASTEXITCODE -ne 0) { throw "Phase 1 mocked tests failed (exit $LASTEXITCODE). No online retry is performed." }
    } finally { Pop-Location }
} finally {
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process') }
}
