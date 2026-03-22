# SSE Notification Test Script
# Usage: ./test-sse.ps1

$BaseUrl = "http://localhost:8085/notification"
$UserId = 1
$GatewaySecret = "my-secret-key-1234"

Write-Host "1. Starting SSE Subscription for User $UserId..."
Write-Host "   Run this command in a SEPARATE terminal to listen for events:"
Write-Host "   curl -N -H 'X-Gateway-Secret: $GatewaySecret' -H 'X-User-Id: $UserId' $BaseUrl/subscribe/$UserId"
Write-Host ""
Write-Host "2. Sending Test Notification..."
$Body = @{
    userId = $UserId
    type = "MEETING_CREATED"
    title = "Test Notification"
    body = "This is a test message from PowerShell"
    deeplink = "myapp://test"
    resourceType = "MEETING"
    resourceId = "123"
    dedupeKey = "test-$(Get-Date -Format 'yyyyMMddHHmmss')"
    actorUserId = 99
} | ConvertTo-Json

Invoke-RestMethod -Uri "$BaseUrl/send" -Method Post -Body $Body -ContentType "application/json" -Headers @{ "X-Gateway-Secret" = $GatewaySecret; "X-User-Id" = "$UserId" }

Write-Host "Notification sent! Check the SSE terminal for output."
