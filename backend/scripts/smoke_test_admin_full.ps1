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

$login = Try-Api 'admin login' {
  Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (
    @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
  )
}
if (-not $login -or [string]::IsNullOrWhiteSpace($login.token)) {
  Print-Case 'admin token exists' $false 'login token missing'
  exit 1
}

$headers = @{ Authorization = "Bearer $($login.token)" }
Print-Case 'admin token exists' $true 'token received'

$current = Try-Api 'auth current' {
  Invoke-RestMethod -Method Get -Uri "$base/auth/current" -Headers $headers
}
if ($current) {
  $role = '' + $current.role
  Print-Case 'auth current role' ($role -eq 'SUPER_ADMIN' -or $role -eq 'ADMIN') ("role=$role")
}

$stats = Try-Api 'dashboard stats' {
  Invoke-RestMethod -Method Get -Uri "$base/dashboard/stats" -Headers $headers
}
if ($stats) {
  $keys = ($stats.PSObject.Properties.Name -join ',')
  Print-Case 'dashboard stats fields' ($keys -match '(^|,)todayPayment(,|$)') 'todayPayment present'
}

$charts = Try-Api 'dashboard charts' {
  Invoke-RestMethod -Method Get -Uri "$base/dashboard/charts" -Headers $headers
}
if ($charts) {
  $months = @($charts.trendMonths).Count
  Print-Case 'dashboard charts months' ($months -gt 0) ("count=$months")
}

$expiring = Try-Api 'dashboard expiring contracts' {
  Invoke-RestMethod -Method Get -Uri "$base/dashboard/expiring-contracts?days=3" -Headers $headers
}
if ($expiring) {
  Print-Case 'dashboard expiring count field' ($expiring.PSObject.Properties.Name -contains 'count') 'count present'
}

$buildings = Try-Api 'buildings list' {
  Invoke-RestMethod -Method Get -Uri "$base/buildings" -Headers $headers
}
if ($buildings) {
  Print-Case 'buildings list count' (@($buildings).Count -ge 0) ("count=" + @($buildings).Count)
}

$rooms = Try-Api 'rooms list' {
  Invoke-RestMethod -Method Get -Uri "$base/rooms" -Headers $headers
}
if ($rooms) {
  Print-Case 'rooms list count' (@($rooms).Count -ge 0) ("count=" + @($rooms).Count)
}

$tenants = Try-Api 'tenants list' {
  Invoke-RestMethod -Method Get -Uri "$base/tenants" -Headers $headers
}
if ($tenants) {
  Print-Case 'tenants list count' (@($tenants).Count -ge 0) ("count=" + @($tenants).Count)
}

$contracts = Try-Api 'contracts list' {
  Invoke-RestMethod -Method Get -Uri "$base/contracts" -Headers $headers
}
if ($contracts) {
  Print-Case 'contracts list count' (@($contracts).Count -ge 0) ("count=" + @($contracts).Count)
}

$bills = Try-Api 'bills list' {
  Invoke-RestMethod -Method Get -Uri "$base/bills" -Headers $headers
}
if ($bills) {
  Print-Case 'bills list count' (@($bills).Count -ge 0) ("count=" + @($bills).Count)
}

$complaints = Try-Api 'complaints list' {
  Invoke-RestMethod -Method Get -Uri "$base/complaints?page=0&size=10" -Headers $headers
}
if ($complaints) {
  $total = 0
  try { $total = [int]$complaints.totalElements } catch { $total = 0 }
  Print-Case 'complaints list totalElements' ($total -ge 0) ("total=$total")
}

$complaintStats = Try-Api 'complaints stats' {
  Invoke-RestMethod -Method Get -Uri "$base/complaints/stats" -Headers $headers
}
if ($complaintStats) {
  $keys = ($complaintStats.PSObject.Properties.Name -join ',')
  Print-Case 'complaints stats total field' ($keys -match '(^|,)total(,|$)') 'total present'
}

$messages = Try-Api 'messages unread count' {
  Invoke-RestMethod -Method Get -Uri "$base/messages/unread-count" -Headers $headers
}
if ($messages) {
  $ok = $false
  $msg = ''
  if ($messages -is [int] -or $messages -is [long] -or $messages -is [double]) {
    $ok = $messages -ge 0
    $msg = "value=$messages"
  }
  else {
    $ok = ($messages.PSObject.Properties.Name -contains 'count')
    $msg = 'count present'
  }
  Print-Case 'messages unread count field' $ok $msg
}

$backupConfig = Try-Api 'backup config' {
  Invoke-RestMethod -Method Get -Uri "$base/backup/config" -Headers $headers
}
if ($backupConfig) {
  $keys = ($backupConfig.PSObject.Properties.Name -join ',')
  Print-Case 'backup config enabled field' ($keys -match '(^|,)enabled(,|$)') 'enabled present'
}

$backups = Try-Api 'backup records' {
  Invoke-RestMethod -Method Get -Uri "$base/backup/records" -Headers $headers
}
if ($backups) {
  Print-Case 'backup records list' (@($backups).Count -ge 0) ("count=" + @($backups).Count)
}

Write-Output ("ADMIN_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) {
  exit 1
}
exit 0
