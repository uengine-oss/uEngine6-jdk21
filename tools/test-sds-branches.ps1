param(
  [string]$BaseUrl = 'http://localhost:9294',
  [string]$CasesPath = 'test-assets/sds-export-runnable/branch-cases.json',
  [string]$ReportPath = 'test-assets/sds-export-runnable/branch-test-report.json'
)

$ErrorActionPreference = 'Stop'
$cases = Get-Content -LiteralPath $CasesPath -Raw -Encoding utf8 | ConvertFrom-Json
$results = [System.Collections.Generic.List[object]]::new()

function Invoke-Utf8Json([string]$Method, [string]$Uri, [object]$Body, [hashtable]$Headers = @{}) {
  $json = $Body | ConvertTo-Json -Compress -Depth 8
  Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
}

function Get-CurrentTaskId([string]$InstanceId, [string]$TracingTag) {
  $query = "SELECT task_id FROM bpm_worklist WHERE inst_id=$InstanceId AND trc_tag='$TracingTag' AND status IN ('NEW','RESERVED','RUNNING') ORDER BY task_id DESC LIMIT 1;"
  $id = (& docker compose -f infra/docker-compose.keycloak-postgres.yml exec -T postgres psql -U uengine -d uengine -At -c $query).Trim()
  if (-not $id) { throw "Running work item was not found. instance=$InstanceId task=$TracingTag" }
  $id
}

$number = 0
foreach ($case in $cases) {
  foreach ($branch in $case.branches) {
    $number++
    $result = [ordered]@{
      no = $number; definitionId = $case.definitionId; gatewayId = $case.gatewayId
      variable = $case.variable; input = $branch.value; expectedTargetId = $branch.targetId
      instanceId = $null; mapperSaved = $false; targetObserved = $false; passed = $false; error = $null
    }
    try {
      $started = Invoke-Utf8Json 'Post' "$BaseUrl/instance" @{ processDefinitionId = $case.definitionId; simulation = $true; instanceName = "branch-$number" }
      $result.instanceId = [string]$started.instanceId
      $null = Invoke-Utf8Json 'Post' "$BaseUrl/instance/$($result.instanceId)/advance-to-activity/$($case.taskId)" @{ maxAttempts = 60 }
      $taskId = Get-CurrentTaskId $result.instanceId $case.taskId
      $parameterValues = @{}
      if ($case.inputDefaults) {
        $case.inputDefaults.PSObject.Properties | ForEach-Object { $parameterValues[$_.Name] = $_.Value }
      }
      $parameterValues[$case.variable] = $branch.value
      $completed = Invoke-Utf8Json 'Post' "$BaseUrl/work-item/$taskId/complete" @{ parameterValues = $parameterValues } @{ isSimulate = 'true' }
      $changed = $completed.changedProcessVariables.PSObject.Properties[$case.variable].Value
      $result.mapperSaved = ($changed.after -eq $branch.value)
      Start-Sleep -Milliseconds 250
      $logs = (& docker compose -f infra/docker-compose.keycloak-postgres.yml logs --tail 300 process-service) -join "`n"
      $target = [regex]::Escape("($($branch.targetId))")
      $instance = [regex]::Escape("* instance = $($result.instanceId)")
      $result.targetObserved = [regex]::IsMatch($logs, "(?s)Start Executing Activity:.*?$target.*?$instance")
      $result.passed = $result.mapperSaved -and $result.targetObserved
    } catch {
      $result.error = $_.Exception.Message
    }
    $results.Add([pscustomobject]$result)
    Write-Host ("[{0}] {1} {2}={3}: {4}" -f $number, $case.gatewayId, $case.variable, $branch.value, $(if ($result.passed) { 'PASS' } else { 'FAIL' }))
  }
}

$report = [ordered]@{
  generatedAt = (Get-Date).ToString('o')
  total = $results.Count
  passed = @($results | Where-Object passed).Count
  failed = @($results | Where-Object { -not $_.passed }).Count
  results = $results
}
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $ReportPath -Encoding utf8
Write-Host ("Branch test summary: {0}/{1} passed. Report: {2}" -f $report.passed, $report.total, $ReportPath)
if ($report.failed -gt 0) { exit 1 }
