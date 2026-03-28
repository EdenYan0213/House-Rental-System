$base = 'http://localhost:8080/api'
$phone = ''

function Print-Case($name, $ok, $msg) {
  if ($ok) { Write-Output "[PASS] $name - $msg" }
  else { Write-Output "[FAIL] $name - $msg" }
}

function Try-Api($name, [scriptblock]$action) {
  try {
    $res = & $action
    Print-Case $name $true 'ok'
    return $res
  } catch {
    $msg = ''
    try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
    if ([string]::IsNullOrWhiteSpace($msg)) { $msg = $_.Exception.Message }
    Print-Case $name $false $msg
    return $null
  }
}

try {
  $phone = (& mysql -N -uroot -proot -e "USE house_db; SELECT t.phone FROM tenant t JOIN contract c ON c.tenant_id=t.id WHERE c.status='ACTIVE' ORDER BY c.id DESC LIMIT 1;").Trim()
} catch {
  Print-Case 'phase3 read active tenant phone' $false ($_.Exception.Message)
}

if ([string]::IsNullOrWhiteSpace($phone)) {
  $phone = '13900009999'
}
Print-Case 'phase3 use tenant phone' $true $phone

$realCode = '246810'
try {
  $sql = "USE house_db; INSERT INTO tenant_login_code(phone, code, expires_at, sent_date, sent_count, last_sent_at) VALUES('$phone', '$realCode', DATE_ADD(NOW(), INTERVAL 10 MINUTE), CURDATE(), 0, NOW()) ON DUPLICATE KEY UPDATE code=VALUES(code), expires_at=VALUES(expires_at), sent_date=CURDATE(), sent_count=0, last_sent_at=NOW();"
  & mysql -N -uroot -proot -e $sql | Out-Null
  Print-Case 'phase3 set login code' $true ('code=' + $realCode)
} catch {
  Print-Case 'phase3 set login code' $false ($_.Exception.Message)
  exit 1
}

$login = Try-Api 'phase3 login' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = $phone; code = $realCode } | ConvertTo-Json)
}
if (-not $login) { exit 1 }

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }

$null = Try-Api 'phase3 get preferences' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/preferences" -Headers $headers
}

$pref = Try-Api 'phase3 update preferences en' {
  Invoke-RestMethod -Method Put -Uri "$base/tenant/preferences" -Headers $headers -ContentType 'application/json' -Body (
    @{ language = 'en-US'; notifyOverdue = $true; notifyRepair = $true; notifyContract = $true; notifyNotice = $true } | ConvertTo-Json
  )
}
if ($pref) {
  $langValue = '' + $pref.language
  if ([string]::IsNullOrWhiteSpace($langValue)) {
    $langValue = '' + $pref.Language
  }
  $ok = ($langValue -eq 'en-US' -or $langValue -eq 'zh-CN')
  Print-Case 'phase3 preferences language' $ok ('language=' + $langValue)
}

$msgList = Try-Api 'phase3 list messages' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/messages?page=0&size=10" -Headers $headers
}

if ($msgList) {
  $content = @($msgList.content)
  if ($content.Count -gt 0) {
    $firstId = $content[0].id

    $null = Try-Api 'phase3 message detail' {
      Invoke-RestMethod -Method Get -Uri "$base/tenant/messages/$firstId" -Headers $headers
    }

    $null = Try-Api 'phase3 message read' {
      Invoke-RestMethod -Method Post -Uri "$base/tenant/messages/$firstId/read" -Headers $headers
    }

    if ($content[0].needAction -eq 1) {
      $null = Try-Api 'phase3 message process todo' {
        Invoke-RestMethod -Method Post -Uri "$base/tenant/messages/$firstId/process" -Headers $headers
      }
    }
  } else {
    Print-Case 'phase3 message list not empty' $false 'no message generated'
  }
}

$contract = Try-Api 'phase3 contract current' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/current" -Headers $headers
}

if ($contract -and $contract.id) {
  $contractId = $contract.id

  $null = Try-Api 'phase3 sign preview' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/preview" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId } | ConvertTo-Json
    )
  }

  $null = Try-Api 'phase3 sign verify' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/verify" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId; name = 'Test Tenant'; idCard = '110101199001011234' } | ConvertTo-Json
    )
  }

  $signSubmit = Try-Api 'phase3 sign submit' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/submit" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId } | ConvertTo-Json
    )
  }

  if ($signSubmit -and $signSubmit.flowNo) {
    $null = Try-Api 'phase3 sign callback timeout' {
      Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/internal/sign/callback' -ContentType 'application/json' -Body (
        @{ flowNo = $signSubmit.flowNo; status = 'TIMEOUT'; error = 'mock timeout' } | ConvertTo-Json
      )
    }

    $null = Try-Api 'phase3 sign retry' {
      Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/retry/$contractId" -Headers $headers
    }
  }

  $null = Try-Api 'phase3 sign history' {
    Invoke-RestMethod -Method Get -Uri "$base/tenant/contract/sign/history" -Headers $headers
  }
}

$null = Try-Api 'phase3 dashboard' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/dashboard?months=12" -Headers $headers
}
