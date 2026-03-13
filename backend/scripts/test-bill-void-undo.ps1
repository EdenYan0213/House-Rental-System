param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Username = "admin",
  [string]$Password = "admin123",
  [long]$BillId = 0,
  [switch]$GenerateIfMissing = $true
)

$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )
  if (-not $Condition) {
    throw $Message
  }
}

function Get-BillDetailStatus {
  param(
    [string]$BaseUrl,
    [hashtable]$Headers,
    [long]$Id
  )
  $detail = Invoke-RestMethod -Method Get -Uri ("$BaseUrl/api/bills/" + $Id) -Headers $Headers
  return $detail.data.bill.status
}

try {
  Write-Output "[1/6] Login..."
  $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
  $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginBody
  Assert-True (-not [string]::IsNullOrWhiteSpace($login.token)) "Login failed: token is empty"
  $headers = @{ Authorization = "Bearer $($login.token)" }

  Write-Output "[2/6] Load bills..."
  $bills = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/bills" -Headers $headers
  if (($bills -eq $null -or $bills.Count -eq 0) -and $GenerateIfMissing) {
    Write-Output "No bills found, generating..."
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/bills/generate" -Headers $headers | Out-Null
    $bills = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/bills" -Headers $headers
  }

  $candidate = $null
  if ($BillId -gt 0) {
    $candidate = $bills | Where-Object { [long]$_.id -eq $BillId } | Select-Object -First 1
    Assert-True ($null -ne $candidate) "BillId=$BillId not found"
  } else {
    $candidate = $bills | Where-Object {
      $s = ("" + $_.status).ToUpper()
      $s -eq "PENDING" -or $s -eq "PARTIAL"
    } | Select-Object -First 1
  }

  Assert-True ($null -ne $candidate) "No PENDING/PARTIAL bill available for test"
  $targetBillId = [long]$candidate.id
  Write-Output ("Selected billId=" + $targetBillId + " status=" + $candidate.status)

  Write-Output "[3/6] Capture status before void..."
  $beforeStatus = Get-BillDetailStatus -BaseUrl $BaseUrl -Headers $headers -Id $targetBillId

  Write-Output "[4/6] Void bill..."
  $voidResp = Invoke-RestMethod -Method Put -Uri ("$BaseUrl/api/bills/" + $targetBillId + "/void") -Headers $headers
  Assert-True ($null -ne $voidResp.undoSnapshotId) "Void API did not return undoSnapshotId"

  $voidStatus = Get-BillDetailStatus -BaseUrl $BaseUrl -Headers $headers -Id $targetBillId
  $voidUpper = ("" + $voidStatus).ToUpper()
  Assert-True (($voidUpper -eq "VOID") -or ($voidUpper -eq "OBSOLETE") -or ($voidStatus -eq "作废")) ("Unexpected void status: " + $voidStatus)

  Write-Output "[5/6] Recover snapshot..."
  $snapshotId = [long]$voidResp.undoSnapshotId
  $recoverResp = Invoke-RestMethod -Method Post -Uri ("$BaseUrl/api/operation-snapshots/" + $snapshotId + "/recover") -Headers $headers
  Assert-True (("" + $recoverResp.status).ToUpper() -eq "RECOVERED") "Recover API did not return RECOVERED"

  Write-Output "[6/6] Verify rollback status..."
  $afterRecoverStatus = Get-BillDetailStatus -BaseUrl $BaseUrl -Headers $headers -Id $targetBillId
  Assert-True ($afterRecoverStatus -eq $beforeStatus) ("Rollback failed: before=" + $beforeStatus + ", after=" + $afterRecoverStatus)

  Write-Output ""
  Write-Output "PASS"
  Write-Output ("billId=" + $targetBillId)
  Write-Output ("beforeStatus=" + $beforeStatus)
  Write-Output ("voidStatus=" + $voidStatus)
  Write-Output ("recoverStatus=" + $afterRecoverStatus)
  Write-Output ("snapshotId=" + $snapshotId)
  exit 0
}
catch {
  Write-Output ""
  Write-Output "FAIL"
  Write-Output $_.Exception.Message
  exit 1
}
