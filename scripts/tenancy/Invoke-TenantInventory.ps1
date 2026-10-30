[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Database,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:/-]{1,120}$')][string]$SourceId,
    [Parameter(Mandatory)][string]$ExpectedHost,
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][switch]$ReadOnlyInventoryApproved,
    [Parameter(Mandatory)][switch]$ProtectedFingerprintReadApproved
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if(-not $ReadOnlyInventoryApproved -or -not $ProtectedFingerprintReadApproved){throw 'Separate approval is required for database inspection.'}
$command=Get-Command mongosh -ErrorAction Stop
if(-not $env:REPS_TENANT_AUDIT_URI -or -not $env:REPS_TENANT_AUDIT_KEY){throw 'Provide the approved read-only URI and audit HMAC key through process environment; never command arguments.'}
$taskNames=@('REPS_TENANT_AUDIT_APPROVED','REPS_TENANT_AUDIT_CONTENT_APPROVED','REPS_TENANT_AUDIT_DATABASE','REPS_TENANT_AUDIT_SOURCE_ID','REPS_TENANT_AUDIT_EXPECTED_HOST','REPS_TENANT_AUDIT_OUT','REPS_TENANT_AUDIT_TOOL_DIR')
$taskPrevious=@{}
foreach($taskName in $taskNames){$taskPrevious[$taskName]=[Environment]::GetEnvironmentVariable($taskName,'Process')}
try {
    $env:REPS_TENANT_AUDIT_APPROVED='read-only-inventory-approved'
    $env:REPS_TENANT_AUDIT_CONTENT_APPROVED='protected-fingerprint-read-approved'
    $env:REPS_TENANT_AUDIT_DATABASE=$Database
    $env:REPS_TENANT_AUDIT_SOURCE_ID=$SourceId
    $env:REPS_TENANT_AUDIT_EXPECTED_HOST=$ExpectedHost
    $env:REPS_TENANT_AUDIT_OUT=[IO.Path]::GetFullPath($OutputPath)
    $env:REPS_TENANT_AUDIT_TOOL_DIR=$PSScriptRoot
    # URI/key are not included in arguments, shell history or output.
    & $command.Source --norc --quiet --nodb --file (Join-Path $PSScriptRoot 'inventory.mongosh.js')
    if($LASTEXITCODE -ne 0){throw 'Read-only tenant inventory failed; details withheld.'}
} finally {
    foreach($taskName in $taskNames){[Environment]::SetEnvironmentVariable($taskName,$taskPrevious[$taskName],'Process')}
}
