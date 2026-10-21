[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('phase1','phase2')][string]$Phase)
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','MAVEN_OPTS','SPRING_APPLICATION_JSON',
    'SPRING_PROFILES_ACTIVE','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION',
    'SPRING_CONFIG_IMPORT','GOOGLE_APPLICATION_CREDENTIALS','CLOUDSDK_CONFIG','GOOGLE_CLOUD_PROJECT')) {
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 before running this test-only launcher.' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$version = & $java -version 2>&1 | Out-String
if ($version -notmatch 'version "17\.') { throw 'This isolation guard requires JDK 17.' }
$agent = Join-Path $env:USERPROFILE '.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar'
Push-Location $repositoryRoot
try {
    # Run verify-email-phase1.ps1 -Phase3 first. Surefire records the resolved
    # test classpath locally; do not commit machine-specific classpath files.
    $report = Get-ChildItem target/surefire-reports/TEST-*.xml | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $report) { throw 'Run the approved mocked email suite first to compile tests and resolve the test classpath.' }
    [xml]$xml = Get-Content -Raw $report.FullName
    $classpath = ($xml.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    if (-not $classpath) { throw 'The Surefire report does not contain a resolved test classpath.' }
    if ($Phase -eq 'phase2') {
        foreach ($marker in @('target/isolated-stop','target/isolated-ready')) {
            if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker }
        }
    }
    & $java '--add-opens=java.base/java.lang=ALL-UNNAMED' "-javaagent:$agent" -cp $classpath com.reps.demogcloud.integration.IsolatedEmailVerification $Phase
    if ($LASTEXITCODE -ne 0) { throw "Isolated verification $Phase failed." }
} finally { Pop-Location }
