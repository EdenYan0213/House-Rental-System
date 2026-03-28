$base = if ($env:PERF_BASE) { $env:PERF_BASE } else { 'http://localhost:8080/api' }
$user = if ($env:PERF_USER) { $env:PERF_USER } else { 'admin' }
$pass = if ($env:PERF_PASS) { $env:PERF_PASS } else { 'admin123' }
$repeat = if ($env:PERF_REPEAT) { [int]$env:PERF_REPEAT } else { 10 }

function Convert-Base64Url([byte[]]$bytes) {
  $base64 = [Convert]::ToBase64String($bytes)
  return $base64.TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function New-Hs256Jwt($subject, $userId, $role, $secret, $expireSeconds = 3600) {
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

function Measure-Endpoint($name, $method, $url, $headers, $body, $times) {
  $results = @()
  for ($i = 0; $i -lt $times; $i++) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
      if ($method -eq 'GET') {
        Invoke-WebRequest -UseBasicParsing -Method Get -Uri $url -Headers $headers | Out-Null
      }
      elseif ($method -eq 'POST') {
        Invoke-WebRequest -UseBasicParsing -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
      }
      else {
        throw "unsupported method: $method"
      }
      $watch.Stop()
      $results += $watch.ElapsedMilliseconds
    }
    catch {
      $watch.Stop()
      $results += -1
    }
  }

  $ok = $results | Where-Object { $_ -ge 0 }
  if ($ok.Count -eq 0) {
    return [PSCustomObject]@{
      name = $name
      count = $times
      success = 0
      fail = $times
      avgMs = -1
      p95Ms = -1
      maxMs = -1
    }
  }

  $sorted = $ok | Sort-Object
  $p95Index = [Math]::Ceiling($sorted.Count * 0.95) - 1
  if ($p95Index -lt 0) { $p95Index = 0 }

  return [PSCustomObject]@{
    name = $name
    count = $times
    success = $ok.Count
    fail = $times - $ok.Count
    avgMs = [Math]::Round((($ok | Measure-Object -Average).Average), 2)
    p95Ms = $sorted[$p95Index]
    maxMs = ($sorted | Select-Object -Last 1)
  }
}

Write-Output "PERF_BASE=$base"
Write-Output "PERF_REPEAT=$repeat"

$loginRes = $null
$candidates = @(
  @{ username = $user; password = $pass },
  @{ username = 'admin'; password = 'admin123' },
  @{ username = 'manager1'; password = '123456' }
)

foreach ($c in $candidates) {
  try {
    $body = @{ username = $c.username; password = $c.password } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $body
    if ($loginRes -and -not [string]::IsNullOrWhiteSpace($loginRes.token)) {
      Write-Output ("PERF_LOGIN_USER=" + $c.username)
      break
    }
  }
  catch {
    $loginRes = $null
  }
}

if (-not $loginRes -or [string]::IsNullOrWhiteSpace($loginRes.token)) {
  $jwtSecret = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'your-secret-key-at-least-256-bits' }
  $fallbackToken = New-Hs256Jwt -subject 'manager1' -userId 2 -role 'ADMIN' -secret $jwtSecret
  $loginRes = [PSCustomObject]@{ token = $fallbackToken }
  Write-Output 'PERF_LOGIN_USER=fallback-jwt(manager1)'
}

$headers = @{ Authorization = "Bearer $($loginRes.token)" }

$targets = @(
  @{ name = 'dashboard-stats'; method = 'GET'; url = "$base/dashboard/stats"; body = $null },
  @{ name = 'dashboard-charts'; method = 'GET'; url = "$base/dashboard/charts"; body = $null },
  @{ name = 'buildings'; method = 'GET'; url = "$base/buildings"; body = $null },
  @{ name = 'rooms-page1'; method = 'GET'; url = "$base/rooms?page=1&size=50"; body = $null },
  @{ name = 'tenants-page1'; method = 'GET'; url = "$base/tenants?page=1&size=50"; body = $null },
  @{ name = 'contracts-page1'; method = 'GET'; url = "$base/contracts?page=1&size=50"; body = $null },
  @{ name = 'bills-page1'; method = 'GET'; url = "$base/bills?page=1&size=50"; body = $null },
  @{ name = 'meter-readings-page1'; method = 'GET'; url = "$base/meter-readings?page=1&size=50"; body = $null },
  @{ name = 'repair-orders-page1'; method = 'GET'; url = "$base/repair-orders?page=0&size=20"; body = $null },
  @{ name = 'complaints-page1'; method = 'GET'; url = "$base/complaints?page=0&size=20"; body = $null }
)

$report = @()
foreach ($t in $targets) {
  $item = Measure-Endpoint -name $t.name -method $t.method -url $t.url -headers $headers -body $t.body -times $repeat
  $report += $item
  Write-Output ("[RESULT] {0} avg={1}ms p95={2}ms fail={3}" -f $item.name, $item.avgMs, $item.p95Ms, $item.fail)
}

$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$outDir = Join-Path $PSScriptRoot 'reports'
if (-not (Test-Path $outDir)) {
  New-Item -Path $outDir -ItemType Directory | Out-Null
}
$outCsv = Join-Path $outDir ("perf_baseline_{0}.csv" -f $timestamp)
$report | Export-Csv -Path $outCsv -NoTypeInformation -Encoding UTF8

Write-Output "PERF_REPORT=$outCsv"
Write-Output 'PERF_BASELINE_DONE'
exit 0
