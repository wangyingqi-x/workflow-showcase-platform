param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$envScript = Join-Path $workspaceRoot ".env.real-smoke.ps1"

if (-not (Test-Path $envScript)) {
    throw "Missing local env script: $envScript"
}

. $envScript

& (Join-Path $PSScriptRoot "run-e2e-smoke.ps1") -RequireExternalMcp -RequireExternalLlm
