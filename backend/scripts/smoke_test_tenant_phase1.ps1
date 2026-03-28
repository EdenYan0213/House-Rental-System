$base = 'http://localhost:8080/api'

function Print-Case($name, $ok, $msg) {
  if ($ok) { Write-Output "[PASS] $name - $msg" }
  else { Write-Output "[FAIL] $name - $msg" }
}

try {
  $resp = Invoke-RestMethod -Method Post -Uri "$base/tenant/login/sms" -ContentType 'application/json' -Body (@{ phone = '13900009999' } | ConvertTo-Json)
  Print-Case 'send sms code(valid phone)' $true ($resp.message)
} catch {
  Print-Case 'send sms code(valid phone)' $false ($_.Exception.Message)
}

$wrongFailed = $false
$wrongMsg = ''
try {
  $null = Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = '13900009999'; code = '000000' } | ConvertTo-Json)
  $wrongFailed = $false
  $wrongMsg = 'unexpected success'
} catch {
  $wrongFailed = $true
  try { $wrongMsg = $_.ErrorDetails.Message } catch { $wrongMsg = $_.Exception.Message }
}
Print-Case 'login with wrong code(expected fail)' $wrongFailed $wrongMsg

$realCode = ''
try {
  $realCode = (& mysql -N -uroot -proot -e "USE house_db; SELECT code FROM tenant_login_code WHERE phone='13900009999' LIMIT 1;").Trim()
} catch {
  Print-Case 'read latest sms code from db' $false ($_.Exception.Message)
}

if ([string]::IsNullOrWhiteSpace($realCode)) {
  Print-Case 'read latest sms code from db' $false 'empty code'
  exit 1
}
Print-Case 'read latest sms code from db' $true ('code=' + $realCode)

$login = $null
try {
  $login = Invoke-RestMethod -Method Post -Uri "$base/tenant/login" -ContentType 'application/json' -Body (@{ phone = '13900009999'; code = $realCode } | ConvertTo-Json)
  $roomCount = @($login.roomList).Count
  Print-Case 'login with correct code' $true ("roomCount=$roomCount currentRoomId=" + $login.currentRoomId)
} catch {
  Print-Case 'login with correct code' $false ($_.Exception.Message)
  exit 1
}

$token = $login.token
$headers = @{ Authorization = "Bearer $token" }
$current = $null

try {
  $current = Invoke-RestMethod -Method Get -Uri "$base/tenant/current" -Headers $headers
  Print-Case 'tenant current' $true ("tenant=" + $current.tenant.name + " room=" + $current.currentRoom.roomText)
} catch {
  Print-Case 'tenant current' $false ($_.Exception.Message)
}

try {
  $homeData = Invoke-RestMethod -Method Get -Uri "$base/tenant/home" -Headers $headers
  Print-Case 'tenant home' $true ("unpaidCount=" + $homeData.unpaidCount + " latestNotice=" + ($homeData.latestNotice.title))
} catch {
  Print-Case 'tenant home' $false ($_.Exception.Message)
}

$billId = $null
try {
  $bills = Invoke-RestMethod -Method Get -Uri "$base/bill/list?page=0&size=10" -Headers $headers
  $count = @($bills.content).Count
  if ($count -gt 0) { $billId = $bills.content[0].id }
  Print-Case 'bill list(all)' $true ("count=" + $count)
} catch {
  Print-Case 'bill list(all)' $false ($_.Exception.Message)
}

try {
  $pending = Invoke-RestMethod -Method Get -Uri "$base/bill/list?status=PENDING&page=0&size=10" -Headers $headers
  $pcount = @($pending.content).Count
  if ($pcount -gt 0) { $billId = $pending.content[0].id }
  Print-Case 'bill list(pending)' $true ("count=" + $pcount)
} catch {
  Print-Case 'bill list(pending)' $false ($_.Exception.Message)
}

if ($billId) {
  $billStatus = ''
  $billUnpaid = 0
  try {
    $detail = Invoke-RestMethod -Method Get -Uri "$base/bill/detail/$billId" -Headers $headers
    $billStatus = '' + $detail.bill.status
    $billUnpaid = [double]$detail.bill.unpaidAmount
    Print-Case 'bill detail' $true ("status=" + $detail.bill.status + " unpaid=" + $detail.bill.unpaidAmount)
  } catch {
    Print-Case 'bill detail' $false ($_.Exception.Message)
  }

  $outTradeNo = $null
  $canPay = ($billStatus -eq 'PENDING' -or $billStatus -eq 'PARTIAL') -and ($billUnpaid -gt 0)
  if ($canPay) {
    try {
      $pay = Invoke-RestMethod -Method Post -Uri "$base/payment/create" -Headers $headers -ContentType 'application/json' -Body (@{ billId = $billId; payType = 'wx' } | ConvertTo-Json)
      $outTradeNo = $pay.outTradeNo
      Print-Case 'payment create' $true ("outTradeNo=" + $outTradeNo + " amount=" + $pay.amount)
    } catch {
      Print-Case 'payment create' $false ($_.Exception.Message)
    }
  } else {
    Print-Case 'payment create' $true ("skip non-payable bill status=" + $billStatus + " unpaid=" + $billUnpaid)
  }

  if ($outTradeNo) {
    try {
      $callback = Invoke-RestMethod -Method Post -Uri "$base/payment/callback?outTradeNo=$outTradeNo&result=success&transactionId=MOCKTX123"
      Print-Case 'payment callback' $true ($callback.message)
    } catch {
      Print-Case 'payment callback' $false ($_.Exception.Message)
    }

    try {
      $detail2 = Invoke-RestMethod -Method Get -Uri "$base/bill/detail/$billId" -Headers $headers
      Print-Case 'bill detail after pay' $true ("status=" + $detail2.bill.status + " unpaid=" + $detail2.bill.unpaidAmount)
    } catch {
      Print-Case 'bill detail after pay' $false ($_.Exception.Message)
    }
  }
}

try {
  $latest = Invoke-RestMethod -Method Get -Uri "$base/notice/latest" -Headers $headers
  Print-Case 'notice latest' $true ("title=" + $latest.title)
} catch {
  Print-Case 'notice latest' $false ($_.Exception.Message)
}

try {
  $nlist = Invoke-RestMethod -Method Get -Uri "$base/notice/list?page=0&size=10" -Headers $headers
  $ncount = @($nlist.content).Count
  $nid = if ($ncount -gt 0) { [string]$nlist.content[0].id } else { $null }
  Print-Case 'notice list' $true ("count=" + $ncount)
  if ($nid) {
    Print-Case 'notice detail id picked' $true ("id=" + $nid)
  }
  if ($nid) {
    $ndetail = Invoke-RestMethod -Method Get -Uri "$base/notice/detail/$nid" -Headers $headers
    Print-Case 'notice detail' $true ("title=" + $ndetail.title)
  }
} catch {
  Print-Case 'notice list/detail' $false ($_.Exception.Message)
}

try {
  $rooms = @($current.rooms)
  $nextRoom = if ($rooms.Count -gt 1) { $rooms[1].roomId } else { $null }
  if ($nextRoom) {
    $switch = Invoke-RestMethod -Method Post -Uri "$base/tenant/switch-room" -Headers $headers -ContentType 'application/json' -Body (@{ roomId = $nextRoom } | ConvertTo-Json)
    Print-Case 'switch room' $true ("newRoomId=" + $switch.currentRoomId)
  } else {
    Print-Case 'switch room' $false 'no secondary room in test data'
  }
} catch {
  Print-Case 'switch room' $false ($_.Exception.Message)
}
