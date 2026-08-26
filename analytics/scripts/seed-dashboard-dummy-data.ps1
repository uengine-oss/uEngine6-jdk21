<#
.SYNOPSIS
Seeds or removes KPI, heatmap, and dashboard dummy data in PostgreSQL.

.EXAMPLE
.\analytics\scripts\seed-dashboard-dummy-data.ps1

.EXAMPLE
.\analytics\scripts\seed-dashboard-dummy-data.ps1 cleanup
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('seed', 'cleanup', '--cleanup')]
    [string]$Mode = 'seed'
)

$ErrorActionPreference = 'Stop'

function Get-EnvironmentValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$DefaultValue
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }
    return $value
}

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlFile = Join-Path $scriptDirectory 'seed-dashboard-dummy-data.sql'
if (-not (Test-Path -LiteralPath $sqlFile -PathType Leaf)) {
    throw "SQL file was not found: $sqlFile"
}

$psqlCandidate = Get-EnvironmentValue -Name 'PSQL_BIN' -DefaultValue 'psql'
$psqlCommand = Get-Command -Name $psqlCandidate -ErrorAction SilentlyContinue
if ($null -eq $psqlCommand) {
    throw 'psql was not found. Install the PostgreSQL client or set PSQL_BIN.'
}
$psqlExecutable = if ([string]::IsNullOrWhiteSpace($psqlCommand.Source)) {
    $psqlCommand.Name
} else {
    $psqlCommand.Source
}

$postgresHost = Get-EnvironmentValue -Name 'POSTGRES_HOST' -DefaultValue 'localhost'
$postgresPort = Get-EnvironmentValue -Name 'POSTGRES_PORT' -DefaultValue '5432'
$postgresDatabase = Get-EnvironmentValue -Name 'POSTGRES_DB' -DefaultValue 'uengine'
$postgresUser = Get-EnvironmentValue -Name 'POSTGRES_USER' -DefaultValue 'uengine'
$postgresPassword = Get-EnvironmentValue -Name 'POSTGRES_PASSWORD' -DefaultValue 'uengine'
$databaseSchema = Get-EnvironmentValue -Name 'UENGINE_DB_SCHEMA' -DefaultValue 'public'
$timeZone = Get-EnvironmentValue -Name 'UENGINE_ANALYTICS_ETL_TIME_ZONE' -DefaultValue 'Asia/Seoul'
$dummyDays = Get-EnvironmentValue -Name 'ANALYTICS_DUMMY_DAYS' -DefaultValue '90'
$processesPerDay = Get-EnvironmentValue -Name 'ANALYTICS_DUMMY_PROCESSES_PER_DAY' -DefaultValue '20'
$tasksPerProcess = Get-EnvironmentValue -Name 'ANALYTICS_DUMMY_TASKS_PER_PROCESS' -DefaultValue '6'
$randomSeed = Get-EnvironmentValue -Name 'ANALYTICS_DUMMY_RANDOM_SEED' -DefaultValue '0.4242'
$cleanup = if ($Mode -eq 'cleanup' -or $Mode -eq '--cleanup') { 'true' } else { 'false' }

[Environment]::SetEnvironmentVariable('PGPASSWORD', $postgresPassword, 'Process')

$psqlArguments = @(
    '-X',
    '--host', $postgresHost,
    '--port', $postgresPort,
    '--dbname', $postgresDatabase,
    '--username', $postgresUser,
    '--set', 'ON_ERROR_STOP=1',
    '--set', "schema=$databaseSchema",
    '--set', "dummy_days=$dummyDays",
    '--set', "processes_per_day=$processesPerDay",
    '--set', "tasks_per_process=$tasksPerProcess",
    '--set', "time_zone=$timeZone",
    '--set', "random_seed=$randomSeed",
    '--set', "cleanup=$cleanup"
)

$dummyEndDate = [Environment]::GetEnvironmentVariable('ANALYTICS_DUMMY_END_DATE')
if (-not [string]::IsNullOrWhiteSpace($dummyEndDate)) {
    $psqlArguments += @('--set', "end_date=$dummyEndDate")
}
$psqlArguments += @('--file', $sqlFile)

& $psqlExecutable @psqlArguments
if ($LASTEXITCODE -ne 0) {
    throw "psql exited with code $LASTEXITCODE"
}
