# Notification Settings Test Script
# Usage: ./test-settings.ps1

$BaseUrl = "http://localhost:8085/notification/settings"
$UserId = 1
$GatewaySecret = "my-secret-key-1234"

Write-Host "1. Getting Default Settings for User $UserId..."
Invoke-RestMethod -Uri "$BaseUrl/$UserId" -Method Get -Headers @{ "X-Gateway-Secret" = $GatewaySecret; "X-User-Id" = "$UserId" }

Write-Host "`n2. Updating Settings for User $UserId (Disable Push, Set DND)..."
$Body = @{
    isMeetingNotification = $true
    isMinutesCompletedNotification = $false
    isTeamNotification = $true
} | ConvertTo-Json

Invoke-RestMethod -Uri "$BaseUrl/$UserId" -Method Post -Body $Body -ContentType "application/json" -Headers @{ "X-Gateway-Secret" = $GatewaySecret; "X-User-Id" = "$UserId" }

Write-Host "`n3. Verifying Updated Settings..."
Invoke-RestMethod -Uri "$BaseUrl/$UserId" -Method Get -Headers @{ "X-Gateway-Secret" = $GatewaySecret; "X-User-Id" = "$UserId" }

Write-Host "`nDone."
