$base = 'http://localhost:8080/api'
$script:passCount = 0
$script:failCount = 0

function Print-Case($name, $ok, $msg) {
  if ($ok) {
    $script:passCount++
    Write-Output "[PASS] $name - $msg"
  }
  else {
    $script:failCount++
    Write-Output "[FAIL] $name - $msg"
  }
}

function Try-Api($name, [scriptblock]$action) {
  try {
    $res = & $action
    Print-Case $name $true 'ok' | Out-Host
    return $res
  }
  catch {
    $msg = ''
    try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
    if ([string]::IsNullOrWhiteSpace($msg)) { $msg = $_.Exception.Message }
    Print-Case $name $false $msg | Out-Host
    return $null
  }
}

$login = Try-Api 'export-csv login' {
  Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (
    @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
  )
}
if (-not $login -or [string]::IsNullOrWhiteSpace($login.token)) {
  Print-Case 'export-csv token exists' $false 'token missing'
  exit 1
}

$headers = @{ Authorization = "Bearer $($login.token)" }
$billPath = Join-Path $env:TEMP 'bills-export-smoke.csv'
$meterPath = Join-Path $env:TEMP 'meter-export-smoke.csv'

$billOk = $false
$meterOk = $false

try {
  $billResp = Invoke-WebRequest -Method Get -Uri "$base/bills/export-csv" -Headers $headers -OutFile $billPath
  $billOk = $true
  $billStatus = if ($billResp -and $billResp.PSObject.Properties.Name -contains 'StatusCode') { $billResp.StatusCode } else { 'ok' }
  Print-Case 'export-csv bills status' $billOk ("status=" + $billStatus)

  $billHead = (Get-Content -Path $billPath -TotalCount 1)
  $billHead = $billHead -replace "^`uFEFF", ''
  $billHeaderOk = (-not [string]::IsNullOrWhiteSpace($billHead)) -and (($billHead.Split(',').Count) -ge 5)
  Print-Case 'export-csv bills header' $billHeaderOk $billHead
}
catch {
  Print-Case 'export-csv bills request' $false $_.Exception.Message
}

try {
  $meterResp = Invoke-WebRequest -Method Get -Uri "$base/meter-readings/export-csv" -Headers $headers -OutFile $meterPath
  $meterOk = $true
  $meterStatus = if ($meterResp -and $meterResp.PSObject.Properties.Name -contains 'StatusCode') { $meterResp.StatusCode } else { 'ok' }
  Print-Case 'export-csv meter status' $meterOk ("status=" + $meterStatus)

  $meterHead = (Get-Content -Path $meterPath -TotalCount 1)
  $meterHead = $meterHead -replace "^`uFEFF", ''
  $meterHeaderOk = (-not [string]::IsNullOrWhiteSpace($meterHead)) -and (($meterHead.Split(',').Count) -ge 4)
  Print-Case 'export-csv meter header' $meterHeaderOk $meterHead
}
catch {
  Print-Case 'export-csv meter request' $false $_.Exception.Message
}

if (Test-Path $billPath) {
  Print-Case 'export-csv bills file size' ((Get-Item $billPath).Length -gt 0) ("size=" + (Get-Item $billPath).Length)
}
if (Test-Path $meterPath) {
  Print-Case 'export-csv meter file size' ((Get-Item $meterPath).Length -gt 0) ("size=" + (Get-Item $meterPath).Length)
}

Write-Output ("EXPORT_CSV_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) { exit 1 }
exit 0
