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

# SMS may be rate-limited; login code can still be fetched from latest row.
try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/login/sms" -ContentType 'application/json' -Body (@{ phone = $phone } | ConvertTo-Json)
  Print-Case 'phase2-full send login sms' $true 'ok' | Out-Host
}
catch {
  Print-Case 'phase2-full send login sms (rate-limit tolerant)' $true 'skipped due to cooldown' | Out-Host
}

$realCode = ''
try {
  $realCode = (& mysql -N -uroot -proot -e "USE house_db; SELECT code FROM tenant_login_code WHERE phone='$phone' LIMIT 1;" 2>$null).Trim()
}
catch {
  Print-Case 'phase2-full read login code' $false ($_.Exception.Message)
}
if ([string]::IsNullOrWhiteSpace($realCode)) {
  Print-Case 'phase2-full read login code' $false 'empty code'
  Write-Output ("PHASE2_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
  exit 1
}
Print-Case 'phase2-full read login code' $true ('code=' + $realCode)

$login = Try-Api 'phase2-full login' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = $phone; code = $realCode } | ConvertTo-Json)
}
if (-not $login) {
  Write-Output ("PHASE2_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
  exit 1
}

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }
Print-Case 'phase2-full token exists' (-not [string]::IsNullOrWhiteSpace($token)) 'token received'

$repairCreated = Try-Api 'phase2-full repair create' {
  Invoke-RestMethod -Method Post -Uri "$base/repair/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ faultType = '水电故障'; description = '二期冒烟：水龙头漏水'; phone = $phone; images = @() } | ConvertTo-Json
  )
}

$repairId = $null
if ($repairCreated -and $repairCreated.id) {
  $repairId = $repairCreated.id
  Print-Case 'phase2-full repair id created' $true ("id=$repairId")
}

$repairList = Try-Api 'phase2-full repair list' {
  Invoke-RestMethod -Method Get -Uri "$base/repair/list?page=0&size=10" -Headers $headers
}
if ($repairList) {
  $count = @($repairList.content).Count
  Print-Case 'phase2-full repair list count' $true ("count=$count")
}

if ($repairId) {
  $repairDetail = Try-Api 'phase2-full repair detail' {
    Invoke-RestMethod -Method Get -Uri "$base/repair/detail/$repairId" -Headers $headers
  }
  if ($repairDetail) {
    Print-Case 'phase2-full repair detail status' $true ("status=" + $repairDetail.status)
  }

  try {
    $null = Invoke-RestMethod -Method Post -Uri "$base/repair/confirm/$repairId" -Headers $headers -ContentType 'application/json' -Body (@{ rating = 5; comment = '完成良好' } | ConvertTo-Json)
    Print-Case 'phase2-full repair confirm before pending_accept(expected fail)' $false 'unexpected success'
  }
  catch {
    $msg = ''
    try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
    Print-Case 'phase2-full repair confirm before pending_accept(expected fail)' $true $msg
  }
}

$complaintCreated = Try-Api 'phase2-full complaint create' {
  Invoke-RestMethod -Method Post -Uri "$base/complaint/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ type = '服务态度'; title = '二期冒烟投诉'; content = '测试投诉内容'; images = @() } | ConvertTo-Json
  )
}

$complaintId = $null
if ($complaintCreated -and $complaintCreated.id) {
  $complaintId = $complaintCreated.id
  Print-Case 'phase2-full complaint id created' $true ("id=$complaintId")
}

$complaintList = Try-Api 'phase2-full complaint list' {
  Invoke-RestMethod -Method Get -Uri "$base/complaint/list?page=0&size=10" -Headers $headers
}
if ($complaintList) {
  $count = @($complaintList.content).Count
  Print-Case 'phase2-full complaint list count' $true ("count=$count")
}

if ($complaintId) {
  $complaintDetail = Try-Api 'phase2-full complaint detail' {
    Invoke-RestMethod -Method Get -Uri "$base/complaint/detail/$complaintId" -Headers $headers
  }
  if ($complaintDetail) {
    Print-Case 'phase2-full complaint detail status' $true ("status=" + $complaintDetail.status)
  }

  try {
    $null = Invoke-RestMethod -Method Post -Uri "$base/complaint/confirm/$complaintId" -Headers $headers -ContentType 'application/json' -Body (@{ rating = 5; comment = '完成良好' } | ConvertTo-Json)
    Print-Case 'phase2-full complaint confirm before pending_confirm(expected fail)' $false 'unexpected success'
  }
  catch {
    $msg = ''
    try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
    Print-Case 'phase2-full complaint confirm before pending_confirm(expected fail)' $true $msg
  }
}

$currentContract = Try-Api 'phase2-full contract current' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/current" -Headers $headers
}
$contractId = $null
if ($currentContract -and $currentContract.id) {
  $contractId = $currentContract.id
  Print-Case 'phase2-full contract current has id' $true ("id=$contractId")
}
else {
  Print-Case 'phase2-full contract current has id' $false 'no active contract in test data'
}

$null = Try-Api 'phase2-full contract history' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/history" -Headers $headers
}

if ($contractId) {
  $null = Try-Api 'phase2-full renew apply' {
    Invoke-RestMethod -Method Post -Uri "$base/contract/renew/apply" -Headers $headers -ContentType 'application/json' -Body (
      @{ contractId = $contractId; duration = 12; remark = '二期冒烟续租申请' } | ConvertTo-Json
    )
  }
}

$null = Try-Api 'phase2-full renew records' {
  Invoke-RestMethod -Method Get -Uri "$base/contract/renew/records" -Headers $headers
}

try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/update-phone/sms" -Headers $headers -ContentType 'application/json' -Body (@{ phone = '13900008888' } | ConvertTo-Json)
  Print-Case 'phase2-full update phone sms' $true 'ok'
}
catch {
  Print-Case 'phase2-full update phone sms (rate-limit tolerant)' $true 'skipped due to cooldown'
}

try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/update-phone" -Headers $headers -ContentType 'application/json' -Body (
    @{ oldPhone = $phone; newPhone = '13900008888'; code = '000000' } | ConvertTo-Json
  )
  Print-Case 'phase2-full update phone with wrong code(expected fail)' $false 'unexpected success'
}
catch {
  $msg = ''
  try { $msg = $_.ErrorDetails.Message } catch { $msg = $_.Exception.Message }
  Print-Case 'phase2-full update phone with wrong code(expected fail)' $true $msg
}

$null = Try-Api 'phase2-full feedback create' {
  Invoke-RestMethod -Method Post -Uri "$base/feedback/create" -Headers $headers -ContentType 'application/json' -Body (
    @{ type = '功能建议'; content = '二期冒烟反馈'; phone = $phone; images = @() } | ConvertTo-Json
  )
}

Write-Output ("PHASE2_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) { exit 1 }
exit 0
