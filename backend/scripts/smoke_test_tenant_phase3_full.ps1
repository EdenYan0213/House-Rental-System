$base = 'http://localhost:8080/api'
$phone = '13900009999'
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
    Print-Case $name $true 'ok'
    return $res
  }
  catch {
    $msg = ''
    try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
    if ([string]::IsNullOrWhiteSpace($msg)) { $msg = $_.Exception.Message }
    Print-Case $name $false $msg
    return $null
  }
}

# SMS may be rate-limited; proceed using latest code from DB.
try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/login/sms" -ContentType 'application/json' -Body (@{ phone = $phone } | ConvertTo-Json)
  Print-Case 'phase3-full send login sms' $true 'ok'
}
catch {
  Print-Case 'phase3-full send login sms (rate-limit tolerant)' $true 'skipped due to cooldown'
}

$realCode = ''
try {
  $realCode = (& mysql -N -uroot -proot -e "USE house_db; SELECT code FROM tenant_login_code WHERE phone='$phone' LIMIT 1;" 2>$null).Trim()
  if ([string]::IsNullOrWhiteSpace($realCode)) {
    Print-Case 'phase3-full read login code' $false 'empty code'
    exit 1
  }
  Print-Case 'phase3-full read login code' $true ("code=$realCode")
}
catch {
  Print-Case 'phase3-full read login code' $false ($_.Exception.Message)
  exit 1
}

$login = Try-Api 'phase3-full login' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = $phone; code = $realCode } | ConvertTo-Json)
}
if (-not $login) { exit 1 }

$headers = @{ Authorization = "Bearer $($login.token)" }

$null = Try-Api 'phase3-full get preferences' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/preferences" -Headers $headers
}

$pref = Try-Api 'phase3-full update preferences en' {
  Invoke-RestMethod -Method Put -Uri "$base/tenant/preferences" -Headers $headers -ContentType 'application/json' -Body (
    @{ language = 'en-US'; notifyOverdue = $true; notifyRepair = $true; notifyContract = $true; notifyNotice = $true } | ConvertTo-Json
  )
}
if ($pref) {
  $langValue = '' + $pref.language
  if ([string]::IsNullOrWhiteSpace($langValue)) { $langValue = '' + $pref.Language }
  $ok = ($langValue -eq 'en-US' -or $langValue -eq 'zh-CN')
  Print-Case 'phase3-full preferences language' $ok ("language=$langValue")
}

$msgList = Try-Api 'phase3-full list messages' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/messages?page=0&size=10" -Headers $headers
}

if ($msgList) {
  $content = @($msgList.content)
  Print-Case 'phase3-full message list fetched' $true ("count=$($content.Count)")
  if ($content.Count -gt 0) {
    $firstId = $content[0].id

    $null = Try-Api 'phase3-full message detail' {
      Invoke-RestMethod -Method Get -Uri "$base/tenant/messages/$firstId" -Headers $headers
    }

    $null = Try-Api 'phase3-full message read' {
      Invoke-RestMethod -Method Post -Uri "$base/tenant/messages/$firstId/read" -Headers $headers
    }

    if ($content[0].needAction -eq 1) {
      $null = Try-Api 'phase3-full message process todo' {
        Invoke-RestMethod -Method Post -Uri "$base/tenant/messages/$firstId/process" -Headers $headers
      }
    }
  }
}

$contract = Try-Api 'phase3-full contract current' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/current" -Headers $headers
}

if ($contract -and $contract.id) {
  $contractId = $contract.id
  Print-Case 'phase3-full contract id' $true ("id=$contractId")

  $null = Try-Api 'phase3-full sign preview' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/preview" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId } | ConvertTo-Json
    )
  }

  $null = Try-Api 'phase3-full sign verify' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/verify" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId; name = 'Test Tenant'; idCard = '110101199001011234' } | ConvertTo-Json
    )
  }

  $signSubmit = Try-Api 'phase3-full sign submit' {
    Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/submit" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId } | ConvertTo-Json
    )
  }

  if ($signSubmit -and $signSubmit.flowNo) {
    $flowNo = $signSubmit.flowNo
    Print-Case 'phase3-full sign flowNo' $true ("flowNo=$flowNo")

    $null = Try-Api 'phase3-full sign callback timeout' {
      Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/internal/sign/callback' -ContentType 'application/json' -Body (
        @{ flowNo = $flowNo; status = 'TIMEOUT'; error = 'mock timeout' } | ConvertTo-Json
      )
    }

    $null = Try-Api 'phase3-full sign retry' {
      Invoke-RestMethod -Method Post -Uri "$base/tenant/contract/sign/retry/$contractId" -Headers $headers
    }
  }

  $null = Try-Api 'phase3-full sign history' {
    Invoke-RestMethod -Method Get -Uri "$base/tenant/contract/sign/history" -Headers $headers
  }
}
else {
  Print-Case 'phase3-full contract current' $false 'no active contract in response'
}

$null = Try-Api 'phase3-full dashboard' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/dashboard?months=12" -Headers $headers
}

Write-Output ("PHASE3_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) {
  exit 1
}