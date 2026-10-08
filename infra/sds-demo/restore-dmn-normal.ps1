param([string]$RulePath = (Join-Path $PSScriptRoot '../process-assets/definitions/businessRules/export-bill-purchase-route.rule'))
$ErrorActionPreference = 'Stop'
try {
    $resolvedPath = (Resolve-Path -LiteralPath $RulePath).Path
    $rule = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8 | ConvertFrom-Json
    [xml]$dmn = $rule.ruleJson.dmnXml
    $ns = New-Object System.Xml.XmlNamespaceManager($dmn.NameTable)
    $ns.AddNamespace('d', $dmn.DocumentElement.NamespaceURI)
    $rows = @($dmn.SelectNodes('//d:decisionTable/d:rule', $ns) | Where-Object {
        $_.SelectSingleNode('d:outputEntry/d:text', $ns).InnerText.Trim() -eq '"COLLECTION"'
    })
    if ($rows.Count -ne 1) { throw 'Expected exactly one COLLECTION rule. No changes made.' }
    $condition = $rows[0].SelectSingleNode('d:inputEntry[2]/d:text', $ns)
    if ($condition.InnerText.Trim() -eq '> purchaseLimit') {
        Write-Host 'Already normal: amount > purchaseLimit routes to COLLECTION.'
        exit 0
    }
    if ($condition.InnerText.Trim() -ne '<= purchaseLimit') { throw 'Unexpected amount condition. No changes made.' }
    $backupDir = Join-Path $PSScriptRoot 'backups/dmn-demo'
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    $backupPath = Join-Path $backupDir ('export-bill-purchase-route-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.rule')
    Copy-Item -LiteralPath $resolvedPath -Destination $backupPath
    $condition.InnerText = '> purchaseLimit'
    $rule.ruleJson.dmnXml = $dmn.OuterXml
    [System.IO.File]::WriteAllText($resolvedPath, ($rule | ConvertTo-Json -Depth 100 -Compress), (New-Object System.Text.UTF8Encoding($false)))
    $saved = Get-Content -LiteralPath $resolvedPath -Raw -Encoding UTF8 | ConvertFrom-Json
    [xml]$verified = $saved.ruleJson.dmnXml
    if ($verified.SelectSingleNode('//d:rule[@id="' + $rows[0].id + '"]/d:inputEntry[2]/d:text', $ns).InnerText -ne '> purchaseLimit') { throw 'Saved condition verification failed.' }
    Write-Host 'Normal DMN restored: amount > purchaseLimit routes to COLLECTION.'
    Write-Host ('Backup: ' + $backupPath)
    Write-Host 'Reload the DMN editor to see the restored condition.'
} catch { Write-Error $_; exit 1 }
