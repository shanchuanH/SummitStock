param(
  [Parameter(Mandatory = $true)][string]$OutputFile,
  [string]$Database = "portfolio"
)
$ErrorActionPreference = "Stop"
$target = [System.IO.Path]::GetFullPath($OutputFile)
$parent = Split-Path -Parent $target
if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
docker compose -f infra/compose.local.yaml exec -T mysql sh -c 'exec mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' -- $Database | Set-Content -LiteralPath $target -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "MySQL backup failed" }
Write-Output "Backup written to $target"
