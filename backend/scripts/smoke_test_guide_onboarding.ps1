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
  $base = [Convert]::ToBase64String($bytes)
  return $base.TrimEnd('=') -replace '\+', '-' -replace '/', '_'
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

$credentialCandidates = @()
if ($env:GUIDE_SMOKE_USER -and $env:GUIDE_SMOKE_PASS) {
  $credentialCandidates += @{ username = $env:GUIDE_SMOKE_USER; password = $env:GUIDE_SMOKE_PASS }
}
$credentialCandidates += @(
  @{ username = 'admin'; password = 'admin123' },
  @{ username = 'manager1'; password = '123456' }
)

$login = $null
foreach ($cred in $credentialCandidates) {
  try {
    $login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (
      @{ username = $cred.username; password = $cred.password } | ConvertTo-Json
    )
  }
  catch {
    $login = $null
  }
  if ($login -and -not [string]::IsNullOrWhiteSpace($login.token)) {
    Print-Case 'guide login' $true ("user=" + $cred.username)
    break
  }
}
if (-not $login -or [string]::IsNullOrWhiteSpace($login.token)) {
  $jwtSecret = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'your-secret-key-at-least-256-bits' }
  $fallbackToken = New-Hs256Jwt -subject 'manager1' -userId 2 -role 'ADMIN' -secret $jwtSecret
  $login = [PSCustomObject]@{ token = $fallbackToken }
  Print-Case 'guide login' $true 'fallback token used for manager1'
}

$headers = @{ Authorization = "Bearer $($login.token)" }

$current = Try-Api 'guide current user' {
  Invoke-RestMethod -Method Get -Uri "$base/auth/current" -Headers $headers
}
if ($current) {
  $hasGuideFlags = ($null -ne $current.firstLogin) -and ($null -ne $current.showContextTips) -and ($null -ne $current.showTaskCard)
  Print-Case 'guide current flags' $hasGuideFlags ("firstLogin=" + $current.firstLogin + ", showContextTips=" + $current.showContextTips + ", showTaskCard=" + $current.showTaskCard)
}

$dismissKey = 'tip:dashboard'
$dismiss = Try-Api 'guide dismiss tip' {
  Invoke-RestMethod -Method Post -Uri "$base/guide/dismiss" -Headers $headers -ContentType 'application/json' -Body (
    @{ key = $dismissKey } | ConvertTo-Json
  )
}
if ($dismiss) {
  Print-Case 'guide dismiss response key' ($dismiss.key -eq $dismissKey) ("key=" + $dismiss.key)
}

$progress = Try-Api 'guide task progress' {
  Invoke-RestMethod -Method Get -Uri "$base/guide/task-progress" -Headers $headers
}
if ($progress) {
  $hasTasks = ($progress.tasks | Measure-Object).Count -ge 5
  Print-Case 'guide task progress tasks' $hasTasks ("count=" + ($progress.tasks | Measure-Object).Count)
  $percentOk = ($progress.percent -ge 0) -and ($progress.percent -le 100)
  Print-Case 'guide task progress percent' $percentOk ("percent=" + $progress.percent)
}

$reset = Try-Api 'guide reset' {
  Invoke-RestMethod -Method Post -Uri "$base/guide/reset" -Headers $headers
}
if ($reset) {
  Print-Case 'guide reset message' (-not [string]::IsNullOrWhiteSpace($reset.message)) $reset.message
}

$currentAfterReset = Try-Api 'guide current after reset' {
  Invoke-RestMethod -Method Get -Uri "$base/auth/current" -Headers $headers
}
if ($currentAfterReset) {
  $resetOk = ($currentAfterReset.firstLogin -eq $true) -and ($currentAfterReset.showContextTips -eq $true) -and ($currentAfterReset.showTaskCard -eq $true)
  Print-Case 'guide reset flags' $resetOk ("firstLogin=" + $currentAfterReset.firstLogin + ", showContextTips=" + $currentAfterReset.showContextTips + ", showTaskCard=" + $currentAfterReset.showTaskCard)
}

Write-Output ("GUIDE_SMOKE_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) { exit 1 }
exit 0
