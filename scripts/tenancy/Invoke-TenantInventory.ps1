[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][switch]$ReadOnlyInventoryApproved
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($IsWindows) { throw 'Use the disposable Linux container. This launcher cannot verify Windows output ACLs.' }
if (-not $ReadOnlyInventoryApproved) { throw 'Inventory approval is required.' }
if (-not $env:REPS_TENANT_AUDIT_PASSWORD) { throw 'Inject the synthetic reader password through the process environment.' }
$settings = @{
    REPS_TENANT_AUDIT_APPROVED = 'read-only-inventory-approved'
    REPS_TENANT_AUDIT_DATABASE = 'reps_inventory_fixture'
    REPS_TENANT_AUDIT_TOOL_DIR = $PSScriptRoot
    REPS_TENANT_AUDIT_OUT = [IO.Path]::GetFullPath($OutputPath)
}
$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    & mongosh --norc --quiet --nodb --file (Join-Path $PSScriptRoot 'inventory.mongosh.js')
    if ($LASTEXITCODE -ne 0) { throw 'Aggregate inventory refused or failed.' }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
