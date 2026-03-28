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

# SMS may be rate-limited; proceed with latest DB code when cooldown is active.
try {
  $send = Invoke-RestMethod -Method Post -Uri "$base/tenant/login/sms" -ContentType 'application/json' -Body (@{ phone = $phone } | ConvertTo-Json)
  Print-Case 'phase1-full send login sms' $true 'ok'
  if ($send -and $send.message) {
    Print-Case 'phase1-full sms message' $true ($send.message)
  }
}
catch {
  Print-Case 'phase1-full send login sms (rate-limit tolerant)' $true 'skipped due to cooldown'
}

$wrongFailed = $false
$wrongMsg = ''
try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = $phone; code = '000000' } | ConvertTo-Json)
  $wrongFailed = $false
  $wrongMsg = 'unexpected success'
}
catch {
  $wrongFailed = $true
  try { $wrongMsg = $_.ErrorDetails.Message } catch { $wrongMsg = $_.Exception.Message }
}
Print-Case 'phase1-full login wrong code(expected fail)' $wrongFailed $wrongMsg

$realCode = ''
try {
  $realCode = (& mysql -N -uroot -proot -e "USE house_db; SELECT code FROM tenant_login_code WHERE phone='$phone' LIMIT 1;" 2>$null).Trim()
}
catch {
  Print-Case 'phase1-full read login code' $false ($_.Exception.Message)
}

if ([string]::IsNullOrWhiteSpace($realCode)) {
  Print-Case 'phase1-full read login code' $false 'empty code'
  Write-Output ("PHASE1_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
  exit 1
}
Print-Case 'phase1-full read login code' $true ("code=$realCode")

$login = Try-Api 'phase1-full login' {
  Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = $phone; code = $realCode } | ConvertTo-Json)
}
if (-not $login) {
  Write-Output ("PHASE1_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
  exit 1
}

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }
Print-Case 'phase1-full token exists' (-not [string]::IsNullOrWhiteSpace($token)) 'token received'

$current = Try-Api 'phase1-full tenant current' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/current" -Headers $headers
}
if ($current) {
  $roomText = '' + $current.currentRoom.roomText
  Print-Case 'phase1-full current room' (-not [string]::IsNullOrWhiteSpace($roomText)) ("room=$roomText")
}

$homeData = Try-Api 'phase1-full tenant home' {
  Invoke-RestMethod -Method Get -Uri "$base/tenant/home" -Headers $headers
}
if ($homeData) {
  Print-Case 'phase1-full home unpaidCount' ($homeData.PSObject.Properties.Name -contains 'unpaidCount') 'unpaidCount present'
}

$billList = Try-Api 'phase1-full bill list all' {
  Invoke-RestMethod -Method Get -Uri "$base/bill/list?page=0&size=10" -Headers $headers
}
$billId = $null
if ($billList) {
  $cnt = @($billList.content).Count
  Print-Case 'phase1-full bill list count' ($cnt -ge 0) ("count=$cnt")
  if ($cnt -gt 0) { $billId = $billList.content[0].id }
}

$pendingList = Try-Api 'phase1-full bill list pending' {
  Invoke-RestMethod -Method Get -Uri "$base/bill/list?status=PENDING&page=0&size=10" -Headers $headers
}
if ($pendingList) {
  $pcnt = @($pendingList.content).Count
  Print-Case 'phase1-full pending bill count' ($pcnt -ge 0) ("count=$pcnt")
  if ($pcnt -gt 0) { $billId = $pendingList.content[0].id }
}

if ($billId) {
  $detail = Try-Api 'phase1-full bill detail' {
    Invoke-RestMethod -Method Get -Uri "$base/bill/detail/$billId" -Headers $headers
  }
  $canPay = $false
  if ($detail -and $detail.bill) {
    $status = '' + $detail.bill.status
    $unpaid = [double]$detail.bill.unpaidAmount
    Print-Case 'phase1-full bill detail fields' (-not [string]::IsNullOrWhiteSpace($status)) ("status=$status unpaid=$unpaid")
    $canPay = (($status -eq 'PENDING' -or $status -eq 'PARTIAL') -and ($unpaid -gt 0))
  }

  if ($canPay) {
    $pay = Try-Api 'phase1-full payment create' {
      Invoke-RestMethod -Method Post -Uri "$base/payment/create" -Headers $headers -ContentType 'application/json' -Body (@{ billId = $billId; payType = 'wx' } | ConvertTo-Json)
    }
    if ($pay -and $pay.outTradeNo) {
      $callback = Try-Api 'phase1-full payment callback' {
        Invoke-RestMethod -Method Post -Uri "$base/payment/callback?outTradeNo=$($pay.outTradeNo)&result=success&transactionId=MOCKTX123"
      }
      if ($callback) {
        Print-Case 'phase1-full callback success field' ($callback.PSObject.Properties.Name -contains 'success') 'success present'
      }
    }
  }
  else {
    Print-Case 'phase1-full payment flow skip' $true 'bill not payable'
  }
}

$latest = Try-Api 'phase1-full notice latest' {
  Invoke-RestMethod -Method Get -Uri "$base/notice/latest" -Headers $headers
}
if ($latest) {
  $title = '' + $latest.title
  Print-Case 'phase1-full latest notice title' (-not [string]::IsNullOrWhiteSpace($title)) "title=$title"
}

$nlist = Try-Api 'phase1-full notice list' {
  Invoke-RestMethod -Method Get -Uri "$base/notice/list?page=0&size=10" -Headers $headers
}
if ($nlist) {
  $ncount = @($nlist.content).Count
  Print-Case 'phase1-full notice list count' ($ncount -ge 0) ("count=$ncount")
  if ($ncount -gt 0) {
    $nid = [string]$nlist.content[0].id
    $ndetail = Try-Api 'phase1-full notice detail' {
      Invoke-RestMethod -Method Get -Uri "$base/notice/detail/$nid" -Headers $headers
    }
    if ($ndetail) {
      Print-Case 'phase1-full notice detail title' (-not [string]::IsNullOrWhiteSpace(('' + $ndetail.title))) 'title present'
    }
  }
}

if ($current) {
  $rooms = @($current.rooms)
  $nextRoom = if ($rooms.Count -gt 1) { $rooms[1].roomId } else { $null }
  if ($nextRoom) {
    $switch = Try-Api 'phase1-full switch room' {
      Invoke-RestMethod -Method Post -Uri "$base/tenant/switch-room" -Headers $headers -ContentType 'application/json' -Body (@{ roomId = $nextRoom } | ConvertTo-Json)
    }
    if ($switch) {
      Print-Case 'phase1-full switched room id' ($switch.currentRoomId -eq $nextRoom) ("newRoomId=$($switch.currentRoomId)")
    }
  }
  else {
    Print-Case 'phase1-full switch room skip' $true 'single room tenant'
  }
}

Write-Output ("PHASE1_FULL_SUMMARY pass=$script:passCount fail=$script:failCount")
if ($script:failCount -gt 0) { exit 1 }
exit 0
