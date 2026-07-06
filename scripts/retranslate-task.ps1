# Re-translate crawl task documents after fixing DeepL (requires logged-in admin JWT).
param(
    [Parameter(Mandatory = $true)][long]$TaskId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Token = $env:TRANSLATION_APP_TOKEN,
    [switch]$AllPages
)
$onlyFailed = -not $AllPages
$uri = "$BaseUrl/api/crawl/tasks/$TaskId/retranslate?onlyFailed=$onlyFailed"
$headers = @{}
if ($Token) { $headers.Authorization = "Bearer $Token" }
Invoke-RestMethod -Method POST -Uri $uri -Headers $headers
