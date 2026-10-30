[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^reps_a7_executor_[0-9a-f]{32}$')][string]$Database,
    [Parameter(Mandatory)][string]$SnapshotPath,
    [Parameter(Mandatory)][string]$ManifestPath,
    [Parameter(Mandatory)][string]$PlanPath,
    [Parameter(Mandatory)][string]$ApprovalPath,
    [Parameter(Mandatory)][string]$ReportPath,
    [ValidateSet('DryRun','Execute')][string]$Mode='DryRun',
    [ValidateSet('Transaction','PerRecord')][string]$Atomicity='Transaction',
    [string]$Uri='mongodb://127.0.0.1:27029/?replicaSet=repsa7exec&directConnection=true&serverSelectionTimeoutMS=5000'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
if($Uri -cne 'mongodb://127.0.0.1:27029/?replicaSet=repsa7exec&directConnection=true&serverSelectionTimeoutMS=5000'){throw 'Only the fixed disposable local target is supported.'}
if(-not $env:REPS_TENANT_AUDIT_KEY -or -not $env:REPS_LOCAL_EXECUTOR_APPROVAL_KEY){throw 'Independent audit and operator approval keys must be supplied through process environment.'}
$command=Get-Command mongosh -ErrorAction Stop
$values=@{
    REPS_LOCAL_EXECUTOR_TOOL_DIR=$PSScriptRoot
    REPS_LOCAL_EXECUTOR_URI=$Uri
    REPS_LOCAL_EXECUTOR_DATABASE=$Database
    REPS_LOCAL_EXECUTOR_MODE=if($Mode -eq 'Execute'){'execute'}else{'dry-run'}
    REPS_LOCAL_EXECUTOR_ATOMICITY=if($Atomicity -eq 'PerRecord'){'per-record'}else{'transaction'}
    REPS_LOCAL_EXECUTOR_SNAPSHOT=[IO.Path]::GetFullPath($SnapshotPath)
    REPS_LOCAL_EXECUTOR_MANIFEST=[IO.Path]::GetFullPath($ManifestPath)
    REPS_LOCAL_EXECUTOR_PLAN=[IO.Path]::GetFullPath($PlanPath)
    REPS_LOCAL_EXECUTOR_APPROVAL=[IO.Path]::GetFullPath($ApprovalPath)
    REPS_LOCAL_EXECUTOR_REPORT=[IO.Path]::GetFullPath($ReportPath)
}
$previous=@{}
foreach($name in $values.Keys){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
    foreach($name in $values.Keys){[Environment]::SetEnvironmentVariable($name,$values[$name],'Process')}
    # No URI, key or document values are passed in CLI arguments. No cloud credential discovery.
    & $command.Source --norc --quiet --nodb --file (Join-Path $PSScriptRoot 'execute-local.mongosh.js')
    if($LASTEXITCODE -ne 0){throw 'Local migration refused, interrupted or uncertain. Inspect the sanitized journal; do not change action/ownership evidence to bypass a conflict.'}
}finally{foreach($name in $values.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}}
