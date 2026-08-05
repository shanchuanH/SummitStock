$ErrorActionPreference = "Stop"
$health = Invoke-RestMethod "http://127.0.0.1:8080/actuator/health"
if ($health.status -ne "UP") { throw "API health is not UP" }
$version = Invoke-RestMethod "http://127.0.0.1:8080/api/v1/version"
if (-not $version.strategyVersion) { throw "Version endpoint lacks strategyVersion" }
$data = Invoke-RestMethod "http://127.0.0.1:8080/api/v1/market/data-health"
if (-not $data.status) { throw "Data health lacks an explicit state" }
Write-Output "Smoke passed: API=$($health.status), strategy=$($version.strategyVersion), data=$($data.status)"
