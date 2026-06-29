param(
    [string]$GoogleServicesPath = "app/google-services.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $GoogleServicesPath)) {
    Write-Output "google-services=missing"
    exit 1
}

$json = Get-Content -Raw $GoogleServicesPath | ConvertFrom-Json
$projectId = $json.project_info.project_id
$apiKey = $json.client[0].api_key[0].current_key

Write-Output "project=$projectId"

$auth = $null
try {
    $auth = Invoke-RestMethod `
        -Method Post `
        -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey" `
        -ContentType "application/json" `
        -Body '{"returnSecureToken":true}' `
        -TimeoutSec 20
    Write-Output "anonymous-auth=ok"
} catch {
    $message = "unknown"
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        try {
            $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
            $message = $errorBody.error.message
        } catch {
            $message = "unreadable-error"
        }
    }
    Write-Output "anonymous-auth=failed:$message"
    exit 1
}

try {
    $collections = @(
        "shared_results?pageSize=1",
        "cloud_results?pageSize=1",
        "cloud_diagnostics/current"
    )
    foreach ($collection in $collections) {
        $request = [System.Net.HttpWebRequest]::Create(
            "https://firestore.googleapis.com/v1/projects/$projectId/databases/%28default%29/documents/$collection"
        )
        $request.Method = "GET"
        $request.Headers.Add("Authorization", "Bearer $($auth.idToken)")
        try {
            $response = $request.GetResponse()
            $statusCode = [int]$response.StatusCode
            $response.Close()
            if ($statusCode -ne 200) {
                throw "HTTP $statusCode"
            }
            Write-Output "firestore-read-$($collection.Split('?')[0])=ok"
        } catch [System.Net.WebException] {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404 -and $collection -eq "cloud_diagnostics/current") {
                Write-Output "firestore-read-cloud_diagnostics/current=ok-empty"
            } else {
                throw
            }
        }
    }
    Write-Output "cloud-collaboratif=ready"
    Write-Output "github-actions-cloud=ready"
} catch [System.Net.WebException] {
    $status = "unknown"
    $message = "unknown"
    if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
        $stream = $_.Exception.Response.GetResponseStream()
        if ($stream) {
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            try {
                $errorBody = $body | ConvertFrom-Json
                $message = "$($errorBody.error.status):$($errorBody.error.message)"
            } catch {
                $message = "unreadable-error"
            }
        }
    }
    Write-Output "firestore-read=failed:http-$status"
    if ($message -like "*Cloud Firestore API has not been used*" -or $message -like "*disabled*") {
        Write-Output "action=enable-firestore-api-and-create-firestore-database"
        Write-Output "url=https://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=$projectId"
    } else {
        Write-Output "firestore-error=$message"
    }
    exit 2
} finally {
    if ($auth -and $auth.idToken) {
        try {
            Invoke-RestMethod `
                -Method Post `
                -Uri "https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$apiKey" `
                -ContentType "application/json" `
                -Body (@{ idToken = $auth.idToken } | ConvertTo-Json -Compress) `
                -TimeoutSec 20 | Out-Null
            Write-Output "anonymous-auth-cleanup=ok"
        } catch {
            Write-Output "anonymous-auth-cleanup=failed"
        }
    }
}
