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

function Convert-Base64Url([byte[]]$bytes) {
  $base64 = [Convert]::ToBase64String($bytes)
  return $base64.TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function New-Hs256Jwt($subject, $userId, $role, $secret, $expireSeconds = 86400) {
  $headerJson = '{"alg":"HS256","typ":"JWT"}'
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $payloadObj = [ordered]@{
    userId = $userId
    role = $role
    sub = $subject
    iat = $now
    exp = $now + $expireSeconds
  }
  $payloadJson = $payloadObj | ConvertTo-Json -Compress

  $header = Convert-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
  $payload = Convert-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
  $unsigned = "$header.$payload"

  $hmac = New-Object System.Security.Cryptography.HMACSHA256
  $hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
  $signature = Convert-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
  return "$unsigned.$signature"
}

$jwtSecret = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'your-secret-key-at-least-256-bits' }
$token = New-Hs256Jwt -subject 'admin' -userId 1 -role 'SUPER_ADMIN' -secret $jwtSecret
$headers = @{ Authorization = "Bearer $token" }

$current = Try-Api 'oplog auth current' {
  Invoke-RestMethod -Method Get -Uri "$base/auth/current" -Headers $headers
}
if ($current) {
  $role = '' + $current.role
  Print-Case 'oplog auth role check' ($role -eq 'SUPER_ADMIN') ("role=$role")
}

$filters = Try-Api 'oplog filters' {
  Invoke-RestMethod -Method Get -Uri "$base/logs/filters" -Headers $headers
}
if ($filters) {
  $m = @($filters.modules).Count
  $a = @($filters.actions).Count
  $u = @($filters.usernames).Count
  Print-Case 'oplog filters basic fields' ($m -ge 0 -and $a -ge 0 -and $u -ge 0) ("modules=$m actions=$a users=$u")
}

$analytics = Try-Api 'oplog analytics' {
  Invoke-RestMethod -Method Get -Uri "$base/logs/analytics" -Headers $headers
}
if ($analytics) {
  $trend = @($analytics.trend).Count
  $slow = @($analytics.slowTop).Count
  $fail = @($analytics.failStats).Count
  Print-Case 'oplog analytics fields' ($trend -ge 0 -and $slow -ge 0 -and $fail -ge 0) ("trend=$trend slowTop=$slow failStats=$fail")
}

$rules = Try-Api 'alerts rules' {
  Invoke-RestMethod -Method Get -Uri "$base/alerts/rules" -Headers $headers
}
if ($rules) {
  Print-Case 'alerts rules count field' (@($rules).Count -ge 0) ("count=" + @($rules).Count)
}

$records = Try-Api 'alerts records' {
  Invoke-RestMethod -Method Get -Uri "$base/alerts/records?page=0&size=10" -Headers $headers
}
if ($records) {
  $total = 0
  try { $total = [int]$records.totalElements } catch { $total = 0 }
  $pageCount = @($records.content).Count
  Print-Case 'alerts records fields' ($total -ge 0 -and $pageCount -ge 0) ("total=$total pageCount=$pageCount")
}

$lifecycle = Try-Api 'log lifecycle run' {
  Invoke-RestMethod -Method Post -Uri "$base/log-lifecycle/run" -Headers $headers
}
if ($lifecycle) {
  $ok = ($lifecycle.PSObject.Properties.Name -contains 'archived') -and
    ($lifecycle.PSObject.Properties.Name -contains 'removedOnline') -and
    ($lifecycle.PSObject.Properties.Name -contains 'removedArchive')
  Print-Case 'log lifecycle fields' $ok ("archived=$($lifecycle.archived) online=$($lifecycle.removedOnline) archive=$($lifecycle.removedArchive)")
}

Write-Output ("OPLOG_ENHANCEMENT_SMOKE_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) {
  exit 1
}
exit 0
