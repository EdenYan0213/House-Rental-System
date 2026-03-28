$base = 'http://localhost:8080/api'

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

$secret = 'your-secret-key-at-least-256-bits'
$cases = @(
  @{ sub='manager1'; uid=2; role='ADMIN' },
  @{ sub='admin'; uid=1; role='SUPER_ADMIN' }
)

foreach ($c in $cases) {
  $t = New-Hs256Jwt -subject $c.sub -userId $c.uid -role $c.role -secret $secret
  $h = @{ Authorization = "Bearer $t" }
  try {
    $res = Invoke-RestMethod -Method Get -Uri "$base/auth/current" -Headers $h
    Write-Output "OK sub=$($c.sub) uid=$($c.uid) role=$($c.role) -> currentUser=$($res.username) currentRole=$($res.role)"
  }
  catch {
    Write-Output "FAIL sub=$($c.sub) uid=$($c.uid) role=$($c.role) -> $($_.Exception.Message)"
  }
}
