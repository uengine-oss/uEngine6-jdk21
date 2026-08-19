param(
    [string]$DefinitionService = 'http://localhost:9293',
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '..\test-assets\sds-export-runnable')
)

$files = Get-ChildItem -LiteralPath $SourceDirectory -File |
    Where-Object { $_.Extension -in '.bpmn', '.form' } |
    Sort-Object Name
foreach ($file in $files) {
    $definition = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    $body = @{ definition = $definition; name = [IO.Path]::GetFileNameWithoutExtension($file.Name) } | ConvertTo-Json -Compress
    $uri = "$DefinitionService/definition/raw?defPath=$([uri]::EscapeDataString($file.Name))"
    Invoke-RestMethod -Method Put -Uri $uri -ContentType 'application/json; charset=utf-8' -Body $body | Out-Null
    Write-Host "Deployed $($file.Name)"
}
