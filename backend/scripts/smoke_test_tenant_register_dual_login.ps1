Param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$MysqlPath = "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe",
  [string]$MysqlUser = "root",
  [string]$MysqlPassword = "root",
  [string]$MysqlDatabase = "house_db"
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Get-ApiMessage($resp) {
  if ($null -eq $resp) { return "" }
  if ($resp.PSObject.Properties.Name -contains "message") {
    return [string]$resp.message
  }
  return ""
}

function Invoke-Api {
  param(
    [ValidateSet('GET','POST')][string]$Method,
    [string]$Url,
    [object]$Body = $null,
    [hashtable]$Headers = @{}
  )

  if ($Body -ne $null) {
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
  }

  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
}

function Invoke-MySqlScalar {
  param([string]$Sql)

  if (-not (Test-Path -LiteralPath $MysqlPath)) {
    throw "MySQL client not found: $MysqlPath"
  }

  $args = @(
    "-u$MysqlUser",
    "-D", $MysqlDatabase,
    "-N",
    "-B",
    "-e", $Sql
  )

  $previousMysqlPwd = $env:MYSQL_PWD
  $env:MYSQL_PWD = $MysqlPassword
  try {
    $result = & $MysqlPath @args 2>$null
  } finally {
    if ($null -eq $previousMysqlPwd) {
      Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
      $env:MYSQL_PWD = $previousMysqlPwd
    }
  }

  if ($LASTEXITCODE -ne 0) {
    throw "MySQL command failed for SQL: $Sql"
  }

  return (($result | Out-String).Trim())
}

try {
  $seed = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $phone = "177" + ($seed % 100000000).ToString("00000000")
  $password = "P@ssw0rd!123"
  $name = "SmokeTenant$($seed % 10000)"

  Info "Using phone=$phone"

  Info "Step 1: Register new tenant"
  $register = Invoke-Api -Method POST -Url "$BaseUrl/api/tenant/register" -Body @{
    name = $name
    phone = $phone
    password = $password
    email = "smoke+$seed@example.com"
  }
  Assert (-not [string]::IsNullOrWhiteSpace($register.token)) ("Register failed: " + (Get-ApiMessage $register))
  $tenantId = [int]$register.tenantInfo.id
  $registerToken = [string]$register.token
  Assert ($tenantId -gt 0) "Register tenant id missing"
  Assert (-not [string]::IsNullOrWhiteSpace($registerToken)) "Register token missing"
  Pass "Register API success"

  Info "Step 2: Password login"
  $pwdLogin = Invoke-Api -Method POST -Url "$BaseUrl/api/tenant/login/password" -Body @{
    phone = $phone
    password = $password
  }
  Assert (-not [string]::IsNullOrWhiteSpace($pwdLogin.token)) ("Password login failed: " + (Get-ApiMessage $pwdLogin))
  $pwdToken = [string]$pwdLogin.token
  Pass "Password login success"

  Info "Step 3: SMS request + code login"
  $smsSend = Invoke-Api -Method POST -Url "$BaseUrl/api/tenant/login/sms" -Body @{ phone = $phone }
  Assert ($smsSend.success -eq $true) "Send SMS code failed"

  Start-Sleep -Milliseconds 300
  $codeSql = "SELECT code FROM tenant_login_code WHERE phone='$phone' ORDER BY last_sent_at DESC, id DESC LIMIT 1;"
  $smsCode = Invoke-MySqlScalar -Sql $codeSql
  Assert (-not [string]::IsNullOrWhiteSpace($smsCode)) "SMS code not found in DB"

  $smsLogin = Invoke-Api -Method POST -Url "$BaseUrl/api/tenant/login" -Body @{
    phone = $phone
    code = $smsCode
  }
  Assert (-not [string]::IsNullOrWhiteSpace($smsLogin.token)) ("SMS login failed: " + (Get-ApiMessage $smsLogin))
  $smsToken = [string]$smsLogin.token
  Pass "SMS login success"

  Info "Step 4: Query vacant rooms"
  $vacant = Invoke-Api -Method GET -Url "$BaseUrl/api/tenant/vacant-rooms" -Headers @{ Authorization = "Bearer $pwdToken" }
  $rooms = @($vacant)
  Pass ("Vacant rooms query success, count=" + $rooms.Count)

  Info "Step 5: Submit rent intent"
  $targetRoomId = if ($rooms.Count -gt 0) { [int]$rooms[0].roomId } else { 1 }
  $rentIntent = Invoke-Api -Method POST -Url "$BaseUrl/api/tenant/rent-intent" -Headers @{ Authorization = "Bearer $smsToken" } -Body @{
    roomId = $targetRoomId
    message = "Smoke test intent at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    contactPhone = $phone
  }
  Assert ($rentIntent.success -eq $true) "Rent intent submit failed"
  Pass "Rent intent submit success"

  Info "Step 6: Verify DB side effects"
  $tenantCount = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(1) FROM tenant WHERE phone='$phone';")
  Assert ($tenantCount -ge 1) "Tenant row not found after register"

  $registerMsg = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(1) FROM system_message WHERE type='TENANT_REGISTER' AND related_id=$tenantId;")
  Assert ($registerMsg -ge 1) "TENANT_REGISTER message not found"

  $intentMsg = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(1) FROM system_message WHERE type='TENANT_RENT_INTENT' AND related_id=$tenantId;")
  Assert ($intentMsg -ge 1) "TENANT_RENT_INTENT message not found"

  Pass "DB validation success"

  Write-Host ""
  Pass "All smoke checks passed"
  exit 0
}
catch {
  Fail $_.Exception.Message
  exit 1
}
