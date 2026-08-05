param(
  [Parameter(Mandatory = $true)][string]$InputFile,
  [Parameter(Mandatory = $true)][switch]$ConfirmRestore,
  [string]$Database = "portfolio"
)
$ErrorActionPreference = "Stop"
$source = [System.IO.Path]::GetFullPath($InputFile)
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Backup does not exist: $source" }
if (-not $ConfirmRestore) { throw "Restore requires -ConfirmRestore" }
Get-Content -LiteralPath $source -Raw | docker compose -f infra/compose.yaml exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' -- $Database
if ($LASTEXITCODE -ne 0) { throw "MySQL restore failed" }
Write-Output "Restore completed from $source"
