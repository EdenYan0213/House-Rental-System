$base = 'http://localhost:8080/api'

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

# 1) login by sms
$null = Try-Api 'phase2 send login sms' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login/sms" -ContentType 'application/json' -Body (@{ phone = '13900009999' } | ConvertTo-Json)
}

$realCode = ''
try {
  $realCode = (& mysql -N -uroot -proot -e "USE house_db; SELECT code FROM tenant_login_code WHERE phone='13900009999' LIMIT 1;").Trim()
} catch {
  Print-Case 'phase2 read login code' $false ($_.Exception.Message)
}
if ([string]::IsNullOrWhiteSpace($realCode)) {
  Print-Case 'phase2 read login code' $false 'empty code'
  exit 1
}
Print-Case 'phase2 read login code' $true ('code=' + $realCode)

$login = Try-Api 'phase2 login' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = '13900009999'; code = $realCode } | ConvertTo-Json)
}
if (-not $login) { exit 1 }

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }

# 2) repair flow
$repairCreated = Try-Api 'repair create' {
  Invoke-RestMethod -Method Post -Uri "$base/repair/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ faultType = '水电故障'; description = '二期冒烟：水龙头漏水'; phone = '13900009999'; images = @() } | ConvertTo-Json
  )
}

$repairId = $null
if ($repairCreated) { $repairId = $repairCreated.id }

$repairList = Try-Api 'repair list' {
  Invoke-RestMethod -Method Get -Uri "$base/repair/list?page=0&size=10" -Headers $headers
}
if ($repairList) {
  $count = @($repairList.content).Count
  Print-Case 'repair list count' $true ("count=$count")
}

if ($repairId) {
  $repairDetail = Try-Api 'repair detail' {
    Invoke-RestMethod -Method Get -Uri "$base/repair/detail/$repairId" -Headers $headers
  }
  if ($repairDetail) {
    Print-Case 'repair detail status' $true ("status=" + $repairDetail.status)
  }

  $repairConfirmFailed = $false
  $repairConfirmMsg = ''
  try {
    $null = Invoke-RestMethod -Method Post -Uri "$base/repair/confirm/$repairId" -Headers $headers -ContentType 'application/json' -Body (@{ rating = 5; comment = '完成良好' } | ConvertTo-Json)
    $repairConfirmFailed = $false
    $repairConfirmMsg = 'unexpected success'
  } catch {
    $repairConfirmFailed = $true
    try { $repairConfirmMsg = $_.ErrorDetails.Message } catch { $repairConfirmMsg = $_.Exception.Message }
  }
  Print-Case 'repair confirm before pending_accept(expected fail)' $repairConfirmFailed $repairConfirmMsg
}

# 3) complaint flow
$complaintCreated = Try-Api 'complaint create' {
  Invoke-RestMethod -Method Post -Uri "$base/complaint/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ type = '服务态度'; title = '二期冒烟投诉'; content = '测试投诉内容'; images = @() } | ConvertTo-Json
  )
}

$complaintId = $null
if ($complaintCreated) { $complaintId = $complaintCreated.id }

$complaintList = Try-Api 'complaint list' {
  Invoke-RestMethod -Method Get -Uri "$base/complaint/list?page=0&size=10" -Headers $headers
}
if ($complaintList) {
  $count = @($complaintList.content).Count
  Print-Case 'complaint list count' $true ("count=$count")
}

if ($complaintId) {
  $complaintDetail = Try-Api 'complaint detail' {
    Invoke-RestMethod -Method Get -Uri "$base/complaint/detail/$complaintId" -Headers $headers
  }
  if ($complaintDetail) {
    Print-Case 'complaint detail status' $true ("status=" + $complaintDetail.status)
  }

  $complaintConfirmFailed = $false
  $complaintConfirmMsg = ''
  try {
    $null = Invoke-RestMethod -Method Post -Uri "$base/complaint/confirm/$complaintId" -Headers $headers -ContentType 'application/json' -Body (@{ rating = 5; comment = '完成良好' } | ConvertTo-Json)
    $complaintConfirmFailed = $false
    $complaintConfirmMsg = 'unexpected success'
  } catch {
    $complaintConfirmFailed = $true
    try { $complaintConfirmMsg = $_.ErrorDetails.Message } catch { $complaintConfirmMsg = $_.Exception.Message }
  }
  Print-Case 'complaint confirm before pending_confirm(expected fail)' $complaintConfirmFailed $complaintConfirmMsg
}

# 4) contract and renew
$currentContract = Try-Api 'contract current' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/current" -Headers $headers
}
$contractId = $null
if ($currentContract -and $currentContract.id) {
  $contractId = $currentContract.id
  Print-Case 'contract current has id' $true ("id=$contractId")
} else {
  Print-Case 'contract current has id' $false 'no active contract in test data'
}

$null = Try-Api 'contract history' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/history" -Headers $headers
}

if ($contractId) {
  $null = Try-Api 'renew apply' {
    Invoke-RestMethod -Method Post -Uri "$base/contract/renew/apply" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId; duration = 12; remark = '二期冒烟续租申请' } | ConvertTo-Json
    )
  }
}

$null = Try-Api 'renew records' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/renew/records" -Headers $headers
}

# 5) update phone
$null = Try-Api 'update phone sms' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/update-phone/sms" -Headers $headers -ContentType 'application/json' -Body (@{ phone = '13900008888' } | ConvertTo-Json)
}

$updatePhoneFail = $false
$updatePhoneMsg = ''
try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/update-phone" -Headers $headers -ContentType 'application/json' -Body (
    @{ oldPhone = '13900009999'; newPhone = '13900008888'; code = '000000' } | ConvertTo-Json
  )
  $updatePhoneFail = $false
  $updatePhoneMsg = 'unexpected success'
} catch {
  $updatePhoneFail = $true
  try { $updatePhoneMsg = $_.ErrorDetails.Message } catch { $updatePhoneMsg = $_.Exception.Message }
}
Print-Case 'update phone with wrong code(expected fail)' $updatePhoneFail $updatePhoneMsg

# 6) feedback
$null = Try-Api 'feedback create' {
  Invoke-RestMethod -Method Post -Uri "$base/feedback/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ type = '功能建议'; content = '二期冒烟反馈'; phone = '13900009999'; images = @() } | ConvertTo-Json
  )
}
